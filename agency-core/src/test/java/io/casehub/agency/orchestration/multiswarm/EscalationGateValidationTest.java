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
                new GateFixture("bjt", new BjtEscalationGate(
                        OnlineParameterUpdater.create(EscalationNetworks.standard(), 0.95), 0.6))
        );
    }

    static Stream<GateFixture> freshGates() {
        return gates();
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
            assertThat(high).as("[%s] high conflict >= low", f.name).isGreaterThanOrEqualTo(low);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void increasing_contention_never_decreases(GateFixture f) {
            double low = f.gate.escalationProbability(0.5, 0.1, 0.5);
            double high = f.gate.escalationProbability(0.5, 0.9, 0.5);
            assertThat(high).as("[%s] high contention >= low", f.name).isGreaterThanOrEqualTo(low);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void increasing_dependency_never_decreases(GateFixture f) {
            double low = f.gate.escalationProbability(0.5, 0.5, 0.1);
            double high = f.gate.escalationProbability(0.5, 0.5, 0.9);
            assertThat(high).as("[%s] high dependency >= low", f.name).isGreaterThanOrEqualTo(low);
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

            assertThat(conflictChanged - baseline).as("[%s] conflict influence", f.name).isGreaterThan(0.03);
            assertThat(contentionChanged - baseline).as("[%s] contention influence", f.name).isGreaterThan(0.03);
            assertThat(dependencyChanged - baseline).as("[%s] dependency influence", f.name).isGreaterThan(0.03);
        }
    }

    @Nested
    class Discrimination {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#gates")
        void clear_separation_between_should_and_should_not_escalate(GateFixture f) {
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

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#freshGates")
        void learning_escalated_shifts_probability_up(GateFixture f) {
            double before = f.gate.escalationProbability(0.5, 0.5, 0.5);
            for (int i = 0; i < 30; i++) f.gate.learnEscalated(0.5, 0.5, 0.5);
            double after = f.gate.escalationProbability(0.5, 0.5, 0.5);
            assertThat(after).as("[%s] probability should increase after escalation training", f.name)
                    .isGreaterThan(before);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#freshGates")
        void learning_not_escalated_shifts_probability_down(GateFixture f) {
            double before = f.gate.escalationProbability(0.5, 0.5, 0.5);
            for (int i = 0; i < 30; i++) f.gate.learnNotEscalated(0.5, 0.5, 0.5);
            double after = f.gate.escalationProbability(0.5, 0.5, 0.5);
            assertThat(after).as("[%s] probability should decrease after not-escalated training", f.name)
                    .isLessThan(before);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.EscalationGateValidationTest#freshGates")
        void probability_never_degenerates_after_learning(GateFixture f) {
            for (int i = 0; i < 50; i++) f.gate.learnEscalated(0.5, 0.5, 0.5);
            double p = f.gate.escalationProbability(0.5, 0.5, 0.5);
            assertThat(p).as("[%s] probability should not degenerate to 0 or 1", f.name)
                    .isBetween(0.01, 0.99);
        }
    }

    @Nested
    class StrategyComparison {

        @Test
        void both_strategies_agree_on_extreme_cases() {
            var weighted = new WeightedEscalationGate(0.6);
            var bjt = new BjtEscalationGate(
                    OnlineParameterUpdater.create(EscalationNetworks.standard(), 0.95), 0.6);

            assertThat(weighted.shouldEscalate(0.95, 0.95, 0.95)).isTrue();
            assertThat(bjt.shouldEscalate(0.95, 0.95, 0.95)).isTrue();

            assertThat(weighted.shouldEscalate(0.0, 0.0, 0.0)).isFalse();
            assertThat(bjt.shouldEscalate(0.0, 0.0, 0.0)).isFalse();
        }

        @Test
        void consistency_check_across_input_space() {
            var weighted = new WeightedEscalationGate(0.6);
            var bjt = new BjtEscalationGate(
                    OnlineParameterUpdater.create(EscalationNetworks.standard(), 0.95), 0.6);

            int agreements = 0;
            int total = 0;
            for (double c = 0.0; c <= 1.0; c += 0.25) {
                for (double r = 0.0; r <= 1.0; r += 0.25) {
                    for (double d = 0.0; d <= 1.0; d += 0.25) {
                        boolean wDecision = weighted.shouldEscalate(c, r, d);
                        boolean bDecision = bjt.shouldEscalate(c, r, d);
                        if (wDecision == bDecision) agreements++;
                        total++;
                    }
                }
            }
            double agreementRate = (double) agreements / total;
            assertThat(agreementRate).as("Strategies should agree on >60%% of the input space")
                    .isGreaterThan(0.6);
        }
    }
}
