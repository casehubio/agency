package io.casehub.agency.deliberation.judgment.protocol;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record JudgmentState(Set<String> reviewers,
                            Map<String, JudgmentVerdict> verdicts,
                            JudgmentOutcome outcome,
                            @Nullable String subject) {
    public JudgmentState {
        Objects.requireNonNull(reviewers);
        Objects.requireNonNull(verdicts);
        Objects.requireNonNull(outcome);
    }

    public Set<String> pendingReviewers() {
        var pending = new LinkedHashSet<>(reviewers);
        pending.removeAll(verdicts.keySet());
        return Set.copyOf(pending);
    }

    public boolean hasAllReviewed() {
        return verdicts.keySet().containsAll(reviewers);
    }

    public long approvalCount() {
        return verdicts.values().stream()
                .filter(v -> v.decision() == JudgmentVerdict.Decision.APPROVE)
                .count();
    }

    public long rejectionCount() {
        return verdicts.values().stream()
                .filter(v -> v.decision() == JudgmentVerdict.Decision.REJECT)
                .count();
    }
}
