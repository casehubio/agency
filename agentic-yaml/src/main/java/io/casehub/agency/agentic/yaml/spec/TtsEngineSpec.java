package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record TtsEngineSpec(
        String type,
        List<TtsVoiceSpec> voices,
        @JsonAlias("cloned-voices") @Nullable List<String> clonedVoices) {

    public TtsEngineSpec {
        Objects.requireNonNull(type, "type");
        voices = voices != null ? List.copyOf(voices) : List.of();
        clonedVoices = clonedVoices != null ? List.copyOf(clonedVoices) : null;
    }
}
