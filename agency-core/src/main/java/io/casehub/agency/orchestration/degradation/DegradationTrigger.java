package io.casehub.agency.orchestration.degradation;

@FunctionalInterface
public interface DegradationTrigger {
    boolean shouldDegrade();
}
