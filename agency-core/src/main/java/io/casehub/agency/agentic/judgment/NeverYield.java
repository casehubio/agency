package io.casehub.agency.agentic.judgment;

public final class NeverYield<T> implements JudgmentTrigger<T> {

    @Override
    public boolean shouldYield(JudgmentContext<T> context) {
        return false;
    }
}
