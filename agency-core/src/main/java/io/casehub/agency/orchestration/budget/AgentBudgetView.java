package io.casehub.agency.orchestration.budget;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AgentBudgetView {

    private final String agentId;
    private final AutonomyBudgetPool pool;
    private final BudgetPriority priority;
    private final int maxCallsPerAgent;
    private final long maxTokensPerAgent;
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final AtomicLong tokenCount = new AtomicLong(0);

    AgentBudgetView(String agentId, AutonomyBudgetPool pool,
                    BudgetPriority priority, AutonomyBudgetPolicy policy) {
        this.agentId = agentId;
        this.pool = pool;
        this.priority = priority;
        this.maxCallsPerAgent = policy.maxCallsPerAgent();
        this.maxTokensPerAgent = policy.maxTokensPerAgent();
    }

    public BudgetGrant tryAcquire() {
        if (tokenCount.get() >= maxTokensPerAgent) {
            return new BudgetGrant.Denied("agent token budget exhausted",
                pool.currentPressure(priority));
        }
        int acquired = callCount.getAndIncrement();
        if (acquired >= maxCallsPerAgent) {
            callCount.decrementAndGet();
            return new BudgetGrant.Denied("agent call budget exhausted",
                pool.currentPressure(priority));
        }
        BudgetGrant poolGrant = pool.tryAcquire(priority);
        if (poolGrant instanceof BudgetGrant.Denied) {
            callCount.decrementAndGet();
            return poolGrant;
        }
        return poolGrant;
    }

    public void recordTokens(String correlationId, long tokens) {
        tokenCount.addAndGet(tokens);
        pool.recordTokens(correlationId, tokens);
    }

    public boolean isBudgetExhausted() {
        return callCount.get() >= maxCallsPerAgent
               || tokenCount.get() >= maxTokensPerAgent
               || pool.callsUsed() >= pool.effectiveCallLimit(priority)
               || pool.tokensUsed() >= pool.effectiveTokenLimit(priority);
    }

    public String agentId() { return agentId; }
    public String topologyId() { return pool.topologyId(); }
    public int callsUsed() { return callCount.get(); }
    public long tokensUsed() { return tokenCount.get(); }

    public BudgetPressure localPressure() {
        double callRatio = (double) callCount.get() / maxCallsPerAgent;
        double tokenRatio = (double) tokenCount.get() / maxTokensPerAgent;
        return new BudgetPressure(Math.min(1.0, Math.max(callRatio, tokenRatio)), priority);
    }
}
