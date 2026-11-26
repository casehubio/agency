package io.casehub.agency.speech.deployment;

import io.casehub.agency.core.yaml.compiler.CompiledAvatar;
import io.casehub.agency.core.yaml.compiler.CompiledSpeechPipeline;
import io.casehub.agency.core.yaml.deployment.AvatarConfigBuildItem;
import io.casehub.agency.core.yaml.runtime.AvatarRecorder;
import io.casehub.agency.speech.CorrectionHooks;
import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.runtime.RuntimeValue;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

public class SpeechAvatarProcessor {

    @BuildStep
    @Record(RUNTIME_INIT)
    List<SyntheticBeanBuildItem> registerAvatarSpeechBeans(
            AvatarRecorder recorder,
            AvatarConfigBuildItem avatarConfig,
            BeanDiscoveryFinishedBuildItem beanDiscovery) {
        if (avatarConfig.definition() == null) return List.of();

        var def = avatarConfig.definition();
        var beans = new ArrayList<SyntheticBeanBuildItem>();

        boolean needsTts = def.tts() != null && !hasAppBean(beanDiscovery, TtsModelRegistry.class);
        boolean needsDefaultTts = def.tts() != null && !def.tts().engines().isEmpty()
                && !hasAppBean(beanDiscovery, TextToSpeech.class);
        boolean needsStt = def.stt() != null && !hasAppBean(beanDiscovery, StreamingSpeechToText.class);
        boolean needsCleanup = def.cleanup() != null && !hasAppBean(beanDiscovery, CleanupConfig.class);
        boolean needsPipeline = !hasAppBean(beanDiscovery, CompiledSpeechPipeline.class);
        boolean needsHooks = !hasAppBean(beanDiscovery, CorrectionHooks.class);

        if (!needsTts && !needsDefaultTts && !needsStt && !needsCleanup && !needsPipeline && !needsHooks) {
            return List.of();
        }

        RuntimeValue<CompiledAvatar> compiled = recorder.compileAvatar(def);

        if (needsTts) {
            beans.add(SyntheticBeanBuildItem.configure(TtsModelRegistry.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .runtimeValue(recorder.extractTtsModelRegistry(compiled))
                    .done());
        }
        if (needsDefaultTts) {
            beans.add(SyntheticBeanBuildItem.configure(TextToSpeech.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .runtimeValue(recorder.extractDefaultTts(compiled))
                    .done());
        }
        if (needsStt) {
            beans.add(SyntheticBeanBuildItem.configure(StreamingSpeechToText.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .runtimeValue(recorder.extractStt(compiled))
                    .done());
        }
        if (needsCleanup) {
            beans.add(SyntheticBeanBuildItem.configure(CleanupConfig.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .runtimeValue(recorder.extractCleanup(compiled))
                    .done());
        }
        if (needsPipeline) {
            beans.add(SyntheticBeanBuildItem.configure(CompiledSpeechPipeline.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .runtimeValue(recorder.extractPipeline(compiled))
                    .done());
        }
        if (needsHooks) {
            beans.add(SyntheticBeanBuildItem.configure(CorrectionHooks.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .createWith(recorder.correctionHooksCreator())
                    .done());
        }

        return beans;
    }

    private static boolean hasAppBean(BeanDiscoveryFinishedBuildItem beanDiscovery, Class<?> beanType) {
        return !beanDiscovery.beanStream()
                .withBeanType(beanType)
                .filter(b -> !b.isDefaultBean() && !b.isSynthetic())
                .collect()
                .isEmpty();
    }
}
