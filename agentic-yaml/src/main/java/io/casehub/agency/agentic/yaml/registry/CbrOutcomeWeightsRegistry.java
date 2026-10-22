package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.yaml.spec.CbrOutcomeWeightsSpec;
import io.casehub.agency.routing.agent.CbrOutcomeWeights;

import java.util.Map;

public class CbrOutcomeWeightsRegistry {
    public CbrOutcomeWeights resolve(CbrOutcomeWeightsSpec spec) {
        var weights = Map.copyOf(spec.weights());
        return () -> weights;
    }
}
