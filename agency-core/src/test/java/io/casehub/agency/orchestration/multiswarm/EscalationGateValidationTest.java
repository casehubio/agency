package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EscalationGateValidationTest {

    record GateFixture(String name, EscalationGate gate) {
        @Override public String toString() { return name; }
    }

    static Stream<GateFixture> gates() {
        return Stream.of(
                new GateFixture("weighted", new WeightedEscalationGate(0.6)),
                new GateFixture("bjt", new BjtEscalationGate(EscalationNetworks.standard(), 0.6))
        );
    }

    @Nested
    class Calibration {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void all_positive_produces_high_probability(GateFixture f) {
            double p = f.gate.escalationProbability(0.95, 0.95, 0.95);
            assertThat(p).as("[%s] all-positive > 0.6", f.name).isGreaterThan(0.6);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void all_negative_produces_low_probability(GateFixture f) {
            double p = f.gate.escalationProbability(0.0, 0.0, 0.0);
            assertThat(p).as("[%s] all-negative < 0.3", f.name).isLessThan(0.3);
        }
    }

    @Nested
    class Monotonicity {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void increasing_conflict_never_decreases(GateFixture f) {
            double low = f.gate.escalationProbability(0.1, 0.5, 0.5);
            double high = f.gate.escalationProbability(0.9, 0.5, 0.5);
            assertThat(high).isGreaterThanOrEqualTo(low);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void increasing_contention_never_decreases(GateFixture f) {
            double low = f.gate.escalationProbability(0.5, 0.1, 0.5);
            double high = f.gate.escalationProbability(0.5, 0.9, 0.5);
            assertThat(high).isGreaterThanOrEqualTo(low);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void increasing_dependency_never_decreases(GateFixture f) {
            double low = f.gate.escalationProbability(0.5, 0.5, 0.1);
            double high = f.gate.escalationProbability(0.5, 0.5, 0.9);
            assertThat(high).isGreaterThanOrEqualTo(low);
        }
    }

    @Nested
    class DimensionalIndependence {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void each_dimension_has_measurable_influence(GateFixture f) {
            double baseline = f.gate.escalationProbability(0.1, 0.1, 0.1);
            double conflictChanged = f.gate.escalationProbability(0.9, 0.1, 0.1);
            double contentionChanged = f.gate.escalationProbability(0.1, 0.9, 0.1);
            double dependencyChanged = f.gate.escalationProbability(0.1, 0.1, 0.9);

            assertThat(conflictChanged - baseline).as("[%s] conflict influence > 0.03", f.name).isGreaterThan(0.03);
            assertThat(contentionChanged - baseline).as("[%s] contention influence > 0.03", f.name).isGreaterThan(0.03);
            assertThat(dependencyChanged - baseline).as("[%s] dependency influence > 0.03", f.name).isGreaterThan(0.03);
        }
    }

    @Nested
    class Discrimination {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void clear_separation_between_should_and_should_not(GateFixture f) {
            double high = f.gate.escalationProbability(0.9, 0.8, 0.8);
            double low = f.gate.escalationProbability(0.0, 0.0, 0.0);
            assertThat(high - low).as("[%s] separation gap > 0.3", f.name).isGreaterThan(0.3);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void extreme_cases_decide_correctly(GateFixture f) {
            assertThat(f.gate.shouldEscalate(0.95, 0.95, 0.95)).as("[%s] obvious escalate", f.name).isTrue();
            assertThat(f.gate.shouldEscalate(0.0, 0.0, 0.0)).as("[%s] obvious no-escalate", f.name).isFalse();
        }
    }

    @Nested
    class OnlineLearning {

        @Test
        void weighted_learning_shifts_probability_up() {
            var gate = new WeightedEscalationGate(0.6);
            double before = gate.escalationProbability(0.5, 0.5, 0.5);
            for (int i = 0; i < 30; i++) gate.learnEscalated(0.5, 0.5, 0.5);
            double after = gate.escalationProbability(0.5, 0.5, 0.5);
            assertThat(after).isGreaterThan(before);
        }

        @Test
        void weighted_learning_shifts_probability_down() {
            var gate = new WeightedEscalationGate(0.6);
            double before = gate.escalationProbability(0.5, 0.5, 0.5);
            for (int i = 0; i < 30; i++) gate.learnNotEscalated(0.5, 0.5, 0.5);
            double after = gate.escalationProbability(0.5, 0.5, 0.5);
            assertThat(after).isLessThan(before);
        }

        @Test
        void weighted_never_degenerates() {
            var gate = new WeightedEscalationGate(0.6);
            for (int i = 0; i < 50; i++) gate.learnEscalated(0.5, 0.5, 0.5);
            double p = gate.escalationProbability(0.5, 0.5, 0.5);
            assertThat(p).isBetween(0.01, 0.99);
        }

        @Test
        void bjt_learning_is_noop_pending_neocortex_856() {
            var gate = new BjtEscalationGate(EscalationNetworks.standard(), 0.6);
            double before = gate.escalationProbability(0.5, 0.5, 0.5);
            gate.learnEscalated(0.5, 0.5, 0.5);
            double after = gate.escalationProbability(0.5, 0.5, 0.5);
            assertThat(after).as("BJT learning is no-op — should not change").isEqualTo(before);
        }
    }

    @Nested
    class StrategyComparison {

        @Test
        void both_agree_on_extremes() {
            var weighted = new WeightedEscalationGate(0.6);
            var bjt = new BjtEscalationGate(EscalationNetworks.standard(), 0.6);

            assertThat(weighted.shouldEscalate(0.95, 0.95, 0.95)).isTrue();
            assertThat(bjt.shouldEscalate(0.95, 0.95, 0.95)).isTrue();

            assertThat(weighted.shouldEscalate(0.0, 0.0, 0.0)).isFalse();
            assertThat(bjt.shouldEscalate(0.0, 0.0, 0.0)).isFalse();
        }
    }
}
