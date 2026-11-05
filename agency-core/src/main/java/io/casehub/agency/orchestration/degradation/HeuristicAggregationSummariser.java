package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.core.AgentResult;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.swarm.SwarmFallbackStrategy;
import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HeuristicAggregationSummariser implements ContentSummariser<ObservationEvent, DecisionOutcome> {

    private final SwarmFallbackStrategy fallback;

    public HeuristicAggregationSummariser(SwarmFallbackStrategy fallback) {
        this.fallback = Objects.requireNonNull(fallback);
    }

    @Override
    public CompletionStage<DecisionOutcome> summarise(List<ObservationEvent> items,
                                                       @Nullable DecisionOutcome previous) {
        var results = items.stream()
            .filter(e -> "member-completed".equals(e.key()))
            .map(e -> (AgentResult) e.value())
            .toList();
        var aggregation = fallback.fallbackAggregation(results);
        return CompletableFuture.completedFuture(new DecisionOutcome.Aggregate(aggregation));
    }
}
