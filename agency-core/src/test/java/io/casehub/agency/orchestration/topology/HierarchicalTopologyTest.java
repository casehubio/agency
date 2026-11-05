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

class HierarchicalTopologyTest {

    Map<String, AgentRole> agentRoles;
    HierarchicalTopology topology;
    TopologyContext topologyContext;

    @BeforeEach
    void setUp() {
        agentRoles = Map.of(
            "root", AgentRole.of("root", StructuralRole.INITIATOR),
            "middle", AgentRole.of("middle", StructuralRole.CONTRACTOR),
            "leaf", AgentRole.of("leaf", StructuralRole.CONTRACTOR));

        Map<String, String> parentMap = Map.of(
            "middle", "root",
            "leaf", "middle");

        topology = new HierarchicalTopology(agentRoles, parentMap);

        topologyContext = new TopologyContext(
            UUID.randomUUID(),
            new TopologyDescriptor(TopologyType.HIERARCHICAL, agentRoles),
            TestAgentProviders.noOp(),
            new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
            AutonomyBudgetPolicy.defaults(),
            io.casehub.agency.orchestration.degradation.DegradationPolicy.defaults());
    }

    @Test
    void describeTopology_returns_hierarchical() {
        var descriptor = topology.describeTopology(UUID.randomUUID());
        assertThat(descriptor.topologyType()).isEqualTo(TopologyType.HIERARCHICAL);
    }

    @Test
    void configure_returns_one_loop_per_agent() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.loopDescriptors()).hasSize(3);
    }

    @Test
    void root_has_full_autonomy_and_broadcast() {
        var plan = topology.configure(topologyContext);
        DecisionLoopDescriptor root = plan.loopDescriptors().stream()
            .filter(d -> d.name().equals("hierarchical-root"))
            .findFirst().orElseThrow();
        assertThat(root.scope()).isEqualTo(DecisionScope.FULL_AUTONOMY);
        assertThat(root.observationStrategy()).isEqualTo(ObservationStrategy.EVENT_DRIVEN);
        assertThat(root.communicationMode()).isEqualTo(CommunicationMode.BROADCAST);
        assertThat(root.budgetPriority()).isEqualTo(BudgetPriority.RESERVED);
    }

    @Test
    void intermediate_has_task_adaptation() {
        var plan = topology.configure(topologyContext);
        DecisionLoopDescriptor middle = plan.loopDescriptors().stream()
            .filter(d -> d.name().equals("hierarchical-middle"))
            .findFirst().orElseThrow();
        assertThat(middle.scope()).isEqualTo(DecisionScope.TASK_ADAPTATION);
        assertThat(middle.observationStrategy()).isEqualTo(ObservationStrategy.TICK_DRIVEN);
        assertThat(middle.communicationMode()).isEqualTo(CommunicationMode.DIRECT_MESSAGING);
        assertThat(middle.budgetPriority()).isEqualTo(BudgetPriority.NORMAL);
    }

    @Test
    void leaf_has_stimulus_driven() {
        var plan = topology.configure(topologyContext);
        DecisionLoopDescriptor leaf = plan.loopDescriptors().stream()
            .filter(d -> d.name().equals("hierarchical-leaf"))
            .findFirst().orElseThrow();
        assertThat(leaf.scope()).isEqualTo(DecisionScope.STIMULUS_DRIVEN);
        assertThat(leaf.observationStrategy()).isEqualTo(ObservationStrategy.EVENT_DRIVEN);
        assertThat(leaf.communicationMode()).isEqualTo(CommunicationMode.STIGMERGY);
        assertThat(leaf.budgetPriority()).isEqualTo(BudgetPriority.NORMAL);
    }

    @Test
    void configure_pool_has_correct_topologyId_and_policy() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.pool()).isNotNull();
        assertThat(plan.pool().topologyId()).endsWith("-HIERARCHICAL");
        assertThat(plan.pool().callsUsed()).isZero();
    }
}
