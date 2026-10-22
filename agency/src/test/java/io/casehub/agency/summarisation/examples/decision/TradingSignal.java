package io.casehub.agency.summarisation.examples.decision;

import java.time.Instant;

public sealed interface TradingSignal
        permits AnalystRouting, HistoricalMatch, TradeResult {
    String tradeId();
    String step();
    Instant timestamp();
}
