package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record TrustRoutingPolicyKeysSpec(
        @JsonAlias("scope-prefix") String scopePrefix,
        @Nullable Map<String, String> floors) {

    public TrustRoutingPolicyKeysSpec {
        Objects.requireNonNull(scopePrefix, "scopePrefix");
        if (floors != null) floors = Map.copyOf(floors);
    }
}
