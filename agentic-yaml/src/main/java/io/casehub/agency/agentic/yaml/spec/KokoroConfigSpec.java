package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Objects;

public record KokoroConfigSpec(
        @JsonAlias("model-dir") String modelDir,
        @JsonAlias("voice-id") int voiceId,
        @JsonAlias("length-scale") float lengthScale,
        @JsonAlias("num-threads") int numThreads,
        String provider) {

    public KokoroConfigSpec {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(provider, "provider");
        if (numThreads < 1)
            throw new IllegalArgumentException("numThreads must be positive");
        if (lengthScale <= 0)
            throw new IllegalArgumentException("lengthScale must be positive");
    }
}
