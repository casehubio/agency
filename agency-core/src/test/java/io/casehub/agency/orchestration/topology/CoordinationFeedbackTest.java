package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CoordinationFeedbackTest {

    static final DecisionOutcome OUTCOME =
        new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());

    @Test
    void creates_with_all_fields() {
        var fb = new CoordinationFeedback("agent-1", OUTCOME, FeedbackSignal.SUCCESS);
        assertThat(fb.agentId()).isEqualTo("agent-1");
        assertThat(fb.outcome()).isEqualTo(OUTCOME);
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void rejects_null_agentId() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CoordinationFeedback(null, OUTCOME, FeedbackSignal.SUCCESS))
            .withMessageContaining("agentId");
    }

    @Test
    void rejects_null_outcome() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CoordinationFeedback("agent-1", null, FeedbackSignal.SUCCESS))
            .withMessageContaining("outcome");
    }

    @Test
    void rejects_null_signal() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CoordinationFeedback("agent-1", OUTCOME, null))
            .withMessageContaining("signal");
    }

    @Test
    void feedback_signal_has_four_values() {
        assertThat(FeedbackSignal.values()).containsExactly(
            FeedbackSignal.SUCCESS, FeedbackSignal.PARTIAL,
            FeedbackSignal.FAILURE, FeedbackSignal.SCOPE_VIOLATION);
    }
}
