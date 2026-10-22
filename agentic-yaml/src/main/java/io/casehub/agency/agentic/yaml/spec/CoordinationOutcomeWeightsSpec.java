package io.casehub.agency.core.yaml.spec;

import java.util.Map;
import java.util.Objects;

public record CoordinationOutcomeWeightsSpec(Map<String, Double> weights) {
    public CoordinationOutcomeWeightsSpec {
        Objects.requireNonNull(weights, "weights");
        weights = Map.copyOf(weights);
    }
}
