package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

public record PersonalityEvolutionConfigSpec(
        @JsonAlias("reflection-period") @Nullable String reflectionPeriod,
        @JsonAlias("retention-per-reflection") @Nullable Double retentionPerReflection,
        @JsonAlias("min-transition-interval") @Nullable String minTransitionInterval,
        @JsonAlias("max-audit-attempts") @Nullable Integer maxAuditAttempts) {}
