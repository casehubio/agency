package io.casehub.agency.social.memory.evolution;

import io.casehub.api.model.stigmergy.HealthScoreSnapshot;
import io.casehub.api.model.stigmergy.RegressionVerdict;
import io.casehub.api.spi.improvement.RegressionEvaluator;
import java.util.UUID;

public class MemoryHygieneRegressionEvaluator implements RegressionEvaluator {

    static final String PREFIX = "memory-hygiene:";
    private final double regressionThreshold;

    public MemoryHygieneRegressionEvaluator(double regressionThreshold) {
        this.regressionThreshold = regressionThreshold;
    }

    public MemoryHygieneRegressionEvaluator() {
        this(0.1);
    }

    @Override
    public String evaluatorId() {
        return "memory-hygiene";
    }

    @Override
    public String domainId() {
        return MemoryHygieneCategoryProvider.DOMAIN_ID;
    }

    @Override
    public RegressionVerdict evaluate(UUID caseId, HealthScoreSnapshot baseline,
                                      HealthScoreSnapshot current, String category) {
        double maxDrop = 0.0;
        String worstDimension = null;

        for (var entry : baseline.componentScores().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX)) continue;
            var currentScore = current.componentScores().get(entry.getKey());
            if (currentScore == null) continue;
            double delta = entry.getValue() - currentScore;
            if (delta > maxDrop) {
                maxDrop = delta;
                worstDimension = entry.getKey().substring(PREFIX.length());
            }
        }

        if (maxDrop > regressionThreshold && worstDimension != null) {
            return new RegressionVerdict.Detected(maxDrop,
                    "Memory hygiene regression in " + worstDimension
                            + ": dropped by " + String.format("%.3f", maxDrop));
        }
        return new RegressionVerdict.NoRegression();
    }
}
