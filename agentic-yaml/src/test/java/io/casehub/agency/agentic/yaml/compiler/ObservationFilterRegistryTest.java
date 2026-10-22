package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.perception.observation.affordance.ObservationFilter;
import io.casehub.agency.perception.observation.affordance.PerceptionFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationFilterRegistryTest {

    @Test
    void perceptionIsRegisteredByDefault() {
        var registry = new ObservationFilterRegistry();
        ObservationFilter filter = registry.resolve("perception");
        assertThat(filter).isInstanceOf(PerceptionFilter.class);
    }

    @Test
    void unknownTypeThrows() {
        var registry = new ObservationFilterRegistry();
        assertThatThrownBy(() -> registry.resolve("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void customFilterCanBeRegistered() {
        var registry = new ObservationFilterRegistry();
        registry.register("custom", () -> (sections, tags) -> sections);
        ObservationFilter filter = registry.resolve("custom");
        assertThat(filter).isNotNull();
    }
}
