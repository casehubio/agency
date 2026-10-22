package io.casehub.agency.agentic.yaml.spec.world;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record AffordanceSpec(
        String actionType,
        @Nullable String label,
        @Nullable String requiredItem,
        @Nullable List<String> acceptsItems) {}
