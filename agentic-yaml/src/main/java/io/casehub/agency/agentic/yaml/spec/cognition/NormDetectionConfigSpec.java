package io.casehub.agency.core.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

public record NormDetectionConfigSpec(
        @Nullable Integer minObservationsForNorm,
        @Nullable Double establishedThreshold,
        @Nullable Double decliningThreshold,
        @Nullable Integer minAgentsForNorm,
        @Nullable String memoryDomain,
        @Nullable String caseType) {}
