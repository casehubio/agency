package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record AvatarDefinition(
        String agent,
        String model,
        @Nullable String ttsVoice,
        @Nullable String cameraView,
        int sampleRate,
        double speed,
        double minPlaybackRate,
        @Nullable TtsSpec tts,
        @Nullable SttSpec stt,
        @Nullable CleanupConfigSpec cleanup,
        @Nullable SpeechPipelineSpec pipeline) {

    public AvatarDefinition {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(model, "model");
    }

    @JsonCreator
    static AvatarDefinition fromJson(
            @JsonProperty("agent") String agent,
            @JsonProperty("model") String model,
            @JsonProperty("ttsVoice") @JsonAlias("tts-voice") @Nullable String ttsVoice,
            @JsonProperty("cameraView") @JsonAlias("camera-view") @Nullable String cameraView,
            @JsonProperty("sampleRate") @JsonAlias("sample-rate") @Nullable Integer sampleRate,
            @JsonProperty("speed") @Nullable Double speed,
            @JsonProperty("minPlaybackRate") @JsonAlias("min-playback-rate") @Nullable Double minPlaybackRate,
            @JsonProperty("tts") @Nullable TtsSpec tts,
            @JsonProperty("stt") @Nullable SttSpec stt,
            @JsonProperty("cleanup") @Nullable CleanupConfigSpec cleanup,
            @JsonProperty("pipeline") @Nullable SpeechPipelineSpec pipeline) {
        return new AvatarDefinition(
                agent, model, ttsVoice,
                cameraView,
                sampleRate != null ? sampleRate : 16000,
                speed != null ? speed : 0.9,
                minPlaybackRate != null ? minPlaybackRate : 0.6,
                tts, stt, cleanup, pipeline);
    }
}
