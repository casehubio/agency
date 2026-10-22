package io.casehub.agency.summarisation;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ContentSummariser<T, R> {

    CompletionStage<R> summarise(List<T> items, @Nullable R previous);

    default StatefulSummariser<T, R, R> asSummariser() {
        return (batch, prev) -> {
            var items = batch.stream().map(LevelEvent::payload).toList();
            return summarise(items, prev)
                .thenApply(out -> new StatefulSummariser.SummariseResult<>(List.of(out), out));
        };
    }
}
