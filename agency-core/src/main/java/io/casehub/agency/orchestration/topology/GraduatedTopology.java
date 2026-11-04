package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.swarm.SwarmCoordinationEvent;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GraduatedTopology implements CoordinationTopology {

    private final Map<String, AgentRole> agentRoles;
    private final GraduationState graduationState;

    public GraduatedTopology(Map<String, AgentRole> agentRoles,
                             GraduationState graduationState) {
        this.agentRoles = Map.copyOf(Objects.requireNonNull(agentRoles));
        this.graduationState = Objects.requireNonNull(graduationState);
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.GRADUATED, agentRoles);
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-GRADUATED");

        var descriptors = context.descriptor().agentRoles().keySet().stream()
                                 .map(agentId -> new DecisionLoopDescriptor(
                                         "graduated-" + agentId,
                                         ObservationStrategy.EVENT_DRIVEN,
                                         graduationState.scopeFor(agentId),
                                         CommunicationMode.STIGMERGY,
                                         BudgetPriority.NORMAL,
                                         List.of("graduated-context")))
                                 .toList();

        DecisionOutcomeExecutor executor = this::executeGraduated;
        return new CoordinationPlan(descriptors, executor, pool);
    }

    private CompletionStage<CoordinationFeedback> executeGraduated(
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
        graduationState.recordFeedback(feedback.agentId(), feedback.signal());
    }

    public GraduationState graduationState() { return graduationState; }
}
