package io.casehub.agency.core.yaml.spec;

import org.jspecify.annotations.Nullable;

public record MultiSwarmCoordinationSpec(
        @Nullable Double relevanceThreshold,
        @Nullable Double escalationThreshold,
        @Nullable Double convergenceThreshold
) {}
