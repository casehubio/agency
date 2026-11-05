package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
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
