package io.casehub.agency.agentic.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

public record RetentionConfigSpec(
        @Nullable Double retentionThreshold,
        @Nullable Double confidenceWeight,
        @Nullable Double recencyWeight,
        @Nullable Double scopeWeight,
        @Nullable Double trustWeight) {}
