package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.Belief;
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
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.coordination.CrossSwarmConvergenceEvaluator;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.api.spi.coordination.RegistryListener;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class MultiSwarmTopology implements CoordinationTopology, RegistryListener, AutoCloseable {

    private final MultiSwarmRegistry registry;
    private final CoordinationBus<MultiSwarmSignal> bus;
    private final ConcurrentHashMap<UUID, RelevanceScorer> relevanceScorers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, EscalationGate> escalationGates = new ConcurrentHashMap<>();
    private final Supplier<RelevanceScorer> scorerFactory;
    private final Supplier<EscalationGate> gateFactory;
    private final AtomicReference<BeliefSet<SwarmConclusion>> beliefSet = new AtomicReference<>(new BeliefSet<>());
    private final ConsistencyChecker<SwarmConclusion> consistencyChecker;
    private final @Nullable CrossSwarmConvergenceEvaluator convergenceEvaluator;
    private final ConcurrentHashMap<UUID, SwarmProgress> latestProgress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<String>> resourceClaims = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<CrossSwarmNotification> pendingNotifications = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<UUID, Set<String>>         completedDomains     = new ConcurrentHashMap<>();

    private final @Nullable AgentProvider agentProvider;

    MultiSwarmTopology(MultiSwarmRegistry registry,
                       Supplier<RelevanceScorer> scorerFactory,
                       Supplier<EscalationGate> gateFactory,
                       ConsistencyChecker<SwarmConclusion> consistencyChecker,
                       @Nullable CrossSwarmConvergenceEvaluator convergenceEvaluator,
                       int replayBufferSize,
                       @Nullable AgentProvider agentProvider) {
        this.registry = registry;
        this.bus = new CoordinationBus<>(replayBufferSize);
        this.scorerFactory = scorerFactory;
        this.gateFactory = gateFactory;
        this.consistencyChecker = consistencyChecker;
        this.convergenceEvaluator = convergenceEvaluator;
        this.agentProvider = agentProvider;
        bus.subscribe(s -> true, this::processSignal);
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
        relevanceScorers.put(descriptor.swarmId(), scorerFactory.get());
        escalationGates.put(descriptor.swarmId(), gateFactory.get());
    }

    @Override
    public void onDeregistered(UUID swarmId) {
        relevanceScorers.remove(swarmId);
        escalationGates.remove(swarmId);
        latestProgress.remove(swarmId);
        resourceClaims.remove(swarmId);
        completedDomains.remove(swarmId);
        beliefSet.updateAndGet(current -> {
            var updated = current;
            for (var belief : current.all()) {
                if (belief.value().swarmId().equals(swarmId)) {
                    updated = updated.contract(belief.key());
                }
            }
            return updated;
        });
    }

    @Override
    public void onInterestsUpdated(UUID swarmId, Set<String> capabilities) {
        relevanceScorers.computeIfPresent(swarmId, (k, old) -> scorerFactory.get());
        escalationGates.computeIfPresent(swarmId, (k, old) -> gateFactory.get());
    }

    private void processSignal(MultiSwarmSignal signal) {
        switch (signal) {
            case MultiSwarmSignal.Finding finding -> processL1Finding(finding);
            case MultiSwarmSignal.Progress progress -> processL1Progress(progress);
            case MultiSwarmSignal.Completion completion -> processCompletion(completion);
            case MultiSwarmSignal.ResourceClaim claim -> processResourceClaim(claim);
            case MultiSwarmSignal.Handoff ignored -> {}
        }
    }

    private void processResourceClaim(MultiSwarmSignal.ResourceClaim claim) {
        resourceClaims.put(claim.sourceSwarmId(), Set.copyOf(claim.agentIds()));
    }

    private void processCompletion(MultiSwarmSignal.Completion completion) {
        completedDomains.computeIfAbsent(completion.sourceSwarmId(),
                                         k -> ConcurrentHashMap.newKeySet()).add(completion.domain());
    }


    private void processL1Finding(MultiSwarmSignal.Finding finding) {
        for (var entry : relevanceScorers.entrySet()) {
            UUID targetSwarmId = entry.getKey();
            if (targetSwarmId.equals(finding.sourceSwarmId())) continue;

            var scorer = entry.getValue();
            var targetDescriptor = registry.get(targetSwarmId);
            if (targetDescriptor == null) continue;

            double capabilityOverlap = computeCapabilityOverlap(finding.domainTags(), targetDescriptor.capabilities());
            double domainMatch = finding.domainTags().stream().anyMatch(targetDescriptor.capabilities()::contains) ? 0.8 : 0.2;

            if (scorer.isRelevant(capabilityOverlap, domainMatch, finding.confidence(), 0.0)) {
                processL2Finding(finding, targetSwarmId);
            }
        }
    }

    private void processL2Finding(MultiSwarmSignal.Finding finding, UUID targetSwarmId) {
        var conclusion = new SwarmConclusion(
                finding.sourceSwarmId(), finding.domain(), finding.conclusion(), finding.confidence());
        var beliefKey = finding.sourceSwarmId() + ":" + finding.domain();
        var newBelief = Belief.of(beliefKey, conclusion, (int) (finding.confidence() * 100));

        boolean contradicted = !consistencyChecker.isConsistent(beliefSet.get().expand(newBelief));

        beliefSet.updateAndGet(current -> current.revise(newBelief, consistencyChecker));

        if (contradicted) {
            var notification = new CrossSwarmNotification(
                    finding.sourceSwarmId(), targetSwarmId,
                    NotificationType.CONFLICT, finding, finding.confidence());
            pendingNotifications.add(notification);
        }
    }

    private void processL1Progress(MultiSwarmSignal.Progress progress) {
        latestProgress.put(progress.sourceSwarmId(), progress.progress());

        if (convergenceEvaluator != null && latestProgress.size() > 1) {
            convergenceEvaluator.evaluate(Map.copyOf(latestProgress));
        }

        for (var entry : escalationGates.entrySet()) {
            UUID swarmId = entry.getKey();
            if (swarmId.equals(progress.sourceSwarmId())) continue;

            var gate = entry.getValue();
            double conflictLevel = pendingNotifications.isEmpty() ? 0.0 :
                    (double) pendingNotifications.size() / Math.max(relevanceScorers.size(), 1);
            double resourceContention = computeResourceContention(progress.sourceSwarmId(), swarmId);
            double dependencyStrength = computeDependencyStrength(progress.sourceSwarmId(), swarmId);

            if (gate.shouldEscalate(conflictLevel, resourceContention, dependencyStrength)) {
                var notification = new CrossSwarmNotification(
                        progress.sourceSwarmId(), swarmId,
                        NotificationType.DEPENDENCY, progress, 0.5);
                pendingNotifications.add(notification);
            }
        }
    }

    private static double computeCapabilityOverlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        long intersection = a.stream().filter(b::contains).count();
        long union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private double computeDependencyStrength(UUID sourceSwarmId, UUID targetSwarmId) {
        var sourceDesc = registry.get(sourceSwarmId);
        var targetDesc = registry.get(targetSwarmId);
        if (sourceDesc == null || targetDesc == null) return 0.0;
        return computeCapabilityOverlap(sourceDesc.capabilities(), targetDesc.capabilities());
    }

    private double computeResourceContention(UUID sourceSwarmId, UUID targetSwarmId) {
        var sourceClaims = resourceClaims.get(sourceSwarmId);
        var targetClaims = resourceClaims.get(targetSwarmId);
        if (sourceClaims == null || targetClaims == null) return 0.0;
        if (sourceClaims.isEmpty() || targetClaims.isEmpty()) return 0.0;
        long intersection = sourceClaims.stream().filter(targetClaims::contains).count();
        long union = sourceClaims.size() + targetClaims.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    public boolean hasScorer(UUID swarmId) {
        return relevanceScorers.containsKey(swarmId);
    }

    public @Nullable RelevanceScorer getScorer(UUID swarmId) {
        return relevanceScorers.get(swarmId);
    }

    public CoordinationBus<MultiSwarmSignal> signalBus() {
        return bus;
    }

    public BeliefSet<SwarmConclusion> currentBeliefs() {
        return beliefSet.get();
    }

    public Set<String> completedDomains(UUID swarmId) {
        var domains = completedDomains.get(swarmId);
        return domains == null ? Set.of() : Set.copyOf(domains);
    }

    public boolean isAllComplete() {
        if (relevanceScorers.isEmpty()) {return false;}
        for (UUID swarmId : relevanceScorers.keySet()) {
            var domains = completedDomains.get(swarmId);
            if (domains == null || domains.isEmpty()) {return false;}
        }
        return true;
    }


    public List<CrossSwarmNotification> drainNotifications() {
        var drained = List.copyOf(pendingNotifications);
        pendingNotifications.clear();
        return drained;
    }

    @Override
    public void close() {
        bus.close();
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
