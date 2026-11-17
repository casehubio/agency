package io.casehub.agency.orchestration.degradation;

import io.casehub.platform.agent.budget.BudgetPriority;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record DegradationPolicy(
        CascadeThresholdType thresholdType,
        double cascadeThreshold,
        Map<BudgetPriority, Double> priorityWeights,
        int latencyBreachCount,
        Duration latencyBreachWindowDuration,
        Duration recoveryTimeout,
        int probeSuccessThreshold
) {
    public DegradationPolicy {
        Objects.requireNonNull(thresholdType, "thresholdType must not be null");
        if (cascadeThreshold <= 0.0 || cascadeThreshold > 1.0) {
            throw new IllegalArgumentException("cascadeThreshold must be in (0.0, 1.0]");
        }
        Objects.requireNonNull(priorityWeights, "priorityWeights must not be null");
        priorityWeights = Map.copyOf(priorityWeights);
        if (latencyBreachCount < 1) {
            throw new IllegalArgumentException("latencyBreachCount must be >= 1");
        }
        Objects.requireNonNull(latencyBreachWindowDuration,
                               "latencyBreachWindowDuration must not be null");
        if (latencyBreachWindowDuration.isNegative() || latencyBreachWindowDuration.isZero()) {
            throw new IllegalArgumentException(
                    "latencyBreachWindowDuration must be positive");
        }
        Objects.requireNonNull(recoveryTimeout, "recoveryTimeout must not be null");
        if (recoveryTimeout.isNegative() || recoveryTimeout.isZero()) {
            throw new IllegalArgumentException("recoveryTimeout must be positive");
        }
        if (probeSuccessThreshold < 1) {
            throw new IllegalArgumentException("probeSuccessThreshold must be >= 1");
        }
    }

    public LatencyCircuitBreakerConfig toLatencyConfig() {
        return new LatencyCircuitBreakerConfig(
                latencyBreachCount, latencyBreachWindowDuration,
                recoveryTimeout, probeSuccessThreshold);
    }

    public static DegradationPolicy defaults() {
        return new DegradationPolicy(
                CascadeThresholdType.SIMPLE_FRACTION, 0.5,
                Map.of(BudgetPriority.RESERVED, 2.0, BudgetPriority.NORMAL, 1.0),
                3, Duration.ofMinutes(2), Duration.ofSeconds(30), 2);
    }
}
