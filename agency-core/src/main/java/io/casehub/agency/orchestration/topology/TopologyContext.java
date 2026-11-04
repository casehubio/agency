package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.platform.agent.AgentProvider;

import java.util.Objects;
import java.util.UUID;

public record TopologyContext(
        UUID caseId,
        TopologyDescriptor descriptor,
        AgentProvider agentProvider,
        DecisionLoopFactory loopFactory,
        AutonomyBudgetPolicy budgetPolicy
) {
    public TopologyContext {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(agentProvider, "agentProvider must not be null");
        Objects.requireNonNull(loopFactory, "loopFactory must not be null");
        Objects.requireNonNull(budgetPolicy, "budgetPolicy must not be null");
    }
}
