package io.casehub.agency.deliberation.judgment.protocol;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentProjectionTest {

    private final JudgmentProjection projection = new JudgmentProjection(
            Set.of("alice", "bob"));

    private MessageView message(String sender, MessageType type) {
        return message(sender, type, "test content");
    }

    private MessageView message(String sender, MessageType type, String content) {
        return new MessageView(1L, UUID.randomUUID(), sender, type, content, null,
                UUID.randomUUID().toString(), null, null, null, List.of(),
                ActorType.AGENT, Instant.now(), null, 0);
    }

    @Test
    void identity_returnsPendingState() {
        var state = projection.identity();
        assertThat(state.outcome()).isEqualTo(JudgmentOutcome.PENDING);
        assertThat(state.reviewers()).containsExactlyInAnyOrder("alice", "bob");
        assertThat(state.verdicts()).isEmpty();
    }

    @Test
    void done_recordsApproval() {
        var state = projection.apply(projection.identity(),
                message("alice", MessageType.DONE));

        assertThat(state.verdicts()).containsKey("alice");
        assertThat(state.verdicts().get("alice").decision())
                .isEqualTo(JudgmentVerdict.Decision.APPROVE);
    }

    @Test
    void decline_recordsRejection() {
        var state = projection.apply(projection.identity(),
                message("alice", MessageType.DECLINE));

        assertThat(state.verdicts()).containsKey("alice");
        assertThat(state.verdicts().get("alice").decision())
                .isEqualTo(JudgmentVerdict.Decision.REJECT);
    }

    @Test
    void unknownSender_ignored() {
        var state = projection.apply(projection.identity(),
                message("charlie", MessageType.DONE));

        assertThat(state.verdicts()).isEmpty();
    }

    @Test
    void otherMessageType_capturesSubjectOnly() {
        var identity = projection.identity();
        var state = projection.apply(identity,
                message("alice", MessageType.QUERY));

        assertThat(state.verdicts()).isEmpty();
        assertThat(state.subject()).isEqualTo("test content");
    }

    @Test
    void duplicateReviewer_supersedes() {
        var state = projection.identity();
        state = projection.apply(state, message("alice", MessageType.DONE));
        state = projection.apply(state, message("alice", MessageType.DECLINE));

        assertThat(state.verdicts().get("alice").decision())
                .isEqualTo(JudgmentVerdict.Decision.REJECT);
    }

    @Test
    void firstMessage_capturedAsSubject() {
        var state = projection.apply(projection.identity(),
                message("someone", MessageType.QUERY, "review this please"));
        assertThat(state.subject()).isEqualTo("review this please");
    }

    @Test
    void terminalState_ignoresMessages() {
        var state = new JudgmentState(Set.of("alice", "bob"), java.util.Map.of(),
                JudgmentOutcome.APPROVED, "done");
        var result = projection.apply(state, message("alice", MessageType.DONE));

        assertThat(result.verdicts()).isEmpty();
        assertThat(result.outcome()).isEqualTo(JudgmentOutcome.APPROVED);
    }
}
