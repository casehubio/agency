package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record InterruptSpec(boolean enabled,
                            @JsonProperty("cannedResponses") @JsonAlias("canned-responses")
                            List<String> cannedResponses,
                            @JsonProperty("preSynthesise") @JsonAlias("pre-synthesise")
                            boolean preSynthesise) {

    @JsonCreator
    public InterruptSpec(@JsonProperty("enabled") boolean enabled,
                         @JsonProperty("cannedResponses") @JsonAlias("canned-responses")
                         @Nullable List<String> cannedResponses,
                         @JsonProperty("preSynthesise") @JsonAlias("pre-synthesise")
                         boolean preSynthesise) {
        this.enabled = enabled;
        this.cannedResponses = cannedResponses != null ? List.copyOf(cannedResponses) : List.of();
        this.preSynthesise = preSynthesise;
    }
}
