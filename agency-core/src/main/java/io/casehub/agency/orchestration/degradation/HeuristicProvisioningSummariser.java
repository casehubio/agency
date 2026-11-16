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
import java.util.function.IntSupplier;

public class HeuristicProvisioningSummariser implements ContentSummariser<ObservationEvent, DecisionOutcome> {

    private final SwarmFallbackStrategy          fallback;
    private final IntSupplier currentSize;
    private final IntSupplier maxSize;

    public HeuristicProvisioningSummariser(SwarmFallbackStrategy fallback,
                                           IntSupplier currentSize, IntSupplier maxSize) {
        this.fallback    = Objects.requireNonNull(fallback);
        this.currentSize = Objects.requireNonNull(currentSize);
        this.maxSize     = Objects.requireNonNull(maxSize);
    }

    @Override
    public CompletionStage<DecisionOutcome> summarise(List<ObservationEvent> items,
                                                      @Nullable DecisionOutcome previous) {
        boolean shouldProvision = fallback.fallbackProvisioningAdvice(currentSize.getAsInt(), maxSize.getAsInt());
        var advice = new ProvisioningAdvice(shouldProvision, null, null, "heuristic-fallback");
        return CompletableFuture.completedFuture(new DecisionOutcome.Provision(advice));
    }
}
