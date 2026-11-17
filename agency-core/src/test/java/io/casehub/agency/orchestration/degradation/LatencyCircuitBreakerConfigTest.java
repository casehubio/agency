package io.casehub.agency.orchestration.degradation;

import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatencyCircuitBreakerConfigTest {

    @Test
    void defaults_returnsValidConfig() {
        var config = LatencyCircuitBreakerConfig.defaults();
        assertThat(config.breachCount()).isEqualTo(3);
        assertThat(config.windowDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.recoveryTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.probeSuccessThreshold()).isEqualTo(2);
    }

    @Test
    void rejects_zeroBreachCount() {
        assertThatThrownBy(() -> new LatencyCircuitBreakerConfig(
            0, Duration.ofMinutes(2), Duration.ofSeconds(30), 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negativeWindowDuration() {
        assertThatThrownBy(() -> new LatencyCircuitBreakerConfig(
            3, Duration.ofMinutes(-1), Duration.ofSeconds(30), 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zeroRecoveryTimeout() {
        assertThatThrownBy(() -> new LatencyCircuitBreakerConfig(
            3, Duration.ofMinutes(2), Duration.ZERO, 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zeroProbeThreshold() {
        assertThatThrownBy(() -> new LatencyCircuitBreakerConfig(
            3, Duration.ofMinutes(2), Duration.ofSeconds(30), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
