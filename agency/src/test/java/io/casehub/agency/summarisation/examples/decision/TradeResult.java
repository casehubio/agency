package io.casehub.agency.summarisation.examples.decision;

import java.time.Duration;
import java.time.Instant;

public record TradeResult(
        String tradeId, String step, Instant timestamp,
        String outcome, Duration elapsed
) implements TradingSignal {}
