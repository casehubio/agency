package io.casehub.agency.orchestration.multiswarm;

import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.OnlineParameterUpdater;
import java.util.Map;
import java.util.Objects;

/**
 * Bayesian junction tree relevance scorer — models conditional independence
 * effects that weighted scoring cannot express (e.g. domain match matters
 * less when capability overlap is already high).
 *
 * <p><b>Generalised, not domain-specific.</b> The CPT encodes structural
 * relevance principles (capability overlap, domain match, signal strength,
 * history) that apply across all use cases. No per-domain training needed.
 *
 * <p>Maintains a single inference instance per scorer, reusing calibrated
 * clique potentials across calls. Each scorer is per-swarm (created by the
 * topology on registration). Evidence is set fresh each call and full
 * propagation is forced — the five-variable network is small enough that
 * forced propagation is sub-microsecond, and this avoids dirty-tracking
 * edge cases while the instance is retained across calls.
 *
 * <p>Online learning adapts the CPT via {@link OnlineParameterUpdater}.
 * After learning, the instance is refreshed from the rebuilt junction tree.
 */
public class BjtRelevanceScorer implements RelevanceScorer {

    private final OnlineParameterUpdater updater;
    private final double threshold;

    public BjtRelevanceScorer(OnlineParameterUpdater updater, double threshold) {
        this.updater = Objects.requireNonNull(updater);
        this.threshold = threshold;
    }

    public BjtRelevanceScorer(BayesNetwork network, double threshold) {
        this(OnlineParameterUpdater.create(network, 0.95), threshold);
    }

    @Override
    public synchronized double score(double capabilityOverlap, double domainMatch,
                                     double signalStrength, double historicalRelevance) {
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
    public synchronized void learnRelevant(double capabilityOverlap, double domainMatch, double signalStrength) {
        updater.update(Map.of(
                "CapabilityOverlap", categorize(capabilityOverlap),
                "DomainMatch", categorize(domainMatch),
                "SignalStrength", categorize(signalStrength),
                "HistoricalRelevance", "high",
                "Relevant", "yes"));
    }

    @Override
    public synchronized void learnIrrelevant(double capabilityOverlap, double domainMatch, double signalStrength) {
        updater.update(Map.of(
                "CapabilityOverlap", categorize(capabilityOverlap),
                "DomainMatch", categorize(domainMatch),
                "SignalStrength", categorize(signalStrength),
                "HistoricalRelevance", "low",
                "Relevant", "no"));
    }

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
