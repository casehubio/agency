package io.casehub.agency.orchestration.multiswarm;

public interface RelevanceScorer {
    double score(double capabilityOverlap, double domainMatch, double signalStrength, double historicalRelevance);

    default boolean isRelevant(double capabilityOverlap, double domainMatch, double signalStrength, double historicalRelevance) {
        return score(capabilityOverlap, domainMatch, signalStrength, historicalRelevance) >= threshold();
    }

    double threshold();

    void learnRelevant(double capabilityOverlap, double domainMatch, double signalStrength);

    void learnIrrelevant(double capabilityOverlap, double domainMatch, double signalStrength);
}
