package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;

import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HybridTopologyTest {

    Map<String, AgentRole> agentRoles;
    HybridTopology topology;
    TopologyContext topologyContext;

    @BeforeEach
    void setUp() {
        agentRoles = Map.of(
            "coordinator", AgentRole.of("coordinator", StructuralRole.INITIATOR),
            "worker-1", AgentRole.of("worker", StructuralRole.CONTRACTOR),
            "worker-2", AgentRole.of("worker", StructuralRole.CONTRACTOR));
        topology = new HybridTopology(agentRoles);

        topologyContext = new TopologyContext(
            UUID.randomUUID(),
            new TopologyDescriptor(TopologyType.HYBRID, agentRoles),
            TestAgentProviders.noOp(),
            new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
            AutonomyBudgetPolicy.defaults());
    }

    @Test
    void describeTopology_returns_hybrid() {
        var descriptor = topology.describeTopology(UUID.randomUUID());
        assertThat(descriptor.topologyType()).isEqualTo(TopologyType.HYBRID);
        assertThat(descriptor.agentRoles()).hasSize(3);
    }

    @Test
    void configure_returns_global_plus_local_loops() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.loopDescriptors()).hasSize(3);
    }

    @Test
    void configure_global_loop_has_full_autonomy() {
        var plan = topology.configure(topologyContext);
        DecisionLoopDescriptor global = plan.loopDescriptors().stream()
            .filter(d -> d.name().equals("hybrid-coordinator"))
            .findFirst().orElseThrow();
        assertThat(global.scope()).isEqualTo(DecisionScope.FULL_AUTONOMY);
        assertThat(global.observationStrategy()).isEqualTo(ObservationStrategy.EVENT_DRIVEN);
        assertThat(global.communicationMode()).isEqualTo(CommunicationMode.BROADCAST);
    }

    @Test
    void configure_local_loops_have_task_adaptation() {
        var plan = topology.configure(topologyContext);
        var locals = plan.loopDescriptors().stream()
            .filter(d -> d.name().startsWith("hybrid-local-"))
            .toList();
        assertThat(locals).hasSize(2);
        for (var d : locals) {
            assertThat(d.scope()).isEqualTo(DecisionScope.TASK_ADAPTATION);
            assertThat(d.observationStrategy()).isEqualTo(ObservationStrategy.TICK_DRIVEN);
            assertThat(d.communicationMode()).isEqualTo(CommunicationMode.STIGMERGY);
        }
    }

    @Test
    void executor_routes_coordinator_centrally() throws Exception {
        var plan = topology.configure(topologyContext);
        var wb = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());

        var fb = plan.executor()
            .execute("hybrid-coordinator", wb, topologyContext)
            .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void executor_routes_worker_locally() throws Exception {
        var plan = topology.configure(topologyContext);
        var wb = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());

        var fb = plan.executor()
            .execute("worker-1", wb, topologyContext)
            .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }
}
