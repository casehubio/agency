package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CleanupConfigSpec(int maxDestructiveness) {

    public CleanupConfigSpec {
        if (maxDestructiveness < 0)
            throw new IllegalArgumentException("maxDestructiveness must be >= 0");
    }

    @JsonCreator
    static CleanupConfigSpec fromJson(
            @JsonProperty("maxDestructiveness") @JsonAlias("max-destructiveness") Integer maxDestructiveness) {
        return new CleanupConfigSpec(
                maxDestructiveness != null ? maxDestructiveness : Integer.MAX_VALUE);
    }
}
