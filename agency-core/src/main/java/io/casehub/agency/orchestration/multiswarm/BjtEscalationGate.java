package io.casehub.agency.orchestration.multiswarm;

import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.JunctionTree;
import io.quarkmind.inference.bayes.JunctionTreeBuilder;
import io.quarkmind.inference.bayes.BayesInstance;
import java.util.Objects;

/**
 * Bayesian junction tree escalation gate — uses conditional independence
 * structure for multi-factor escalation decisions.
 *
 * <p>Online learning is disabled pending neocortex#856.
 */
public class BjtEscalationGate implements EscalationGate {

    private final JunctionTree tree;
    private final double threshold;

    public BjtEscalationGate(BayesNetwork network, double threshold) {
        this.tree = new JunctionTreeBuilder(Objects.requireNonNull(network)).build();
        this.threshold = threshold;
    }

    @Override
    public double escalationProbability(double conflictLevel, double resourceContention, double dependencyStrength) {
        var instance = new BayesInstance<>(tree);
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
    public void learnEscalated(double conflictLevel, double resourceContention, double dependencyStrength) {
        // no-op: blocked by neocortex#856
    }

    @Override
    public void learnNotEscalated(double conflictLevel, double resourceContention, double dependencyStrength) {
        // no-op: blocked by neocortex#856
    }
}
