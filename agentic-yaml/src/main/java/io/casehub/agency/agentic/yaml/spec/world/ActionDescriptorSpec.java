package io.casehub.agency.core.yaml.spec.world;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

public record ActionDescriptorSpec(
        String type,
        String description,
        @JsonAlias("parameter-format") @Nullable String parameterFormat) {}
