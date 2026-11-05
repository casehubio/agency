package io.casehub.agency.orchestration.degradation;

public class CascadeDegradationTrigger implements DegradationTrigger {

    private volatile boolean activated;

    public void activate() {
        activated = true;
    }

    @Override
    public boolean shouldDegrade() {
        return activated;
    }
}
