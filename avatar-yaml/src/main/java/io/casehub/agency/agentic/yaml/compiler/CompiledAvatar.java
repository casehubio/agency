package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import org.jspecify.annotations.Nullable;

public record CompiledAvatar(
        @Nullable TtsModelRegistry ttsModelRegistry,
        @Nullable TextToSpeech defaultTts,
        @Nullable StreamingSpeechToText stt,
        @Nullable CleanupConfig cleanupConfig,
        @Nullable CompiledSpeechPipeline pipeline) {}
