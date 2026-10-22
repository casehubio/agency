package io.casehub.agency.summarisation.examples.decision;

import java.time.Instant;

public record HistoricalMatch(
        String tradeId, String step, Instant timestamp,
        int matchCount, double topSimilarity
) implements TradingSignal {}
