package io.casehub.agency.orchestration.multiswarm;

import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.BayesNetworkBuilder;
import io.quarkmind.inference.bayes.OnlineParameterUpdater;

public final class RelevanceNetworks {

    private RelevanceNetworks() {}

    public static BayesNetwork standard() {
        return BayesNetworkBuilder.create("relevance")
                .variable("CapabilityOverlap", "high", "medium", "low").prior(0.2, 0.3, 0.5)
                .variable("DomainMatch", "match", "partial", "none").prior(0.2, 0.3, 0.5)
                .variable("SignalStrength", "strong", "moderate", "weak").prior(0.3, 0.4, 0.3)
                .variable("HistoricalRelevance", "high", "low").prior(0.3, 0.7)
                .variable("Relevant", "yes", "no")
                    .given("CapabilityOverlap", "DomainMatch", "SignalStrength", "HistoricalRelevance")
                    .cpt(new double[][] {
                        // CO=high, DM=match
                        {0.98, 0.02}, {0.90, 0.10},
                        {0.93, 0.07}, {0.83, 0.17},
                        {0.83, 0.17}, {0.73, 0.27},
                        // CO=high, DM=partial
                        {0.88, 0.12}, {0.78, 0.22},
                        {0.78, 0.22}, {0.68, 0.32},
                        {0.68, 0.32}, {0.53, 0.47},
                        // CO=high, DM=none
                        {0.73, 0.27}, {0.58, 0.42},
                        {0.63, 0.37}, {0.48, 0.52},
                        {0.48, 0.52}, {0.33, 0.67},
                        // CO=medium, DM=match
                        {0.83, 0.17}, {0.73, 0.27},
                        {0.73, 0.27}, {0.58, 0.42},
                        {0.58, 0.42}, {0.43, 0.57},
                        // CO=medium, DM=partial
                        {0.68, 0.32}, {0.53, 0.47},
                        {0.53, 0.47}, {0.38, 0.62},
                        {0.38, 0.62}, {0.23, 0.77},
                        // CO=medium, DM=none
                        {0.48, 0.52}, {0.33, 0.67},
                        {0.33, 0.67}, {0.23, 0.77},
                        {0.23, 0.77}, {0.13, 0.87},
                        // CO=low, DM=match
                        {0.63, 0.37}, {0.48, 0.52},
                        {0.48, 0.52}, {0.33, 0.67},
                        {0.33, 0.67}, {0.18, 0.82},
                        // CO=low, DM=partial
                        {0.43, 0.57}, {0.28, 0.72},
                        {0.28, 0.72}, {0.18, 0.82},
                        {0.18, 0.82}, {0.08, 0.92},
                        // CO=low, DM=none
                        {0.23, 0.77}, {0.13, 0.87},
                        {0.13, 0.87}, {0.08, 0.92},
                        {0.08, 0.92}, {0.03, 0.97}
                    })
                .build();
    }

    public static OnlineParameterUpdater updaterWithDecay(double decayFactor) {
        return OnlineParameterUpdater.create(standard(), decayFactor);
    }
}
