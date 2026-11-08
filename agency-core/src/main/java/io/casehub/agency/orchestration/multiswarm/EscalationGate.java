package io.casehub.agency.orchestration.multiswarm;

public interface EscalationGate {
    double escalationProbability(double conflictLevel, double resourceContention, double dependencyStrength);

    default boolean shouldEscalate(double conflictLevel, double resourceContention, double dependencyStrength) {
        return escalationProbability(conflictLevel, resourceContention, dependencyStrength) >= threshold();
    }

    double threshold();

    void learnEscalated(double conflictLevel, double resourceContention, double dependencyStrength);

    void learnNotEscalated(double conflictLevel, double resourceContention, double dependencyStrength);
}
