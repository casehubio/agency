package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.api.model.stigmergy.HealthScoreSnapshot;
import io.casehub.api.model.stigmergy.RegressionVerdict;
import io.casehub.api.spi.improvement.RegressionEvaluator;

import java.util.UUID;

public class SwarmPromptRegressionEvaluator implements RegressionEvaluator {

    static final String PREFIX = "swarm-prompt:";
    private final double regressionThreshold;

    public SwarmPromptRegressionEvaluator(double regressionThreshold) {
        this.regressionThreshold = regressionThreshold;
    }

    public SwarmPromptRegressionEvaluator() {
        this(0.1);
    }

    @Override
    public String evaluatorId() {
        return "swarm-prompt-quality";
    }

    @Override
    public String domainId() {
        return SwarmPromptCategoryProvider.DOMAIN_ID;
    }

    @Override
    public RegressionVerdict evaluate(UUID caseId, HealthScoreSnapshot baseline,
                                      HealthScoreSnapshot current, String category) {
        double maxDrop = 0.0;
        String worstSignature = null;

        for (var entry : baseline.componentScores().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX)) continue;
            var currentScore = current.componentScores().get(entry.getKey());
            if (currentScore == null) continue;
            double delta = entry.getValue() - currentScore;
            if (delta > maxDrop) {
                maxDrop = delta;
                worstSignature = entry.getKey().substring(PREFIX.length());
            }
        }

        if (maxDrop > regressionThreshold && worstSignature != null) {
            return new RegressionVerdict.Detected(maxDrop,
                    "Swarm prompt quality regression in " + worstSignature
                            + ": dropped by " + String.format("%.3f", maxDrop));
        }
        return new RegressionVerdict.NoRegression();
    }
}
