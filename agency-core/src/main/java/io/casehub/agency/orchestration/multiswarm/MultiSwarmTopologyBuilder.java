package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.ConsistencyChecker;
import io.casehub.api.spi.coordination.CrossSwarmConvergenceEvaluator;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

public class MultiSwarmTopologyBuilder {

    private MultiSwarmRegistry registry;
    private ConsistencyChecker<SwarmConclusion> consistencyChecker;
    private @Nullable Supplier<RelevanceScorer> scorerFactory;
    private @Nullable Supplier<EscalationGate> gateFactory;
    private @Nullable CrossSwarmConvergenceEvaluator convergenceEvaluator;
    private int replayBufferSize = 100;
    private @Nullable AgentProvider agentProvider;

    public MultiSwarmTopologyBuilder registry(MultiSwarmRegistry registry) {
        this.registry = registry;
        return this;
    }

    public MultiSwarmTopologyBuilder consistencyChecker(ConsistencyChecker<SwarmConclusion> checker) {
        this.consistencyChecker = checker;
        return this;
    }

    public MultiSwarmTopologyBuilder weightedScoring(double relevanceThreshold, double escalationThreshold) {
        this.scorerFactory = () -> new WeightedRelevanceScorer(relevanceThreshold);
        this.gateFactory = () -> new WeightedEscalationGate(escalationThreshold);
        return this;
    }

    public MultiSwarmTopologyBuilder bjtScoring(double relevanceThreshold, double escalationThreshold) {
        this.scorerFactory = () -> new BjtRelevanceScorer(
                RelevanceNetworks.updaterWithDecay(0.95), relevanceThreshold);
        this.gateFactory = () -> new BjtEscalationGate(
                EscalationNetworks.updaterWithDecay(0.95), escalationThreshold);
        return this;
    }

    public MultiSwarmTopologyBuilder scorerFactory(Supplier<RelevanceScorer> factory) {
        this.scorerFactory = factory;
        return this;
    }

    public MultiSwarmTopologyBuilder gateFactory(Supplier<EscalationGate> factory) {
        this.gateFactory = factory;
        return this;
    }

    public MultiSwarmTopologyBuilder convergenceEvaluator(CrossSwarmConvergenceEvaluator evaluator) {
        this.convergenceEvaluator = evaluator;
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
        Objects.requireNonNull(consistencyChecker, "consistencyChecker is required");
        Supplier<RelevanceScorer> sf = scorerFactory != null ? scorerFactory
                : () -> new WeightedRelevanceScorer(0.5);
        Supplier<EscalationGate> gf = gateFactory != null ? gateFactory
                : () -> new WeightedEscalationGate(0.6);
        var topology = new MultiSwarmTopology(registry,
                sf, gf, consistencyChecker,
                convergenceEvaluator, replayBufferSize, agentProvider);
        registry.addListener(topology);
        return topology;
    }
}
