package io.casehub.agency.core.yaml.registry;

import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.agency.core.yaml.spec.DecisionScopeSpec;

public class DecisionScopeSpecRegistry {

    public DecisionScope resolve(DecisionScopeSpec spec) {
        return switch (spec) {
            case DecisionScopeSpec.Reactive r -> DecisionScope.STIMULUS_DRIVEN;
            case DecisionScopeSpec.TaskAdaptation t -> DecisionScope.TASK_ADAPTATION;
            case DecisionScopeSpec.FullAutonomy f -> DecisionScope.FULL_AUTONOMY;
        };
    }
}
