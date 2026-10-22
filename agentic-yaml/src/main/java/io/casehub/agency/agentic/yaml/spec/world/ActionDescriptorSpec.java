package io.casehub.agency.core.yaml.spec.world;

import org.jspecify.annotations.Nullable;

public record ActionDescriptorSpec(
        String type,
        String description,
        @Nullable String parameterFormat) {}
