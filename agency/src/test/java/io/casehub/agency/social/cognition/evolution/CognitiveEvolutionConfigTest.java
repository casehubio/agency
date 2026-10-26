package io.casehub.agency.social.cognition.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CognitiveEvolutionConfigTest {

    @Test
    void defaults_have_expected_values() {
        var config = CognitiveEvolutionConfig.defaults();
        assertThat(config.curiosityFloor()).isEqualTo(0.3);
        assertThat(config.curiosityFullEngage()).isEqualTo(0.7);
        assertThat(config.circuitBreakerMoodEnabled()).isTrue();
    }

    @Test
    void rejects_floor_above_full_engage() {
        assertThatThrownBy(() -> new CognitiveEvolutionConfig(0.8, 0.3, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
