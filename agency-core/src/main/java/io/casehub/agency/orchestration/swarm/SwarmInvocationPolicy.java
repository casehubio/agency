package io.casehub.agency.orchestration.swarm;

import java.time.Duration;

public record SwarmInvocationPolicy(
        int maxCallsPerExecution,
        long maxTokensPerExecution,
        int provisioningReserve,
        Duration warnAfter,
        Duration errorAfter) {

    public SwarmInvocationPolicy {
        if (maxCallsPerExecution < 1) throw new IllegalArgumentException("maxCallsPerExecution must be >= 1");
        if (maxTokensPerExecution < 1) throw new IllegalArgumentException("maxTokensPerExecution must be >= 1");
        if (provisioningReserve < 0) throw new IllegalArgumentException("provisioningReserve must be >= 0");
    }

    public static SwarmInvocationPolicy defaults() {
        return new SwarmInvocationPolicy(50, 500_000, 3, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }
}
