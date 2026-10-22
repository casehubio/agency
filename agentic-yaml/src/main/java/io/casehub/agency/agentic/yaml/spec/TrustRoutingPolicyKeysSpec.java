package io.casehub.agency.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record TrustRoutingPolicyKeysSpec(
        String scopePrefix,
        @Nullable Map<String, String> floors) {

    public TrustRoutingPolicyKeysSpec {
        Objects.requireNonNull(scopePrefix, "scopePrefix");
        if (floors != null) floors = Map.copyOf(floors);
    }
}
