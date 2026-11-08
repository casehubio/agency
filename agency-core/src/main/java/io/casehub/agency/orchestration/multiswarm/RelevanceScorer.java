package io.casehub.agency.orchestration.multiswarm;

import io.quarkmind.inference.bayes.OnlineParameterUpdater;
import java.util.Map;
import java.util.Objects;

public class RelevanceScorer {

    private final OnlineParameterUpdater updater;
    private final double threshold;

    public RelevanceScorer(OnlineParameterUpdater updater, double threshold) {
        this.updater = Objects.requireNonNull(updater);
        this.threshold = threshold;
    }

    public double score(double capabilityOverlap, double domainMatch, double signalStrength, double historicalRelevance) {
        var instance = updater.getInstance();
        instance.setLikelyhood("CapabilityOverlap", discretize3(capabilityOverlap));
        instance.setLikelyhood("DomainMatch", discretize3(domainMatch));
        instance.setLikelyhood("SignalStrength", discretize3(signalStrength));
        instance.setLikelyhood("HistoricalRelevance", new double[]{historicalRelevance, 1.0 - historicalRelevance});
        instance.globalUpdate();
        var state = instance.marginalize("Relevant");
        return state.getDistribution()[0];
    }

    public boolean isRelevant(double capabilityOverlap, double domainMatch, double signalStrength, double historicalRelevance) {
        return score(capabilityOverlap, domainMatch, signalStrength, historicalRelevance) >= threshold;
    }

    public void learnRelevant(double capabilityOverlap, double domainMatch, double signalStrength) {
        String overlap = capabilityOverlap > 0.6 ? "high" : capabilityOverlap > 0.3 ? "medium" : "low";
        String domain = domainMatch > 0.6 ? "match" : domainMatch > 0.3 ? "partial" : "none";
        String strength = signalStrength > 0.7 ? "strong" : signalStrength > 0.3 ? "moderate" : "weak";
        updater.update(Map.of("CapabilityOverlap", overlap, "DomainMatch", domain,
                "SignalStrength", strength, "Relevant", "yes"));
    }

    public void learnIrrelevant(double capabilityOverlap, double domainMatch, double signalStrength) {
        String overlap = capabilityOverlap > 0.6 ? "high" : capabilityOverlap > 0.3 ? "medium" : "low";
        String domain = domainMatch > 0.6 ? "match" : domainMatch > 0.3 ? "partial" : "none";
        String strength = signalStrength > 0.7 ? "strong" : signalStrength > 0.3 ? "moderate" : "weak";
        updater.update(Map.of("CapabilityOverlap", overlap, "DomainMatch", domain,
                "SignalStrength", strength, "Relevant", "no"));
    }

    private static double[] discretize3(double value) {
        if (value > 0.6) return new double[]{0.8, 0.15, 0.05};
        if (value > 0.3) return new double[]{0.15, 0.7, 0.15};
        return new double[]{0.05, 0.15, 0.8};
    }
}
