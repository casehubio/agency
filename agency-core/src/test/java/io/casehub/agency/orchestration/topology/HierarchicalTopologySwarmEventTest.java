package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.degradation.DegradationTrigger;
import io.casehub.agency.orchestration.degradation.DegradedEvent;
import io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser;
import io.casehub.agency.orchestration.loop.AgentDecisionLoop;
import io.casehub.agency.orchestration.loop.DecisionContext;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionLoopEvent;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.platform.agent.budget.AutonomyBudgetPolicy;
import io.casehub.platform.agent.budget.AutonomyBudgetPool;
import io.casehub.platform.agent.budget.BudgetPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class HierarchicalTopologySwarmEventTest {

    private static final UUID SWARM_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();

    private HierarchicalTopology topology;
    private final List<DecisionLoopEvent> capturedEvents = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        Map<String, AgentRole> agentRoles = Map.of(
                "root", AgentRole.of("root", StructuralRole.INITIATOR),
                "middle", AgentRole.of("middle", StructuralRole.CONTRACTOR),
                "leaf", AgentRole.of("leaf", StructuralRole.CONTRACTOR));

        Map<String, String> parentMap = Map.of(
                "middle", "root",
                "leaf", "middle");

        topology = new HierarchicalTopology(agentRoles, parentMap);

        DecisionLoopFactory factory = new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>());
        AutonomyBudgetPool pool = new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), CASE_ID + "-TEST");
        TopologyDescriptor topologyDesc = new TopologyDescriptor(TopologyType.HIERARCHICAL, agentRoles);
        ContentSummariser<ObservationEvent, DecisionOutcome> noOp = new NoOpHeuristicSummariser<>();
        DegradationTrigger noOpTrigger = () -> false;

        topology.registerLoop("root", createLoop(factory, "hierarchical-root", "root",
                DecisionScope.FULL_AUTONOMY, topologyDesc, pool, noOp, noOpTrigger));
        topology.registerLoop("middle", createLoop(factory, "hierarchical-middle", "middle",
                DecisionScope.TASK_ADAPTATION, topologyDesc, pool, noOp, noOpTrigger));
        topology.registerLoop("leaf", createLoop(factory, "hierarchical-leaf", "leaf",
                DecisionScope.STIMULUS_DRIVEN, topologyDesc, pool, noOp, noOpTrigger));
    }

    private AgentDecisionLoop createLoop(DecisionLoopFactory factory, String loopName, String agentId,
                                         DecisionScope scope, TopologyDescriptor topologyDesc,
                                         AutonomyBudgetPool pool,
                                         ContentSummariser<ObservationEvent, DecisionOutcome> heuristic,
                                         DegradationTrigger trigger) {
        var descriptor = new DecisionLoopDescriptor(loopName, ObservationStrategy.EVENT_DRIVEN, scope,
                CommunicationMode.BROADCAST, BudgetPriority.NORMAL, List.of());
        var context = new DecisionContext(CASE_ID, agentId, scope, CommunicationMode.BROADCAST, topologyDesc);
        return factory.create(descriptor, context, heuristic, heuristic, pool, trigger, e -> {}, e -> {}, capturedEvents::add);
    }

    private List<String> agentIdsFromCaptured() {
        return capturedEvents.stream()
                .filter(DecisionLoopEvent.ObservationGathered.class::isInstance)
                .map(DecisionLoopEvent.ObservationGathered.class::cast)
                .map(DecisionLoopEvent.ObservationGathered::agentId)
                .toList();
    }

    private List<String> eventKeysFromCaptured() {
        return capturedEvents.stream()
                .filter(DecisionLoopEvent.ObservationGathered.class::isInstance)
                .map(DecisionLoopEvent.ObservationGathered.class::cast)
                .map(DecisionLoopEvent.ObservationGathered::key)
                .toList();
    }

    // --- SwarmAwareTopology contract ---

    @Test
    void implements_swarm_aware_topology() {
        assertThat(topology).isInstanceOf(SwarmAwareTopology.class);
    }

    // --- Event routing ---

    @Test
    void swarm_initialised_routes_to_root_only() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.SwarmInitialised(
                SWARM_ID, List.of(), SwarmProgress.EMPTY));

        assertThat(agentIdsFromCaptured()).containsExactly("root");
    }

    @Test
    void team_changed_routes_to_root_only() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.TeamChanged(
                SWARM_ID, List.of(), SwarmProgress.EMPTY));

        assertThat(agentIdsFromCaptured()).containsExactly("root");
    }

    @Test
    void provisioning_changed_routes_to_root_only() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.ProvisioningChanged(SWARM_ID, 3, 5));

        assertThat(agentIdsFromCaptured()).containsExactly("root");
    }

    @Test
    void role_changed_routes_to_root_and_non_leaf() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(
                SWARM_ID, List.of(), SwarmProgress.EMPTY));

        assertThat(agentIdsFromCaptured()).containsExactlyInAnyOrder("root", "middle");
    }

    @Test
    void progress_updated_broadcasts_to_all_loops() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(SWARM_ID, SwarmProgress.EMPTY));

        assertThat(agentIdsFromCaptured()).containsExactlyInAnyOrder("root", "middle", "leaf");
    }

    @Test
    void progress_broadcast_skips_collapsed_loop_and_redirects_to_absorber() {
        topology.onDegradation(new DegradedEvent("hierarchical-middle", "latency", true, true));

        topology.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(SWARM_ID, SwarmProgress.EMPTY));

        assertThat(agentIdsFromCaptured()).containsExactlyInAnyOrder("root", "leaf");
    }


    @Test
    void member_completed_routes_to_parent_of_completed_agent() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(SWARM_ID, "leaf", "done"));

        assertThat(agentIdsFromCaptured()).containsExactly("middle");
    }

    @Test
    void member_completed_for_root_agent_is_silent() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(SWARM_ID, "root", "done"));

        assertThat(agentIdsFromCaptured()).isEmpty();
    }

    @Test
    void member_completed_for_unknown_agent_is_silent() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(SWARM_ID, "unknown", "done"));

        assertThat(agentIdsFromCaptured()).isEmpty();
    }

    @Test
    void event_key_contains_event_type() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.SwarmInitialised(
                SWARM_ID, List.of(), SwarmProgress.EMPTY));

        assertThat(eventKeysFromCaptured()).containsExactly("swarm-initialised");
    }

    // --- Scope absorption ---

    @Test
    void leaf_degradation_does_not_affect_parent_routing() {
        topology.onDegradation(new DegradedEvent("hierarchical-leaf", "latency", true));

        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(SWARM_ID, "leaf", "done"));

        assertThat(agentIdsFromCaptured()).containsExactly("middle");
    }

    @Test
    void collapsed_middle_reroutes_to_root() {
        topology.onDegradation(new DegradedEvent("hierarchical-middle", "latency", true, true));

        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(SWARM_ID, "leaf", "done"));

        assertThat(agentIdsFromCaptured()).containsExactly("root");
    }

    @Test
    void multi_level_collapse_walks_up_to_root() {
        topology.onDegradation(new DegradedEvent("hierarchical-middle", "latency", true, true));
        topology.onDegradation(new DegradedEvent("hierarchical-leaf", "latency", true, true));

        topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(
                SWARM_ID, List.of(), SwarmProgress.EMPTY));

        assertThat(agentIdsFromCaptured()).containsExactly("root");
    }


    @Test
    void degradation_without_collapse_does_not_trigger_absorption() {
        topology.onDegradation(new DegradedEvent("hierarchical-middle", "latency", true));

        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(SWARM_ID, "leaf", "done"));

        assertThat(agentIdsFromCaptured()).containsExactly("middle");
    }

    @Test
    void explicit_collapse_triggers_absorption() {
        topology.onDegradation(new DegradedEvent("hierarchical-middle", "latency", true, true));

        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(SWARM_ID, "leaf", "done"));

        assertThat(agentIdsFromCaptured()).containsExactly("root");
    }

    @Test
    void collapse_recovery_restores_routing() {
        topology.onDegradation(new DegradedEvent("hierarchical-middle", "latency", true, true));
        topology.onDegradation(new DegradedEvent("hierarchical-middle", "recovered", false, false));

        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(SWARM_ID, "leaf", "done"));

        assertThat(agentIdsFromCaptured()).containsExactly("middle");
    }

    @Test
    void recovery_from_degradation_restores_normal_routing() {
        topology.onDegradation(new DegradedEvent("hierarchical-leaf", "latency", true));
        topology.onDegradation(new DegradedEvent("hierarchical-leaf", "recovered", false));

        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(SWARM_ID, "leaf", "done"));

        assertThat(agentIdsFromCaptured()).containsExactly("middle");
    }

    // --- registerLoop ---

    @Test
    void unregistered_loop_events_are_silently_dropped() {
        HierarchicalTopology fresh = new HierarchicalTopology(
                Map.of("root", AgentRole.of("root", StructuralRole.INITIATOR)),
                Map.of());

        fresh.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(SWARM_ID, SwarmProgress.EMPTY));
    }
}
