package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.degradation.DegradationPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record TopologyContext(
        UUID caseId,
        TopologyDescriptor descriptor,
        AgentProvider agentProvider,
        DecisionLoopFactory loopFactory,
        AutonomyBudgetPolicy budgetPolicy,
        DegradationPolicy degradationPolicy,
        @Nullable String groupId,
        @Nullable String tenancyId
) {
    public TopologyContext {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(agentProvider, "agentProvider must not be null");
        Objects.requireNonNull(loopFactory, "loopFactory must not be null");
        Objects.requireNonNull(budgetPolicy, "budgetPolicy must not be null");
        Objects.requireNonNull(degradationPolicy, "degradationPolicy must not be null");
    }

    public TopologyContext(
            UUID caseId,
            TopologyDescriptor descriptor,
            AgentProvider agentProvider,
            DecisionLoopFactory loopFactory,
            AutonomyBudgetPolicy budgetPolicy,
            DegradationPolicy degradationPolicy) {
        this(caseId, descriptor, agentProvider, loopFactory, budgetPolicy, degradationPolicy, null, null);
    }
}
