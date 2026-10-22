package io.casehub.agency.agentic.aggregation;

import io.casehub.agency.agentic.AgentResult;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface BidExtractor {
    @Nullable Bid extract(AgentResult result, int round);
}
