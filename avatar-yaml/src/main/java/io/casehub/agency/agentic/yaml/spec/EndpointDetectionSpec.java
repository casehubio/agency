package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record EndpointDetectionSpec(boolean enabled,
                                    @JsonProperty("silenceThresholdMs") @JsonAlias("silence-threshold-ms")
                                    @Nullable Integer silenceThresholdMs,
                                    @JsonProperty("minSpeechMs") @JsonAlias("min-speech-ms")
                                    @Nullable Integer minSpeechMs) {

    @JsonCreator
    public EndpointDetectionSpec(@JsonProperty("enabled") boolean enabled,
                                 @JsonProperty("silenceThresholdMs") @JsonAlias("silence-threshold-ms")
                                 @Nullable Integer silenceThresholdMs,
                                 @JsonProperty("minSpeechMs") @JsonAlias("min-speech-ms")
                                 @Nullable Integer minSpeechMs) {
        if (silenceThresholdMs != null && silenceThresholdMs <= 0) {
            throw new IllegalArgumentException("silenceThresholdMs must be positive: " + silenceThresholdMs);
        }
        if (minSpeechMs != null && minSpeechMs <= 0) {
            throw new IllegalArgumentException("minSpeechMs must be positive: " + minSpeechMs);
        }
        this.enabled = enabled;
        this.silenceThresholdMs = silenceThresholdMs;
        this.minSpeechMs = minSpeechMs;
    }

    public EndpointDetectionSpec(boolean enabled, @Nullable Integer silenceThresholdMs) {
        this(enabled, silenceThresholdMs, null);
    }
}
