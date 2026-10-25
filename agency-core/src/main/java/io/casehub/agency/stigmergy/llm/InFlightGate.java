package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.summarisation.EmissionPolicy;
import io.casehub.agency.summarisation.LevelEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletionStage;

public final class InFlightGate<E, R> {

    private final ContentSummariser<E, R> delegate;
    private volatile CompletionStage<?> inflight;

    public InFlightGate(ContentSummariser<E, R> delegate) {
        this.delegate = delegate;
    }

    public ContentSummariser<E, R> summariser() {
        return (items, previous) -> {
            var result = delegate.summarise(items, previous);
            inflight = result;
            result.whenComplete((r, t) -> {
                if (inflight == result) {
                    inflight = null;
                }
            });
            return result;
        };
    }

    public <S> EmissionPolicy<E, S> gating(EmissionPolicy<E, S> wrapped) {
        return (List<LevelEvent<E>> buffered, @Nullable S state, long now) -> {
            if (isInFlight()) return false;
            return wrapped.shouldEmit(buffered, state, now);
        };
    }

    private boolean isInFlight() {
        var current = inflight;
        if (current == null) return false;
        return !current.toCompletableFuture().isDone();
    }
}
