package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkmind.inference.bayes.OnlineParameterUpdater;
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

    static Stream<GateFixture> freshGates() {
        return Stream.of(
                new GateFixture("weighted", new WeightedEscalationGate(0.6)),
                new GateFixture("bjt", new BjtEscalationGate(
                        OnlineParameterUpdater.create(EscalationNetworks.standard(), 0.95), 0.6))
        );
    }

    @Nested
    class Calibration {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void all_positive_produces_high(GateFixture f) {
            assertThat(f.gate.escalationProbability(0.95, 0.95, 0.95)).as("[%s]", f.name).isGreaterThan(0.6);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void all_negative_produces_low(GateFixture f) {
            assertThat(f.gate.escalationProbability(0.0, 0.0, 0.0)).as("[%s]", f.name).isLessThan(0.3);
        }
    }

    @Nested
    class Monotonicity {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void increasing_conflict_never_decreases(GateFixture f) {
            assertThat(f.gate.escalationProbability(0.9, 0.5, 0.5))
                    .isGreaterThanOrEqualTo(f.gate.escalationProbability(0.1, 0.5, 0.5));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void increasing_contention_never_decreases(GateFixture f) {
            assertThat(f.gate.escalationProbability(0.5, 0.9, 0.5))
                    .isGreaterThanOrEqualTo(f.gate.escalationProbability(0.5, 0.1, 0.5));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void increasing_dependency_never_decreases(GateFixture f) {
            assertThat(f.gate.escalationProbability(0.5, 0.5, 0.9))
                    .isGreaterThanOrEqualTo(f.gate.escalationProbability(0.5, 0.5, 0.1));
        }
    }

    @Nested
    class DimensionalIndependence {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void each_dimension_has_influence(GateFixture f) {
            double baseline = f.gate.escalationProbability(0.1, 0.1, 0.1);
            assertThat(f.gate.escalationProbability(0.9, 0.1, 0.1) - baseline).isGreaterThan(0.03);
            assertThat(f.gate.escalationProbability(0.1, 0.9, 0.1) - baseline).isGreaterThan(0.03);
            assertThat(f.gate.escalationProbability(0.1, 0.1, 0.9) - baseline).isGreaterThan(0.03);
        }
    }

    @Nested
    class Discrimination {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void clear_separation(GateFixture f) {
            double high = f.gate.escalationProbability(0.9, 0.8, 0.8);
            double low = f.gate.escalationProbability(0.0, 0.0, 0.0);
            assertThat(high - low).as("[%s] gap > 0.3", f.name).isGreaterThan(0.3);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void extreme_cases_decide_correctly(GateFixture f) {
            assertThat(f.gate.shouldEscalate(0.95, 0.95, 0.95)).isTrue();
            assertThat(f.gate.shouldEscalate(0.0, 0.0, 0.0)).isFalse();
        }
    }

    @Nested
    class OnlineLearning {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#freshGates")
        void learning_escalated_shifts_up(GateFixture f) {
            double before = f.gate.escalationProbability(0.5, 0.5, 0.5);
            for (int i = 0; i < 30; i++) f.gate.learnEscalated(0.5, 0.5, 0.5);
            double after = f.gate.escalationProbability(0.5, 0.5, 0.5);
            assertThat(after).as("[%s] should increase", f.name).isGreaterThan(before);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#freshGates")
        void learning_not_escalated_shifts_down(GateFixture f) {
            double before = f.gate.escalationProbability(0.5, 0.5, 0.5);
            for (int i = 0; i < 30; i++) f.gate.learnNotEscalated(0.5, 0.5, 0.5);
            double after = f.gate.escalationProbability(0.5, 0.5, 0.5);
            assertThat(after).as("[%s] should decrease", f.name).isLessThan(before);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#freshGates")
        void never_degenerates(GateFixture f) {
            for (int i = 0; i < 50; i++) f.gate.learnEscalated(0.5, 0.5, 0.5);
            double p = f.gate.escalationProbability(0.5, 0.5, 0.5);
            assertThat(p).as("[%s] should not degenerate", f.name).isBetween(0.01, 0.99);
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
