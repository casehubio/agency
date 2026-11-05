package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class NoOpHeuristicSummariser<T, R> implements ContentSummariser<T, R> {

    @Override
    public CompletionStage<R> summarise(List<T> items, @Nullable R previous) {
        return CompletableFuture.completedFuture(previous);
    }
}
