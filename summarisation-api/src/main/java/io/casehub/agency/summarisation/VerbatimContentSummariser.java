package io.casehub.agency.summarisation;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class VerbatimContentSummariser<T> implements ContentSummariser<T, String> {

    private final Function<T, String> renderer;

    public VerbatimContentSummariser(Function<T, String> renderer) {
        this.renderer = renderer;
    }

    @Override
    public CompletionStage<String> summarise(List<T> items, @Nullable String previous) {
        var sb = new StringBuilder();
        if (previous != null && !previous.isBlank()) {
            sb.append(previous).append("\n\n");
        }
        for (var item : items) {
            sb.append("- ").append(renderer.apply(item)).append('\n');
        }
        return CompletableFuture.completedFuture(sb.toString().stripTrailing());
    }
}
