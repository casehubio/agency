package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.core.yaml.spec.DiversityStrategySpec;
import io.casehub.agency.optimisation.prompt.DiversityStrategy;
import io.casehub.agency.optimisation.prompt.optimiser.OutcomeAwareDiversityStrategy;
import io.casehub.agency.optimisation.prompt.optimiser.TopNDiversityStrategy;

public class DiversityStrategyRegistry {

    public DiversityStrategy resolve(DiversityStrategySpec spec) {
        return switch (spec) {
            case DiversityStrategySpec.TopN ignored -> new TopNDiversityStrategy();
            case DiversityStrategySpec.OutcomeAware oa ->
                    new OutcomeAwareDiversityStrategy(oa.weight());
        };
    }
}
