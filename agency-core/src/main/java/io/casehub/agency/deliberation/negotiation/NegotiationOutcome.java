package io.casehub.agency.deliberation.negotiation;

public enum NegotiationOutcome {
    PENDING,
    AGREED,
    DEADLOCKED,
    WITHDRAWN;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
