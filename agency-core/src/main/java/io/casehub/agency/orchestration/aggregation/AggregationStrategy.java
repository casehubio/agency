package io.casehub.agency.orchestration.aggregation;

import io.casehub.agency.core.AgentResult;
import java.util.List;

public interface AggregationStrategy<T> {
    AggregationResult aggregate(List<AgentResult> results,
                                AggregationContext<T> context);
}
