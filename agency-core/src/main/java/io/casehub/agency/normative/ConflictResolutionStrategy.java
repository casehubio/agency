package io.casehub.agency.normative;

import java.util.List;

@FunctionalInterface
public interface ConflictResolutionStrategy<T> {
    NormResolution<T> resolve(List<NormDecision<T>> conflicting);
}
