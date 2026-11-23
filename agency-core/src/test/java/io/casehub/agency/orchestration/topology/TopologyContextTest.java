package io.casehub.agency.orchestration.topology;

import io.casehub.eidos.api.RoleChangePolicy;
import io.casehub.platform.agent.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.degradation.DegradationPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TopologyContextTest {

    static final TopologyDescriptor   DESCRIPTOR     =
            new TopologyDescriptor(TopologyType.CENTRALISED, Map.of());
    static final AgentProvider        AGENT_PROVIDER =
            TestAgentProviders.noOp();
    static final DecisionLoopFactory  LOOP_FACTORY   =
            new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>());
    static final AutonomyBudgetPolicy POLICY         = AutonomyBudgetPolicy.defaults();
    static final DegradationPolicy    DEG_POLICY     = DegradationPolicy.defaults();

    @Test
    void creates_with_all_fields() {
        var ctx = new TopologyContext(
                UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY);
        assertThat(ctx.caseId()).isNotNull();
        assertThat(ctx.descriptor()).isEqualTo(DESCRIPTOR);
        assertThat(ctx.agentProvider()).isEqualTo(AGENT_PROVIDER);
        assertThat(ctx.loopFactory()).isEqualTo(LOOP_FACTORY);
        assertThat(ctx.budgetPolicy()).isEqualTo(POLICY);
        assertThat(ctx.degradationPolicy()).isEqualTo(DEG_POLICY);
    }

    @Test
    void creates_with_group_context() {
        var ctx = new TopologyContext(
                UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY,
                null, "group-1", "tenant-1", null);
        assertThat(ctx.groupId()).isEqualTo("group-1");
        assertThat(ctx.tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void creates_with_null_group_context() {
        var ctx = new TopologyContext(
                UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY,
                null, null, null, null);
        assertThat(ctx.groupId()).isNull();
        assertThat(ctx.tenancyId()).isNull();
    }

    @Test
    void creates_with_bridging_policy() {
        var bridgingPolicy = new RoleChangePolicy(
                RoleChangePolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
        var ctx = new TopologyContext(
                UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY,
                bridgingPolicy, "group-1", "tenant-1", null);
        assertThat(ctx.bridgingPolicy()).isEqualTo(bridgingPolicy);
    }

    @Test
    void convenience_constructor_has_null_bridging_policy() {
        var ctx = new TopologyContext(
                UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY);
        assertThat(ctx.bridgingPolicy()).isNull();
    }

    @Test
    void convenience_constructor_has_null_loopEventSink() {
        var ctx = new TopologyContext(
                UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY);
        assertThat(ctx.loopEventSink()).isNull();
    }

    @Test
    void loopEventSinkOrDefault_returnsNoOp_whenNull() {
        var ctx = new TopologyContext(
                UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY);
        var sink = ctx.loopEventSinkOrDefault();
        assertThat(sink).isNotNull();
        sink.accept(new io.casehub.agency.orchestration.loop.DecisionLoopEvent.ObservationGathered(
                UUID.randomUUID(), "agent-1", "key", java.time.Instant.now()));
    }

    @Test
    void loopEventSinkOrDefault_returnsProvidedSink_whenSet() {
        var events = new java.util.ArrayList<io.casehub.agency.orchestration.loop.DecisionLoopEvent>();
        var ctx = new TopologyContext(
                UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY,
                null, null, null, events::add);
        var sink = ctx.loopEventSinkOrDefault();
        var event = new io.casehub.agency.orchestration.loop.DecisionLoopEvent.ObservationGathered(
                UUID.randomUUID(), "agent-1", "key", java.time.Instant.now());
        sink.accept(event);
        assertThat(events).containsExactly(event);
    }


    @Test
    void rejects_null_caseId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TopologyContext(null, DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY))
                .withMessageContaining("caseId");
    }

    @Test
    void rejects_null_descriptor() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TopologyContext(UUID.randomUUID(), null, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY))
                .withMessageContaining("descriptor");
    }

    @Test
    void rejects_null_agentProvider() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TopologyContext(UUID.randomUUID(), DESCRIPTOR, null, LOOP_FACTORY, POLICY, DEG_POLICY))
                .withMessageContaining("agentProvider");
    }

    @Test
    void rejects_null_loopFactory() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TopologyContext(UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, null, POLICY, DEG_POLICY))
                .withMessageContaining("loopFactory");
    }

    @Test
    void rejects_null_budgetPolicy() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TopologyContext(UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, null, DEG_POLICY))
                .withMessageContaining("budgetPolicy");
    }

    @Test
    void rejects_null_degradationPolicy() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TopologyContext(UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, null))
                .withMessageContaining("degradationPolicy");
    }
}
