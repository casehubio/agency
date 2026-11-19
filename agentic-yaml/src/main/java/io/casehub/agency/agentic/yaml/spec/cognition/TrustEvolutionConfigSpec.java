package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record TrustEvolutionConfigSpec(
    @Nullable List<TrustEventMappingSpec> events,
    @Nullable TrustScoringSpec scoring,
    @Nullable TrustConsolidationSpec consolidation,
    @Nullable TrustLevelSpec levels
) {
    public record TrustEventMappingSpec(
        @JsonAlias("action-type") String actionType,
        String verdict,
        double confidence,
        @JsonAlias("witness-confidence") double witnessConfidence
    ) {}

    public record TrustScoringSpec(
        @JsonAlias("decay-half-life-days") int decayHalfLifeDays,
        @JsonAlias("negative-decay-multiplier") double negativeDecayMultiplier
    ) {}

    public record TrustConsolidationSpec(
        @JsonAlias("overlay-property") String overlayProperty,
        @JsonAlias("significant-change-threshold") double significantChangeThreshold
    ) {}

    public record TrustLevelSpec(
        double high,
        double moderate,
        double low
    ) {}
}
