package io.casehub.agency.orchestration.multiswarm;

import io.quarkmind.inference.bayes.OnlineParameterUpdater;
import java.util.Map;
import java.util.Objects;

public class BjtRelevanceScorer implements RelevanceScorer {

    private final OnlineParameterUpdater updater;
    private final double threshold;

    public BjtRelevanceScorer(OnlineParameterUpdater updater, double threshold) {
        this.updater = Objects.requireNonNull(updater);
        this.threshold = threshold;
    }

    @Override
    public double score(double capabilityOverlap, double domainMatch, double signalStrength, double historicalRelevance) {
        var instance = updater.getInstance();
        instance.setLikelyhood("CapabilityOverlap", discretize(capabilityOverlap));
        instance.setLikelyhood("DomainMatch", discretize(domainMatch));
        instance.setLikelyhood("SignalStrength", discretize(signalStrength));
        instance.setLikelyhood("HistoricalRelevance", new double[]{historicalRelevance, 1.0 - historicalRelevance});
        instance.globalUpdate();
        return instance.marginalize("Relevant").getDistribution()[0];
    }

    @Override
    public double threshold() {
        return threshold;
    }

    @Override
    public void learnRelevant(double capabilityOverlap, double domainMatch, double signalStrength) {
        updater.update(Map.of(
                "CapabilityOverlap", categorize(capabilityOverlap),
                "DomainMatch", categorize(domainMatch),
                "SignalStrength", categorize(signalStrength),
                "Relevant", "yes"));
    }

    @Override
    public void learnIrrelevant(double capabilityOverlap, double domainMatch, double signalStrength) {
        updater.update(Map.of(
                "CapabilityOverlap", categorize(capabilityOverlap),
                "DomainMatch", categorize(domainMatch),
                "SignalStrength", categorize(signalStrength),
                "Relevant", "no"));
    }

    // Consistent thresholds used by BOTH score() and learn() — fixes the mismatch
    private static final double HIGH_THRESHOLD = 0.6;
    private static final double LOW_THRESHOLD = 0.3;

    static double[] discretize(double value) {
        if (value > HIGH_THRESHOLD) return new double[]{0.8, 0.15, 0.05};
        if (value > LOW_THRESHOLD) return new double[]{0.15, 0.7, 0.15};
        return new double[]{0.05, 0.15, 0.8};
    }

    static String categorize(double value) {
        if (value > HIGH_THRESHOLD) return "high";
        if (value > LOW_THRESHOLD) return "medium";
        return "low";
    }
}
