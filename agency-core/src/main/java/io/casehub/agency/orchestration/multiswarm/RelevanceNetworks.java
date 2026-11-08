package io.casehub.agency.orchestration.multiswarm;

import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.BayesNetworkBuilder;

public final class RelevanceNetworks {

    private RelevanceNetworks() {}

    public static BayesNetwork standard() {
        // Parents: CO(3) × DM(3) × SS(3) × HR(2) = 54 rows, each {P(yes), P(no)}
        return BayesNetworkBuilder.create("relevance")
                .variable("CapabilityOverlap", "high", "medium", "low").prior(0.2, 0.3, 0.5)
                .variable("DomainMatch", "match", "partial", "none").prior(0.2, 0.3, 0.5)
                .variable("SignalStrength", "strong", "moderate", "weak").prior(0.3, 0.4, 0.3)
                .variable("HistoricalRelevance", "high", "low").prior(0.3, 0.7)
                .variable("Relevant", "yes", "no")
                    .given("CapabilityOverlap", "DomainMatch", "SignalStrength", "HistoricalRelevance")
                    .cpt(new double[][] {
                        // CO=high, DM=match
                        {0.95, 0.05}, {0.85, 0.15},  // SS=strong, HR=high/low
                        {0.90, 0.10}, {0.80, 0.20},  // SS=moderate
                        {0.80, 0.20}, {0.70, 0.30},  // SS=weak
                        // CO=high, DM=partial
                        {0.85, 0.15}, {0.75, 0.25},
                        {0.75, 0.25}, {0.65, 0.35},
                        {0.65, 0.35}, {0.50, 0.50},
                        // CO=high, DM=none
                        {0.70, 0.30}, {0.55, 0.45},
                        {0.60, 0.40}, {0.45, 0.55},
                        {0.45, 0.55}, {0.30, 0.70},
                        // CO=medium, DM=match
                        {0.80, 0.20}, {0.70, 0.30},
                        {0.70, 0.30}, {0.55, 0.45},
                        {0.55, 0.45}, {0.40, 0.60},
                        // CO=medium, DM=partial
                        {0.65, 0.35}, {0.50, 0.50},
                        {0.50, 0.50}, {0.35, 0.65},
                        {0.35, 0.65}, {0.20, 0.80},
                        // CO=medium, DM=none
                        {0.45, 0.55}, {0.30, 0.70},
                        {0.30, 0.70}, {0.20, 0.80},
                        {0.20, 0.80}, {0.10, 0.90},
                        // CO=low, DM=match
                        {0.60, 0.40}, {0.45, 0.55},
                        {0.45, 0.55}, {0.30, 0.70},
                        {0.30, 0.70}, {0.15, 0.85},
                        // CO=low, DM=partial
                        {0.40, 0.60}, {0.25, 0.75},
                        {0.25, 0.75}, {0.15, 0.85},
                        {0.15, 0.85}, {0.05, 0.95},
                        // CO=low, DM=none
                        {0.20, 0.80}, {0.10, 0.90},
                        {0.10, 0.90}, {0.05, 0.95},
                        {0.05, 0.95}, {0.02, 0.98}
                    })
                .build();
    }
}
