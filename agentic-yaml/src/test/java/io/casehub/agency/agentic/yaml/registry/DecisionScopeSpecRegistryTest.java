package io.casehub.agency.core.yaml.registry;

import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.agency.core.yaml.spec.DecisionScopeSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionScopeSpecRegistryTest {

    private final DecisionScopeSpecRegistry registry = new DecisionScopeSpecRegistry();

    @Test
    void reactive_resolves() {
        assertThat(registry.resolve(new DecisionScopeSpec.Reactive()))
                .isEqualTo(DecisionScope.STIMULUS_DRIVEN);
    }

    @Test
    void taskAdaptation_resolves() {
        assertThat(registry.resolve(new DecisionScopeSpec.TaskAdaptation()))
                .isEqualTo(DecisionScope.TASK_ADAPTATION);
    }

    @Test
    void fullAutonomy_resolves() {
        assertThat(registry.resolve(new DecisionScopeSpec.FullAutonomy()))
                .isEqualTo(DecisionScope.FULL_AUTONOMY);
    }
}
