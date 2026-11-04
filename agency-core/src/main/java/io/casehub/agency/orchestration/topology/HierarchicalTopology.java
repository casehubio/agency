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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HierarchicalTopology implements CoordinationTopology {

    private final Map<String, AgentRole> agentRoles;
    private final Map<String, String> parentMap;

    public HierarchicalTopology(Map<String, AgentRole> agentRoles,
                                Map<String, String> parentMap) {
        this.agentRoles = Map.copyOf(Objects.requireNonNull(agentRoles));
        this.parentMap = Map.copyOf(Objects.requireNonNull(parentMap));
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.HIERARCHICAL, agentRoles);
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-HIERARCHICAL");

        var descriptors = new ArrayList<DecisionLoopDescriptor>();
        var roles       = context.descriptor().agentRoles();

        for (var entry : roles.entrySet()) {
            String  agentId = entry.getKey();
            int     depth   = computeDepth(agentId);
            boolean isLeaf  = isLeaf(agentId, roles.keySet());

            DecisionScope       scope;
            ObservationStrategy strategy;
            CommunicationMode   comms;

            if (depth == 0) {
                scope    = DecisionScope.FULL_AUTONOMY;
                strategy = ObservationStrategy.EVENT_DRIVEN;
                comms    = CommunicationMode.BROADCAST;
            } else if (isLeaf) {
                scope    = DecisionScope.STIMULUS_DRIVEN;
                strategy = ObservationStrategy.EVENT_DRIVEN;
                comms    = CommunicationMode.STIGMERGY;
            } else {
                scope    = DecisionScope.TASK_ADAPTATION;
                strategy = ObservationStrategy.TICK_DRIVEN;
                comms    = CommunicationMode.DIRECT_MESSAGING;
            }

            descriptors.add(new DecisionLoopDescriptor(
                    "hierarchical-" + agentId,
                    strategy, scope, comms,
                    depth == 0 ? BudgetPriority.RESERVED : BudgetPriority.NORMAL,
                    List.of("hierarchy-context")));
        }

        DecisionOutcomeExecutor executor = this::executeDelegated;
        return new CoordinationPlan(descriptors, executor, pool);
    }

    private int computeDepth(String agentId) {
        int depth = 0;
        String current = agentId;
        while (parentMap.containsKey(current)) {
            current = parentMap.get(current);
            depth++;
        }
        return depth;
    }

    private boolean isLeaf(String agentId, Set<String> allAgents) {
        return allAgents.stream()
            .noneMatch(other -> agentId.equals(parentMap.get(other)));
    }

    private CompletionStage<CoordinationFeedback> executeDelegated(
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
