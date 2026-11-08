package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;
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
import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class HiveTopology implements CoordinationTopology {

    private final Map<String, AgentRole> agentRoles;
    private final HivePatternAdapter patternAdapter;
    private volatile OrchestrationPatternType patternType;
    private final Set<String> degradedLoops = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cascadeTriggered = new AtomicBoolean(false);
    private CascadeThreshold cascadeThreshold;
    private Map<String, BudgetPriority> loopPriorities;
    private List<CascadeDegradationTrigger> cascadeTriggers;
    private volatile @org.jspecify.annotations.Nullable String groupId;
    private volatile @org.jspecify.annotations.Nullable String tenancyId;

    public HiveTopology(Map<String, AgentRole> agentRoles,
                        HivePatternAdapter patternAdapter,
                        OrchestrationPatternType patternType) {
        this.agentRoles = Map.copyOf(Objects.requireNonNull(agentRoles));
        this.patternAdapter = Objects.requireNonNull(patternAdapter);
        this.patternType = Objects.requireNonNull(patternType);
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.CENTRALISED, agentRoles);
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        this.groupId = context.groupId();
        this.tenancyId = context.tenancyId();

        context.descriptor().coordinatorAgentId()
               .orElseThrow(() -> new IllegalStateException(
                       "HiveTopology requires a coordinator agent (INITIATOR role)"));

        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-CENTRALISED");
        var dp   = context.degradationPolicy();

        this.cascadeThreshold = switch (dp.thresholdType()) {
            case SIMPLE_FRACTION -> new SimpleFractionThreshold(dp.cascadeThreshold());
            case PRIORITY_WEIGHTED -> new PriorityWeightedThreshold(dp.cascadeThreshold(), dp.priorityWeights());
        };

        var defaultPolicy = LlmInvocationPolicy.of(java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(8), java.time.Duration.ofSeconds(10));

        var coordinatorDescriptor = new DecisionLoopDescriptor(
                "hive-coordinator",
                ObservationStrategy.EVENT_DRIVEN,
                DecisionScope.FULL_AUTONOMY,
                CommunicationMode.BROADCAST,
                BudgetPriority.RESERVED,
                List.of("hive-coordination-strategy", "role-assignments"));

        var workerDescriptors = context.descriptor().agentRoles().entrySet().stream()
                                       .filter(e -> e.getValue().structuralRole() != StructuralRole.INITIATOR)
                                       .map(e -> new DecisionLoopDescriptor(
                                               "hive-worker-" + e.getKey(),
                                               ObservationStrategy.DEMAND_DRIVEN,
                                               DecisionScope.TASK_ADAPTATION,
                                               CommunicationMode.DIRECT_MESSAGING,
                                               BudgetPriority.NORMAL,
                                               List.of("role-context")))
                                       .toList();

        var allDescriptors = new ArrayList<DecisionLoopDescriptor>();
        allDescriptors.add(coordinatorDescriptor);
        allDescriptors.addAll(workerDescriptors);

        this.loopPriorities = new LinkedHashMap<>();
        for (var d : allDescriptors) {
            loopPriorities.put(d.name(), d.budgetPriority());
        }

        var triggers    = new LinkedHashMap<String, DegradationTrigger>();
        var heuristics  = new LinkedHashMap<String, ContentSummariser<ObservationEvent, DecisionOutcome>>();
        var cascadeList = new ArrayList<CascadeDegradationTrigger>();

        // Coordinator: latency only, no cascade trigger
        var coordLatency = new LatencyDegradationTrigger(defaultPolicy, dp.latencyBreachWindow(), dp.latencyBreachCount(), dp.latencyBreachWindowDuration());
        triggers.put(coordinatorDescriptor.name(), coordLatency);
        heuristics.put(coordinatorDescriptor.name(), new NoOpHeuristicSummariser<>());

        // Workers: cascade + latency triggers
        for (var wd : workerDescriptors) {
            var cascade = new CascadeDegradationTrigger();
            cascadeList.add(cascade);
            var latency = new LatencyDegradationTrigger(defaultPolicy, dp.latencyBreachWindow(), dp.latencyBreachCount(), dp.latencyBreachWindowDuration());
            triggers.put(wd.name(), new CompositeDegradationTrigger(List.of(cascade, latency)));
            heuristics.put(wd.name(), new NoOpHeuristicSummariser<>());
        }

        this.cascadeTriggers = List.copyOf(cascadeList);

        DecisionOutcomeExecutor executor = (agentId, outcome, ctx) ->
                                                   patternAdapter.execute(patternType, agentId, outcome, ctx);

        return new CoordinationPlan(allDescriptors, executor, pool, triggers, heuristics);
    }

    @Override
    public void onSwarmEvent(SwarmCoordinationEvent event) {
    }

    @Override
    public void onFeedback(CoordinationFeedback feedback) {
        patternAdapter.onFeedback(patternType, feedback);
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
            patternType = switch (patternType) {
                case DEBATE -> OrchestrationPatternType.VOTING;
                case VOTING -> OrchestrationPatternType.SUPERVISOR;
                case SUPERVISOR -> OrchestrationPatternType.SUPERVISOR;
            };
            cascadeTriggers.forEach(CascadeDegradationTrigger::activate);
        }
    }


    public OrchestrationPatternType patternType() {return patternType;}

    public @org.jspecify.annotations.Nullable String groupId() { return groupId; }

    public @org.jspecify.annotations.Nullable String tenancyId() { return tenancyId; }
}
