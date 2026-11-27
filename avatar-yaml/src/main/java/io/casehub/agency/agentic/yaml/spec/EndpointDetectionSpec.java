package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record EndpointDetectionSpec(boolean enabled,
                                    @JsonProperty("silenceThresholdMs") @JsonAlias("silence-threshold-ms")
                                    @Nullable Integer silenceThresholdMs) {

    @JsonCreator
    public EndpointDetectionSpec(@JsonProperty("enabled") boolean enabled,
                                 @JsonProperty("silenceThresholdMs") @JsonAlias("silence-threshold-ms")
                                 @Nullable Integer silenceThresholdMs) {
        if (silenceThresholdMs != null && silenceThresholdMs <= 0) {
            throw new IllegalArgumentException("silenceThresholdMs must be positive: " + silenceThresholdMs);
        }
        this.enabled = enabled;
        this.silenceThresholdMs = silenceThresholdMs;
    }
}
