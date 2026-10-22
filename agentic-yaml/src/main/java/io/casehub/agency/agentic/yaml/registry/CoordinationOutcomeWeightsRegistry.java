package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.yaml.spec.CoordinationOutcomeWeightsSpec;
import io.casehub.agency.routing.agent.CoordinationOutcomeWeights;

import java.util.Map;

public class CoordinationOutcomeWeightsRegistry {
    public CoordinationOutcomeWeights resolve(CoordinationOutcomeWeightsSpec spec) {
        var weights = Map.copyOf(spec.weights());
        return () -> weights;
    }
}
