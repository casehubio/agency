package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.swarm.SwarmCoordinationEvent;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DistributedTopology implements CoordinationTopology {

    private final Map<String, AgentRole> agentRoles;

    public DistributedTopology(Map<String, AgentRole> agentRoles) {
        this.agentRoles = Map.copyOf(Objects.requireNonNull(agentRoles));
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.DISTRIBUTED, agentRoles);
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-DISTRIBUTED");

        var descriptors = context.descriptor().agentRoles().keySet().stream()
                                 .map(agentId -> new DecisionLoopDescriptor(
                                         "distributed-" + agentId,
                                         ObservationStrategy.EVENT_DRIVEN,
                                         DecisionScope.FULL_AUTONOMY,
                                         CommunicationMode.STIGMERGY,
                                         BudgetPriority.NORMAL,
                                         List.of("environment-observer", "peer-state")))
                                 .toList();

        DecisionOutcomeExecutor executor = this::executeLocal;
        return new CoordinationPlan(descriptors, executor, pool);
    }

    private CompletionStage<CoordinationFeedback> executeLocal(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        return switch (outcome) {
            case DecisionOutcome.ScopeGated sg ->
                CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, sg, FeedbackSignal.SCOPE_VIOLATION));
            default ->
                CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
        };
    }

    @Override
    public void onSwarmEvent(SwarmCoordinationEvent event) {
    }

    @Override
    public void onFeedback(CoordinationFeedback feedback) {
    }
}
