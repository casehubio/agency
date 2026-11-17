package io.casehub.agency.orchestration.degradation;

import java.time.Duration;
import java.util.Objects;

public record LatencyCircuitBreakerConfig(
    int breachCount,
    Duration windowDuration,
    Duration recoveryTimeout,
    int probeSuccessThreshold
) {
    public LatencyCircuitBreakerConfig {
        if (breachCount < 1)
            throw new IllegalArgumentException("breachCount must be >= 1");
        Objects.requireNonNull(windowDuration, "windowDuration must not be null");
        if (windowDuration.isNegative() || windowDuration.isZero())
            throw new IllegalArgumentException("windowDuration must be positive");
        Objects.requireNonNull(recoveryTimeout, "recoveryTimeout must not be null");
        if (recoveryTimeout.isNegative() || recoveryTimeout.isZero())
            throw new IllegalArgumentException("recoveryTimeout must be positive");
        if (probeSuccessThreshold < 1)
            throw new IllegalArgumentException("probeSuccessThreshold must be >= 1");
    }

    public static LatencyCircuitBreakerConfig defaults() {
        return new LatencyCircuitBreakerConfig(
            3, Duration.ofMinutes(2), Duration.ofSeconds(30), 2);
    }
}
