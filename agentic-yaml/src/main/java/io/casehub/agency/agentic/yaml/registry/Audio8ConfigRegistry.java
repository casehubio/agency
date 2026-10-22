package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.yaml.spec.Audio8ConfigSpec;
import io.casehub.agency.speech.sherpa.Audio8Config;

import java.nio.file.Path;

public class Audio8ConfigRegistry {
    public Audio8Config resolve(Audio8ConfigSpec spec) {
        return new Audio8Config(
                Path.of(spec.modelDir()), spec.variant(),
                spec.numThreads(), spec.provider(), spec.maxTokens(),
                spec.temperature(), spec.topP(), spec.topK());
    }
}
