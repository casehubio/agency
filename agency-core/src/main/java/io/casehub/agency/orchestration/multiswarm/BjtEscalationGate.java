package io.casehub.agency.orchestration.multiswarm;

import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.OnlineParameterUpdater;
import java.util.Map;
import java.util.Objects;

/**
 * Bayesian junction tree escalation gate. Same per-call instance pattern
 * as {@link BjtRelevanceScorer} — appropriate for the current 4-variable
 * network where all evidence changes every call.
 */
public class BjtEscalationGate implements EscalationGate {

    private final OnlineParameterUpdater updater;
    private final double threshold;

    public BjtEscalationGate(OnlineParameterUpdater updater, double threshold) {
        this.updater = Objects.requireNonNull(updater);
        this.threshold = threshold;
    }

    public BjtEscalationGate(BayesNetwork network, double threshold) {
        this(OnlineParameterUpdater.create(network, 0.95), threshold);
    }

    @Override
    public double escalationProbability(double conflictLevel, double resourceContention,
                                        double dependencyStrength) {
        var instance = updater.getInstanceSnapshot();
        instance.setLikelyhood("ConflictIndicators", BjtRelevanceScorer.discretize(conflictLevel));
        instance.setLikelyhood("ResourceContention", new double[]{resourceContention, 1.0 - resourceContention});
        instance.setLikelyhood("DependencySignals", new double[]{dependencyStrength, 1.0 - dependencyStrength});
        instance.globalUpdate();
        return instance.marginalize("EscalateNeeded").getDistribution()[0];
    }

    @Override
    public double threshold() {
        return threshold;
    }

    @Override
    public synchronized void learnEscalated(double conflictLevel, double resourceContention,
                                            double dependencyStrength) {
        updater.update(Map.of(
                "ConflictIndicators", BjtRelevanceScorer.categorize(conflictLevel),
                "ResourceContention", resourceContention > 0.5 ? "high" : "low",
                "DependencySignals", dependencyStrength > 0.5 ? "present" : "absent",
                "EscalateNeeded", "yes"));
    }

    @Override
    public synchronized void learnNotEscalated(double conflictLevel, double resourceContention,
                                               double dependencyStrength) {
        updater.update(Map.of(
                "ConflictIndicators", BjtRelevanceScorer.categorize(conflictLevel),
                "ResourceContention", resourceContention > 0.5 ? "high" : "low",
                "DependencySignals", dependencyStrength > 0.5 ? "present" : "absent",
                "EscalateNeeded", "no"));
    }
}
