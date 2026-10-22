package io.casehub.agency.agentic.yaml.spec;

import java.util.Map;

public record PromptOptimisationDefinition(
        Map<String, PromptOptimisationPipelineSpec> pipelines) {

    public PromptOptimisationDefinition {
        if (pipelines == null || pipelines.isEmpty())
            throw new IllegalArgumentException("at least one pipeline required");
        pipelines = Map.copyOf(pipelines);
    }
}
