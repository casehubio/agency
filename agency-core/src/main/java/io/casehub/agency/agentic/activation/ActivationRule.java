package io.casehub.agency.agentic.activation;

public interface ActivationRule<T> {
    boolean shouldActivate(ActivationContext<T> context);
}
