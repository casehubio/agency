package io.casehub.agency.deliberation.judgment.protocol;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record JudgmentVerdict(String reviewer, Decision decision,
                              @Nullable String reason, Instant respondedAt) {
    public enum Decision { APPROVE, REJECT }

    public JudgmentVerdict {
        Objects.requireNonNull(reviewer);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(respondedAt);
    }
}
