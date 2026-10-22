package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.yaml.spec.DiversityStrategySpec;
import io.casehub.agency.prompt.DiversityStrategy;
import io.casehub.agency.prompt.optimiser.OutcomeAwareDiversityStrategy;
import io.casehub.agency.prompt.optimiser.TopNDiversityStrategy;

public class DiversityStrategyRegistry {

    public DiversityStrategy resolve(DiversityStrategySpec spec) {
        return switch (spec) {
            case DiversityStrategySpec.TopN ignored -> new TopNDiversityStrategy();
            case DiversityStrategySpec.OutcomeAware oa ->
                    new OutcomeAwareDiversityStrategy(oa.weight());
        };
    }
}
