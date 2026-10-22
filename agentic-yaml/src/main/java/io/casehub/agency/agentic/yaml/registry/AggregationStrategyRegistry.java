package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.orchestration.aggregation.AggregationStrategy;
import io.casehub.agency.orchestration.aggregation.CollectAll;
import io.casehub.agency.orchestration.aggregation.MajorityVote;
import io.casehub.agency.orchestration.aggregation.PassThrough;
import io.casehub.agency.core.yaml.spec.AggregationSpec;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

public class AggregationStrategyRegistry {

    private @Nullable Function<AggregationSpec, @Nullable AggregationStrategy<?>> fallback;

    public void registerFallback(Function<AggregationSpec, @Nullable AggregationStrategy<?>> fallback) {
        this.fallback = fallback;
    }

    @SuppressWarnings("unchecked")
    public <T> AggregationStrategy<T> resolve(AggregationSpec spec) {
        if (fallback != null) {
            var result = fallback.apply(spec);
            if (result != null) return (AggregationStrategy<T>) result;
        }
        return switch (spec) {
            case AggregationSpec.PassThrough pt -> new PassThrough<>();
            case AggregationSpec.CollectAll ca -> new CollectAll<>();
            case AggregationSpec.MajorityVote mv -> new MajorityVote<>();
            case AggregationSpec.Auction au ->
                    throw new UnsupportedOperationException(
                            "auction requires BidExtractor — use CDI injection");
        };
    }
}
