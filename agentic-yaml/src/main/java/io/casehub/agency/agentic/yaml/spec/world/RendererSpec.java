package io.casehub.agency.core.yaml.spec.world;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

public record RendererSpec(
        @JsonAlias("verbatim-threshold") @Nullable Integer verbatimThreshold,
        @JsonAlias("grouped-threshold") @Nullable Integer groupedThreshold) {}
