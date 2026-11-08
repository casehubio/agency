package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkmind.inference.bayes.OnlineParameterUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelevanceScorerTest {

    RelevanceScorer scorer;

    @BeforeEach
    void setUp() {
        var network = RelevanceNetworks.standard();
        var updater = OnlineParameterUpdater.create(network);
        scorer = new RelevanceScorer(updater, 0.5);
    }

    @Test
    void high_overlap_scores_higher_than_low_overlap() {
        double highScore = scorer.score(0.8, 0.9, 0.95, 0.7);
        double lowScore = scorer.score(0.0, 0.0, 0.5, 0.0);
        assertThat(highScore).isGreaterThan(lowScore);
    }

    @Test
    void score_is_between_zero_and_one() {
        double score = scorer.score(0.5, 0.5, 0.5, 0.5);
        assertThat(score).isBetween(0.0, 1.0);
    }

    @Test
    void isRelevant_consistent_with_score() {
        double score = scorer.score(0.8, 0.9, 0.95, 0.7);
        assertThat(scorer.isRelevant(0.8, 0.9, 0.95, 0.7)).isEqualTo(score >= 0.5);
    }

    @Test
    void learn_does_not_throw() {
        scorer.learnRelevant(0.5, 0.5, 0.8);
        scorer.learnIrrelevant(0.1, 0.1, 0.2);
    }

    @Test
    void full_overlap_with_strong_signal_and_history_is_relevant() {
        assertThat(scorer.isRelevant(0.9, 0.9, 0.9, 0.9)).isTrue();
    }
}
