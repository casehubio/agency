package io.casehub.agency.deliberation.judgment;

@FunctionalInterface
public interface JudgmentTrigger<T> {

    boolean shouldYield(JudgmentContext<T> context);

    default JudgmentTrigger<T> and(JudgmentTrigger<T> other) {
        return ctx -> this.shouldYield(ctx) && other.shouldYield(ctx);
    }

    default JudgmentTrigger<T> or(JudgmentTrigger<T> other) {
        return ctx -> this.shouldYield(ctx) || other.shouldYield(ctx);
    }
}
