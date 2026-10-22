package io.casehub.agency.speech.sherpa;

import org.jspecify.annotations.Nullable;

interface TtsGenerator {
    GeneratorOutput generate(int[] tokens, @Nullable VoiceData voiceData,
                             GeneratorConfig config);
}
