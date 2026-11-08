package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkmind.inference.bayes.OnlineParameterUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EscalationGateTest {

    EscalationGate gate;

    @BeforeEach
    void setUp() {
        var network = EscalationNetworks.standard();
        var updater = OnlineParameterUpdater.create(network);
        gate = new EscalationGate(updater, 0.6);
    }

    @Test
    void strong_conflict_scores_higher_than_weak() {
        double strong = gate.escalationProbability(0.95, 0.8, 0.8);
        double weak = gate.escalationProbability(0.0, 0.0, 0.0);
        assertThat(strong).isGreaterThan(weak);
    }

    @Test
    void probability_is_between_zero_and_one() {
        double p = gate.escalationProbability(0.5, 0.5, 0.5);
        assertThat(p).isBetween(0.0, 1.0);
    }

    @Test
    void shouldEscalate_consistent_with_probability() {
        double p = gate.escalationProbability(0.95, 0.8, 0.8);
        assertThat(gate.shouldEscalate(0.95, 0.8, 0.8)).isEqualTo(p >= 0.6);
    }

    @Test
    void learn_does_not_throw() {
        gate.learnEscalated(0.3, 0.3, 0.0);
        gate.learnNotEscalated(0.1, 0.1, 0.0);
    }

    @Test
    void full_conflict_with_contention_and_dependencies_escalates() {
        assertThat(gate.shouldEscalate(0.95, 0.95, 0.95)).isTrue();
    }
}
