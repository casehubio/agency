package io.casehub.agency.core.yaml.runtime;

import io.casehub.agency.core.yaml.compiler.AvatarCompiler;
import io.casehub.agency.core.yaml.compiler.CompiledAvatar;
import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import io.casehub.agency.speech.CorrectionHooks;
import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

import java.util.function.Function;

@Recorder
public class AvatarRecorder {

    public RuntimeValue<CompiledAvatar> compileAvatar(AvatarDefinition definition) {
        return new RuntimeValue<>(new AvatarCompiler().compile(definition));
    }

    public RuntimeValue<TtsModelRegistry> extractTtsModelRegistry(
            RuntimeValue<CompiledAvatar> compiled) {
        return new RuntimeValue<>(compiled.getValue().ttsModelRegistry());
    }

    public RuntimeValue<TextToSpeech> extractDefaultTts(
            RuntimeValue<CompiledAvatar> compiled) {
        return new RuntimeValue<>(compiled.getValue().defaultTts());
    }

    public RuntimeValue<StreamingSpeechToText> extractStt(
            RuntimeValue<CompiledAvatar> compiled) {
        return new RuntimeValue<>(compiled.getValue().stt());
    }

    public RuntimeValue<CleanupConfig> extractCleanup(
            RuntimeValue<CompiledAvatar> compiled) {
        return new RuntimeValue<>(compiled.getValue().cleanupConfig());
    }

    public Function<io.quarkus.arc.SyntheticCreationalContext<CorrectionHooks>, CorrectionHooks>
            correctionHooksCreator() {
        return ctx -> {
            var correctorHandle = io.quarkus.arc.Arc.container()
                    .instance(io.casehub.connectors.voice.sherpa.correction.TranscriptCorrector.class);
            var vocabularyHandle = io.quarkus.arc.Arc.container()
                    .instance(io.casehub.connectors.voice.sherpa.correction.ConversationVocabulary.class);
            if (!correctorHandle.isAvailable() || !vocabularyHandle.isAvailable()) {
                return new CorrectionHooks(s -> s, s -> {}, () -> "");
            }
            var corrector = correctorHandle.get();
            var vocabulary = vocabularyHandle.get();
            return new CorrectionHooks(
                    corrector::correct,
                    response -> {
                        vocabulary.addFromText(response);
                        corrector.addVocabulary(vocabulary.terms().toArray(String[]::new));
                    },
                    vocabulary::asPromptHint);
        };
    }

    public RuntimeValue<AvatarConfigBean> createAvatarConfigBean(AvatarDefinition definition, String json) {
        return new RuntimeValue<>(new AvatarConfigBean(definition, json));
    }
}
