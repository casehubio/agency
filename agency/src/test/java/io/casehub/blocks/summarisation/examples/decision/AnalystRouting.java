package io.casehub.blocks.summarisation.examples.decision;

import java.time.Instant;

public record AnalystRouting(
        String tradeId, String step, Instant timestamp,
        String selectedAnalyst, double score
) implements TradingSignal {}
