package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.swarm.SwarmFallbackStrategy;
import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class HeuristicRoutingSummariser implements ContentSummariser<ObservationEvent, DecisionOutcome> {

    private final SwarmFallbackStrategy fallback;
    private final Supplier<List<RoutingCandidate>> candidateSupplier;

    public HeuristicRoutingSummariser(SwarmFallbackStrategy fallback,
                                      Supplier<List<RoutingCandidate>> candidateSupplier) {
        this.fallback = Objects.requireNonNull(fallback);
        this.candidateSupplier = Objects.requireNonNull(candidateSupplier);
    }

    @Override
    public CompletionStage<DecisionOutcome> summarise(List<ObservationEvent> items,
                                                       @Nullable DecisionOutcome previous) {
        var decision = fallback.fallbackRouting(candidateSupplier.get());
        return CompletableFuture.completedFuture(new DecisionOutcome.Route(decision));
    }
}
