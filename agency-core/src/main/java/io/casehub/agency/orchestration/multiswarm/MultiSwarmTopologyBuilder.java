package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.ConsistencyChecker;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.platform.agent.AgentProvider;
import io.quarkmind.inference.bayes.BayesNetwork;
import org.jspecify.annotations.Nullable;
import java.util.Objects;

public class MultiSwarmTopologyBuilder {

    private MultiSwarmRegistry registry;
    private BayesNetwork relevanceNetwork;
    private BayesNetwork escalationNetwork;
    private ConsistencyChecker<SwarmConclusion> consistencyChecker;
    private double relevanceThreshold = 0.7;
    private double escalationThreshold = 0.8;
    private double convergenceThreshold = 0.85;
    private int replayBufferSize = 100;
    private @Nullable AgentProvider agentProvider;

    public MultiSwarmTopologyBuilder registry(MultiSwarmRegistry registry) {
        this.registry = registry;
        return this;
    }

    public MultiSwarmTopologyBuilder relevanceNetwork(BayesNetwork network) {
        this.relevanceNetwork = network;
        return this;
    }

    public MultiSwarmTopologyBuilder escalationNetwork(BayesNetwork network) {
        this.escalationNetwork = network;
        return this;
    }

    public MultiSwarmTopologyBuilder consistencyChecker(ConsistencyChecker<SwarmConclusion> checker) {
        this.consistencyChecker = checker;
        return this;
    }

    public MultiSwarmTopologyBuilder relevanceThreshold(double threshold) {
        this.relevanceThreshold = threshold;
        return this;
    }

    public MultiSwarmTopologyBuilder escalationThreshold(double threshold) {
        this.escalationThreshold = threshold;
        return this;
    }

    public MultiSwarmTopologyBuilder convergenceThreshold(double threshold) {
        this.convergenceThreshold = threshold;
        return this;
    }

    public MultiSwarmTopologyBuilder replayBufferSize(int size) {
        this.replayBufferSize = size;
        return this;
    }

    public MultiSwarmTopologyBuilder agentProvider(AgentProvider provider) {
        this.agentProvider = provider;
        return this;
    }

    public MultiSwarmTopology build() {
        Objects.requireNonNull(registry, "registry is required");
        Objects.requireNonNull(relevanceNetwork, "relevanceNetwork is required");
        Objects.requireNonNull(escalationNetwork, "escalationNetwork is required");
        Objects.requireNonNull(consistencyChecker, "consistencyChecker is required");
        var topology = new MultiSwarmTopology(registry,
                relevanceNetwork, escalationNetwork, consistencyChecker,
                relevanceThreshold, escalationThreshold, convergenceThreshold,
                replayBufferSize, agentProvider);
        registry.addListener(topology);
        return topology;
    }
}
