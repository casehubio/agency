package io.casehub.agency.agentic.yaml.spec.world;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record InlineEntitySpec(
        String id,
        String displayName,
        @Nullable String description,
        @Nullable List<AffordanceSpec> affordances) {}
