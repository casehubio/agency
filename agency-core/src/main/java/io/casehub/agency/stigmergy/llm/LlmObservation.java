package io.casehub.agency.stigmergy.llm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LlmObservation(
    String patternId,
    double confidence,
    List<String> evidence,
    List<String> derivedFrom,
    Map<String, Object> details,
    String reasoning) {

    public LlmObservation {
        Objects.requireNonNull(patternId, "patternId");
        if (patternId.isBlank()) {
            throw new IllegalArgumentException("patternId must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
        }
        evidence = List.copyOf(evidence);
        derivedFrom = List.copyOf(derivedFrom);
        details = Map.copyOf(details);
    }
}
