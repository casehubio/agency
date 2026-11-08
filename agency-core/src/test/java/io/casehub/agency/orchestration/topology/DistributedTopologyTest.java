package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.DecisionType;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedTopologyTest {

    Map<String, AgentRole> agentRoles;
    DistributedTopology topology;
    TopologyContext topologyContext;

    @BeforeEach
    void setUp() {
        agentRoles = Map.of(
            "agent-a", AgentRole.of("peer-a", StructuralRole.PEER),
            "agent-b", AgentRole.of("peer-b", StructuralRole.PEER),
            "agent-c", AgentRole.of("peer-c", StructuralRole.PEER));
        topology = new DistributedTopology(agentRoles);

        topologyContext = new TopologyContext(
            UUID.randomUUID(),
            new TopologyDescriptor(TopologyType.DISTRIBUTED, agentRoles),
            TestAgentProviders.noOp(),
            new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
            AutonomyBudgetPolicy.defaults(),
            io.casehub.agency.orchestration.degradation.DegradationPolicy.defaults());
    }

    @Test
    void describeTopology_returns_distributed() {
        var descriptor = topology.describeTopology(UUID.randomUUID());
        assertThat(descriptor.topologyType()).isEqualTo(TopologyType.DISTRIBUTED);
        assertThat(descriptor.agentRoles()).hasSize(3);
    }

    @Test
    void configure_returns_one_loop_per_agent() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.loopDescriptors()).hasSize(3);
    }

    @Test
    void configure_loops_have_correct_properties() {
        var plan = topology.configure(topologyContext);
        for (DecisionLoopDescriptor d : plan.loopDescriptors()) {
            assertThat(d.name()).startsWith("distributed-");
            assertThat(d.scope()).isEqualTo(DecisionScope.FULL_AUTONOMY);
            assertThat(d.observationStrategy()).isEqualTo(ObservationStrategy.EVENT_DRIVEN);
            assertThat(d.communicationMode()).isEqualTo(CommunicationMode.STIGMERGY);
            assertThat(d.budgetPriority()).isEqualTo(BudgetPriority.NORMAL);
        }
    }

    @Test
    void configure_returns_non_null_executor() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.executor()).isNotNull();
    }

    @Test
    void configure_pool_has_correct_topologyId_and_policy() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.pool()).isNotNull();
        assertThat(plan.pool().topologyId()).endsWith("-DISTRIBUTED");
        assertThat(plan.pool().callsUsed()).isZero();
    }


    @Test
    void executor_handles_scope_gated() throws Exception {
        var plan = topology.configure(topologyContext);
        var gated = new DecisionOutcome.ScopeGated(
            DecisionType.PROVISIONING,
            new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));

        var fb = plan.executor()
            .execute("agent-a", gated, topologyContext)
            .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SCOPE_VIOLATION);
    }

    @Test
    void executor_handles_writeback() throws Exception {
        var plan = topology.configure(topologyContext);
        var wb = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());

        var fb = plan.executor()
            .execute("agent-a", wb, topologyContext)
            .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void onSwarmEvent_does_not_throw() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(UUID.randomUUID(),
            new SwarmProgress(0.5, 0.3, 0.8, Instant.now())));
    }

    @Test
    void onFeedback_does_not_throw() {
        topology.onFeedback(new CoordinationFeedback(
            "agent-a",
            new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()),
            FeedbackSignal.SUCCESS));
    }
}
