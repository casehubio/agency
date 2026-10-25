package io.casehub.agency.deliberation.judgment.protocol;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentFoldTest {

    private JudgmentState emptyState() {
        return new JudgmentState(Set.of("alice", "bob"), Map.of(),
                JudgmentOutcome.PENDING, null);
    }

    @Test
    void submitVerdict_addsVerdict() {
        var verdict = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var state = JudgmentFold.submitVerdict(emptyState(), verdict);

        assertThat(state.verdicts()).containsKey("alice");
        assertThat(state.verdicts().get("alice").decision())
                .isEqualTo(JudgmentVerdict.Decision.APPROVE);
    }

    @Test
    void submitVerdict_duplicateReviewer_supersedes() {
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.REJECT,
                "changed mind", Instant.now());
        var state = JudgmentFold.submitVerdict(emptyState(), v1);
        state = JudgmentFold.submitVerdict(state, v2);

        assertThat(state.verdicts().get("alice").decision())
                .isEqualTo(JudgmentVerdict.Decision.REJECT);
    }

    @Test
    void approve_setsOutcome() {
        var state = JudgmentFold.approve(emptyState());
        assertThat(state.outcome()).isEqualTo(JudgmentOutcome.APPROVED);
    }

    @Test
    void reject_setsOutcome() {
        var state = JudgmentFold.reject(emptyState());
        assertThat(state.outcome()).isEqualTo(JudgmentOutcome.REJECTED);
    }

    @Test
    void escalate_setsOutcome() {
        var state = JudgmentFold.escalate(emptyState());
        assertThat(state.outcome()).isEqualTo(JudgmentOutcome.ESCALATED);
    }

    @Test
    void pendingReviewers_returnsUnreviewed() {
        var verdict = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var state = JudgmentFold.submitVerdict(emptyState(), verdict);

        assertThat(state.pendingReviewers()).containsExactly("bob");
    }

    @Test
    void hasAllReviewed_falseWhenPending() {
        assertThat(emptyState().hasAllReviewed()).isFalse();
    }

    @Test
    void hasAllReviewed_trueWhenComplete() {
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("bob", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var state = JudgmentFold.submitVerdict(emptyState(), v1);
        state = JudgmentFold.submitVerdict(state, v2);

        assertThat(state.hasAllReviewed()).isTrue();
    }

    @Test
    void approvalCount_countsCorrectly() {
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("bob", JudgmentVerdict.Decision.REJECT,
                null, Instant.now());
        var state = JudgmentFold.submitVerdict(emptyState(), v1);
        state = JudgmentFold.submitVerdict(state, v2);

        assertThat(state.approvalCount()).isEqualTo(1);
        assertThat(state.rejectionCount()).isEqualTo(1);
    }

    @Test
    void outcome_isTerminal() {
        assertThat(JudgmentOutcome.PENDING.isTerminal()).isFalse();
        assertThat(JudgmentOutcome.APPROVED.isTerminal()).isTrue();
        assertThat(JudgmentOutcome.REJECTED.isTerminal()).isTrue();
        assertThat(JudgmentOutcome.ESCALATED.isTerminal()).isTrue();
    }
}
