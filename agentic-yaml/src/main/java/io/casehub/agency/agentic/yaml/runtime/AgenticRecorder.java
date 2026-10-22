package io.casehub.agency.agentic.yaml.runtime;

import io.casehub.agency.agentic.yaml.compiler.CognitionCompiler;
import io.casehub.agency.agentic.yaml.compiler.PatternCompiler;
import io.casehub.agency.agentic.yaml.compiler.PromptOptimisationCompiler;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.quarkus.runtime.annotations.Recorder;
import org.jspecify.annotations.Nullable;

@Recorder
public class AgenticRecorder {

    public PatternCompiler createCompiler(ExpressionEngine expressionEngine) {
        return new PatternCompiler(expressionEngine);
    }

    public CognitionCompiler createCognitionCompiler() {
        return new CognitionCompiler();
    }

    public PromptOptimisationCompiler createPromptOptimisationCompiler(
            @Nullable AgentProvider agentProvider) {
        var diversityRegistry = new io.casehub.agency.agentic.yaml.registry.DiversityStrategyRegistry();
        var optimiserRegistry = new io.casehub.agency.agentic.yaml.registry.PromptOptimiserRegistry(diversityRegistry);
        var scorerRegistry    = new io.casehub.agency.agentic.yaml.registry.ConfidenceScorerRegistry();
        return new PromptOptimisationCompiler(
                optimiserRegistry, scorerRegistry, agentProvider);
    }


}
