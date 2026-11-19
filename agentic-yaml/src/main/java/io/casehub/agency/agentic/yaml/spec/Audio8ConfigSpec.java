package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Objects;

public record Audio8ConfigSpec(
        @JsonAlias("model-dir") String modelDir,
        String variant,
        @JsonAlias("num-threads") int numThreads,
        String provider,
        @JsonAlias("max-tokens") int maxTokens,
        float temperature,
        @JsonAlias("top-p") float topP,
        @JsonAlias("top-k") int topK) {

    public Audio8ConfigSpec {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(provider, "provider");
        if (numThreads < 1)
            throw new IllegalArgumentException("numThreads must be positive");
        if (maxTokens < 1)
            throw new IllegalArgumentException("maxTokens must be positive");
    }
}
