package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.activation.ActivationRule;
import io.casehub.agency.agentic.activation.MaxIterationsGuard;
import io.casehub.agency.agentic.activation.OnExplicitDispatch;
import io.casehub.agency.agentic.yaml.spec.ActivationSpec;

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
