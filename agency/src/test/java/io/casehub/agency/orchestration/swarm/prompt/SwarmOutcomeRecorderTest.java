package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwarmOutcomeRecorderTest {

    @SuppressWarnings("unchecked")
    private TerminationCondition<SwarmContext> mockDelegate() {
        return mock(TerminationCondition.class);
    }

    private TerminationContext<SwarmContext> contextWithProgress(double consensus, double stability) {
        var ctx = new SwarmContext();
        ctx.onProgressUpdated(new SwarmProgress(consensus, stability, 0.5, java.time.Instant.now()));
        return new TerminationContext<>(ctx, 5, Duration.ofSeconds(10), List.of());
    }

    @Test
    void recordsOutcome_onCompleteDecision() {
        var delegate = mockDelegate();
        when(delegate.evaluate(any())).thenReturn(
                new TerminationDecision.Complete("Swarm converged — consensus: 0.90, stability: 0.85"));

        var recorder = new SwarmOutcomeRecorder(delegate, "control");
        var decision = recorder.evaluate(contextWithProgress(0.9, 0.85));

        assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
        assertThat(recorder.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).hasSize(1);
        assertThat(recorder.outcomes(SwarmPromptSignatures.COLLECTIVE_REASONING.id())).hasSize(1);
        assertThat(recorder.outcomes(SwarmPromptSignatures.PROVISIONING.id())).hasSize(1);
    }

    @Test
    void doesNotRecord_onContinueDecision() {
        var delegate = mockDelegate();
        when(delegate.evaluate(any())).thenReturn(TerminationDecision.Continue.INSTANCE);

        var recorder = new SwarmOutcomeRecorder(delegate, "control");
        recorder.evaluate(contextWithProgress(0.5, 0.3));

        assertThat(recorder.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).isEmpty();
    }

    @Test
    void recordsOnlyOnce_evenIfEvaluatedMultipleTimes() {
        var delegate = mockDelegate();
        when(delegate.evaluate(any())).thenReturn(
                new TerminationDecision.Complete("Swarm converged"));

        var recorder = new SwarmOutcomeRecorder(delegate, "control");
        var termCtx = contextWithProgress(0.9, 0.85);

        recorder.evaluate(termCtx);
        recorder.evaluate(termCtx);

        assertThat(recorder.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).hasSize(1);
    }

    @Test
    void recordsDeadlock_onEscalateDecision() {
        var delegate = mockDelegate();
        when(delegate.evaluate(any())).thenReturn(
                new TerminationDecision.Escalate("Deadlock — stable but low consensus"));

        var recorder = new SwarmOutcomeRecorder(delegate, "experiment");
        recorder.evaluate(contextWithProgress(0.3, 0.9));

        var outcomes = recorder.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id());
        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.get(0).outcome()).isEqualTo("DEADLOCK");
    }

    @Test
    void delegatesDecisionTransparently() {
        var delegate = mockDelegate();
        var expected = TerminationDecision.Continue.INSTANCE;
        when(delegate.evaluate(any())).thenReturn(expected);

        var recorder = new SwarmOutcomeRecorder(delegate, "control");
        var result = recorder.evaluate(contextWithProgress(0.5, 0.3));

        assertThat(result).isSameAs(expected);
    }

    @Test
    void close_deregistersFromProvider() {
        var provider = new SwarmPromptOutcomeProvider();
        var delegate = mockDelegate();
        when(delegate.evaluate(any())).thenReturn(
                new TerminationDecision.Complete("Swarm converged"));

        var recorder = new SwarmOutcomeRecorder(delegate, "control", provider);
        recorder.evaluate(contextWithProgress(0.9, 0.85));

        assertThat(provider.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).hasSize(1);

        recorder.close();

        assertThat(provider.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).isEmpty();
    }
}
