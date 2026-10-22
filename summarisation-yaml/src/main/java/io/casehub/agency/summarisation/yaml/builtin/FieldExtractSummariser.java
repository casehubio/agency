package io.casehub.agency.summarisation.yaml.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.Summariser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class FieldExtractSummariser implements Summariser<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Function<JsonNode, List<JsonNode>> evaluator;

    private FieldExtractSummariser(Function<JsonNode, List<JsonNode>> evaluator) {
        this.evaluator = evaluator;
    }

    public static FieldExtractSummariser create(Map<String, Object> config,
                                                  Function<String, Function<JsonNode, List<JsonNode>>> jqCompiler) {
        var expression = (String) config.get("expression");
        if (expression == null) {
            throw new IllegalArgumentException("field-extract requires 'expression'");
        }
        return new FieldExtractSummariser(jqCompiler.apply(expression));
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<List<Map<String, Object>>> summarise(
            List<LevelEvent<Map<String, Object>>> batch) {
        var results = new ArrayList<Map<String, Object>>();
        for (var event : batch) {
            JsonNode node = MAPPER.valueToTree(event.payload());
            var extracted = evaluator.apply(node);
            for (var resultNode : extracted) {
                results.add(MAPPER.convertValue(resultNode,
                        MAPPER.getTypeFactory().constructMapType(
                                LinkedHashMap.class, String.class, Object.class)));
            }
        }
        return CompletableFuture.completedFuture(results);
    }
}
