package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

public record RetentionConfigSpec(
        @JsonAlias("retention-threshold") @Nullable Double retentionThreshold,
        @JsonAlias("confidence-weight") @Nullable Double confidenceWeight,
        @JsonAlias("recency-weight") @Nullable Double recencyWeight,
        @JsonAlias("scope-weight") @Nullable Double scopeWeight,
        @JsonAlias("trust-weight") @Nullable Double trustWeight) {}
