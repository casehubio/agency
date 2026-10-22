package io.casehub.blocks.summarisation.examples.decision;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TradeStepSummary(
        String tradeId, String step,
        List<Map<String, String>> signalFacts,
        Instant from, Instant to
) {}
