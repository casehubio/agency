package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.budget.BudgetPriority;
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

class GraduatedTopologyTest {

    Map<String, AgentRole> agentRoles;
    GraduationState graduationState;
    GraduatedTopology topology;
    TopologyContext topologyContext;

    @BeforeEach
    void setUp() {
        agentRoles = Map.of(
            "agent-1", AgentRole.of("worker", StructuralRole.PEER),
            "agent-2", AgentRole.of("worker", StructuralRole.PEER));

        graduationState = new GraduationState(
            Map.of("agent-1", DecisionScope.STIMULUS_DRIVEN,
                   "agent-2", DecisionScope.TASK_ADAPTATION), 5);

        topology = new GraduatedTopology(agentRoles, graduationState);

        topologyContext = new TopologyContext(
            UUID.randomUUID(),
            new TopologyDescriptor(TopologyType.GRADUATED, agentRoles),
            TestAgentProviders.noOp(),
            new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
            AutonomyBudgetPolicy.defaults(),
            io.casehub.agency.orchestration.degradation.DegradationPolicy.defaults());
    }

    @Test
    void describeTopology_returns_graduated() {
        var descriptor = topology.describeTopology(UUID.randomUUID());
        assertThat(descriptor.topologyType()).isEqualTo(TopologyType.GRADUATED);
    }

    @Test
    void configure_returns_one_loop_per_agent() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.loopDescriptors()).hasSize(2);
    }

    @Test
    void configure_uses_graduation_state_for_scope() {
        var plan = topology.configure(topologyContext);
        DecisionLoopDescriptor d1 = plan.loopDescriptors().stream()
            .filter(d -> d.name().equals("graduated-agent-1"))
            .findFirst().orElseThrow();
        assertThat(d1.scope()).isEqualTo(DecisionScope.STIMULUS_DRIVEN);

        DecisionLoopDescriptor d2 = plan.loopDescriptors().stream()
            .filter(d -> d.name().equals("graduated-agent-2"))
            .findFirst().orElseThrow();
        assertThat(d2.scope()).isEqualTo(DecisionScope.TASK_ADAPTATION);
    }

    @Test
    void configure_all_loops_use_event_driven_and_stigmergy() {
        var plan = topology.configure(topologyContext);
        for (var d : plan.loopDescriptors()) {
            assertThat(d.observationStrategy()).isEqualTo(ObservationStrategy.EVENT_DRIVEN);
            assertThat(d.communicationMode()).isEqualTo(CommunicationMode.STIGMERGY);
            assertThat(d.budgetPriority()).isEqualTo(BudgetPriority.NORMAL);
        }
    }

    @Test
    void onFeedback_adjusts_graduation_state() {
        topology.onFeedback(new CoordinationFeedback(
            "agent-1",
            new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()),
            FeedbackSignal.SCOPE_VIOLATION));
        assertThat(graduationState.scopeFor("agent-1")).isEqualTo(DecisionScope.STIMULUS_DRIVEN);
    }

    @Test
    void reconfigure_reflects_scope_change() {
        for (int i = 0; i < 4; i++) {
            graduationState.recordFeedback("agent-1", FeedbackSignal.SUCCESS);
        }
        var plan = topology.configure(topologyContext);
        DecisionLoopDescriptor d1 = plan.loopDescriptors().stream()
                                        .filter(d -> d.name().equals("graduated-agent-1"))
                                        .findFirst().orElseThrow();
        assertThat(d1.scope()).isEqualTo(DecisionScope.TASK_ADAPTATION);
    }

    @Test
    void configure_pool_has_correct_topologyId_and_policy() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.pool()).isNotNull();
        assertThat(plan.pool().topologyId()).endsWith("-GRADUATED");
        assertThat(plan.pool().callsUsed()).isZero();
    }
}
