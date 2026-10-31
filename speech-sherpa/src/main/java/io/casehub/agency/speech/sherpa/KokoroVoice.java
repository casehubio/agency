package io.casehub.agency.speech.sherpa;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.TextToSpeech;

final class KokoroVoice implements TextToSpeech {

    private final KokoroTextToSpeech engine;
    private final int voiceId;

    KokoroVoice(KokoroTextToSpeech engine, int voiceId) {
        this.engine = engine;
        this.voiceId = voiceId;
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        return engine.synthesise(text, voiceId, options);
    }

    @Override
    public void warmUp() {
        engine.warmUp();
    }
}
