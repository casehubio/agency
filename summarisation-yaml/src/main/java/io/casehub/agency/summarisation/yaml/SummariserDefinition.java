package io.casehub.agency.summarisation.yaml;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public record SummariserDefinition(String type, @Nullable String mode, Map<String, Object> config) {

    @JsonCreator
    static SummariserDefinition fromJson(
            @JsonProperty("type") String type,
            @JsonProperty("mode") @Nullable String mode,
            @JsonAnySetter Map<String, Object> config) {
        return new SummariserDefinition(type, mode, config != null ? Collections.unmodifiableMap(config) : Map.of());
    }
}
