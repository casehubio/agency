package io.casehub.agency.agentic.belief;

@FunctionalInterface
public interface ConsistencyChecker<T> {
    boolean isConsistent(BeliefSet<T> beliefs);
}
