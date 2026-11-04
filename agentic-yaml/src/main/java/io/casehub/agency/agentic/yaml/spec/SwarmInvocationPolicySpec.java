package io.casehub.agency.core.yaml.spec;

public record SwarmInvocationPolicySpec(
        int maxCalls,
        long maxTokens,
        double reserveFraction,
        int maxCallsPerAgent,
        long maxTokensPerAgent) {

    public SwarmInvocationPolicySpec {
        if (maxCalls < 1) {throw new IllegalArgumentException("maxCalls must be >= 1");}
        if (maxTokens < 1) {throw new IllegalArgumentException("maxTokens must be >= 1");}
        if (reserveFraction < 0.0 || reserveFraction > 1.0) {
            throw new IllegalArgumentException("reserveFraction must be in [0.0, 1.0]");
        }
        if (maxCallsPerAgent < 1) {throw new IllegalArgumentException("maxCallsPerAgent must be >= 1");}
        if (maxTokensPerAgent < 1) {throw new IllegalArgumentException("maxTokensPerAgent must be >= 1");}
    }

    public SwarmInvocationPolicySpec() {
        this(50, 500_000, 0.06, 10, 50_000);
    }
}
