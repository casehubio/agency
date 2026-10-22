package io.casehub.agency.orchestration.activation;

public class OnExplicitDispatch<T> implements ActivationRule<T> {

    @Override
    public boolean shouldActivate(ActivationContext<T> context) {
        return true;
    }
}
