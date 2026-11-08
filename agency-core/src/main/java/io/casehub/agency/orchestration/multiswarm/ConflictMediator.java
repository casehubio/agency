package io.casehub.agency.orchestration.multiswarm;

@FunctionalInterface
public interface ConflictMediator {
    MediationResult mediate(ConflictContext context);
}
