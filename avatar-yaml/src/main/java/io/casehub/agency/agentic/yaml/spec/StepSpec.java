package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

public record StepSpec(String name, Map<String, Object> config) {

    @JsonCreator
    public StepSpec(@JsonProperty("name") String name,
                    @JsonProperty("config") Map<String, Object> config) {
        this.name = Objects.requireNonNull(name, "step name is required");
        this.config = config != null ? config : Map.of();
    }
}
