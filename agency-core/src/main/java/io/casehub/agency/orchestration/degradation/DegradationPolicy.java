package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.budget.BudgetPriority;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record DegradationPolicy(
    CascadeThresholdType thresholdType,
    double cascadeThreshold,
    Map<BudgetPriority, Double> priorityWeights,
    int latencyBreachWindow,
    int latencyBreachCount,
    Duration latencyBreachWindowDuration
) {
    public DegradationPolicy {
        Objects.requireNonNull(thresholdType, "thresholdType must not be null");
        if (cascadeThreshold <= 0.0 || cascadeThreshold > 1.0)
            throw new IllegalArgumentException("cascadeThreshold must be in (0.0, 1.0]");
        Objects.requireNonNull(priorityWeights, "priorityWeights must not be null");
        priorityWeights = Map.copyOf(priorityWeights);
        if (latencyBreachWindow < 1)
            throw new IllegalArgumentException("latencyBreachWindow must be >= 1");
        if (latencyBreachCount < 1 || latencyBreachCount > latencyBreachWindow)
            throw new IllegalArgumentException(
                "latencyBreachCount must be in [1, latencyBreachWindow]");
        Objects.requireNonNull(latencyBreachWindowDuration,
            "latencyBreachWindowDuration must not be null");
        if (latencyBreachWindowDuration.isNegative() || latencyBreachWindowDuration.isZero())
            throw new IllegalArgumentException(
                "latencyBreachWindowDuration must be positive");
    }

    public static DegradationPolicy defaults() {
        return new DegradationPolicy(
            CascadeThresholdType.SIMPLE_FRACTION, 0.5,
            Map.of(BudgetPriority.RESERVED, 2.0, BudgetPriority.NORMAL, 1.0),
            5, 3, Duration.ofMinutes(2));
    }
}
