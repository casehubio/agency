package io.casehub.agency.agentic.yaml.spec;

import java.util.Objects;

public record KokoroConfigSpec(
        String modelDir,
        int voiceId,
        float lengthScale,
        int numThreads,
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
