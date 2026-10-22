package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.TextToSpeechService;

import java.util.Map;

public record TtsModelRegistry(Map<String, TextToSpeechService> models) {
    public TtsModelRegistry { models = Map.copyOf(models); }
    public TtsModelRegistry() { this(Map.of()); }
}
