package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.topology.CoordinationFeedback;
import io.casehub.agency.orchestration.topology.DecisionOutcomeExecutor;
import io.casehub.agency.orchestration.topology.TopologyContext;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class CrossSwarmOutcomeDecorator implements DecisionOutcomeExecutor {

    private final DecisionOutcomeExecutor delegate;
    private final CoordinationBus<MultiSwarmSignal> bus;
    private final MultiSwarmRegistry registry;
    private final UUID swarmId;

    public CrossSwarmOutcomeDecorator(DecisionOutcomeExecutor delegate,
                                      CoordinationBus<MultiSwarmSignal> bus,
                                      MultiSwarmRegistry registry,
                                      UUID swarmId) {
        this.delegate = Objects.requireNonNull(delegate);
        this.bus = Objects.requireNonNull(bus);
        this.registry = Objects.requireNonNull(registry);
        this.swarmId = Objects.requireNonNull(swarmId);
    }

    @Override
    public CompletionStage<CoordinationFeedback> execute(
            String agentId, DecisionOutcome outcome, TopologyContext context) {
        if (outcome instanceof DecisionOutcome.WriteBack wb) {
            extractCrossSwarmSignals(wb);
        }
        return delegate.execute(agentId, outcome, context);
    }

    private void extractCrossSwarmSignals(DecisionOutcome.WriteBack wb) {
        var descriptor = registry.get(swarmId);
        if (descriptor == null) return;
        for (var obs : wb.observations()) {
            bus.publish(new MultiSwarmSignal.Finding(swarmId, descriptor.capabilities(),
                    System.currentTimeMillis(), obs.patternId(),
                    obs.patternId() + ":detected", obs.confidence(), Map.of()));
        }
    }
}
