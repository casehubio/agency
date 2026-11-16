package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.topology.DistributedTopology;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistributedTopologyBuilderTest {

    @Test
    void buildsTopologyWithCorrectDescriptor() {
        var topology = new DistributedTopologyBuilder()
                .agentProvider(noOp())
                .agentRoles(Map.of(
                        "analyst", AgentRole.of("analysis", StructuralRole.PEER),
                        "researcher", AgentRole.of("research", StructuralRole.PEER)))
                .buildTopology();

        var descriptor = topology.describeTopology(UUID.randomUUID());
        assertThat(descriptor.topologyType()).isEqualTo(TopologyType.DISTRIBUTED);
        assertThat(descriptor.agentRoles()).hasSize(2);
        assertThat(descriptor.agentRoles()).containsKeys("analyst", "researcher");
    }

    @Test
    void requiresAgentProvider() {
        assertThatThrownBy(() -> new DistributedTopologyBuilder()
                .agentRoles(Map.of("a", AgentRole.of("r", StructuralRole.PEER)))
                .buildTopology())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("agentProvider");
    }

    @Test
    void requiresAtLeastOneRole() {
        assertThatThrownBy(() -> new DistributedTopologyBuilder()
                .agentProvider(noOp())
                .buildTopology())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void defaultConvergenceThreshold() {
        var topology = new DistributedTopologyBuilder()
                .agentProvider(noOp())
                .agentRoles(Map.of("a", AgentRole.of("r", StructuralRole.PEER)))
                .buildTopology();

        assertThat(topology.convergenceThreshold()).isEqualTo(0.8);
    }

    @Test
    void customConvergenceThreshold() {
        var topology = new DistributedTopologyBuilder()
                .agentProvider(noOp())
                .agentRoles(Map.of("a", AgentRole.of("r", StructuralRole.PEER)))
                .convergenceThreshold(0.6)
                .buildTopology();

        assertThat(topology.convergenceThreshold()).isEqualTo(0.6);
    }

    @Test
    void modelOverrides() {
        var topology = new DistributedTopologyBuilder()
                .agentProvider(noOp())
                .agentRoles(Map.of("a", AgentRole.of("r", StructuralRole.PEER)))
                .agentModelOverride("a", "claude-haiku")
                .buildTopology();

        assertThat(topology.modelOverrides()).containsEntry("a", "claude-haiku");
    }

    private static AgentProvider noOp() {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                return Multi.createFrom().empty();
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("no-op");
            }
        };
    }
}
