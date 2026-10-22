package io.casehub.agency.summarisation.yaml.builtin;

import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThresholdClassifySummariserTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    @Test
    void classifies_eventsMatchingRules() {
        var config = Map.<String, Object>of("rules", List.of(
                Map.of("name", "heavy", "when", "weight > 50.0",
                        "category", "WEIGHT_MISMATCH", "severity", "HIGH")));

        var summariser = ThresholdClassifySummariser.create(config, new MvelExpressionEngine());

        var batch = List.of(
                new LevelEvent<>(Map.<String, Object>of("weight", 55.0, "id", "P1"), 100L, LEVEL, null),
                new LevelEvent<>(Map.<String, Object>of("weight", 10.0, "id", "P2"), 200L, LEVEL, null));

        var result = summariser.summarise(batch).toCompletableFuture().join();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("category", "WEIGHT_MISMATCH");
        assertThat(result.get(0)).containsEntry("severity", "HIGH");
        assertThat(result.get(0)).containsEntry("id", "P1");
        assertThat(result.get(0)).containsEntry("ruleName", "heavy");
    }

    @Test
    void multipleRules_multipleMatches() {
        var config = Map.<String, Object>of("rules", List.of(
                Map.of("name", "heavy", "when", "weight > 50.0",
                        "category", "HEAVY"),
                Map.of("name", "fast", "when", "speed > 100",
                        "category", "FAST")));

        var summariser = ThresholdClassifySummariser.create(config, new MvelExpressionEngine());

        var batch = List.of(
                new LevelEvent<>(Map.<String, Object>of("weight", 55.0, "speed", 120), 100L, LEVEL, null));

        var result = summariser.summarise(batch).toCompletableFuture().join();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("category", "HEAVY");
        assertThat(result.get(1)).containsEntry("category", "FAST");
    }

    @Test
    void noMatches_returnsEmpty() {
        var config = Map.<String, Object>of("rules", List.of(
                Map.of("name", "heavy", "when", "weight > 50.0",
                        "category", "HEAVY")));

        var summariser = ThresholdClassifySummariser.create(config, new MvelExpressionEngine());

        var batch = List.of(
                new LevelEvent<>(Map.<String, Object>of("weight", 10.0), 100L, LEVEL, null));

        var result = summariser.summarise(batch).toCompletableFuture().join();
        assertThat(result).isEmpty();
    }

    @Test
    void ruleFieldsOverridePayloadOnCollision() {
        var config = Map.<String, Object>of("rules", List.of(
                Map.of("name", "r1", "when", "weight > 0",
                        "category", "CAT", "weight", "overridden")));

        var summariser = ThresholdClassifySummariser.create(config, new MvelExpressionEngine());

        var batch = List.of(
                new LevelEvent<>(Map.<String, Object>of("weight", 55.0), 100L, LEVEL, null));

        var result = summariser.summarise(batch).toCompletableFuture().join();
        assertThat(result.get(0)).containsEntry("weight", "overridden");
    }

    @Test
    void rejectsMissingRules() {
        assertThatThrownBy(() -> ThresholdClassifySummariser.create(
                Map.of(), new MvelExpressionEngine()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
