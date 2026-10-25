package io.casehub.agency.stigmergy.llm;

import io.casehub.api.spi.observation.RuleAction;

import java.util.List;
import java.util.Objects;

public record RecommendedAction(
    RuleAction action,
    List<String> justifyingPatternIds,
    double confidence) {

    public RecommendedAction {
        Objects.requireNonNull(action, "action");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
        }
        justifyingPatternIds = List.copyOf(justifyingPatternIds);
    }
}
