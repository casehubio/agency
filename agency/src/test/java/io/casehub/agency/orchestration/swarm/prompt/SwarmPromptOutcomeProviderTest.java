package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwarmPromptOutcomeProviderTest {

    @Test
    void returnsEmpty_whenNoRecorders() {
        var provider = new SwarmPromptOutcomeProvider();
        assertThat(provider.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).isEmpty();
    }

    @Test
    void aggregatesOutcomesFromMultipleRecorders() {
        var provider = new SwarmPromptOutcomeProvider();

        var recorder1 = recorderWithOutcome("Swarm converged");
        var recorder2 = recorderWithOutcome("Deadlock detected");
        provider.register(recorder1);
        provider.register(recorder2);

        var outcomes = provider.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id());
        assertThat(outcomes).hasSize(2);
    }

    @Test
    void separatesOutcomesBySignature() {
        var provider = new SwarmPromptOutcomeProvider();
        provider.register(recorderWithOutcome("Swarm converged"));

        assertThat(provider.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).hasSize(1);
        assertThat(provider.outcomes(SwarmPromptSignatures.COLLECTIVE_REASONING.id())).hasSize(1);
        assertThat(provider.outcomes(SwarmPromptSignatures.PROVISIONING.id())).hasSize(1);
    }

    private SwarmOutcomeRecorder recorderWithOutcome(String reason) {
        TerminationCondition<SwarmContext> delegate = ctx ->
                new TerminationDecision.Complete(reason);

        var recorder = new SwarmOutcomeRecorder(delegate, "control");

        var swarmCtx = new SwarmContext();
        swarmCtx.onProgressUpdated(new SwarmProgress(0.9, 0.85, 0.5, Instant.now()));
        var termCtx = new TerminationContext<>(swarmCtx, 5, Duration.ofSeconds(10), List.of());
        recorder.evaluate(termCtx);

        return recorder;
    }

    @Test
    void deregister_removesRecorderOutcomes() {
        var provider = new SwarmPromptOutcomeProvider();
        var recorder = recorderWithOutcome("Swarm converged");
        provider.register(recorder);

        assertThat(provider.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).hasSize(1);

        provider.deregister(recorder);

        assertThat(provider.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).isEmpty();
    }
}
