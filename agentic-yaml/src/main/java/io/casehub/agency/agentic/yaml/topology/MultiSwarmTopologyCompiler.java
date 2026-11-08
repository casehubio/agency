package io.casehub.agency.agentic.yaml.topology;

import io.casehub.agency.core.yaml.spec.MultiSwarmCoordinationSpec;
import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.casehub.agency.orchestration.multiswarm.DomainConsistencyChecker;
import io.casehub.agency.orchestration.multiswarm.EscalationNetworks;
import io.casehub.agency.orchestration.multiswarm.MultiSwarmTopology;
import io.casehub.agency.orchestration.multiswarm.RelevanceNetworks;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

public class MultiSwarmTopologyCompiler {

    public record CompiledMultiSwarm(MultiSwarmTopology topology) {}

    public CompiledMultiSwarm compile(TopologySpec.MultiSwarm spec,
                                      @Nullable AgentProvider agentProvider,
                                      MultiSwarmRegistry registry) {
        if (spec.swarms() == null || spec.swarms().isEmpty()) {
            throw new IllegalArgumentException("Multi-swarm topology requires at least one child swarm");
        }
        var builder = MultiSwarmTopology.builder()
                .registry(registry)
                .relevanceNetwork(RelevanceNetworks.standard())
                .escalationNetwork(EscalationNetworks.standard())
                .consistencyChecker(new DomainConsistencyChecker());

        var coord = spec.coordination();
        if (coord != null) {
            if (coord.relevanceThreshold() != null) builder.relevanceThreshold(coord.relevanceThreshold());
            if (coord.escalationThreshold() != null) builder.escalationThreshold(coord.escalationThreshold());
            if (coord.convergenceThreshold() != null) builder.convergenceThreshold(coord.convergenceThreshold());
        }
        if (agentProvider != null) {
            builder.agentProvider(agentProvider);
        }
        return new CompiledMultiSwarm(builder.build());
    }
}
