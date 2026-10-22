package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.social.memory.ConfidenceScorer;
import io.casehub.agency.optimisation.prompt.FewShotExample;
import io.casehub.agency.optimisation.prompt.OptimiserConfig;
import io.casehub.agency.optimisation.prompt.PromptOptimiser;
import io.casehub.agency.optimisation.prompt.PromptSignature;
import io.casehub.agency.optimisation.prompt.SafetyConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record CompiledPromptOptimisation(Map<String, CompiledPipeline> pipelines) {

    public record CompiledPipeline(
            PromptSignature signature,
            PromptOptimiser optimiser,
            OptimiserConfig config,
            SafetyConfig safety,
            @Nullable ConfidenceScorer confidenceScorer,
            List<FewShotExample> examples) {}
}
