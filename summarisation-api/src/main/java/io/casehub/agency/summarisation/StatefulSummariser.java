package io.casehub.agency.summarisation;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface StatefulSummariser<IN, OUT, S> extends Summariser<IN, OUT> {

    CompletionStage<SummariseResult<OUT, S>> summarise(
            List<LevelEvent<IN>> batch, @Nullable S previousState);

    @Override
    default CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>> batch) {
        return summarise(batch, null).thenApply(SummariseResult::outputs);
    }

    record SummariseResult<OUT, S>(List<OUT> outputs, @Nullable S newState) {}
}
