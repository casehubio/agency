package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import io.casehub.agency.core.yaml.spec.CleanupConfigSpec;
import io.casehub.agency.core.yaml.spec.LipSyncMode;
import io.casehub.agency.core.yaml.spec.SttSpec;
import io.casehub.agency.core.yaml.spec.TtsEngineSpec;
import io.casehub.agency.core.yaml.spec.TtsSpec;
import io.casehub.agency.core.yaml.spec.TtsVoiceSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AvatarCompilerTest {

    private static boolean vitsAvailable;

    @BeforeAll
    static void checkVitsNativeLibs() {
        try {
            io.casehub.connectors.voice.sherpa.VitsTextToSpeech.withDefaults().close();
            vitsAvailable = true;
        } catch (Exception e) {
            vitsAvailable = false;
        }
    }

    private final AvatarCompiler compiler = new AvatarCompiler();

    @Test
    void null_sections_produce_null_fields() {
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, null, null, null, null);

        var result = compiler.compile(def);

        assertThat(result.ttsModelRegistry()).isNull();
        assertThat(result.defaultTts()).isNull();
        assertThat(result.stt()).isNull();
        assertThat(result.cleanupConfig()).isNull();
    }

    @Test
    void empty_engines_produces_empty_registry_and_null_default() {
        var spec = new TtsSpec(List.of());
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, spec, null, null, null);

        var result = compiler.compile(def);

        assertThat(result.ttsModelRegistry()).isNotNull();
        assertThat(result.ttsModelRegistry().models()).isEmpty();
        assertThat(result.defaultTts()).isNull();
    }

    @Test
    void compiles_vits_engines_to_tts_registry() {
        assumeTrue(vitsAvailable, "VITS native libs (espeak-ng) not available");
        var spec = new TtsSpec(List.of(
                new TtsEngineSpec("vits", List.of(new TtsVoiceSpec("lessac-medium", null)), false, null)));
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, spec, null, null, null);

        var result = compiler.compile(def);

        assertThat(result.ttsModelRegistry()).isNotNull();
        assertThat(result.ttsModelRegistry().models()).containsKey("lessac-medium");
        assertThat(result.defaultTts()).isNotNull();
    }

    @Test
    void compiles_sherpa_voices_with_key_prefix() {
        var spec = new TtsSpec(List.of(
                new TtsEngineSpec("sherpa", List.of(
                        new TtsVoiceSpec("lessac-medium", null)), false, LipSyncMode.DISABLED)));
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, spec, null, null, null);

        var result = compiler.compile(def);

        assertThat(result.ttsModelRegistry().models()).containsKey("sherpa:lessac-medium");
    }

    @Test
    void compiles_kokoro_voices_with_key_prefix() {
        var spec = new TtsSpec(List.of(
                new TtsEngineSpec("kokoro", List.of(
                        new TtsVoiceSpec("af_alloy", null),
                        new TtsVoiceSpec("af_bella", null)), false, null)));
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, spec, null, null, null);

        var result = compiler.compile(def);

        assertThat(result.ttsModelRegistry().models())
                .containsKey("kokoro:af_alloy")
                .containsKey("kokoro:af_bella");
    }

    @Test
    void compiles_stt_whisper_fallback() {
        var sttSpec = new SttSpec("whisper-fallback");
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, null, sttSpec, null, null);

        var result = compiler.compile(def);

        assertThat(result.stt()).isNotNull();
    }

    @Test
    void compiles_cleanup_with_max_destructiveness() {
        var cleanupSpec = new CleanupConfigSpec(2);
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, null, null, cleanupSpec, null);

        var result = compiler.compile(def);

        assertThat(result.cleanupConfig()).isNotNull();
    }

    @Test
    void compiles_multiple_engine_types() {
        assumeTrue(vitsAvailable, "VITS native libs (espeak-ng) not available");
        var spec = new TtsSpec(List.of(
                new TtsEngineSpec("vits", List.of(new TtsVoiceSpec("lessac-medium", null)), false, null),
                new TtsEngineSpec("sherpa", List.of(new TtsVoiceSpec("lessac-medium", null)), false, LipSyncMode.DISABLED)));
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, spec, null, null, null);

        var result = compiler.compile(def);

        assertThat(result.ttsModelRegistry().models())
                .containsKey("lessac-medium")
                .containsKey("sherpa:lessac-medium")
                .hasSize(2);
    }

    @Test
    void default_tts_is_first_voice_of_first_engine() {
        assumeTrue(vitsAvailable, "VITS native libs (espeak-ng) not available");
        var spec = new TtsSpec(List.of(
                new TtsEngineSpec("vits", List.of(
                        new TtsVoiceSpec("first-voice", null),
                        new TtsVoiceSpec("second-voice", null)), false, null)));
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, spec, null, null, null);

        var result = compiler.compile(def);

        assertThat(result.defaultTts()).isSameAs(result.ttsModelRegistry().models().get("first-voice"));
    }
}
