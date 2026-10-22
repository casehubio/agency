package io.casehub.agency.core.yaml.registry;

import io.casehub.api.spi.routing.CandidateSetStrategy;
import io.casehub.api.spi.routing.StaticSetStrategy;
import io.casehub.agency.core.yaml.spec.CandidateSetStrategySpec;

public class CandidateSetStrategyRegistry {

    public CandidateSetStrategy resolve(CandidateSetStrategySpec spec) {
        return switch (spec) {
            case CandidateSetStrategySpec.Static s ->
                    StaticSetStrategy.of(s.groups());
        };
    }
}
