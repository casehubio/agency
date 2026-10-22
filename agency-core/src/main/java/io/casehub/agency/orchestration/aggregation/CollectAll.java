package io.casehub.agency.orchestration.aggregation;

import io.casehub.agency.core.AgentResult;

import java.util.List;

public class CollectAll<T> implements AggregationStrategy<T> {

    @Override
    public AggregationResult aggregate(List<AgentResult> results,
                                       AggregationContext<T> context) {
        return new AggregationResult.Resolved(List.copyOf(results));
    }
}
