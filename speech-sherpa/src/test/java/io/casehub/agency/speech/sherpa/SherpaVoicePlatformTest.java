package io.casehub.agency.speech.sherpa;

import io.casehub.agency.speech.SpeechToTextService;
import io.casehub.agency.speech.SynthesisOptions;
import io.casehub.agency.speech.SynthesisResult;
import io.casehub.agency.speech.TextToSpeechService;
import io.casehub.agency.speech.TranscriptionResult;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SherpaVoicePlatformTest {

    @Test
    void idIsSherpa() {
        var platform = new SherpaVoicePlatform(stubStt(), stubTts());
        assertThat(platform.id()).isEqualTo("sherpa");
    }

    @Test
    void supportsBothCapabilities() {
        var platform = new SherpaVoicePlatform(stubStt(), stubTts());
        assertThat(platform.supports(SpeechToText.class)).isTrue();
        assertThat(platform.supports(TextToSpeech.class)).isTrue();
    }

    @Test
    void transcribeDelegatesToSttService() {
        var platform = new SherpaVoicePlatform(stubStt(), stubTts());
        var result = platform.speechToText()
                .transcribe(new byte[]{1, 2, 3},
                        io.casehub.connectors.voice.model.TranscriptionOptions.defaults());
        assertThat(result.text()).isEqualTo("hello from sherpa");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void synthesiseDelegatesToTtsService() {
        var platform = new SherpaVoicePlatform(stubStt(), stubTts());
        var result = platform.textToSpeech()
                .synthesise("hello",
                        new io.casehub.connectors.voice.model.SynthesisOptions(null, "en", "wav", false));
        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.audioFormat()).isEqualTo("wav");
    }

    private static SpeechToTextService stubStt() {
        return (audioFile, options) -> new TranscriptionResult("hello from sherpa", "en", 0.95);
    }

    private static TextToSpeechService stubTts() {
        return new TextToSpeechService() {
            @Override
            public SynthesisResult synthesise(String text, SynthesisOptions options) {
                return new SynthesisResult(new byte[]{1, 2, 3, 4}, "wav", null);
            }
        };
    }
}
