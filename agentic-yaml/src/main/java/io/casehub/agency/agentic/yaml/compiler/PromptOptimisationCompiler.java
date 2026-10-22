package io.casehub.agency.agentic.yaml.compiler;

import io.casehub.agency.agentic.yaml.registry.ConfidenceScorerRegistry;
import io.casehub.agency.agentic.yaml.registry.PromptOptimiserRegistry;
import io.casehub.agency.agentic.yaml.spec.PromptOptimisationDefinition;
import io.casehub.agency.agentic.yaml.spec.PromptOptimisationPipelineSpec;
import io.casehub.agency.agentic.yaml.spec.PromptSignatureSpec;
import io.casehub.agency.prompt.FewShotExample;
import io.casehub.agency.prompt.OptimiserConfig;
import io.casehub.agency.prompt.PromptSignature;
import io.casehub.agency.prompt.SafetyConfig;
import io.casehub.agency.prompt.optimiser.FewShotOptimiser;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;

public class PromptOptimisationCompiler {

    private final PromptOptimiserRegistry optimiserRegistry;
    private final ConfidenceScorerRegistry confidenceScorerRegistry;
    private final @Nullable AgentProvider agentProvider;

    public PromptOptimisationCompiler(
            PromptOptimiserRegistry optimiserRegistry,
            ConfidenceScorerRegistry confidenceScorerRegistry,
            @Nullable AgentProvider agentProvider) {
        this.optimiserRegistry = optimiserRegistry;
        this.confidenceScorerRegistry = confidenceScorerRegistry;
        this.agentProvider = agentProvider;
    }

    public CompiledPromptOptimisation compile(PromptOptimisationDefinition definition) {
        var compiled = new LinkedHashMap<String, CompiledPromptOptimisation.CompiledPipeline>();
        for (var entry : definition.pipelines().entrySet()) {
            compiled.put(entry.getKey(), compilePipeline(entry.getValue()));
        }
        return new CompiledPromptOptimisation(compiled);
    }

    private CompiledPromptOptimisation.CompiledPipeline compilePipeline(
            PromptOptimisationPipelineSpec spec) {
        var signature = compileSignature(spec.signature());
        var optimiser = spec.optimiser() != null
                ? optimiserRegistry.resolve(spec.optimiser(), agentProvider)
                : new FewShotOptimiser();
        var config = spec.config() != null ? spec.config() : OptimiserConfig.defaults();
        var safety = spec.safety() != null ? spec.safety() : SafetyConfig.defaults();
        var confidenceScorer = spec.confidenceScorer() != null
                ? confidenceScorerRegistry.resolve(spec.confidenceScorer())
                : null;
        var examples = spec.examples() != null
                ? List.copyOf(spec.examples())
                : List.<FewShotExample>of();
        return new CompiledPromptOptimisation.CompiledPipeline(
                signature, optimiser, config, safety, confidenceScorer, examples);
    }

    private PromptSignature compileSignature(PromptSignatureSpec spec) {
        Class<?> inputType = resolveClass(spec.inputType());
        Class<?> outputType = resolveClass(spec.outputType());
        return new PromptSignature(
                spec.id(), spec.description(), spec.baseSystemPrompt(),
                inputType, outputType);
    }

    private static @Nullable Class<?> resolveClass(@Nullable String className) {
        if (className == null) return null;
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown class: " + className, e);
        }
    }
}
