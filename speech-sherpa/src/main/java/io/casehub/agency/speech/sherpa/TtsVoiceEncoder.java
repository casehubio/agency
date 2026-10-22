package io.casehub.agency.speech.sherpa;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
interface TtsVoiceEncoder {
    VoiceData encode(byte[] audioData, @Nullable String transcriptText);
}
