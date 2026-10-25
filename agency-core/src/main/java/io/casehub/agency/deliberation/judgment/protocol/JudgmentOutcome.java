package io.casehub.agency.deliberation.judgment.protocol;

public enum JudgmentOutcome {
    PENDING,
    APPROVED,
    REJECTED,
    ESCALATED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
