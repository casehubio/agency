package io.casehub.agency.negotiation;

public enum NegotiationOutcome {
    PENDING,
    AGREED,
    DEADLOCKED,
    WITHDRAWN;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
