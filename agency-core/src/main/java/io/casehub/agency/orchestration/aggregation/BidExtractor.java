package io.casehub.agency.orchestration.aggregation;

import io.casehub.agency.core.AgentResult;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface BidExtractor {
    @Nullable Bid extract(AgentResult result, int round);
}
