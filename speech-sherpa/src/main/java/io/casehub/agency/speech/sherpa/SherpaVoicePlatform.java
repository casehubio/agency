package io.casehub.agency.speech.sherpa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.casehub.agency.speech.SpeechToTextService;
import io.casehub.agency.speech.TextToSpeechService;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatform;

public class SherpaVoicePlatform implements VoicePlatform {

    private final SpeechToTextService sttService;
    private final TextToSpeechService ttsService;

    public SherpaVoicePlatform(SpeechToTextService sttService,
                                TextToSpeechService ttsService) {
        this.sttService = sttService;
        this.ttsService = ttsService;
    }

    @Override
    public String id() {
        return "sherpa";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == SpeechToText.class || capability == TextToSpeech.class;
    }

    @Override
    public SpeechToText speechToText() {
        return (audio, options) -> {
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("voice-stt-", ".audio");
                Files.write(tempFile, audio);
                var agencyOptions = bridgeTranscriptionOptions(options);
                var agencyResult = sttService.transcribe(tempFile, agencyOptions);
                return new TranscriptionResult(
                        agencyResult.text(), agencyResult.language(), agencyResult.confidence());
            } catch (IOException e) {
                throw new RuntimeException("Failed to transcribe audio", e);
            } finally {
                if (tempFile != null) {
                    try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                }
            }
        };
    }

    @Override
    public TextToSpeech textToSpeech() {
        return new TextToSpeech() {
            @Override
            public SynthesisResult synthesise(String text, SynthesisOptions options) {
                var agencyOptions = bridgeSynthesisOptions(options);
                var agencyResult = ttsService.synthesise(text, agencyOptions);
                return new SynthesisResult(agencyResult.audioData(), agencyResult.audioFormat());
            }

            @Override
            public void warmUp() {
                ttsService.warmUp();
            }
        };
    }

    private static io.casehub.agency.speech.TranscriptionOptions bridgeTranscriptionOptions(
            TranscriptionOptions options) {
        return new io.casehub.agency.speech.TranscriptionOptions(
                options.audioFormat(), options.languageHint(),
                options.modelSize(), options.vocabularyHint());
    }

    private static io.casehub.agency.speech.SynthesisOptions bridgeSynthesisOptions(
            SynthesisOptions options) {
        return new io.casehub.agency.speech.SynthesisOptions(
                options.voice(), options.language(),
                options.audioFormat(), options.includePhonemes());
    }
}
