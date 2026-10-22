package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.orchestration.activation.ActivationRule;
import io.casehub.agency.orchestration.activation.MaxIterationsGuard;
import io.casehub.agency.orchestration.activation.OnExplicitDispatch;
import io.casehub.agency.core.yaml.spec.ActivationSpec;

public class ActivationRuleRegistry {

    @SuppressWarnings("unchecked")
    public <T> ActivationRule<T> resolve(ActivationSpec spec) {
        return switch (spec) {
            case ActivationSpec.OnDispatch od -> new OnExplicitDispatch<>();
            case ActivationSpec.MaxIterationsGuard mig ->
                    new MaxIterationsGuard<>(mig.maxIterations());
        };
    }
}
