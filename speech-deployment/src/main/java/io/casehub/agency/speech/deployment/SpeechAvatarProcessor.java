package io.casehub.agency.speech.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.core.yaml.compiler.CompiledAvatar;
import io.casehub.agency.core.yaml.runtime.AvatarConfigBean;
import io.casehub.agency.core.yaml.runtime.AvatarConfigResource;
import io.casehub.agency.core.yaml.runtime.AvatarRecorder;
import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import io.casehub.agency.speech.CorrectionHooks;
import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.yaml.jackson.YamlMappers;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.runtime.RuntimeValue;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

public class SpeechAvatarProcessor {

    private static final ObjectMapper YAML = YamlMappers.create();
    private static final ObjectMapper JSON = new ObjectMapper();

    @BuildStep
    SpeechAvatarConfigBuildItem discoverAvatarConfig() throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL resource = cl.getResource("META-INF/avatar.yaml");
        if (resource == null) {
            resource = cl.getResource("META-INF/avatar.yml");
        }
        if (resource == null) {
            return new SpeechAvatarConfigBuildItem(null);
        }
        try (InputStream in = resource.openStream()) {
            var definition = YAML.readValue(in, AvatarDefinition.class);
            return new SpeechAvatarConfigBuildItem(definition);
        }
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    List<SyntheticBeanBuildItem> registerAvatarSpeechBeans(
            AvatarRecorder recorder,
            SpeechAvatarConfigBuildItem avatarConfig,
            BeanDiscoveryFinishedBuildItem beanDiscovery) {
        if (avatarConfig.definition() == null) return List.of();

        var def = avatarConfig.definition();
        var beans = new ArrayList<SyntheticBeanBuildItem>();

        boolean needsTts = def.tts() != null && !hasAppBean(beanDiscovery, TtsModelRegistry.class);
        boolean needsDefaultTts = def.tts() != null && !def.tts().engines().isEmpty()
                && !hasAppBean(beanDiscovery, TextToSpeech.class);
        boolean needsStt = def.stt() != null && !hasAppBean(beanDiscovery, StreamingSpeechToText.class);
        boolean needsCleanup = def.cleanup() != null && !hasAppBean(beanDiscovery, CleanupConfig.class);
        boolean needsHooks = !hasAppBean(beanDiscovery, CorrectionHooks.class);

        if (!needsTts && !needsDefaultTts && !needsStt && !needsCleanup && !needsHooks) {
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
        if (needsHooks) {
            beans.add(SyntheticBeanBuildItem.configure(CorrectionHooks.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .createWith(recorder.correctionHooksCreator())
                    .done());
        }

        return beans;
    }

    @BuildStep
    @Record(STATIC_INIT)
    SyntheticBeanBuildItem registerAvatarConfig(
            AvatarRecorder recorder,
            SpeechAvatarConfigBuildItem avatarConfig) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (avatarConfig.definition() == null) {
            return null;
        }
        String json = JSON.writeValueAsString(avatarConfig.definition());
        RuntimeValue<AvatarConfigBean> rv =
                recorder.createAvatarConfigBean(avatarConfig.definition(), json);
        return SyntheticBeanBuildItem.configure(AvatarConfigBean.class)
                .scope(Singleton.class)
                .runtimeValue(rv)
                .done();
    }

    @BuildStep
    AdditionalBeanBuildItem registerAvatarResource(SpeechAvatarConfigBuildItem avatarConfig) {
        if (avatarConfig.definition() == null) {
            return null;
        }
        return AdditionalBeanBuildItem.unremovableOf(AvatarConfigResource.class);
    }

    private static boolean hasAppBean(BeanDiscoveryFinishedBuildItem beanDiscovery, Class<?> beanType) {
        return !beanDiscovery.beanStream()
                .withBeanType(beanType)
                .filter(b -> !b.isDefaultBean() && !b.isSynthetic())
                .collect()
                .isEmpty();
    }
}
