package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.yaml.spec.ConfidenceScorerSpec;
import io.casehub.agency.memory.ArousalScorer;
import io.casehub.agency.memory.CompositeConfidenceScorer;
import io.casehub.agency.memory.ConfidenceScorer;
import io.casehub.agency.memory.SurpriseScorer;
import io.casehub.agency.memory.WeightedScorer;

public class ConfidenceScorerRegistry {

    public ConfidenceScorer resolve(ConfidenceScorerSpec spec) {
        return switch (spec) {
            case ConfidenceScorerSpec.Arousal ignored -> new ArousalScorer();
            case ConfidenceScorerSpec.Surprise ignored -> new SurpriseScorer();
            case ConfidenceScorerSpec.Composite c -> new CompositeConfidenceScorer(
                    c.scorers().stream()
                            .map(ws -> new WeightedScorer(resolve(ws.scorer()), ws.weight()))
                            .toList());
        };
    }
}
