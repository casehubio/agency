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
 * <p>Each score call creates a fresh {@code BayesInstance} from the cached
 * junction tree via {@code getInstance()}. This is correct by design for
 * the current 5-variable network: all four evidence variables change on
 * every call (no partial evidence reuse), and the tree has ~3 cliques
 * (incremental subtree propagation saves nothing at this size). The
 * instance creation copies ~20 doubles — sub-microsecond.
 *
 * <p>For larger networks (20+ variables, 200+ cliques), the architecture
 * supports stored instances with evidence retraction for incremental
 * subtree-only re-propagation. That path becomes valuable when the copy
 * cost and full-propagation cost outweigh the retract+set+partial-propagate
 * path.
 *
 * <p>Thread safety: each {@code score()} call is independent (no shared
 * mutable state). {@code learnRelevant/learnIrrelevant} are synchronized
 * because they modify the shared {@link OnlineParameterUpdater}.
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
    public double score(double capabilityOverlap, double domainMatch,
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
