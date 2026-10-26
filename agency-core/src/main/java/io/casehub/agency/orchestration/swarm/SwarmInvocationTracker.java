package io.casehub.agency.orchestration.swarm;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SwarmInvocationTracker {

    private final SwarmInvocationPolicy policy;
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final AtomicLong tokenCount = new AtomicLong(0);

    public SwarmInvocationTracker(SwarmInvocationPolicy policy) {
        this.policy = policy;
    }

    public boolean tryAcquire(String capability) {
        int maxNonReserved = "provisioning".equals(capability)
                ? policy.maxCallsPerExecution()
                : policy.maxCallsPerExecution() - policy.provisioningReserve();
        int acquired = callCount.getAndIncrement();
        if (acquired >= maxNonReserved) {
            callCount.decrementAndGet();
            return false;
        }
        return true;
    }

    public void recordTokens(long tokens) {
        tokenCount.addAndGet(tokens);
    }

    public boolean isTokenBudgetExhausted() {
        return tokenCount.get() >= policy.maxTokensPerExecution();
    }

    public int callsUsed() { return callCount.get(); }

    public long tokensUsed() { return tokenCount.get(); }
}
