package io.casehub.agency.core.yaml.spec;

import io.casehub.agency.optimisation.prompt.FewShotExample;
import io.casehub.agency.optimisation.prompt.OptimiserConfig;
import io.casehub.agency.optimisation.prompt.SafetyConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record PromptOptimisationPipelineSpec(
        PromptSignatureSpec signature,
        @Nullable PromptOptimiserSpec optimiser,
        @Nullable OptimiserConfig config,
        @Nullable SafetyConfig safety,
        @Nullable ConfidenceScorerSpec confidenceScorer,
        @Nullable List<FewShotExample> examples) {

    public PromptOptimisationPipelineSpec {
        Objects.requireNonNull(signature, "signature");
    }
}
