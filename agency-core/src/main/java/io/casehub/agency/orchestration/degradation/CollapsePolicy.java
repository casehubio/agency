package io.casehub.agency.orchestration.degradation;

import java.time.Duration;

@FunctionalInterface
public interface CollapsePolicy {

    boolean isCollapsed(int consecutiveDegradedCalls, long degradedDurationMs);

    static CollapsePolicy never() {
        return (calls, duration) -> false;
    }

    static CollapsePolicy afterCalls(int threshold) {
        if (threshold < 1) throw new IllegalArgumentException("threshold must be >= 1, was " + threshold);
        return (calls, duration) -> calls >= threshold;
    }

    static CollapsePolicy afterDuration(Duration threshold) {
        if (threshold.isNegative() || threshold.isZero()) throw new IllegalArgumentException("threshold must be positive");
        long thresholdMs = threshold.toMillis();
        return (calls, duration) -> duration >= thresholdMs;
    }
}
