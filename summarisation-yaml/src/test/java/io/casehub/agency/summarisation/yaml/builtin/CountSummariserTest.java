package io.casehub.agency.summarisation.yaml.builtin;

import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountSummariserTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    @Test
    void counts_categoriesInBatch() {
        var config = Map.<String, Object>of("category-field", "severity");
        var summariser = CountSummariser.create(config);

        var batch = List.of(
                new LevelEvent<>(Map.<String, Object>of("severity", "HIGH"), 100L, LEVEL, null),
                new LevelEvent<>(Map.<String, Object>of("severity", "LOW"), 200L, LEVEL, null),
                new LevelEvent<>(Map.<String, Object>of("severity", "HIGH"), 300L, LEVEL, null));

        var result = summariser.summarise(batch).toCompletableFuture().join();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("HIGH", 2).containsEntry("LOW", 1);
    }

    @Test
    void skipsMissingField() {
        var config = Map.<String, Object>of("category-field", "severity");
        var summariser = CountSummariser.create(config);

        var batch = List.of(
                new LevelEvent<>(Map.<String, Object>of("severity", "HIGH"), 100L, LEVEL, null),
                new LevelEvent<>(Map.<String, Object>of("other", "val"), 200L, LEVEL, null));

        var result = summariser.summarise(batch).toCompletableFuture().join();
        assertThat(result.get(0)).containsEntry("HIGH", 1).hasSize(1);
    }

    @Test
    void emptyBatch_returnsEmptyMap() {
        var config = Map.<String, Object>of("category-field", "severity");
        var summariser = CountSummariser.create(config);

        var result = summariser.summarise(List.of()).toCompletableFuture().join();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEmpty();
    }

    @Test
    void rejectsMissingCategoryField() {
        assertThatThrownBy(() -> CountSummariser.create(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
