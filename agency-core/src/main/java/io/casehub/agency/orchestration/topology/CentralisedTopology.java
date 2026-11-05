package io.casehub.agency.orchestration.topology;

import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.budget.BudgetTelemetryEvent;
import io.casehub.agency.orchestration.degradation.CascadeDegradationTrigger;
import io.casehub.agency.orchestration.degradation.CascadeThreshold;
import io.casehub.agency.orchestration.degradation.CompositeDegradationTrigger;
import io.casehub.agency.orchestration.degradation.DegradationPolicy;
import io.casehub.agency.orchestration.degradation.DegradationTrigger;
import io.casehub.agency.orchestration.degradation.LatencyDegradationTrigger;
import io.casehub.agency.orchestration.degradation.PriorityWeightedThreshold;
import io.casehub.agency.orchestration.degradation.SimpleFractionThreshold;
import io.casehub.agency.orchestration.loop.AgentDecisionLoop;
import io.casehub.agency.orchestration.loop.DecisionContext;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.orchestration.aggregation.AggregationStrategy;
import io.casehub.agency.orchestration.routing.RoutingStrategy;
import io.casehub.agency.orchestration.swarm.SwarmCoordinationEvent;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor;
import org.jspecify.annotations.Nullable;

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
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CentralisedTopology<C> implements CoordinationTopology {

    private final C                                                              coordinationContext;
    private final RoutingStrategy<C>                                             routingStrategy;
    private final AggregationStrategy<C>                                         aggregationStrategy;
    private final TerminationCondition<C>                                        terminationCondition;
    private final List<String>                                                   promptSectionNames;
    private final SwarmProvisioningAdvisor                                       provisioningAdvisor;
    private final Supplier<List<RoutingCandidate>>                               candidateSupplier;
    private final ContentSummariser<ObservationEvent, DecisionOutcome>           routingHeuristic;
    private final ContentSummariser<ObservationEvent, DecisionOutcome>           aggregationHeuristic;
    private final ContentSummariser<ObservationEvent, DecisionOutcome>           provisioningHeuristic;
    private final @Nullable Consumer<SwarmCoordinationEvent>                     swarmEventHandler;
    private final Consumer<io.casehub.agency.orchestration.loop.DecisionLoopEvent> loopEventSink;
    private       List<AgentDecisionLoop>                                        loops = List.of();

    private final Set<String> degradedLoops = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cascadeTriggered = new AtomicBoolean(false);
    private CascadeThreshold cascadeThreshold;
    private Map<String, BudgetPriority> loopPriorities;
    private List<CascadeDegradationTrigger> cascadeTriggers;

    public CentralisedTopology(
            C coordinationContext,
            RoutingStrategy<C> routingStrategy,
            AggregationStrategy<C> aggregationStrategy,
            TerminationCondition<C> terminationCondition,
            List<String> promptSectionNames,
            SwarmProvisioningAdvisor provisioningAdvisor,
            Supplier<List<RoutingCandidate>> candidateSupplier,
            ContentSummariser<ObservationEvent, DecisionOutcome> routingHeuristic,
            ContentSummariser<ObservationEvent, DecisionOutcome> aggregationHeuristic,
            ContentSummariser<ObservationEvent, DecisionOutcome> provisioningHeuristic,
            @Nullable Consumer<SwarmCoordinationEvent> swarmEventHandler,
            Consumer<io.casehub.agency.orchestration.loop.DecisionLoopEvent> loopEventSink) {
        this.coordinationContext  = Objects.requireNonNull(coordinationContext);
        this.routingStrategy      = Objects.requireNonNull(routingStrategy);
        this.aggregationStrategy  = Objects.requireNonNull(aggregationStrategy);
        this.terminationCondition = Objects.requireNonNull(terminationCondition);
        this.promptSectionNames   = List.copyOf(promptSectionNames);
        this.provisioningAdvisor  = Objects.requireNonNull(provisioningAdvisor);
        this.candidateSupplier    = Objects.requireNonNull(candidateSupplier);
        this.routingHeuristic     = Objects.requireNonNull(routingHeuristic);
        this.aggregationHeuristic = Objects.requireNonNull(aggregationHeuristic);
        this.provisioningHeuristic = Objects.requireNonNull(provisioningHeuristic);
        this.swarmEventHandler    = swarmEventHandler;
        this.loopEventSink        = Objects.requireNonNull(loopEventSink);
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.CENTRALISED, Map.of());
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-CENTRALISED");

        if (routingStrategy instanceof io.casehub.agency.orchestration.swarm.SwarmRoutingStrategy srs) {
            srs.setTokenRecorder(t -> pool.recordTokens("routing", t));
        }
        if (aggregationStrategy instanceof io.casehub.agency.orchestration.swarm.CollectiveReasoningAggregation cra) {
            cra.setTokenRecorder(t -> pool.recordTokens("aggregation", t));
        }
        if (provisioningAdvisor instanceof io.casehub.agency.orchestration.swarm.SwarmProvisioningAdvisorImpl impl) {
            impl.setTokenRecorder(t -> pool.recordTokens("provisioning", t));
        }

        var teamFormation = new DecisionLoopDescriptor(
                "centralised-team-formation",
                ObservationStrategy.EVENT_DRIVEN,
                DecisionScope.FULL_AUTONOMY,
                CommunicationMode.BROADCAST,
                BudgetPriority.RESERVED,
                promptSectionNames);

        var collectiveReasoning = new DecisionLoopDescriptor(
                "centralised-collective-reasoning",
                ObservationStrategy.EVENT_DRIVEN,
                DecisionScope.FULL_AUTONOMY,
                CommunicationMode.BROADCAST,
                BudgetPriority.RESERVED,
                promptSectionNames);

        var provisioning = new DecisionLoopDescriptor(
                "centralised-provisioning",
                ObservationStrategy.TICK_DRIVEN,
                DecisionScope.FULL_AUTONOMY,
                CommunicationMode.BROADCAST,
                BudgetPriority.RESERVED,
                List.of());

        var routingReasoner      = new RoutingReasoner<>(routingStrategy, coordinationContext, candidateSupplier);
        var aggregationReasoner  = new AggregationReasoner<>(aggregationStrategy, coordinationContext);
        var provisioningReasoner = new ProvisioningReasoner(provisioningAdvisor, context.caseId());

        var topologyDesc = describeTopology(context.caseId());
        var factory      = context.loopFactory();
        var dp           = context.degradationPolicy();

        this.cascadeThreshold = switch (dp.thresholdType()) {
            case SIMPLE_FRACTION -> new SimpleFractionThreshold(dp.cascadeThreshold());
            case PRIORITY_WEIGHTED -> new PriorityWeightedThreshold(dp.cascadeThreshold(), dp.priorityWeights());
        };

        this.loopPriorities = new LinkedHashMap<>();
        loopPriorities.put(teamFormation.name(), teamFormation.budgetPriority());
        loopPriorities.put(collectiveReasoning.name(), collectiveReasoning.budgetPriority());
        loopPriorities.put(provisioning.name(), provisioning.budgetPriority());

        Consumer<SwarmDegradedEvent> degradationSink = this::onDegradation;

        var routingCascade      = new CascadeDegradationTrigger();
        var aggregationCascade  = new CascadeDegradationTrigger();
        var provisioningCascade = new CascadeDegradationTrigger();
        this.cascadeTriggers = List.of(routingCascade, aggregationCascade, provisioningCascade);

        var routingLatency      = new LatencyDegradationTrigger(io.casehub.agency.stigmergy.llm.LlmInvocationPolicy.of(java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(8), java.time.Duration.ofSeconds(10)), dp.latencyBreachWindow(), dp.latencyBreachCount(), dp.latencyBreachWindowDuration());
        var aggregationLatency  = new LatencyDegradationTrigger(io.casehub.agency.stigmergy.llm.LlmInvocationPolicy.of(java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(8), java.time.Duration.ofSeconds(10)), dp.latencyBreachWindow(), dp.latencyBreachCount(), dp.latencyBreachWindowDuration());
        var provisioningLatency = new LatencyDegradationTrigger(io.casehub.agency.stigmergy.llm.LlmInvocationPolicy.of(java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(8), java.time.Duration.ofSeconds(10)), dp.latencyBreachWindow(), dp.latencyBreachCount(), dp.latencyBreachWindowDuration());

        DegradationTrigger routingAdditional      = new CompositeDegradationTrigger(List.of(routingCascade, routingLatency));
        DegradationTrigger aggregationAdditional   = new CompositeDegradationTrigger(List.of(aggregationCascade, aggregationLatency));
        DegradationTrigger provisioningAdditional  = new CompositeDegradationTrigger(List.of(provisioningCascade, provisioningLatency));

        this.loops = List.of(
                factory.create(teamFormation,
                               new DecisionContext(context.caseId(), teamFormation.name(), DecisionScope.FULL_AUTONOMY,
                                                   CommunicationMode.BROADCAST, topologyDesc),
                               routingReasoner, routingHeuristic, pool, routingAdditional, degradationSink, routingLatency, loopEventSink),
                factory.create(collectiveReasoning,
                               new DecisionContext(context.caseId(), collectiveReasoning.name(), DecisionScope.FULL_AUTONOMY,
                                                   CommunicationMode.BROADCAST, topologyDesc),
                               aggregationReasoner, aggregationHeuristic, pool, aggregationAdditional, degradationSink, aggregationLatency, loopEventSink),
                factory.create(provisioning,
                               new DecisionContext(context.caseId(), provisioning.name(), DecisionScope.FULL_AUTONOMY,
                                                   CommunicationMode.BROADCAST, topologyDesc),
                               provisioningReasoner, provisioningHeuristic, pool, provisioningAdditional, degradationSink, provisioningLatency, loopEventSink)
                            );

        DecisionOutcomeExecutor executor = (agentId, outcome, ctx) ->
                                                   executeCentralised(agentId, outcome, ctx);

        return new CoordinationPlan(
                List.of(teamFormation, collectiveReasoning, provisioning), executor, pool);
    }

    private CompletionStage<CoordinationFeedback> executeCentralised(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        return switch (outcome) {
            case DecisionOutcome.Route r -> executeRouting(agentId, r, ctx);
            case DecisionOutcome.Aggregate a -> executeAggregation(agentId, a, ctx);
            case DecisionOutcome.WriteBack w -> executeWriteBack(agentId, w);
            case DecisionOutcome.Composite c -> executeComposite(agentId, c, ctx);
            case DecisionOutcome.Provision p -> CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, p, FeedbackSignal.SUCCESS));
            case DecisionOutcome.ScopeGated sg -> CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, sg, FeedbackSignal.SCOPE_VIOLATION));
        };
    }

    private CompletionStage<CoordinationFeedback> executeRouting(
            String agentId, DecisionOutcome.Route route, TopologyContext ctx) {
        return CompletableFuture.completedFuture(
                new CoordinationFeedback(agentId, route, FeedbackSignal.SUCCESS));
    }

    private CompletionStage<CoordinationFeedback> executeAggregation(
            String agentId, DecisionOutcome.Aggregate agg, TopologyContext ctx) {
        return CompletableFuture.completedFuture(
                new CoordinationFeedback(agentId, agg, FeedbackSignal.SUCCESS));
    }

    private CompletionStage<CoordinationFeedback> executeWriteBack(
            String agentId, DecisionOutcome.WriteBack wb) {
        return CompletableFuture.completedFuture(
                new CoordinationFeedback(agentId, wb, FeedbackSignal.SUCCESS));
    }

    private CompletionStage<CoordinationFeedback> executeComposite(
            String agentId, DecisionOutcome.Composite composite, TopologyContext ctx) {
        CompletionStage<CoordinationFeedback> chain =
                CompletableFuture.completedFuture(
                        new CoordinationFeedback(agentId, composite, FeedbackSignal.SUCCESS));
        for (var sub : composite.outcomes()) {
            chain = chain.thenCompose(prev -> {
                if (prev.signal() == FeedbackSignal.FAILURE) {
                    return CompletableFuture.completedFuture(prev);
                }
                return executeCentralised(agentId, sub, ctx);
            });
        }
        return chain;
    }

    @Override
    public void onSwarmEvent(SwarmCoordinationEvent event) {
        if (swarmEventHandler != null) {
            swarmEventHandler.accept(event);
        }
        switch (event) {
            case SwarmCoordinationEvent.SwarmInitialised e ->
                feedLoop("centralised-team-formation", ObservationEvent.of("candidates-available", e));
            case SwarmCoordinationEvent.RoleChanged e ->
                feedLoop("centralised-team-formation", ObservationEvent.of("roles-changed", e));
            case SwarmCoordinationEvent.TeamChanged e ->
                feedLoop("centralised-collective-reasoning", ObservationEvent.of("team-changed", e));
            case SwarmCoordinationEvent.ProgressUpdated e -> {
                for (var loop : loops) { loop.collect(ObservationEvent.of("progress-updated", e)); }
            }
            case SwarmCoordinationEvent.MemberCompleted e ->
                feedLoop("centralised-collective-reasoning", ObservationEvent.of("member-completed", e));
            case SwarmCoordinationEvent.ProvisioningChanged e ->
                feedLoop("centralised-provisioning", ObservationEvent.of("provisioning-changed", e));
        }
    }

    private void feedLoop(String loopName, ObservationEvent event) {
        loops.stream()
             .filter(l -> l.descriptor().name().equals(loopName))
             .findFirst()
             .ifPresent(loop -> loop.collect(event));
    }


    @Override
    public void onFeedback(CoordinationFeedback feedback) {
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
            cascadeTriggers.forEach(CascadeDegradationTrigger::activate);
        }
    }


    public C coordinationContext()                       {return coordinationContext;}

    public RoutingStrategy<C> routingStrategy()           {return routingStrategy;}

    public AggregationStrategy<C> aggregationStrategy()   {return aggregationStrategy;}

    public TerminationCondition<C> terminationCondition() {return terminationCondition;}

    public List<AgentDecisionLoop> loops()                {return loops;}

}
