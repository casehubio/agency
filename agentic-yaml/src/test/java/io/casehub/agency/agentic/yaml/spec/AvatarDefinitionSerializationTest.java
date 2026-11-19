package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarDefinitionSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void deserialises_full_avatar_definition() throws Exception {
        var yaml = """
                agent: mentor-coach
                model: avatar-brunette.glb
                ttsVoice: qwen3:jodi_krangle
                cameraView: head
                sampleRate: 16000
                speed: 0.9
                minPlaybackRate: 0.6
                tts:
                  engines:
                    - type: kokoro
                      voices:
                        - name: af_heart
                        - name: am_adam
                    - type: qwen3
                      voices:
                        - name: ryan
                      clonedVoices:
                        - jodi_krangle
                stt:
                  type: whisper
                cleanup:
                  maxDestructiveness: 2
                """;
        var def = mapper.readValue(yaml, AvatarDefinition.class);
        assertThat(def.agent()).isEqualTo("mentor-coach");
        assertThat(def.model()).isEqualTo("avatar-brunette.glb");
        assertThat(def.ttsVoice()).isEqualTo("qwen3:jodi_krangle");
        assertThat(def.cameraView()).isEqualTo("head");
        assertThat(def.sampleRate()).isEqualTo(16000);
        assertThat(def.speed()).isEqualTo(0.9);
        assertThat(def.minPlaybackRate()).isEqualTo(0.6);
        assertThat(def.tts()).isNotNull();
        assertThat(def.tts().engines()).hasSize(2);
        assertThat(def.tts().engines().get(0).type()).isEqualTo("kokoro");
        assertThat(def.tts().engines().get(0).voices()).hasSize(2);
        assertThat(def.tts().engines().get(0).voices().get(0).name()).isEqualTo("af_heart");
        assertThat(def.tts().engines().get(1).clonedVoices()).containsExactly("jodi_krangle");
        assertThat(def.stt()).isNotNull();
        assertThat(def.stt().type()).isEqualTo("whisper");
        assertThat(def.cleanup()).isNotNull();
        assertThat(def.cleanup().maxDestructiveness()).isEqualTo(2);
    }

    @Test
    void deserialises_minimal_avatar_definition() throws Exception {
        var yaml = """
                agent: coach
                model: avatar.glb
                """;
        var def = mapper.readValue(yaml, AvatarDefinition.class);
        assertThat(def.agent()).isEqualTo("coach");
        assertThat(def.model()).isEqualTo("avatar.glb");
        assertThat(def.ttsVoice()).isNull();
        assertThat(def.cameraView()).isNull();
        assertThat(def.sampleRate()).isEqualTo(16000);
        assertThat(def.speed()).isEqualTo(0.9);
        assertThat(def.minPlaybackRate()).isEqualTo(0.6);
        assertThat(def.tts()).isNull();
        assertThat(def.stt()).isNull();
        assertThat(def.cleanup()).isNull();
    }

    @Test
    void deserialises_kebab_case_avatar_definition() throws Exception {
        var yaml = """
                agent: mentor-coach
                model: avatar-brunette.glb
                tts-voice: qwen3:jodi_krangle
                camera-view: head
                sample-rate: 24000
                min-playback-rate: 0.5
                tts:
                  engines:
                    - type: kokoro
                      voices:
                        - af_heart
                      cloned-voices:
                        - jodi_krangle
                stt:
                  type: whisper
                cleanup:
                  max-destructiveness: 3
                """;
        var def = mapper.readValue(yaml, AvatarDefinition.class);
        assertThat(def.agent()).isEqualTo("mentor-coach");
        assertThat(def.model()).isEqualTo("avatar-brunette.glb");
        assertThat(def.ttsVoice()).isEqualTo("qwen3:jodi_krangle");
        assertThat(def.cameraView()).isEqualTo("head");
        assertThat(def.sampleRate()).isEqualTo(24000);
        assertThat(def.minPlaybackRate()).isEqualTo(0.5);
        assertThat(def.tts().engines().get(0).clonedVoices()).containsExactly("jodi_krangle");
        assertThat(def.cleanup().maxDestructiveness()).isEqualTo(3);
    }

    @Test
    void rejects_null_agent() {
        assertThatThrownBy(() -> new AvatarDefinition(
                null, "avatar.glb", null, null, 16000, 0.9, 0.6, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_null_model() {
        assertThatThrownBy(() -> new AvatarDefinition(
                "agent", null, null, null, 16000, 0.9, 0.6, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void tts_voice_spec_deserialises_from_string_shorthand() throws Exception {
        var yaml = """
                type: kokoro
                voices:
                  - af_heart
                  - name: am_adam
                    model: custom-model
                """;
        var spec = mapper.readValue(yaml, TtsEngineSpec.class);
        assertThat(spec.voices()).hasSize(2);
        assertThat(spec.voices().get(0).name()).isEqualTo("af_heart");
        assertThat(spec.voices().get(0).model()).isNull();
        assertThat(spec.voices().get(1).name()).isEqualTo("am_adam");
        assertThat(spec.voices().get(1).model()).isEqualTo("custom-model");
    }

    @Test
    void tts_engine_spec_defaults_empty_voices() throws Exception {
        var yaml = """
                type: kokoro
                """;
        var spec = mapper.readValue(yaml, TtsEngineSpec.class);
        assertThat(spec.type()).isEqualTo("kokoro");
        assertThat(spec.voices()).isEmpty();
        assertThat(spec.clonedVoices()).isNull();
    }

    @Test
    void stt_spec_deserialises() throws Exception {
        var yaml = "type: zipformer";
        var spec = mapper.readValue(yaml, SttSpec.class);
        assertThat(spec.type()).isEqualTo("zipformer");
    }
}
