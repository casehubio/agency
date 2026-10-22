package io.casehub.agency.deliberation.intention;

public enum IntentionStatus {
    FORMED,
    ACTIVE,
    RECONSIDERING,
    DROPPED,
    FULFILLED;

    public boolean isTerminal() {
        return this == DROPPED || this == FULFILLED;
    }

    public boolean isActive() {
        return this == ACTIVE || this == RECONSIDERING;
    }
}
