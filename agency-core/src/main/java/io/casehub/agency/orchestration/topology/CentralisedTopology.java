package io.casehub.agency.orchestration.topology;

import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.loop.AgentDecisionLoop;
import io.casehub.agency.orchestration.loop.DecisionContext;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.swarm.CollectiveReasoningAggregation;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.orchestration.swarm.SwarmCoordinationEvent;
import io.casehub.agency.orchestration.swarm.SwarmFallbackStrategy;
import io.casehub.agency.orchestration.swarm.SwarmRoutingStrategy;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class CentralisedTopology implements CoordinationTopology {

    private final SwarmContext                       swarmContext;
    private final SwarmRoutingStrategy               routingStrategy;
    private final CollectiveReasoningAggregation     aggregation;
    private final TerminationCondition<SwarmContext> terminationCondition;
    private final SwarmFallbackStrategy              fallbackStrategy;
    private final List<String>                       promptSectionNames;
    private final SwarmProvisioningAdvisor           provisioningAdvisor;
    private final Supplier<List<RoutingCandidate>>   candidateSupplier;
    private       List<AgentDecisionLoop>            loops = List.of();


    public CentralisedTopology(
            SwarmContext swarmContext,
            SwarmRoutingStrategy routingStrategy,
            CollectiveReasoningAggregation aggregation,
            TerminationCondition<SwarmContext> terminationCondition,
            SwarmFallbackStrategy fallbackStrategy,
            List<String> promptSectionNames,
            SwarmProvisioningAdvisor provisioningAdvisor,
            Supplier<List<RoutingCandidate>> candidateSupplier) {
        this.swarmContext         = Objects.requireNonNull(swarmContext);
        this.routingStrategy      = Objects.requireNonNull(routingStrategy);
        this.aggregation          = Objects.requireNonNull(aggregation);
        this.terminationCondition = Objects.requireNonNull(terminationCondition);
        this.fallbackStrategy     = Objects.requireNonNull(fallbackStrategy);
        this.promptSectionNames   = List.copyOf(promptSectionNames);
        this.provisioningAdvisor  = Objects.requireNonNull(provisioningAdvisor);
        this.candidateSupplier    = Objects.requireNonNull(candidateSupplier);
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.CENTRALISED, Map.of());
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-CENTRALISED");

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

        var routingReasoner      = new RoutingReasoner(routingStrategy, swarmContext, candidateSupplier);
        var aggregationReasoner  = new AggregationReasoner(aggregation, swarmContext);
        var provisioningReasoner = new ProvisioningReasoner(provisioningAdvisor, swarmContext, context.caseId());

        var topologyDesc = describeTopology(context.caseId());
        var factory      = context.loopFactory();

        this.loops = List.of(
                factory.create(teamFormation,
                               new DecisionContext(context.caseId(), teamFormation.name(), DecisionScope.FULL_AUTONOMY,
                                                   CommunicationMode.BROADCAST, topologyDesc),
                               routingReasoner, pool),
                factory.create(collectiveReasoning,
                               new DecisionContext(context.caseId(), collectiveReasoning.name(), DecisionScope.FULL_AUTONOMY,
                                                   CommunicationMode.BROADCAST, topologyDesc),
                               aggregationReasoner, pool),
                factory.create(provisioning,
                               new DecisionContext(context.caseId(), provisioning.name(), DecisionScope.FULL_AUTONOMY,
                                                   CommunicationMode.BROADCAST, topologyDesc),
                               provisioningReasoner, pool)
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
        switch (event) {
            case SwarmCoordinationEvent.SwarmInitialised e -> {
                swarmContext.onSwarmInitialised(e.experiences(), e.progress());
                feedLoop("centralised-team-formation",
                         ObservationEvent.of("candidates-available", e));
            }
            case SwarmCoordinationEvent.RoleChanged e -> {
                swarmContext.onRoleChanged(e.roles(), e.progress());
                feedLoop("centralised-team-formation",
                         ObservationEvent.of("roles-changed", e));
            }
            case SwarmCoordinationEvent.TeamChanged e -> {
                swarmContext.onTeamChanged(e.teams(), e.progress());
                feedLoop("centralised-collective-reasoning",
                         ObservationEvent.of("team-changed", e));
            }
            case SwarmCoordinationEvent.ProgressUpdated e -> {
                swarmContext.onProgressUpdated(e.progress());
                for (var loop : loops) {
                    loop.collect(ObservationEvent.of("progress-updated", e));
                }
            }
            case SwarmCoordinationEvent.MemberCompleted e -> {
                swarmContext.onMemberCompleted(e.agentId());
                feedLoop("centralised-collective-reasoning",
                         ObservationEvent.of("member-completed", e));
            }
            case SwarmCoordinationEvent.ProvisioningChanged e -> {
                swarmContext.onProvisioningChanged(e.currentSize(), e.maxSize());
                feedLoop("centralised-provisioning",
                         ObservationEvent.of("provisioning-changed", e));
            }
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

    public SwarmContext swarmContext()                               {return swarmContext;}

    public SwarmRoutingStrategy routingStrategy()                    {return routingStrategy;}

    public CollectiveReasoningAggregation aggregation()              {return aggregation;}

    public TerminationCondition<SwarmContext> terminationCondition() {return terminationCondition;}

    public SwarmFallbackStrategy fallbackStrategy()                  {return fallbackStrategy;}

    public List<AgentDecisionLoop> loops()                           {return loops;}

}
