package io.casehub.agency.negotiation;

@FunctionalInterface
public interface AcceptancePolicy {
    boolean isAccepted(NegotiationState state);
}
