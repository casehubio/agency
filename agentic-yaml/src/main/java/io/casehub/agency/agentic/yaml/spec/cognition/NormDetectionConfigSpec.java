package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

public record NormDetectionConfigSpec(
        @JsonAlias("min-observations-for-norm") @Nullable Integer minObservationsForNorm,
        @JsonAlias("established-threshold") @Nullable Double establishedThreshold,
        @JsonAlias("declining-threshold") @Nullable Double decliningThreshold,
        @JsonAlias("min-agents-for-norm") @Nullable Integer minAgentsForNorm,
        @JsonAlias("memory-domain") @Nullable String memoryDomain,
        @JsonAlias("case-type") @Nullable String caseType) {}
