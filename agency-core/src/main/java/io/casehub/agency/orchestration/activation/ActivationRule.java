package io.casehub.agency.orchestration.activation;

public interface ActivationRule<T> {
    boolean shouldActivate(ActivationContext<T> context);
}
