package io.casehub.agency.deliberation.judgment;

import java.util.function.ToDoubleFunction;

public final class ConfidenceThreshold<T> implements JudgmentTrigger<T> {

    private final double threshold;
    private final ToDoubleFunction<JudgmentContext<T>> extractor;

    private ConfidenceThreshold(double threshold, ToDoubleFunction<JudgmentContext<T>> extractor) {
        this.threshold = threshold;
        this.extractor = extractor;
    }

    public static <T> ConfidenceThreshold<T> below(double threshold,
                                                     ToDoubleFunction<JudgmentContext<T>> extractor) {
        return new ConfidenceThreshold<>(threshold, extractor);
    }

    @Override
    public boolean shouldYield(JudgmentContext<T> context) {
        return extractor.applyAsDouble(context) < threshold;
    }
}
