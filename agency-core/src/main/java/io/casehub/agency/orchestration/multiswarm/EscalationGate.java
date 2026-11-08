package io.casehub.agency.orchestration.multiswarm;

import io.quarkmind.inference.bayes.OnlineParameterUpdater;
import java.util.Map;
import java.util.Objects;

public class EscalationGate {

    private final OnlineParameterUpdater updater;
    private final double threshold;

    public EscalationGate(OnlineParameterUpdater updater, double threshold) {
        this.updater = Objects.requireNonNull(updater);
        this.threshold = threshold;
    }

    public double escalationProbability(double conflictLevel, double resourceContention, double dependencyStrength) {
        var instance = updater.getInstance();
        instance.setLikelyhood("ConflictIndicators", discretize3(conflictLevel));
        instance.setLikelyhood("ResourceContention", new double[]{resourceContention, 1.0 - resourceContention});
        instance.setLikelyhood("DependencySignals", new double[]{dependencyStrength, 1.0 - dependencyStrength});
        instance.globalUpdate();
        return instance.marginalize("EscalateNeeded").getDistribution()[0];
    }

    public boolean shouldEscalate(double conflictLevel, double resourceContention, double dependencyStrength) {
        return escalationProbability(conflictLevel, resourceContention, dependencyStrength) >= threshold;
    }

    public void learnEscalated(double conflictLevel, double resourceContention, double dependencyStrength) {
        String conflict = conflictLevel > 0.6 ? "high" : conflictLevel > 0.3 ? "medium" : "low";
        String resource = resourceContention > 0.5 ? "high" : "low";
        String dep = dependencyStrength > 0.5 ? "present" : "absent";
        updater.update(Map.of("ConflictIndicators", conflict, "ResourceContention", resource,
                "DependencySignals", dep, "EscalateNeeded", "yes"));
    }

    public void learnNotEscalated(double conflictLevel, double resourceContention, double dependencyStrength) {
        String conflict = conflictLevel > 0.6 ? "high" : conflictLevel > 0.3 ? "medium" : "low";
        String resource = resourceContention > 0.5 ? "high" : "low";
        String dep = dependencyStrength > 0.5 ? "present" : "absent";
        updater.update(Map.of("ConflictIndicators", conflict, "ResourceContention", resource,
                "DependencySignals", dep, "EscalateNeeded", "no"));
    }

    private static double[] discretize3(double value) {
        if (value > 0.6) return new double[]{0.8, 0.15, 0.05};
        if (value > 0.3) return new double[]{0.15, 0.7, 0.15};
        return new double[]{0.05, 0.15, 0.8};
    }
}
