package io.casehub.agency.agentic.judgment;

@FunctionalInterface
public interface JudgmentDispatcher {

    JudgmentResponse dispatch(JudgmentDispatchRequest request);
}
