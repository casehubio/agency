package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkmind.inference.bayes.OnlineParameterUpdater;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RelevanceScorerValidationTest {

    record ScorerFixture(String name, RelevanceScorer scorer) {
        @Override public String toString() { return name; }
    }

    static Stream<ScorerFixture> scorers() {
        return Stream.of(
                new ScorerFixture("weighted", new WeightedRelevanceScorer(0.5)),
                new ScorerFixture("bjt", new BjtRelevanceScorer(RelevanceNetworks.standard(), 0.5))
        );
    }

    static Stream<ScorerFixture> freshScorers() {
        return Stream.of(
                new ScorerFixture("weighted", new WeightedRelevanceScorer(0.5)),
                new ScorerFixture("bjt", new BjtRelevanceScorer(
                        OnlineParameterUpdater.create(RelevanceNetworks.standard(), 0.95), 0.5))
        );
    }

    @Nested
    class CptCalibration {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void all_positive_evidence_produces_high_probability(ScorerFixture f) {
            assertThat(f.scorer.score(0.9, 0.9, 0.9, 0.9)).as("[%s] all-positive > 0.7", f.name).isGreaterThan(0.7);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void all_negative_evidence_produces_low_probability(ScorerFixture f) {
            assertThat(f.scorer.score(0.0, 0.0, 0.0, 0.0)).as("[%s] all-negative < 0.15", f.name).isLessThan(0.15);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void mixed_evidence_produces_intermediate(ScorerFixture f) {
            assertThat(f.scorer.score(0.8, 0.1, 0.5, 0.5)).isBetween(0.15, 0.85);
        }
    }

    @Nested
    class Monotonicity {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void increasing_overlap_never_decreases_score(ScorerFixture f) {
            double low = f.scorer.score(0.1, 0.5, 0.5, 0.5);
            double mid = f.scorer.score(0.5, 0.5, 0.5, 0.5);
            double high = f.scorer.score(0.9, 0.5, 0.5, 0.5);
            assertThat(high).isGreaterThanOrEqualTo(mid);
            assertThat(mid).isGreaterThanOrEqualTo(low);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void increasing_domain_match_never_decreases(ScorerFixture f) {
            assertThat(f.scorer.score(0.5, 0.9, 0.5, 0.5)).isGreaterThanOrEqualTo(f.scorer.score(0.5, 0.1, 0.5, 0.5));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void increasing_signal_strength_never_decreases(ScorerFixture f) {
            assertThat(f.scorer.score(0.5, 0.5, 0.9, 0.5)).isGreaterThanOrEqualTo(f.scorer.score(0.5, 0.5, 0.1, 0.5));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void increasing_history_never_decreases(ScorerFixture f) {
            assertThat(f.scorer.score(0.5, 0.5, 0.5, 0.9)).isGreaterThanOrEqualTo(f.scorer.score(0.5, 0.5, 0.5, 0.1));
        }
    }

    @Nested
    class DimensionalIndependence {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void each_dimension_has_measurable_influence(ScorerFixture f) {
            double baseline = f.scorer.score(0.1, 0.1, 0.1, 0.1);
            assertThat(f.scorer.score(0.9, 0.1, 0.1, 0.1) - baseline).as("[%s] overlap", f.name).isGreaterThan(0.03);
            assertThat(f.scorer.score(0.1, 0.9, 0.1, 0.1) - baseline).as("[%s] domain", f.name).isGreaterThan(0.03);
            assertThat(f.scorer.score(0.1, 0.1, 0.9, 0.1) - baseline).as("[%s] strength", f.name).isGreaterThan(0.03);
            assertThat(f.scorer.score(0.1, 0.1, 0.1, 0.9) - baseline).as("[%s] history", f.name).isGreaterThan(0.01);
        }
    }

    @Nested
    class DiscriminationQuality {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void three_tier_separation(ScorerFixture f) {
            double relevant = f.scorer.score(0.9, 0.9, 0.9, 0.8);
            double borderline = f.scorer.score(0.4, 0.4, 0.5, 0.3);
            double irrelevant = f.scorer.score(0.0, 0.0, 0.1, 0.0);
            assertThat(relevant).isGreaterThan(borderline);
            assertThat(borderline).isGreaterThan(irrelevant);
            assertThat(relevant - borderline).as("[%s] top gap", f.name).isGreaterThan(0.1);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void extreme_cases_classify_correctly(ScorerFixture f) {
            assertThat(f.scorer.isRelevant(0.9, 0.9, 0.9, 0.8)).isTrue();
            assertThat(f.scorer.isRelevant(0.0, 0.0, 0.0, 0.0)).isFalse();
        }
    }

    @Nested
    class OnlineLearning {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#freshScorers")
        void learning_relevant_shifts_score_up(ScorerFixture f) {
            double before = f.scorer.score(0.5, 0.5, 0.5, 0.0);
            for (int i = 0; i < 30; i++) f.scorer.learnRelevant(0.5, 0.5, 0.5);
            double after = f.scorer.score(0.5, 0.5, 0.5, 0.0);
            assertThat(after).as("[%s] should increase after relevant training", f.name).isGreaterThan(before);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#freshScorers")
        void learning_irrelevant_shifts_score_down(ScorerFixture f) {
            double before = f.scorer.score(0.5, 0.5, 0.5, 0.5);
            for (int i = 0; i < 30; i++) f.scorer.learnIrrelevant(0.5, 0.5, 0.5);
            double after = f.scorer.score(0.5, 0.5, 0.5, 0.5);
            assertThat(after).as("[%s] should decrease after irrelevant training", f.name).isLessThan(before);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#freshScorers")
        void score_never_degenerates_after_learning(ScorerFixture f) {
            for (int i = 0; i < 50; i++) f.scorer.learnRelevant(0.5, 0.5, 0.5);
            double score = f.scorer.score(0.5, 0.5, 0.5, 0.5);
            assertThat(score).as("[%s] should not degenerate", f.name).isBetween(0.01, 0.99);
        }
    }

    @Nested
    class StrategyComparison {

        @Test
        void both_agree_on_extremes() {
            var weighted = new WeightedRelevanceScorer(0.5);
            var bjt = new BjtRelevanceScorer(RelevanceNetworks.standard(), 0.5);
            assertThat(weighted.isRelevant(0.9, 0.9, 0.9, 0.9)).isTrue();
            assertThat(bjt.isRelevant(0.9, 0.9, 0.9, 0.9)).isTrue();
            assertThat(weighted.isRelevant(0.0, 0.0, 0.0, 0.0)).isFalse();
            assertThat(bjt.isRelevant(0.0, 0.0, 0.0, 0.0)).isFalse();
        }

        @Test
        void weighted_produces_smooth_gradient() {
            var scorer = new WeightedRelevanceScorer(0.5);
            double prev = scorer.score(0.0, 0.5, 0.5, 0.5);
            for (int i = 1; i <= 10; i++) {
                double current = scorer.score(i / 10.0, 0.5, 0.5, 0.5);
                assertThat(current).isGreaterThanOrEqualTo(prev);
                prev = current;
            }
        }
    }
}
