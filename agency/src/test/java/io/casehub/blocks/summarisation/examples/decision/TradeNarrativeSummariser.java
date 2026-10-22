package io.casehub.blocks.summarisation.examples.decision;

import io.casehub.blocks.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TradeNarrativeSummariser
        implements ContentSummariser<TradeStepSummary, TradeNarrative> {

    @Override
    public CompletionStage<TradeNarrative> summarise(
            List<TradeStepSummary> items, @Nullable TradeNarrative previous) {
        var steps = new ArrayList<String>();
        if (previous != null) steps.addAll(previous.steps());
        items.forEach(s -> steps.add(s.step()));

        var sb = new StringBuilder();
        if (previous != null) sb.append(previous.explanation()).append(" ");
        items.forEach(s -> sb.append("Step ").append(s.step()).append(": ")
                .append(s.signalFacts().size()).append(" signals. "));

        return CompletableFuture.completedFuture(
                new TradeNarrative(items.get(0).tradeId(), steps,
                        sb.toString().strip(), 0.85, Instant.now()));
    }
}
