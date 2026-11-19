package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record TtsEngineSpec(
        String type,
        List<TtsVoiceSpec> voices,
        @JsonAlias("cloned-voices") boolean cloneDiscovery,
        @JsonAlias("lip-sync") @Nullable LipSyncMode lipSync) {

    public TtsEngineSpec {
        Objects.requireNonNull(type, "type");
        voices = voices != null ? List.copyOf(voices) : List.of();
    }

    @JsonCreator
    static TtsEngineSpec fromJson(
            @JsonProperty("type") String type,
            @JsonProperty("voices") @Nullable List<TtsVoiceSpec> voices,
            @JsonProperty("cloneDiscovery") @JsonAlias("cloned-voices") @Nullable Boolean cloneDiscovery,
            @JsonProperty("lipSync") @JsonAlias("lip-sync") @Nullable LipSyncMode lipSync) {
        return new TtsEngineSpec(
                type,
                voices,
                cloneDiscovery != null && cloneDiscovery,
                lipSync);
    }
}
