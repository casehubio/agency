package io.casehub.agency.orchestration.topology;

import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.routing.RoutingContext;
import io.casehub.agency.orchestration.routing.RoutingStrategy;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

class RoutingReasoner implements ContentSummariser<ObservationEvent, DecisionOutcome> {

    private final RoutingStrategy<SwarmContext> strategy;
    private final SwarmContext swarmContext;
    private final Supplier<List<RoutingCandidate>> candidateSupplier;

    RoutingReasoner(RoutingStrategy<SwarmContext> strategy,
                    SwarmContext swarmContext,
                    Supplier<List<RoutingCandidate>> candidateSupplier) {
        this.strategy = strategy;
        this.swarmContext = swarmContext;
        this.candidateSupplier = candidateSupplier;
    }

    @Override
    public CompletionStage<DecisionOutcome> summarise(
            List<ObservationEvent> items, @Nullable DecisionOutcome previous) {
        var candidates = candidateSupplier.get();
        var routingCtx = new RoutingContext<>("swarm-team-formation", candidates, swarmContext);
        var decision = strategy.route(routingCtx);
        return CompletableFuture.completedFuture(new DecisionOutcome.Route(decision));
    }
}
