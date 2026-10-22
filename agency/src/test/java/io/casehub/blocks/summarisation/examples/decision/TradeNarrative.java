package io.casehub.blocks.summarisation.examples.decision;

import java.time.Instant;
import java.util.List;

public record TradeNarrative(
        String tradeId, List<String> steps, String explanation,
        double confidence, Instant producedAt
) {}
