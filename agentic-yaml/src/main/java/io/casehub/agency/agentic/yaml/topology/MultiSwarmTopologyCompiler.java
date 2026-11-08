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
            if (coord.convergenceThreshold() != null) {
                double threshold = coord.convergenceThreshold();
                builder.convergenceEvaluator(progress -> {
                    if (progress.isEmpty()) {
                        return new io.casehub.api.spi.stigmergy.ConvergenceSignal(
                                io.casehub.api.spi.stigmergy.ConvergenceState.CONSENSUS, 1.0, "No swarms");
                    }
                    boolean all = progress.values().stream()
                            .allMatch(p -> p.consensusScore() >= threshold && p.stabilityScore() >= threshold);
                    return all
                            ? new io.casehub.api.spi.stigmergy.ConvergenceSignal(
                                    io.casehub.api.spi.stigmergy.ConvergenceState.CONSENSUS, 1.0, "All converged")
                            : new io.casehub.api.spi.stigmergy.ConvergenceSignal(
                                    io.casehub.api.spi.stigmergy.ConvergenceState.PROGRESSING, 0.0, "Still progressing");
                });
            }
        }
        if (agentProvider != null) {
            builder.agentProvider(agentProvider);
        }
        return new CompiledMultiSwarm(builder.build());
    }
}
