package io.casehub.agency.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record SherpaConfigSpec(
        String modelDir,
        int numThreads,
        String provider,
        @Nullable String punctuationModelDir) {

    public SherpaConfigSpec {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(provider, "provider");
        if (numThreads < 1)
            throw new IllegalArgumentException("numThreads must be positive");
    }
}
