package io.casehub.agency.agentic.yaml.registry;

import io.casehub.api.spi.routing.CandidateSetStrategy;
import io.casehub.api.spi.routing.StaticSetStrategy;
import io.casehub.agency.agentic.yaml.spec.CandidateSetStrategySpec;

public class CandidateSetStrategyRegistry {

    public CandidateSetStrategy resolve(CandidateSetStrategySpec spec) {
        return switch (spec) {
            case CandidateSetStrategySpec.Static s ->
                    StaticSetStrategy.of(s.groups());
        };
    }
}
