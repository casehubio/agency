package io.casehub.agency.stigmergy.llm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LlmRecommendation(
    String actionType,
    Map<String, Object> parameters,
    List<String> justifyingPatternIds,
    double confidence,
    String reasoning) {

    public LlmRecommendation {
        Objects.requireNonNull(actionType, "actionType");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
        }
        parameters = Map.copyOf(parameters);
        justifyingPatternIds = List.copyOf(justifyingPatternIds);
    }
}
