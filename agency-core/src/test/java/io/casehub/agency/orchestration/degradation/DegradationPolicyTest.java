package io.casehub.agency.orchestration.degradation;

import io.casehub.platform.agent.budget.BudgetPriority;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DegradationPolicyTest {

    @Test
    void defaults_returnsValidPolicy() {
        var policy = DegradationPolicy.defaults();
        assertThat(policy.thresholdType()).isEqualTo(CascadeThresholdType.SIMPLE_FRACTION);
        assertThat(policy.cascadeThreshold()).isEqualTo(0.5);
        assertThat(policy.latencyBreachCount()).isEqualTo(3);
        assertThat(policy.latencyBreachWindowDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.recoveryTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.probeSuccessThreshold()).isEqualTo(2);
        assertThat(policy.priorityWeights()).containsEntry(BudgetPriority.RESERVED, 2.0);
        assertThat(policy.priorityWeights()).containsEntry(BudgetPriority.NORMAL, 1.0);
    }

    @Test
    void toLatencyConfig_extractsCorrectValues() {
        var policy = DegradationPolicy.defaults();
        var config = policy.toLatencyConfig();
        assertThat(config.breachCount()).isEqualTo(3);
        assertThat(config.windowDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.recoveryTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.probeSuccessThreshold()).isEqualTo(2);
    }

    @Test
    void rejects_zeroCascadeThreshold() {
        assertThatThrownBy(() -> new DegradationPolicy(
                CascadeThresholdType.SIMPLE_FRACTION, 0.0,
                Map.of(), 3, Duration.ofMinutes(2), Duration.ofSeconds(30), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negativeBreachWindowDuration() {
        assertThatThrownBy(() -> new DegradationPolicy(
                CascadeThresholdType.SIMPLE_FRACTION, 0.5,
                Map.of(), 3, Duration.ofMinutes(-1), Duration.ofSeconds(30), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zeroBreachCount() {
        assertThatThrownBy(() -> new DegradationPolicy(
                CascadeThresholdType.SIMPLE_FRACTION, 0.5,
                Map.of(), 0, Duration.ofMinutes(2), Duration.ofSeconds(30), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zeroRecoveryTimeout() {
        assertThatThrownBy(() -> new DegradationPolicy(
                CascadeThresholdType.SIMPLE_FRACTION, 0.5,
                Map.of(), 3, Duration.ofMinutes(2), Duration.ZERO, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zeroProbeSuccessThreshold() {
        assertThatThrownBy(() -> new DegradationPolicy(
                CascadeThresholdType.SIMPLE_FRACTION, 0.5,
                Map.of(), 3, Duration.ofMinutes(2), Duration.ofSeconds(30), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
