package io.casehub.agency.orchestration.multiswarm;

import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.BayesNetworkBuilder;
import io.quarkmind.inference.bayes.OnlineParameterUpdater;

public final class EscalationNetworks {

    private EscalationNetworks() {}

    public static BayesNetwork standard() {
        return BayesNetworkBuilder.create("escalation")
                .variable("ConflictIndicators", "high", "medium", "low").prior(0.1, 0.2, 0.7)
                .variable("ResourceContention", "high", "low").prior(0.2, 0.8)
                .variable("DependencySignals", "present", "absent").prior(0.3, 0.7)
                .variable("EscalateNeeded", "yes", "no")
                    .given("ConflictIndicators", "ResourceContention", "DependencySignals")
                    .cpt(new double[][] {
                        {0.95, 0.05}, {0.85, 0.15}, {0.80, 0.20}, {0.60, 0.40},
                        {0.75, 0.25}, {0.55, 0.45}, {0.50, 0.50}, {0.30, 0.70},
                        {0.40, 0.60}, {0.20, 0.80}, {0.25, 0.75}, {0.10, 0.90}
                    })
                .build();
    }

    public static OnlineParameterUpdater updaterWithDecay(double decayFactor) {
        return OnlineParameterUpdater.create(standard(), decayFactor);
    }
}
