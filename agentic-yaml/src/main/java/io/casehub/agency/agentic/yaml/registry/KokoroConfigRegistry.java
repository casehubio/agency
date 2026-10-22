package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.yaml.spec.KokoroConfigSpec;
import io.casehub.agency.speech.sherpa.KokoroConfig;

import java.nio.file.Path;

public class KokoroConfigRegistry {
    public KokoroConfig resolve(KokoroConfigSpec spec) {
        return new KokoroConfig(
                Path.of(spec.modelDir()), spec.voiceId(),
                spec.lengthScale(), spec.numThreads(), spec.provider());
    }
}
