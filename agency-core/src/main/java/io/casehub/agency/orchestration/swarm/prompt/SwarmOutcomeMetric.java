package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.PromptQualityMetric;
import io.casehub.agency.optimisation.prompt.VariantOutcome;

import java.util.List;
import java.util.Map;

public class SwarmOutcomeMetric implements PromptQualityMetric {

    static final Map<String, Double> WEIGHTS = Map.of(
            "CONVERGED", 1.0,
            "CONSENSUS", 1.0,
            "COMPLETED", 0.8,
            "CONTINUE", 0.5,
            "DEGRADED", 0.25,
            "DEADLOCK", 0.0,
            "FAILED", 0.0);

    @Override
    public double score(List<VariantOutcome> outcomes) {
        if (outcomes.isEmpty()) {return 0.0;}
        double total = 0.0;
        for (var outcome : outcomes) {
            total += WEIGHTS.getOrDefault(outcome.outcome(), 0.0);
        }
        return total / outcomes.size();
    }
}
