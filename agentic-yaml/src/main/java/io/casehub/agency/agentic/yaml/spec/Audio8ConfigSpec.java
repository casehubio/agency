package io.casehub.agency.core.yaml.spec;

import java.util.Objects;

public record Audio8ConfigSpec(
        String modelDir,
        String variant,
        int numThreads,
        String provider,
        int maxTokens,
        float temperature,
        float topP,
        int topK) {

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
