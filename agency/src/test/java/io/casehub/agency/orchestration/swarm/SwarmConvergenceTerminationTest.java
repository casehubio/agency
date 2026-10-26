package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwarmConvergenceTerminationTest {

    @Test
    void converged_whenBothMetricsAboveThreshold() {
        var term = new SwarmConvergenceTermination(0.8);
        var ctx = contextWithProgress(0.9, 0.85);
        assertThat(term.evaluate(ctx)).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void deadlock_whenStableButNoConsensus() {
        var term = new SwarmConvergenceTermination(0.8);
        var ctx = contextWithProgress(0.3, 0.9);
        var decision = term.evaluate(ctx);
        assertThat(decision).isInstanceOf(TerminationDecision.Escalate.class);
        assertThat(((TerminationDecision.Escalate) decision).reason()).contains("Deadlock");
    }

    @Test
    void continue_whenStillEvolving() {
        var term = new SwarmConvergenceTermination(0.8);
        var ctx = contextWithProgress(0.5, 0.4);
        assertThat(term.evaluate(ctx)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void continue_whenLowStabilityHighConsensus() {
        var term = new SwarmConvergenceTermination(0.8);
        var ctx = contextWithProgress(0.9, 0.3);
        assertThat(term.evaluate(ctx)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void continue_whenNoProgress() {
        var term = new SwarmConvergenceTermination(0.8);
        var context = new SwarmContext();
        var ctx = new TerminationContext<>(context, 1, Duration.ofSeconds(10), List.of());
        assertThat(term.evaluate(ctx)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThatThrownBy(() -> new SwarmConvergenceTermination(1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SwarmConvergenceTermination(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void composableWithOr() {
        var convergence = new SwarmConvergenceTermination(0.9);
        var maxIter = new io.casehub.agency.orchestration.termination.MaxIterationsTermination<SwarmContext>(5);
        var combined = convergence.or(maxIter);

        var ctx = new TerminationContext<>(new SwarmContext(), 6, Duration.ofSeconds(30), List.of());
        assertThat(combined.evaluate(ctx)).isInstanceOf(TerminationDecision.Complete.class);
    }

    private TerminationContext<SwarmContext> contextWithProgress(double consensus, double stability) {
        var progress = new SwarmProgress(0.5, consensus, stability, Instant.now());
        var context = new SwarmContext();
        context.onProgressUpdated(progress);
        return new TerminationContext<>(context, 1, Duration.ofSeconds(10), List.of());
    }
}
