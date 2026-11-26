package io.casehub.agency.speech.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.core.yaml.deployment.AvatarConfigBuildItem;
import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import io.casehub.yaml.jackson.YamlMappers;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechAvatarProcessorTest {

    private static final ObjectMapper YAML = YamlMappers.create();

    @Test
    void discoversAvatarYamlWithSpeechSpecs() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("META-INF/avatar.yaml")) {
            assertThat(in).isNotNull();
            var def = YAML.readValue(in, AvatarDefinition.class);
            var buildItem = new AvatarConfigBuildItem(def);

            assertThat(buildItem.definition()).isNotNull();
            assertThat(buildItem.definition().agent()).isEqualTo("test-avatar");
            assertThat(buildItem.definition().sampleRate()).isEqualTo(16000);
            assertThat(buildItem.definition().speed()).isEqualTo(0.9);

            assertThat(buildItem.definition().tts()).isNotNull();
            assertThat(buildItem.definition().tts().engines()).hasSize(1);
            assertThat(buildItem.definition().tts().engines().get(0).type()).isEqualTo("kokoro");
            assertThat(buildItem.definition().tts().engines().get(0).voices()).hasSize(2);

            assertThat(buildItem.definition().stt()).isNotNull();
            assertThat(buildItem.definition().stt().type()).isEqualTo("whisper-fallback");

            assertThat(buildItem.definition().cleanup()).isNotNull();
            assertThat(buildItem.definition().cleanup().maxDestructiveness()).isEqualTo(2);
        }
    }

    @Test
    void nullDefinitionIndicatesNoAvatarYaml() {
        var buildItem = new AvatarConfigBuildItem(null);
        assertThat(buildItem.definition()).isNull();
    }

    @Test
    void discoversAvatarYamlWithPipelineSection() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                                        .getResourceAsStream("META-INF/avatar-with-pipeline.yaml")) {
            assertThat(in).isNotNull();
            var def = YAML.readValue(in, AvatarDefinition.class);
            assertThat(def.pipeline()).isNotNull();
            assertThat(def.pipeline().postLlm()).hasSize(1);
            assertThat(def.pipeline().postLlm().get(0).name()).isEqualTo("test-filter");
            assertThat(def.pipeline().postStt()).isNull();
            assertThat(def.pipeline().postSegmenter()).isNull();
        }
    }

}
