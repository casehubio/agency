package io.casehub.agency.speech.ws;

import io.casehub.connectors.voice.spi.TextToSpeech;

import java.util.Map;

public record TtsModelRegistry(Map<String, TextToSpeech> models) {
    public TtsModelRegistry { models = Map.copyOf(models); }
    public TtsModelRegistry() { this(Map.of()); }
}
