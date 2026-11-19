package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record SherpaConfigSpec(
        @JsonAlias("model-dir") String modelDir,
        @JsonAlias("num-threads") int numThreads,
        String provider,
        @JsonAlias("punctuation-model-dir") @Nullable String punctuationModelDir) {

    public SherpaConfigSpec {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(provider, "provider");
        if (numThreads < 1)
            throw new IllegalArgumentException("numThreads must be positive");
    }
}
