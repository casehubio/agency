package io.casehub.agency.orchestration.topology;

import io.casehub.agency.core.AgentResult;
import io.casehub.agency.orchestration.aggregation.AggregationContext;
import io.casehub.agency.orchestration.aggregation.AggregationStrategy;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class AggregationReasoner<C> implements ContentSummariser<ObservationEvent, DecisionOutcome> {

    private final AggregationStrategy<C> strategy;
    private final C coordinationContext;

    AggregationReasoner(AggregationStrategy<C> strategy,
                        C coordinationContext) {
        this.strategy = strategy;
        this.coordinationContext = coordinationContext;
    }

    @Override
    public CompletionStage<DecisionOutcome> summarise(
            List<ObservationEvent> items, @Nullable DecisionOutcome previous) {
        var agentResults = items.stream()
                .filter(e -> "member-completed".equals(e.key()))
                .map(e -> (AgentResult) e.value())
                .toList();
        var ctx = new AggregationContext<>(coordinationContext);
        var result = strategy.aggregate(agentResults, ctx);
        return CompletableFuture.completedFuture(new DecisionOutcome.Aggregate(result));
    }
}
