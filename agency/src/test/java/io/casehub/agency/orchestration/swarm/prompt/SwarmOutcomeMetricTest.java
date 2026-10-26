package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.VariantOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SwarmOutcomeMetricTest {

    private final SwarmOutcomeMetric metric = new SwarmOutcomeMetric();

    @Test
    void emptyOutcomes_scoresZero() {
        assertThat(metric.score(List.of())).isEqualTo(0.0);
    }

    @Test
    void allConverged_scoresOne() {
        var outcomes = List.of(
                outcome("CONVERGED"),
                outcome("CONVERGED"));
        assertThat(metric.score(outcomes)).isEqualTo(1.0);
    }

    @Test
    void allDeadlocked_scoresZero() {
        var outcomes = List.of(
                outcome("DEADLOCK"),
                outcome("DEADLOCK"));
        assertThat(metric.score(outcomes)).isEqualTo(0.0);
    }

    @Test
    void mixedOutcomes_scoresWeightedMean() {
        var outcomes = List.of(
                outcome("CONVERGED"),  // 1.0
                outcome("CONTINUE"),   // 0.5
                outcome("DEADLOCK"));  // 0.0
        assertThat(metric.score(outcomes)).isCloseTo(0.5, within(0.001));
    }

    @Test
    void completedOutcome_scores08() {
        var outcomes = List.of(outcome("COMPLETED"));
        assertThat(metric.score(outcomes)).isEqualTo(0.8);
    }

    @Test
    void degradedOutcome_scores025() {
        var outcomes = List.of(outcome("DEGRADED"));
        assertThat(metric.score(outcomes)).isEqualTo(0.25);
    }

    @Test
    void unknownOutcome_scoresZero() {
        var outcomes = List.of(outcome("SOMETHING_UNEXPECTED"));
        assertThat(metric.score(outcomes)).isEqualTo(0.0);
    }

    @Test
    void consensus_treatedSameAsConverged() {
        var outcomes = List.of(outcome("CONSENSUS"));
        assertThat(metric.score(outcomes)).isEqualTo(1.0);
    }

    private VariantOutcome outcome(String outcome) {
        return new VariantOutcome("v1", "swarm-team-formation", outcome, 1.0, null, Instant.now());
    }
}
