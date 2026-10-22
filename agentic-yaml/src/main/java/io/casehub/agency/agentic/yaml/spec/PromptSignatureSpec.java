package io.casehub.agency.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record PromptSignatureSpec(
        String id,
        @Nullable String description,
        String baseSystemPrompt,
        @Nullable String inputType,
        @Nullable String outputType) {

    public PromptSignatureSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(baseSystemPrompt, "baseSystemPrompt");
    }
}
