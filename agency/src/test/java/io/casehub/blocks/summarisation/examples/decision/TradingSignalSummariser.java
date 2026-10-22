package io.casehub.blocks.summarisation.examples.decision;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.List;
import java.util.Map;

public class TradingSignalSummariser
        implements Summariser.SyncSummariser<TradingSignal, TradeStepSummary> {

    @Override
    public List<TradeStepSummary> summarise(List<LevelEvent<TradingSignal>> batch) {
        if (batch.isEmpty()) return List.of();
        var first = batch.get(0).payload();
        var facts = batch.stream().map(e -> toFacts(e.payload())).toList();
        return List.of(new TradeStepSummary(
                first.tradeId(), first.step(), facts,
                batch.get(0).payload().timestamp(),
                batch.get(batch.size() - 1).payload().timestamp()));
    }

    static Map<String, String> toFacts(TradingSignal signal) {
        return switch (signal) {
            case AnalystRouting r -> Map.of(
                    "type", "routing",
                    "analyst", r.selectedAnalyst(),
                    "score", String.valueOf(r.score()));
            case HistoricalMatch h -> Map.of(
                    "type", "historical",
                    "matches", String.valueOf(h.matchCount()),
                    "similarity", String.valueOf(h.topSimilarity()));
            case TradeResult t -> Map.of(
                    "type", "result",
                    "outcome", t.outcome(),
                    "elapsed", t.elapsed().toString());
        };
    }
}
