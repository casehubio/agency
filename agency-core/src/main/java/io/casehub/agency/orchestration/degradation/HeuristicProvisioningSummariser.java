package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.swarm.SwarmFallbackStrategy;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor.ProvisioningAdvice;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HeuristicProvisioningSummariser implements ContentSummariser<ObservationEvent, DecisionOutcome> {

    private final SwarmFallbackStrategy fallback;
    private final int currentSize;
    private final int maxSize;

    public HeuristicProvisioningSummariser(SwarmFallbackStrategy fallback,
                                           int currentSize, int maxSize) {
        this.fallback = Objects.requireNonNull(fallback);
        this.currentSize = currentSize;
        this.maxSize = maxSize;
    }

    @Override
    public CompletionStage<DecisionOutcome> summarise(List<ObservationEvent> items,
                                                       @Nullable DecisionOutcome previous) {
        boolean shouldProvision = fallback.fallbackProvisioningAdvice(currentSize, maxSize);
        var advice = new ProvisioningAdvice(shouldProvision, null, null, "heuristic-fallback");
        return CompletableFuture.completedFuture(new DecisionOutcome.Provision(advice));
    }
}
