package io.casehub.agency.orchestration.multiswarm;

/**
 * Scores the relevance of a cross-swarm signal to a target swarm.
 *
 * <p>All four input features are domain-agnostic structural properties —
 * capability overlap (Jaccard), domain tag match, signal confidence, and
 * historical relevance. The CPT and weights encode general principles about
 * inter-swarm information relevance, not domain-specific knowledge.
 * No per-domain training is needed.
 *
 * <p>Two implementations: {@link WeightedRelevanceScorer} (default — linear
 * combination with online gradient-descent learning) and
 * {@link BjtRelevanceScorer} (Bayesian junction tree — models conditional
 * independence effects that linear scoring cannot express, e.g. domain match
 * matters less when capability overlap is already high).
 *
 * @see WeightedRelevanceScorer
 * @see BjtRelevanceScorer
 */
public interface RelevanceScorer {
    double score(double capabilityOverlap, double domainMatch, double signalStrength, double historicalRelevance);

    default boolean isRelevant(double capabilityOverlap, double domainMatch, double signalStrength, double historicalRelevance) {
        return score(capabilityOverlap, domainMatch, signalStrength, historicalRelevance) >= threshold();
    }

    double threshold();

    void learnRelevant(double capabilityOverlap, double domainMatch, double signalStrength);

    void learnIrrelevant(double capabilityOverlap, double domainMatch, double signalStrength);
}
