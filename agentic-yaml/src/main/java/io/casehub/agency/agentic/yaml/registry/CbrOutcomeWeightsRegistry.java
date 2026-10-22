package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.core.yaml.spec.CbrOutcomeWeightsSpec;
import io.casehub.agency.governance.routing.agent.CbrOutcomeWeights;

import java.util.Map;

public class CbrOutcomeWeightsRegistry {
    public CbrOutcomeWeights resolve(CbrOutcomeWeightsSpec spec) {
        var weights = Map.copyOf(spec.weights());
        return () -> weights;
    }
}
