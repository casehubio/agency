package io.casehub.agency.deliberation.judgment;

@FunctionalInterface
public interface JudgmentDispatcher {

    JudgmentResponse dispatch(JudgmentDispatchRequest request);
}
