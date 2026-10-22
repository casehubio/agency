package io.casehub.agency.deliberation.belief;

@FunctionalInterface
public interface ConsistencyChecker<T> {
    boolean isConsistent(BeliefSet<T> beliefs);
}
