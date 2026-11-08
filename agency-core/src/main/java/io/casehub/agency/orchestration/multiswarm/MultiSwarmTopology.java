package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.agency.deliberation.belief.ConsistencyChecker;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.agency.orchestration.topology.CoordinationFeedback;
import io.casehub.agency.orchestration.topology.CoordinationPlan;
import io.casehub.agency.orchestration.topology.CoordinationTopology;
import io.casehub.agency.orchestration.topology.FeedbackSignal;
import io.casehub.agency.orchestration.topology.TopologyContext;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.api.spi.coordination.RegistryListener;
import io.casehub.platform.agent.AgentProvider;
import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.OnlineParameterUpdater;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class MultiSwarmTopology implements CoordinationTopology, RegistryListener {

    private final MultiSwarmRegistry registry;
    private final CoordinationBus<MultiSwarmSignal> bus;
    private final ConcurrentHashMap<UUID, RelevanceScorer> relevanceScorers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, EscalationGate> escalationGates = new ConcurrentHashMap<>();
    private final BayesNetwork relevanceNetworkTemplate;
    private final BayesNetwork escalationNetworkTemplate;
    private volatile BeliefSet<SwarmConclusion> beliefSet = new BeliefSet<>();
    private final ConsistencyChecker<SwarmConclusion> consistencyChecker;
    private final double relevanceThreshold;
    private final double escalationThreshold;
    private final double convergenceThreshold;
    private final @Nullable AgentProvider agentProvider;

    MultiSwarmTopology(MultiSwarmRegistry registry,
                       BayesNetwork relevanceNetwork, BayesNetwork escalationNetwork,
                       ConsistencyChecker<SwarmConclusion> consistencyChecker,
                       double relevanceThreshold, double escalationThreshold,
                       double convergenceThreshold, int replayBufferSize,
                       @Nullable AgentProvider agentProvider) {
        this.registry = registry;
        this.bus = new CoordinationBus<>(replayBufferSize);
        this.relevanceNetworkTemplate = relevanceNetwork;
        this.escalationNetworkTemplate = escalationNetwork;
        this.consistencyChecker = consistencyChecker;
        this.relevanceThreshold = relevanceThreshold;
        this.escalationThreshold = escalationThreshold;
        this.convergenceThreshold = convergenceThreshold;
        this.agentProvider = agentProvider;
    }

    public static MultiSwarmTopologyBuilder builder() {
        return new MultiSwarmTopologyBuilder();
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.MULTI_SWARM, Map.of());
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-MULTI_SWARM");
        var coordinator = new DecisionLoopDescriptor(
                "multi-swarm-coordinator",
                ObservationStrategy.EVENT_DRIVEN,
                DecisionScope.FULL_AUTONOMY,
                CommunicationMode.BROADCAST,
                BudgetPriority.RESERVED,
                List.of("cross-swarm-signals", "conflict-state", "convergence-progress"));
        return new CoordinationPlan(List.of(coordinator), this::executeCoordination, pool);
    }

    private CompletionStage<CoordinationFeedback> executeCoordination(
            String agentId, DecisionOutcome outcome, TopologyContext context) {
        return CompletableFuture.completedFuture(
                new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
    }

    @Override
    public void onSwarmEvent(SwarmCoordinationEvent event) {
        var descriptor = registry.get(event.swarmId());
        if (descriptor == null) return;
        var signal = toSignal(event, descriptor);
        if (signal != null) bus.publish(signal);
    }

    @Override
    public void onFeedback(CoordinationFeedback feedback) {}

    @Override
    public void onDegradation(SwarmDegradedEvent event) {}

    @Override
    public void onRegistered(SwarmDescriptor descriptor) {
        var relevanceUpdater = OnlineParameterUpdater.create(relevanceNetworkTemplate);
        relevanceScorers.put(descriptor.swarmId(), new RelevanceScorer(relevanceUpdater, relevanceThreshold));
        var escalationUpdater = OnlineParameterUpdater.create(escalationNetworkTemplate);
        escalationGates.put(descriptor.swarmId(), new EscalationGate(escalationUpdater, escalationThreshold));
    }

    @Override
    public void onDeregistered(UUID swarmId) {
        relevanceScorers.remove(swarmId);
        escalationGates.remove(swarmId);
        var current = beliefSet;
        for (var belief : current.all()) {
            if (belief.value().swarmId().equals(swarmId)) {
                current = current.contract(belief.key());
            }
        }
        beliefSet = current;
    }

    @Override
    public void onInterestsUpdated(UUID swarmId, Set<String> capabilities) {}

    public boolean hasScorer(UUID swarmId) {
        return relevanceScorers.containsKey(swarmId);
    }

    public @Nullable RelevanceScorer getScorer(UUID swarmId) {
        return relevanceScorers.get(swarmId);
    }

    public CoordinationBus<MultiSwarmSignal> signalBus() {
        return bus;
    }

    private static @Nullable MultiSwarmSignal toSignal(SwarmCoordinationEvent event, SwarmDescriptor descriptor) {
        return switch (event) {
            case SwarmCoordinationEvent.ProgressUpdated e ->
                    new MultiSwarmSignal.Progress(e.swarmId(), descriptor.capabilities(),
                            System.currentTimeMillis(), e.progress());
            case SwarmCoordinationEvent.SwarmInitialised e ->
                    new MultiSwarmSignal.Progress(e.swarmId(), descriptor.capabilities(),
                            System.currentTimeMillis(), e.progress());
            case SwarmCoordinationEvent.RoleChanged e ->
                    new MultiSwarmSignal.Progress(e.swarmId(), descriptor.capabilities(),
                            System.currentTimeMillis(), e.progress());
            case SwarmCoordinationEvent.TeamChanged e ->
                    new MultiSwarmSignal.Progress(e.swarmId(), descriptor.capabilities(),
                            System.currentTimeMillis(), e.progress());
            case SwarmCoordinationEvent.MemberCompleted e ->
                    new MultiSwarmSignal.Completion(e.swarmId(), descriptor.capabilities(),
                            System.currentTimeMillis(), "agent", e.agentId());
            case SwarmCoordinationEvent.ProvisioningChanged e -> null;
        };
    }
}
