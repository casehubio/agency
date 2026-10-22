package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.core.yaml.spec.ConfidenceScorerSpec;
import io.casehub.agency.social.memory.ArousalScorer;
import io.casehub.agency.social.memory.CompositeConfidenceScorer;
import io.casehub.agency.social.memory.ConfidenceScorer;
import io.casehub.agency.social.memory.SurpriseScorer;
import io.casehub.agency.social.memory.WeightedScorer;

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
