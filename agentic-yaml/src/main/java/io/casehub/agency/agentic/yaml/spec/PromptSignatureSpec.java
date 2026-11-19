package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record PromptSignatureSpec(
        String id,
        @Nullable String description,
        @JsonAlias("base-system-prompt") String baseSystemPrompt,
        @JsonAlias("input-type") @Nullable String inputType,
        @JsonAlias("output-type") @Nullable String outputType) {

    public PromptSignatureSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(baseSystemPrompt, "baseSystemPrompt");
    }
}
