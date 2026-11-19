package io.casehub.agency.speech.demo;

import io.casehub.agency.core.yaml.compiler.AvatarCompiler;
import io.casehub.agency.core.yaml.compiler.CompiledAvatar;
import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import io.casehub.agency.speech.CorrectionHooks;
import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.yaml.jackson.YamlMappers;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;

@ApplicationScoped
public class AvatarSpeechProducer {

    private static final System.Logger LOG = System.getLogger("avatar-speech-producer");

    private final CompiledAvatar compiled;

    AvatarSpeechProducer() {
        var cl = Thread.currentThread().getContextClassLoader();
        var resource = cl.getResource("META-INF/avatar.yaml");
        if (resource == null) {
            throw new IllegalStateException("META-INF/avatar.yaml not found on classpath");
        }
        try (InputStream in = resource.openStream()) {
            var definition = YamlMappers.create().readValue(in, AvatarDefinition.class);
            this.compiled = new AvatarCompiler().compile(definition);
            LOG.log(System.Logger.Level.INFO, "Avatar speech pipeline compiled from META-INF/avatar.yaml");
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse META-INF/avatar.yaml", e);
        }
    }

    @Produces @Singleton
    TtsModelRegistry ttsModelRegistry() {
        return compiled.ttsModelRegistry() != null ? compiled.ttsModelRegistry() : new TtsModelRegistry();
    }

    @Produces @Singleton
    TextToSpeech defaultTts() {
        if (compiled.defaultTts() == null) {
            throw new IllegalStateException("No TTS voices configured in avatar.yaml");
        }
        return compiled.defaultTts();
    }

    @Produces @Singleton
    StreamingSpeechToText stt() {
        if (compiled.stt() == null) {
            throw new IllegalStateException("No STT configured in avatar.yaml");
        }
        return compiled.stt();
    }

    @Produces @Singleton
    CleanupConfig cleanupConfig() {
        if (compiled.cleanupConfig() == null) {
            return CleanupConfig.of();
        }
        return compiled.cleanupConfig();
    }

    @Produces @Singleton
    CorrectionHooks correctionHooks() {
        try {
            var corrector = new io.casehub.connectors.voice.sherpa.correction.TranscriptCorrector(
                    java.util.List.of(), null, java.util.Set.of());
            var vocabulary = new io.casehub.connectors.voice.sherpa.correction.ConversationVocabulary();
            try {
                var symSpell = io.casehub.connectors.voice.sherpa.correction.SymSpellIndex.fromResource(
                        "frequency_dictionary_en_82_765.txt");
                var phonetic = io.casehub.connectors.voice.sherpa.correction.PhoneticIndex.fromSymSpellIndex(symSpell);
                var ngram = io.casehub.connectors.voice.sherpa.correction.NgramModel.fromResource(
                        "frequency_bigramdictionary_en_243_342.txt");
                corrector = new io.casehub.connectors.voice.sherpa.correction.TranscriptCorrector(
                        java.util.List.of(
                                new io.casehub.connectors.voice.sherpa.correction.SymSpellStrategy(symSpell),
                                new io.casehub.connectors.voice.sherpa.correction.PhoneticStrategy(phonetic)),
                        ngram, symSpell.dictionary());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "TranscriptCorrector resources unavailable: " + e.getMessage());
            }
            var finalCorrector = corrector;
            return new CorrectionHooks(
                    finalCorrector::correct,
                    response -> {
                        vocabulary.addFromText(response);
                        finalCorrector.addVocabulary(vocabulary.terms().toArray(String[]::new));
                    },
                    vocabulary::asPromptHint);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "CorrectionHooks unavailable: " + e.getMessage());
            return new CorrectionHooks(s -> s, s -> {}, () -> "");
        }
    }
}
