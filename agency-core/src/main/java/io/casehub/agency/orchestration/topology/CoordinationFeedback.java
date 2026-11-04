package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.loop.DecisionOutcome;

import java.util.Objects;

public record CoordinationFeedback(
    String agentId,
    DecisionOutcome outcome,
    FeedbackSignal signal
) {
    public CoordinationFeedback {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(signal, "signal must not be null");
    }
}
