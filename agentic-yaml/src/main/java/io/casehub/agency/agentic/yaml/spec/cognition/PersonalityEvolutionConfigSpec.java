package io.casehub.agency.core.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

public record PersonalityEvolutionConfigSpec(
        @Nullable String reflectionPeriod,
        @Nullable Double retentionPerReflection,
        @Nullable String minTransitionInterval,
        @Nullable Integer maxAuditAttempts) {}
