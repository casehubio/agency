package io.casehub.agency.agentic.yaml.compiler;

import io.casehub.agency.memory.ConfidenceScorer;
import io.casehub.agency.prompt.FewShotExample;
import io.casehub.agency.prompt.OptimiserConfig;
import io.casehub.agency.prompt.PromptOptimiser;
import io.casehub.agency.prompt.PromptSignature;
import io.casehub.agency.prompt.SafetyConfig;
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
