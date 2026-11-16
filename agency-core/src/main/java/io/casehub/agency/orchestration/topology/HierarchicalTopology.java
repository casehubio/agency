package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.loop.AgentDecisionLoop;
import io.casehub.agency.orchestration.degradation.CascadeDegradationTrigger;
import io.casehub.agency.orchestration.degradation.CascadeThreshold;
import io.casehub.agency.orchestration.degradation.CompositeDegradationTrigger;
import io.casehub.agency.orchestration.degradation.DegradationTrigger;
import io.casehub.agency.orchestration.degradation.LatencyDegradationTrigger;
import io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser;
import io.casehub.agency.orchestration.degradation.PriorityWeightedThreshold;
import io.casehub.agency.orchestration.degradation.SimpleFractionThreshold;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.platform.agent.budget.AutonomyBudgetPool;
import io.casehub.platform.agent.budget.BudgetPriority;

import java.util.ArrayList;
import java.util.HashSet;
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

public class HierarchicalTopology implements SwarmAwareTopology {

    private final Map<String, AgentRole>                       agentRoles;
    private final Map<String, String>                          parentMap;
    private final Set<String>                                  degradedLoops    = ConcurrentHashMap.newKeySet();
    private final Set<String>                                  collapsedLoops   = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean                                cascadeTriggered = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, AgentDecisionLoop> loops            = new ConcurrentHashMap<>();
    private       CascadeThreshold                             cascadeThreshold;
    private       Map<String, BudgetPriority>                  loopPriorities;
    private       List<CascadeDegradationTrigger>              cascadeTriggers;

    public HierarchicalTopology(Map<String, AgentRole> agentRoles,
                                Map<String, String> parentMap) {
        this.agentRoles = Map.copyOf(Objects.requireNonNull(agentRoles));
        this.parentMap  = Map.copyOf(Objects.requireNonNull(parentMap));
    }

    public void registerLoop(String agentId, AgentDecisionLoop loop) {
        loops.put(agentId, loop);
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.HIERARCHICAL, agentRoles);
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-HIERARCHICAL");
        var dp   = context.degradationPolicy();

        this.cascadeThreshold = switch (dp.thresholdType()) {
            case SIMPLE_FRACTION -> new SimpleFractionThreshold(dp.cascadeThreshold());
            case PRIORITY_WEIGHTED -> new PriorityWeightedThreshold(dp.cascadeThreshold(), dp.priorityWeights());
        };

        var defaultPolicy = LlmInvocationPolicy.of(java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(8), java.time.Duration.ofSeconds(10));

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

        this.loopPriorities = new LinkedHashMap<>();
        for (var d : descriptors) {
            loopPriorities.put(d.name(), d.budgetPriority());
        }

        var triggers    = new LinkedHashMap<String, DegradationTrigger>();
        var heuristics  = new LinkedHashMap<String, ContentSummariser<ObservationEvent, DecisionOutcome>>();
        var cascadeList = new ArrayList<CascadeDegradationTrigger>();

        for (var entry : roles.entrySet()) {
            String  agentId  = entry.getKey();
            String  loopName = "hierarchical-" + agentId;
            boolean isLeaf   = isLeaf(agentId, roles.keySet());

            var latency = new LatencyDegradationTrigger(defaultPolicy, dp.latencyBreachWindow(), dp.latencyBreachCount(), dp.latencyBreachWindowDuration());

            if (isLeaf) {
                var cascade = new CascadeDegradationTrigger();
                cascadeList.add(cascade);
                triggers.put(loopName, new CompositeDegradationTrigger(List.of(cascade, latency)));
            } else {
                triggers.put(loopName, latency);
            }
            heuristics.put(loopName, new NoOpHeuristicSummariser<>());
        }

        this.cascadeTriggers = List.copyOf(cascadeList);

        DecisionOutcomeExecutor executor = this::executeDelegated;
        return new CoordinationPlan(descriptors, executor, pool, triggers, heuristics);
    }

    @Override
    public void onSwarmEvent(SwarmCoordinationEvent event) {
        switch (event) {
            case SwarmCoordinationEvent.SwarmInitialised e -> feedLoop(rootAgentId(), ObservationEvent.of("swarm-initialised", e));
            case SwarmCoordinationEvent.TeamChanged e -> feedLoop(rootAgentId(), ObservationEvent.of("team-changed", e));
            case SwarmCoordinationEvent.ProvisioningChanged e -> feedLoop(rootAgentId(), ObservationEvent.of("provisioning-changed", e));
            case SwarmCoordinationEvent.RoleChanged e -> feedNonLeafLoops(ObservationEvent.of("roles-changed", e));
            case SwarmCoordinationEvent.ProgressUpdated e -> {
                for (var loop : loops.values()) {
                    loop.collect(ObservationEvent.of("progress-updated", e));
                }
            }
            case SwarmCoordinationEvent.MemberCompleted e -> {
                String parent = parentMap.get(e.agentId());
                if (parent != null) {
                    feedLoop(resolveAbsorber(parent), ObservationEvent.of("member-completed", e));
                }
            }
        }
    }

    private int computeDepth(String agentId) {
        int    depth   = 0;
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

    private String rootAgentId() {
        for (String agentId : agentRoles.keySet()) {
            if (!parentMap.containsKey(agentId)) {
                return agentId;
            }
        }
        return null;
    }

    private void feedLoop(String agentId, ObservationEvent event) {
        if (agentId == null) {return;}
        AgentDecisionLoop loop = loops.get(agentId);
        if (loop != null) {
            loop.collect(event);
        }
    }

    private void feedNonLeafLoops(ObservationEvent event) {
        Set<String> delivered = new HashSet<>();
        for (String agentId : agentRoles.keySet()) {
            if (!isLeaf(agentId, agentRoles.keySet())) {
                String target = resolveAbsorber(agentId);
                if (delivered.add(target)) {
                    feedLoop(target, event);
                }
            }
        }
    }

    private String resolveAbsorber(String agentId) {
        String current = agentId;
        while (collapsedLoops.contains("hierarchical-" + current)) {
            String parent = parentMap.get(current);
            if (parent == null) {break;}
            current = parent;
        }
        return current;
    }

    private CompletionStage<CoordinationFeedback> executeDelegated(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        return switch (outcome) {
            case DecisionOutcome.ScopeGated sg -> CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, sg, FeedbackSignal.SCOPE_VIOLATION));
            default -> CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
        };
    }

    @Override
    public void onFeedback(CoordinationFeedback feedback) {
    }

    @Override
    public void onDegradation(SwarmDegradedEvent event) {
        if (event.fallbackActive()) {
            degradedLoops.add(event.capability());
            collapsedLoops.add(event.capability());
        } else {
            degradedLoops.remove(event.capability());
            collapsedLoops.remove(event.capability());
        }
        if (event.fallbackActive() && !cascadeTriggered.get()
            && cascadeThreshold != null
            && cascadeThreshold.isBreached(degradedLoops, loopPriorities.size(), loopPriorities)
            && cascadeTriggered.compareAndSet(false, true)) {
            cascadeTriggers.forEach(CascadeDegradationTrigger::activate);
        }
    }

}
