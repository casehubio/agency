package io.casehub.agency.summarisation.yaml.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldExtractSummariserTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);
    static final ObjectMapper MAPPER = new ObjectMapper();

    private static Function<String, Function<JsonNode, List<JsonNode>>> simpleJqCompiler() {
        return expression -> node -> {
            if (expression.startsWith(".") && !expression.contains("|")) {
                var fieldName = expression.substring(1);
                var field = node.get(fieldName);
                if (field != null) {
                    return List.of(field);
                }
                return List.of();
            }
            return List.of(node);
        };
    }

    @Test
    void extractsField_fromPayload() {
        var config = Map.<String, Object>of("expression", ".details");
        var summariser = FieldExtractSummariser.create(config, simpleJqCompiler());

        var payload = Map.<String, Object>of("id", "P1",
                "details", Map.of("weight", 55.0, "fragile", true));
        var batch = List.of(new LevelEvent<>(payload, 100L, LEVEL, null));

        var result = summariser.summarise(batch).toCompletableFuture().join();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("weight", 55.0).containsEntry("fragile", true);
    }

    @Test
    void missingField_skips() {
        var config = Map.<String, Object>of("expression", ".nonexistent");
        var summariser = FieldExtractSummariser.create(config, simpleJqCompiler());

        var payload = Map.<String, Object>of("id", "P1");
        var batch = List.of(new LevelEvent<>(payload, 100L, LEVEL, null));

        var result = summariser.summarise(batch).toCompletableFuture().join();
        assertThat(result).isEmpty();
    }

    @Test
    void multipleEvents_extractsFromEach() {
        var config = Map.<String, Object>of("expression", ".data");
        var summariser = FieldExtractSummariser.create(config, simpleJqCompiler());

        var batch = List.of(
                new LevelEvent<>(Map.<String, Object>of("data", Map.of("a", 1)), 100L, LEVEL, null),
                new LevelEvent<>(Map.<String, Object>of("data", Map.of("b", 2)), 200L, LEVEL, null));

        var result = summariser.summarise(batch).toCompletableFuture().join();
        assertThat(result).hasSize(2);
    }

    @Test
    void rejectsMissingExpression() {
        assertThatThrownBy(() -> FieldExtractSummariser.create(
                Map.of(), simpleJqCompiler()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
