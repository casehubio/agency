package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class HiveTopologyTest {

    Map<String, AgentRole> agentRoles;
    HiveTopology topology;
    TopologyContext topologyContext;

    @BeforeEach
    void setUp() {
        agentRoles = Map.of(
            "supervisor", AgentRole.of("supervisor", StructuralRole.INITIATOR),
            "builder", AgentRole.of("builder", StructuralRole.CONTRACTOR),
            "critic", AgentRole.of("critic", StructuralRole.CONTRACTOR));

        var adapter = new HivePatternAdapter(TestAgentProviders.noOp());
        topology = new HiveTopology(agentRoles, adapter, OrchestrationPatternType.SUPERVISOR);

        topologyContext = new TopologyContext(
            UUID.randomUUID(),
            new TopologyDescriptor(TopologyType.CENTRALISED, agentRoles),
            TestAgentProviders.noOp(),
            new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
            AutonomyBudgetPolicy.defaults(),
            io.casehub.agency.orchestration.degradation.DegradationPolicy.defaults());
    }

    @Test
    void describeTopology_returns_centralised() {
        var descriptor = topology.describeTopology(UUID.randomUUID());
        assertThat(descriptor.topologyType()).isEqualTo(TopologyType.CENTRALISED);
        assertThat(descriptor.agentRoles()).hasSize(3);
    }

    @Test
    void configure_returns_coordinator_plus_worker_loops() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.loopDescriptors()).hasSize(3);
    }

    @Test
    void configure_coordinator_has_full_autonomy() {
        var plan = topology.configure(topologyContext);
        DecisionLoopDescriptor coord = plan.loopDescriptors().stream()
            .filter(d -> d.name().equals("hive-coordinator"))
            .findFirst().orElseThrow();
        assertThat(coord.scope()).isEqualTo(DecisionScope.FULL_AUTONOMY);
        assertThat(coord.observationStrategy()).isEqualTo(ObservationStrategy.EVENT_DRIVEN);
        assertThat(coord.communicationMode()).isEqualTo(CommunicationMode.BROADCAST);
        assertThat(coord.budgetPriority()).isEqualTo(BudgetPriority.RESERVED);
    }

    @Test
    void configure_workers_have_task_adaptation_and_demand_driven() {
        var plan = topology.configure(topologyContext);
        var workers = plan.loopDescriptors().stream()
            .filter(d -> d.name().startsWith("hive-worker-"))
            .toList();
        assertThat(workers).hasSize(2);
        for (var d : workers) {
            assertThat(d.scope()).isEqualTo(DecisionScope.TASK_ADAPTATION);
            assertThat(d.observationStrategy()).isEqualTo(ObservationStrategy.DEMAND_DRIVEN);
            assertThat(d.communicationMode()).isEqualTo(CommunicationMode.DIRECT_MESSAGING);
            assertThat(d.budgetPriority()).isEqualTo(BudgetPriority.NORMAL);
        }
    }

    @Test
    void configure_requires_coordinator() {
        var noCoordRoles = Map.of(
            "worker-1", AgentRole.of("worker", StructuralRole.CONTRACTOR));
        var noCoordCtx = new TopologyContext(
            UUID.randomUUID(),
            new TopologyDescriptor(TopologyType.HYBRID, noCoordRoles),
            TestAgentProviders.noOp(),
            new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
            AutonomyBudgetPolicy.defaults(),
            io.casehub.agency.orchestration.degradation.DegradationPolicy.defaults());

        assertThatIllegalStateException()
            .isThrownBy(() -> topology.configure(noCoordCtx))
            .withMessageContaining("coordinator");
    }

    @Test
    void configure_pool_has_correct_topologyId_and_policy() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.pool()).isNotNull();
        assertThat(plan.pool().topologyId()).endsWith("-CENTRALISED");
        assertThat(plan.pool().callsUsed()).isZero();
    }

    @Test
    void configure_capturesGroupIdAndTenancyId() {
        var ctxWithGroup = new TopologyContext(
                UUID.randomUUID(),
                new TopologyDescriptor(TopologyType.CENTRALISED, agentRoles),
                TestAgentProviders.noOp(),
                new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
                AutonomyBudgetPolicy.defaults(),
                io.casehub.agency.orchestration.degradation.DegradationPolicy.defaults(),
                "group-42", "tenant-1");

        topology.configure(ctxWithGroup);

        assertThat(topology.groupId()).isEqualTo("group-42");
        assertThat(topology.tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void configure_withoutGroupId_storesNull() {
        topology.configure(topologyContext);

        assertThat(topology.groupId()).isNull();
        assertThat(topology.tenancyId()).isNull();
    }
}
