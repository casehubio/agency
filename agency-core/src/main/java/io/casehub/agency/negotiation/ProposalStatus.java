package io.casehub.agency.negotiation;

public enum ProposalStatus {
    ACTIVE,
    SUPERSEDED,
    ACCEPTED,
    REJECTED;

    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED;
    }
}
