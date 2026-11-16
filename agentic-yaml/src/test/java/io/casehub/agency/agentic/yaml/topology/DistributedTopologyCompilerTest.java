package io.casehub.agency.agentic.yaml.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistributedTopologyCompilerTest {

    private final AgentProvider agentProvider = noOp();
    private final DistributedTopologyCompiler compiler = new DistributedTopologyCompiler();

    @Test
    void compilesDistributedSpec() {
        var spec = new TopologySpec.Distributed(
                null,
                Map.of("analyst", new TopologySpec.Distributed.AgentSpec("analysis", List.of("data-analysis"), null),
                       "researcher", new TopologySpec.Distributed.AgentSpec("research", List.of("web-search"), null)),
                0.8, null);

        var result = compiler.compile(spec, agentProvider);

        assertThat(result.topology()).isNotNull();
        assertThat(result.agentRoles()).hasSize(2);
        assertThat(result.topology().describeTopology(java.util.UUID.randomUUID()).topologyType())
                .isEqualTo(TopologyType.DISTRIBUTED);
    }

    @Test
    void compilesWithModelOverrides() {
        var spec = new TopologySpec.Distributed(
                null,
                Map.of("fast-agent", new TopologySpec.Distributed.AgentSpec("fast", null, "claude-haiku")),
                null, null);

        var result = compiler.compile(spec, agentProvider);

        assertThat(result.topology().modelOverrides()).containsEntry("fast-agent", "claude-haiku");
    }

    @Test
    void compilesWithCustomConvergenceThreshold() {
        var spec = new TopologySpec.Distributed(
                null,
                Map.of("a", new TopologySpec.Distributed.AgentSpec("r", null, null)),
                0.6, null);

        var result = compiler.compile(spec, agentProvider);
        assertThat(result.topology().convergenceThreshold()).isEqualTo(0.6);
    }

    @Test
    void rejectsEmptyAgents() {
        var spec = new TopologySpec.Distributed(null, Map.of(), null, null);
        assertThatThrownBy(() -> compiler.compile(spec, agentProvider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one agent");
    }

    @Test
    void rejectsNullAgents() {
        var spec = new TopologySpec.Distributed(null, null, null, null);
        assertThatThrownBy(() -> compiler.compile(spec, agentProvider))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deserializesDistributedYaml() throws Exception {
        var yaml = """
            pattern: distributed
            agents:
              analyst:
                role: analysis
                capabilities:
                  - data-analysis
              researcher:
                role: research
                model: claude-haiku
            convergenceThreshold: 0.8
            """;
        var mapper = new ObjectMapper(new YAMLFactory());
        var spec = mapper.readValue(yaml, TopologySpec.class);

        assertThat(spec).isInstanceOf(TopologySpec.Distributed.class);
        var dist = (TopologySpec.Distributed) spec;
        assertThat(dist.agents()).hasSize(2);
        assertThat(dist.convergenceThreshold()).isEqualTo(0.8);
        assertThat(dist.agents().get("researcher").model()).isEqualTo("claude-haiku");
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
