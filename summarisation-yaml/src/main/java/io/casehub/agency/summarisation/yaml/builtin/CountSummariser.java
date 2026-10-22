package io.casehub.agency.summarisation.yaml.builtin;

import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.Summariser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CountSummariser implements Summariser<Map<String, Object>, Map<String, Object>> {

    private final String categoryField;

    private CountSummariser(String categoryField) {
        this.categoryField = categoryField;
    }

    public static CountSummariser create(Map<String, Object> config) {
        var field = (String) config.get("category-field");
        if (field == null) {
            throw new IllegalArgumentException("count requires 'category-field'");
        }
        return new CountSummariser(field);
    }

    @Override
    public CompletionStage<List<Map<String, Object>>> summarise(
            List<LevelEvent<Map<String, Object>>> batch) {
        var counts = new LinkedHashMap<String, Object>();
        for (var event : batch) {
            var value = event.payload().get(categoryField);
            if (value != null) {
                var key = value.toString();
                counts.merge(key, 1, (a, b) -> (Integer) a + (Integer) b);
            }
        }
        return CompletableFuture.completedFuture(List.of(counts));
    }
}
