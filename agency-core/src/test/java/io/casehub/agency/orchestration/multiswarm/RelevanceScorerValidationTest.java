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
                new ScorerFixture("bjt", new BjtRelevanceScorer(
                        OnlineParameterUpdater.create(RelevanceNetworks.standard(), 0.95), 0.5))
        );
    }

    static Stream<ScorerFixture> freshScorers() {
        return scorers();
    }

    @Nested
    class CptCalibration {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void all_positive_evidence_produces_high_probability(ScorerFixture f) {
            double score = f.scorer.score(0.9, 0.9, 0.9, 0.9);
            assertThat(score).as("[%s] all-positive should yield > 0.7", f.name).isGreaterThan(0.7);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void all_negative_evidence_produces_low_probability(ScorerFixture f) {
            double score = f.scorer.score(0.0, 0.0, 0.0, 0.0);
            assertThat(score).as("[%s] all-negative should yield < 0.15", f.name).isLessThan(0.15);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void mixed_evidence_produces_intermediate(ScorerFixture f) {
            double score = f.scorer.score(0.8, 0.1, 0.5, 0.5);
            assertThat(score).as("[%s] mixed evidence should be intermediate", f.name).isBetween(0.15, 0.85);
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
            assertThat(high).as("[%s] high >= mid overlap", f.name).isGreaterThanOrEqualTo(mid);
            assertThat(mid).as("[%s] mid >= low overlap", f.name).isGreaterThanOrEqualTo(low);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void increasing_domain_match_never_decreases_score(ScorerFixture f) {
            double low = f.scorer.score(0.5, 0.1, 0.5, 0.5);
            double high = f.scorer.score(0.5, 0.9, 0.5, 0.5);
            assertThat(high).as("[%s] high >= low domain", f.name).isGreaterThanOrEqualTo(low);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void increasing_signal_strength_never_decreases_score(ScorerFixture f) {
            double low = f.scorer.score(0.5, 0.5, 0.1, 0.5);
            double high = f.scorer.score(0.5, 0.5, 0.9, 0.5);
            assertThat(high).as("[%s] high >= low strength", f.name).isGreaterThanOrEqualTo(low);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void increasing_history_never_decreases_score(ScorerFixture f) {
            double low = f.scorer.score(0.5, 0.5, 0.5, 0.1);
            double high = f.scorer.score(0.5, 0.5, 0.5, 0.9);
            assertThat(high).as("[%s] high >= low history", f.name).isGreaterThanOrEqualTo(low);
        }
    }

    @Nested
    class DimensionalIndependence {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void each_dimension_has_measurable_influence(ScorerFixture f) {
            double baseline = f.scorer.score(0.1, 0.1, 0.1, 0.1);
            double overlapChanged = f.scorer.score(0.9, 0.1, 0.1, 0.1);
            double domainChanged = f.scorer.score(0.1, 0.9, 0.1, 0.1);
            double strengthChanged = f.scorer.score(0.1, 0.1, 0.9, 0.1);
            double historyChanged = f.scorer.score(0.1, 0.1, 0.1, 0.9);

            assertThat(overlapChanged - baseline).as("[%s] overlap influence", f.name).isGreaterThan(0.03);
            assertThat(domainChanged - baseline).as("[%s] domain influence", f.name).isGreaterThan(0.03);
            assertThat(strengthChanged - baseline).as("[%s] strength influence", f.name).isGreaterThan(0.03);
            assertThat(historyChanged - baseline).as("[%s] history influence", f.name).isGreaterThan(0.01);
        }
    }

    @Nested
    class DiscriminationQuality {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void three_tier_separation_with_meaningful_gaps(ScorerFixture f) {
            double relevant = f.scorer.score(0.9, 0.9, 0.9, 0.8);
            double borderline = f.scorer.score(0.4, 0.4, 0.5, 0.3);
            double irrelevant = f.scorer.score(0.0, 0.0, 0.1, 0.0);

            assertThat(relevant).as("[%s] relevant > borderline", f.name).isGreaterThan(borderline);
            assertThat(borderline).as("[%s] borderline > irrelevant", f.name).isGreaterThan(irrelevant);
            assertThat(relevant - borderline).as("[%s] top gap > 0.1", f.name).isGreaterThan(0.1);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#scorers")
        void extreme_cases_classify_correctly(ScorerFixture f) {
            assertThat(f.scorer.isRelevant(0.9, 0.9, 0.9, 0.8)).as("[%s] obvious relevant", f.name).isTrue();
            assertThat(f.scorer.isRelevant(0.0, 0.0, 0.0, 0.0)).as("[%s] obvious irrelevant", f.name).isFalse();
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
            assertThat(after).as("[%s] score should increase after relevant training", f.name)
                    .isGreaterThan(before);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#freshScorers")
        void learning_irrelevant_shifts_score_down(ScorerFixture f) {
            double before = f.scorer.score(0.5, 0.5, 0.5, 0.5);
            for (int i = 0; i < 30; i++) f.scorer.learnIrrelevant(0.5, 0.5, 0.5);
            double after = f.scorer.score(0.5, 0.5, 0.5, 0.5);
            assertThat(after).as("[%s] score should decrease after irrelevant training", f.name)
                    .isLessThan(before);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.casehub.agency.orchestration.multiswarm.RelevanceScorerValidationTest#freshScorers")
        void score_never_goes_to_zero_or_one_after_learning(ScorerFixture f) {
            for (int i = 0; i < 50; i++) f.scorer.learnRelevant(0.5, 0.5, 0.5);
            double afterRelevant = f.scorer.score(0.5, 0.5, 0.5, 0.5);
            assertThat(afterRelevant).as("[%s] score should not degenerate to 0 or 1", f.name)
                    .isBetween(0.01, 0.99);
        }
    }

    @Nested
    class StrategyComparison {

        @Test
        void both_strategies_agree_on_extreme_cases() {
            var weighted = new WeightedRelevanceScorer(0.5);
            var bjt = new BjtRelevanceScorer(
                    OnlineParameterUpdater.create(RelevanceNetworks.standard(), 0.95), 0.5);

            assertThat(weighted.isRelevant(0.9, 0.9, 0.9, 0.9)).isTrue();
            assertThat(bjt.isRelevant(0.9, 0.9, 0.9, 0.9)).isTrue();

            assertThat(weighted.isRelevant(0.0, 0.0, 0.0, 0.0)).isFalse();
            assertThat(bjt.isRelevant(0.0, 0.0, 0.0, 0.0)).isFalse();
        }

        @Test
        void weighted_scorer_produces_smooth_gradient() {
            var scorer = new WeightedRelevanceScorer(0.5);
            double prev = scorer.score(0.0, 0.5, 0.5, 0.5);
            for (int i = 1; i <= 10; i++) {
                double v = i / 10.0;
                double current = scorer.score(v, 0.5, 0.5, 0.5);
                assertThat(current).as("weighted should increase smoothly at overlap=%.1f", v)
                        .isGreaterThanOrEqualTo(prev);
                prev = current;
            }
        }
    }
}
