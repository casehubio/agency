package io.casehub.agency.orchestration.topology;

import io.casehub.platform.agent.budget.AutonomyBudgetPolicy;
import io.casehub.platform.agent.budget.AutonomyBudgetPool;
import io.casehub.platform.agent.budget.BudgetPriority;
import io.casehub.agency.orchestration.degradation.DegradationTrigger;
import io.casehub.agency.orchestration.degradation.LatencyDegradationTrigger;
import io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser;
import io.casehub.agency.orchestration.distributed.DistributedAgentReasoner;
import io.casehub.agency.orchestration.loop.AgentDecisionLoop;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.LoopPromptSection;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DistributedTopology implements SwarmAwareTopology {

    private static final Logger LOG = Logger.getLogger(DistributedTopology.class.getName());

    private final Map<String, AgentRole> agentRoles;
    private final DistributedAgentReasoner reasoner;
    private final AutonomyBudgetPolicy budgetPolicy;
    private final double convergenceThreshold;
    private final List<LoopPromptSection> additionalSections;
    private final Map<String, String> modelOverrides;
    private final ConcurrentHashMap<String, AgentDecisionLoop> activeLoops = new ConcurrentHashMap<>();

    public DistributedTopology(Map<String, AgentRole> agentRoles,
                               DistributedAgentReasoner reasoner,
                               AutonomyBudgetPolicy budgetPolicy,
                               double convergenceThreshold,
                               List<LoopPromptSection> additionalSections,
                               Map<String, String> modelOverrides) {
        this.agentRoles = Map.copyOf(Objects.requireNonNull(agentRoles));
        this.reasoner = Objects.requireNonNull(reasoner);
        this.budgetPolicy = Objects.requireNonNull(budgetPolicy);
        this.convergenceThreshold = convergenceThreshold;
        this.additionalSections = List.copyOf(additionalSections);
        this.modelOverrides = Map.copyOf(modelOverrides);
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.DISTRIBUTED, agentRoles);
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-DISTRIBUTED");
        var dp = context.degradationPolicy();
        var defaultPolicy = LlmInvocationPolicy.of(
                java.time.Duration.ofSeconds(5),
                java.time.Duration.ofSeconds(8),
                java.time.Duration.ofSeconds(10));

        var sectionNames = new java.util.ArrayList<>(List.of(
                "role-context", "stigmergy-signals", "peer-state", "swarm-progress"));

        var descriptors = context.descriptor().agentRoles().keySet().stream()
                .map(agentId -> new DecisionLoopDescriptor(
                        "distributed-" + agentId,
                        ObservationStrategy.EVENT_DRIVEN,
                        DecisionScope.FULL_AUTONOMY,
                        CommunicationMode.STIGMERGY,
                        BudgetPriority.NORMAL,
                        sectionNames))
                .toList();

        var triggers = new LinkedHashMap<String, DegradationTrigger>();
        var heuristics = new LinkedHashMap<String, ContentSummariser<ObservationEvent, DecisionOutcome>>();
        for (var d : descriptors) {
            triggers.put(d.name(), new LatencyDegradationTrigger(
                    defaultPolicy, dp.latencyBreachWindow(),
                    dp.latencyBreachCount(), dp.latencyBreachWindowDuration()));
            heuristics.put(d.name(), new NoOpHeuristicSummariser<>());
        }

        DecisionOutcomeExecutor executor = this::executeLocal;
        return new CoordinationPlan(descriptors, executor, pool, triggers, heuristics);
    }

    public void registerLoop(String agentId, AgentDecisionLoop loop) {
        activeLoops.put(agentId, loop);
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
        var observation = toObservationEvent(event);
        if (observation == null) return;
        for (var loop : activeLoops.values()) {
            try {
                loop.collect(observation);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to route swarm event to loop", e);
            }
        }
    }

    @Override
    public void onFeedback(CoordinationFeedback feedback) {
        if (feedback.signal() == FeedbackSignal.SCOPE_VIOLATION) {
            LOG.warning("Scope violation from agent " + feedback.agentId()
                    + ": " + feedback.outcome());
        }
    }

    public Map<String, AgentRole> agentRoles() {
        return agentRoles;
    }

    public DistributedAgentReasoner reasoner() {
        return reasoner;
    }

    public double convergenceThreshold() {
        return convergenceThreshold;
    }

    public Map<String, String> modelOverrides() {
        return modelOverrides;
    }

    private static ObservationEvent toObservationEvent(SwarmCoordinationEvent event) {
        return switch (event) {
            case SwarmCoordinationEvent.ProgressUpdated e ->
                    ObservationEvent.of("swarm-progress", e.progress());
            case SwarmCoordinationEvent.RoleChanged e ->
                    ObservationEvent.of("swarm-roles", e.roles());
            case SwarmCoordinationEvent.TeamChanged e ->
                    ObservationEvent.of("swarm-teams", e.teams());
            case SwarmCoordinationEvent.MemberCompleted e ->
                    ObservationEvent.of("member-completed", e.agentId());
            case SwarmCoordinationEvent.ProvisioningChanged e ->
                    ObservationEvent.of("provisioning-changed",
                            Map.of("currentSize", e.currentSize(), "maxSize", e.maxSize()));
            case SwarmCoordinationEvent.SwarmInitialised e ->
                    ObservationEvent.of("swarm-initialised", e.progress());
        };
    }
}
