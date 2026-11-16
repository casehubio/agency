package io.casehub.agency.agentic.yaml.topology;

import io.casehub.agency.orchestration.distributed.DistributedTopologyBuilder;
import io.casehub.agency.orchestration.topology.DistributedTopology;
import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.platform.agent.AgentProvider;

import java.util.LinkedHashMap;
import java.util.Map;

public class DistributedTopologyCompiler {

    public record CompiledDistributed(
            DistributedTopology topology,
            Map<String, AgentRole> agentRoles
    ) {}

    public CompiledDistributed compile(TopologySpec.Distributed spec, AgentProvider agentProvider) {
        if (spec.agents() == null || spec.agents().isEmpty()) {
            throw new IllegalArgumentException("Distributed topology requires at least one agent");
        }

        var agentRoles = buildAgentRoles(spec.agents());
        var builder = new DistributedTopologyBuilder()
                .agentProvider(agentProvider)
                .agentRoles(agentRoles);

        if (spec.convergenceThreshold() != null) {
            builder.convergenceThreshold(spec.convergenceThreshold());
        }

        for (var entry : spec.agents().entrySet()) {
            if (entry.getValue().model() != null) {
                builder.agentModelOverride(entry.getKey(), entry.getValue().model());
            }
        }

        return new CompiledDistributed(builder.buildTopology(), agentRoles);
    }

    private static Map<String, AgentRole> buildAgentRoles(Map<String, TopologySpec.Distributed.AgentSpec> agents) {
        var result = new LinkedHashMap<String, AgentRole>();
        for (var entry : agents.entrySet()) {
            result.put(entry.getKey(), AgentRole.of(entry.getValue().role(), StructuralRole.PEER));
        }
        return result;
    }
}
