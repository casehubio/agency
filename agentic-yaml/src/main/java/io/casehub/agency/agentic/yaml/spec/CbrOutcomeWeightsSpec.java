package io.casehub.agency.core.yaml.spec;

import io.casehub.api.spi.routing.RoutingOutcome;

import java.util.Map;
import java.util.Objects;

public record CbrOutcomeWeightsSpec(Map<RoutingOutcome, Double> weights) {
    public CbrOutcomeWeightsSpec {
        Objects.requireNonNull(weights, "weights");
        weights = Map.copyOf(weights);
    }
}
