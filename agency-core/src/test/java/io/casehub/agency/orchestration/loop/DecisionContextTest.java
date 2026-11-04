package io.casehub.agency.orchestration.loop;

import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class DecisionContextTest {

    @Test
    void validConstruction() {
        var topology = new TopologyDescriptor(TopologyType.CENTRALISED, Map.of());
        var ctx = new DecisionContext(
            UUID.randomUUID(), "agent-1",
            DecisionScope.FULL_AUTONOMY,
            CommunicationMode.STIGMERGY,
            topology);
        assertThat(ctx.agentId()).isEqualTo("agent-1");
        assertThat(ctx.scope()).isEqualTo(DecisionScope.FULL_AUTONOMY);
    }

    @Test
    void nullCaseId_throws() {
        var topology = new TopologyDescriptor(TopologyType.CENTRALISED, Map.of());
        assertThatNullPointerException()
            .isThrownBy(() -> new DecisionContext(
                null, "agent-1", DecisionScope.FULL_AUTONOMY,
                CommunicationMode.STIGMERGY, topology));
    }

    @Test
    void nullAgentId_throws() {
        var topology = new TopologyDescriptor(TopologyType.CENTRALISED, Map.of());
        assertThatNullPointerException()
            .isThrownBy(() -> new DecisionContext(
                UUID.randomUUID(), null, DecisionScope.FULL_AUTONOMY,
                CommunicationMode.STIGMERGY, topology));
    }

    @Test
    void nullScope_throws() {
        var topology = new TopologyDescriptor(TopologyType.CENTRALISED, Map.of());
        assertThatNullPointerException()
            .isThrownBy(() -> new DecisionContext(
                UUID.randomUUID(), "agent-1", null,
                CommunicationMode.STIGMERGY, topology));
    }

    @Test
    void nullTopology_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DecisionContext(
                UUID.randomUUID(), "agent-1", DecisionScope.FULL_AUTONOMY,
                CommunicationMode.STIGMERGY, null));
    }
}
