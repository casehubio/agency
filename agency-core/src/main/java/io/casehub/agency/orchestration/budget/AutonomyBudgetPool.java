package io.casehub.agency.orchestration.budget;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AutonomyBudgetPool {

    private final AutonomyBudgetPolicy policy;
    private final String topologyId;
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final AtomicLong tokenCount = new AtomicLong(0);
    private final ConcurrentHashMap<String, AgentBudgetView> views = new ConcurrentHashMap<>();

    public AutonomyBudgetPool(AutonomyBudgetPolicy policy, String topologyId) {
        this.policy = policy;
        this.topologyId = topologyId;
    }

    // Token check and call increment are not atomic together — a concurrent recordTokens() can push
// tokens past the limit between the check and the grant. Acceptable for an advisory budget system.
    public BudgetGrant tryAcquire(BudgetPriority priority) {
        if (tokenCount.get() >= effectiveTokenLimit(priority)) {
            return new BudgetGrant.Denied("token budget exhausted", currentPressure(priority));
        }
        int acquired = callCount.getAndIncrement();
        if (acquired >= effectiveCallLimit(priority)) {
            callCount.decrementAndGet();
            return new BudgetGrant.Denied("call budget exhausted", currentPressure(priority));
        }
        return new BudgetGrant.Granted(currentPressure(priority), UUID.randomUUID().toString());
    }

    // correlationId reserved for telemetry correlation — passed through from AgentBudgetView
    public void recordTokens(String correlationId, long tokens) {
        tokenCount.addAndGet(tokens);
    }

    public AgentBudgetView createView(String agentId, BudgetPriority priority) {
        var view     = new AgentBudgetView(agentId, this, priority, policy);
        var existing = views.putIfAbsent(agentId, view);
        if (existing != null) {
            throw new IllegalStateException("View already exists for agent: " + agentId);
        }
        return view;
    }

    public void releaseView(String agentId) {
        views.remove(agentId);
    }

    public String topologyId() { return topologyId; }

    public BudgetPressure currentPressure() {
        return currentPressure(BudgetPriority.NORMAL);
    }

    BudgetPressure currentPressure(BudgetPriority priority) {
        double callRatio = (double) callCount.get() / policy.maxCalls();
        double tokenRatio = (double) tokenCount.get() / policy.maxTokens();
        return new BudgetPressure(Math.min(1.0, Math.max(callRatio, tokenRatio)), priority);
    }

    public int callsUsed() { return callCount.get(); }
    public long tokensUsed() { return tokenCount.get(); }

    int effectiveCallLimit(BudgetPriority priority) {
        return priority == BudgetPriority.RESERVED
            ? policy.maxCalls()
            : (int) (policy.maxCalls() * (1 - policy.reserveFraction()));
    }

    long effectiveTokenLimit(BudgetPriority priority) {
        return priority == BudgetPriority.RESERVED
            ? policy.maxTokens()
            : (long) (policy.maxTokens() * (1 - policy.reserveFraction()));
    }

    AutonomyBudgetPolicy policy() { return policy; }
}
