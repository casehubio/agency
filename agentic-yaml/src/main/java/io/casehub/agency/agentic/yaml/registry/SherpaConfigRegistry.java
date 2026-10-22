package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.yaml.spec.SherpaConfigSpec;
import io.casehub.agency.speech.sherpa.SherpaConfig;

import java.nio.file.Path;

public class SherpaConfigRegistry {
    public SherpaConfig resolve(SherpaConfigSpec spec) {
        var config = new SherpaConfig(
                Path.of(spec.modelDir()), spec.numThreads(), spec.provider());
        if (spec.punctuationModelDir() != null) {
            config = config.withPunctuation(Path.of(spec.punctuationModelDir()));
        }
        return config;
    }
}
