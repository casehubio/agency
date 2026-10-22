package io.casehub.blocks.summarisation.examples.decision;

import java.time.Instant;

public record HistoricalMatch(
        String tradeId, String step, Instant timestamp,
        int matchCount, double topSimilarity
) implements TradingSignal {}
