package io.casehub.agency.agentic.aggregation;

import io.casehub.agency.agentic.AgentResult;
import java.util.List;

public interface AggregationStrategy<T> {
    AggregationResult aggregate(List<AgentResult> results,
                                AggregationContext<T> context);
}
