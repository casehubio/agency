package io.casehub.agency.deliberation.negotiation;

@FunctionalInterface
public interface AcceptancePolicy {
    boolean isAccepted(NegotiationState state);
}
