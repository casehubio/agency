package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.coalition.CapabilityCoverageEvaluator;
import io.casehub.agency.agentic.coalition.CoalitionEvaluator;
import io.casehub.agency.agentic.yaml.spec.CoalitionEvaluatorSpec;

public class CoalitionEvaluatorRegistry {

    public CoalitionEvaluator resolve(CoalitionEvaluatorSpec spec) {
        return switch (spec) {
            case CoalitionEvaluatorSpec.CapabilityCoverage ignored ->
                    new CapabilityCoverageEvaluator();
        };
    }
}
