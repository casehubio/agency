package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record TtsVoiceSpec(
        String name,
        @Nullable String model) {

    public TtsVoiceSpec {
        Objects.requireNonNull(name, "name");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static TtsVoiceSpec fromString(String name) {
        return new TtsVoiceSpec(name, null);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    static TtsVoiceSpec fromJson(
            @JsonProperty("name") String name,
            @JsonProperty("model") @Nullable String model) {
        return new TtsVoiceSpec(name, model);
    }
}
