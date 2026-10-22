package io.casehub.agency.core.yaml.spec.world;

import org.jspecify.annotations.Nullable;

public record RendererSpec(
        @Nullable Integer verbatimThreshold,
        @Nullable Integer groupedThreshold) {}
