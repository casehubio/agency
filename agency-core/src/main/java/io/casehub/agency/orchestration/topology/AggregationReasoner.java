package io.casehub.agency.orchestration.topology;

import io.casehub.agency.core.AgentResult;
import io.casehub.agency.orchestration.aggregation.AggregationContext;
import io.casehub.agency.orchestration.aggregation.AggregationStrategy;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class AggregationReasoner implements ContentSummariser<ObservationEvent, DecisionOutcome> {

    private final AggregationStrategy<SwarmContext> strategy;
    private final SwarmContext swarmContext;

    AggregationReasoner(AggregationStrategy<SwarmContext> strategy,
                        SwarmContext swarmContext) {
        this.strategy = strategy;
        this.swarmContext = swarmContext;
    }

    @Override
    public CompletionStage<DecisionOutcome> summarise(
            List<ObservationEvent> items, @Nullable DecisionOutcome previous) {
        var agentResults = items.stream()
                .filter(e -> "member-completed".equals(e.key()))
                .map(e -> (AgentResult) e.value())
                .toList();
        var ctx = new AggregationContext<>(swarmContext);
        var result = strategy.aggregate(agentResults, ctx);
        return CompletableFuture.completedFuture(new DecisionOutcome.Aggregate(result));
    }
}
