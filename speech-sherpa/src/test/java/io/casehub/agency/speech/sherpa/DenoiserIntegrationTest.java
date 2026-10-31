package io.casehub.agency.speech.sherpa;

import io.casehub.connectors.voice.spi.StreamingSpeechDenoiser;
import io.casehub.connectors.voice.spi.StreamingSpeechDenoiserFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DenoiserIntegrationTest {

    @Test
    @EnabledIf("io.casehub.agency.speech.sherpa.WhisperLibrary#isAvailable")
    void whisperSttCallsDenoiserWhenEnabled() {
        var callCount = new AtomicInteger();
        var enabled = new AtomicBoolean(true);

        StreamingSpeechDenoiserFactory factory = () -> new StreamingSpeechDenoiser() {
            @Override
            public float[] processChunk(float[] samples, int sampleRate) {
                callCount.incrementAndGet();
                return samples;
            }
            @Override public void reset() {}
            @Override public void close() {}
        };

        var stt = WhisperSpeechToText.withDefaults()
                .withStreamingDenoiser(factory, enabled::get);
        var stream = stt.startStream(
                io.casehub.connectors.voice.model.TranscriptionOptions.defaults());

        stream.acceptSamples(new float[1600], 16000);
        assertEquals(1, callCount.get());

        enabled.set(false);
        stream.acceptSamples(new float[1600], 16000);
        assertEquals(1, callCount.get());

        stream.close();
    }

    @Test
    @EnabledIf("io.casehub.agency.speech.sherpa.WhisperLibrary#isAvailable")
    void sttWithoutDenoiserPassesThroughUnchanged() {
        var stt = WhisperSpeechToText.withDefaults();
        var stream = stt.startStream(
                io.casehub.connectors.voice.model.TranscriptionOptions.defaults());
        assertDoesNotThrow(() -> stream.acceptSamples(new float[1600], 16000));
        stream.close();
    }
}
