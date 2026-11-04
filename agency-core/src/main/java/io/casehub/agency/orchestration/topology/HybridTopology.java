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
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HybridTopology implements CoordinationTopology {

    private final Map<String, AgentRole> agentRoles;

    public HybridTopology(Map<String, AgentRole> agentRoles) {
        this.agentRoles = Map.copyOf(Objects.requireNonNull(agentRoles));
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.HYBRID, agentRoles);
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-HYBRID");

        var globalDescriptor = new DecisionLoopDescriptor(
                "hybrid-coordinator",
                ObservationStrategy.EVENT_DRIVEN,
                DecisionScope.FULL_AUTONOMY,
                CommunicationMode.BROADCAST,
                BudgetPriority.RESERVED,
                List.of("global-state", "coordination-strategy"));

        var localDescriptors = context.descriptor().agentRoles().entrySet().stream()
                                      .filter(e -> e.getValue().structuralRole() != StructuralRole.INITIATOR)
                                      .map(e -> new DecisionLoopDescriptor(
                                              "hybrid-local-" + e.getKey(),
                                              ObservationStrategy.TICK_DRIVEN,
                                              DecisionScope.TASK_ADAPTATION,
                                              CommunicationMode.STIGMERGY,
                                              BudgetPriority.NORMAL,
                                              List.of("local-task-context")))
                                      .toList();

        var allDescriptors = new ArrayList<DecisionLoopDescriptor>();
        allDescriptors.add(globalDescriptor);
        allDescriptors.addAll(localDescriptors);

        DecisionOutcomeExecutor executor = (agentId, outcome, ctx) -> {
            if ("hybrid-coordinator".equals(agentId)) {
                return executeCentrally(agentId, outcome);
            }
            return executeLocally(agentId, outcome);
        };

        return new CoordinationPlan(allDescriptors, executor, pool);
    }

    private CompletionStage<CoordinationFeedback> executeCentrally(
            String agentId, DecisionOutcome outcome) {
        return switch (outcome) {
            case DecisionOutcome.ScopeGated sg ->
                CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, sg, FeedbackSignal.SCOPE_VIOLATION));
            default ->
                CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
        };
    }

    private CompletionStage<CoordinationFeedback> executeLocally(
            String agentId, DecisionOutcome outcome) {
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
