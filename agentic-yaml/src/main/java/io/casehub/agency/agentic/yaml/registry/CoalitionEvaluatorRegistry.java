package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.deliberation.coalition.CapabilityCoverageEvaluator;
import io.casehub.agency.deliberation.coalition.CoalitionEvaluator;
import io.casehub.agency.core.yaml.spec.CoalitionEvaluatorSpec;

public class CoalitionEvaluatorRegistry {

    public CoalitionEvaluator resolve(CoalitionEvaluatorSpec spec) {
        return switch (spec) {
            case CoalitionEvaluatorSpec.CapabilityCoverage ignored ->
                    new CapabilityCoverageEvaluator();
        };
    }
}
