package io.casehub.agency.orchestration.loop;

public class BudgetExhaustedException extends RuntimeException {

    private final String agentId;

    public BudgetExhaustedException(String agentId) {
        super("Budget exhausted for agent: " + agentId);
        this.agentId = agentId;
    }

    public String agentId() { return agentId; }
}
