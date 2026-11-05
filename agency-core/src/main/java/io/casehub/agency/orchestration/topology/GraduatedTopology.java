package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.degradation.CascadeThreshold;
import io.casehub.agency.orchestration.degradation.DegradationTrigger;
import io.casehub.agency.orchestration.degradation.LatencyDegradationTrigger;
import io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser;
import io.casehub.agency.orchestration.degradation.PriorityWeightedThreshold;
import io.casehub.agency.orchestration.degradation.SimpleFractionThreshold;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.orchestration.swarm.SwarmCoordinationEvent;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GraduatedTopology implements CoordinationTopology {

    private final Map<String, AgentRole> agentRoles;
    private final GraduationState graduationState;
    private final Set<String> degradedLoops = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cascadeTriggered = new AtomicBoolean(false);
    private CascadeThreshold cascadeThreshold;
    private Map<String, BudgetPriority> loopPriorities;

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
        var dp   = context.degradationPolicy();

        this.cascadeThreshold = switch (dp.thresholdType()) {
            case SIMPLE_FRACTION -> new SimpleFractionThreshold(dp.cascadeThreshold());
            case PRIORITY_WEIGHTED -> new PriorityWeightedThreshold(dp.cascadeThreshold(), dp.priorityWeights());
        };

        var descriptors = context.descriptor().agentRoles().keySet().stream()
                                 .map(agentId -> new DecisionLoopDescriptor(
                                         "graduated-" + agentId,
                                         ObservationStrategy.EVENT_DRIVEN,
                                         graduationState.scopeFor(agentId),
                                         CommunicationMode.STIGMERGY,
                                         BudgetPriority.NORMAL,
                                         List.of("graduated-context")))
                                 .toList();

        this.loopPriorities = new LinkedHashMap<>();
        for (var d : descriptors) {
            loopPriorities.put(d.name(), d.budgetPriority());
        }

        var defaultPolicy = LlmInvocationPolicy.of(java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(8), java.time.Duration.ofSeconds(10));
        var triggers   = new LinkedHashMap<String, DegradationTrigger>();
        var heuristics = new LinkedHashMap<String, ContentSummariser<ObservationEvent, DecisionOutcome>>();
        for (var d : descriptors) {
            triggers.put(d.name(), new LatencyDegradationTrigger(defaultPolicy, dp.latencyBreachWindow(), dp.latencyBreachCount(), dp.latencyBreachWindowDuration()));
            heuristics.put(d.name(), new NoOpHeuristicSummariser<>());
        }

        DecisionOutcomeExecutor executor = this::executeGraduated;
        return new CoordinationPlan(descriptors, executor, pool, triggers, heuristics);
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

    @Override
    public void onDegradation(SwarmDegradedEvent event) {
        if (event.fallbackActive()) {
            degradedLoops.add(event.capability());
        } else {
            degradedLoops.remove(event.capability());
        }
        if (event.fallbackActive() && !cascadeTriggered.get()
            && cascadeThreshold != null
            && cascadeThreshold.isBreached(degradedLoops, loopPriorities.size(), loopPriorities)
            && cascadeTriggered.compareAndSet(false, true)) {
            for (String agentId : agentRoles.keySet()) {
                graduationState.forceScope(agentId, DecisionScope.STIMULUS_DRIVEN);
            }
        }
    }


    public GraduationState graduationState() { return graduationState; }
}
