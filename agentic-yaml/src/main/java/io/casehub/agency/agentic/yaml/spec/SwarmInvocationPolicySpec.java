package io.casehub.agency.core.yaml.spec;

import org.jspecify.annotations.Nullable;

public record SwarmInvocationPolicySpec(
        int maxCalls,
        long maxTokens,
        int provisioningReserve,
        @Nullable String warnAfter,
        @Nullable String errorAfter) {

    public SwarmInvocationPolicySpec {
        if (maxCalls < 1) throw new IllegalArgumentException("maxCalls must be >= 1");
        if (maxTokens < 1) throw new IllegalArgumentException("maxTokens must be >= 1");
        if (provisioningReserve < 0) throw new IllegalArgumentException("provisioningReserve must be >= 0");
    }

    public SwarmInvocationPolicySpec() {
        this(50, 500_000, 3, null, null);
    }
}
