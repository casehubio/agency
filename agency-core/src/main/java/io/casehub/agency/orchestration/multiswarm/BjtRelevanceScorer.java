package io.casehub.agency.orchestration.multiswarm;

import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.JunctionTree;
import io.quarkmind.inference.bayes.JunctionTreeBuilder;
import io.quarkmind.inference.bayes.BayesInstance;
import java.util.Objects;

/**
 * Bayesian junction tree relevance scorer — uses conditional independence
 * structure in the CPT for more nuanced inference than weighted scoring.
 *
 * <p>Online learning is disabled pending neocortex#856 (OnlineParameterUpdater
 * corrupts junction tree inference after update()). The CPT provides a
 * reasonable prior; the weighted scorer handles adaptive learning.
 */
public class BjtRelevanceScorer implements RelevanceScorer {

    private final JunctionTree tree;
    private final double threshold;

    public BjtRelevanceScorer(BayesNetwork network, double threshold) {
        this.tree = new JunctionTreeBuilder(Objects.requireNonNull(network)).build();
        this.threshold = threshold;
    }

    @Override
    public double score(double capabilityOverlap, double domainMatch, double signalStrength, double historicalRelevance) {
        var instance = new BayesInstance<>(tree);
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
        // no-op: blocked by neocortex#856
    }

    @Override
    public void learnIrrelevant(double capabilityOverlap, double domainMatch, double signalStrength) {
        // no-op: blocked by neocortex#856
    }

    static double[] discretize(double value) {
        if (value > 0.6) return new double[]{0.8, 0.15, 0.05};
        if (value > 0.3) return new double[]{0.15, 0.7, 0.15};
        return new double[]{0.05, 0.15, 0.8};
    }
}
