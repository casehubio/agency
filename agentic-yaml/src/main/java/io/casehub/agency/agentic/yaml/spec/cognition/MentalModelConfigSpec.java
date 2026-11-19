package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record MentalModelConfigSpec(
        @JsonAlias("belief-half-life") @Nullable Duration beliefHalfLife,
        @JsonAlias("desire-half-life") @Nullable Duration desireHalfLife,
        @JsonAlias("intention-half-life") @Nullable Duration intentionHalfLife,
        @JsonAlias("confidence-floor") @Nullable Double confidenceFloor,
        @JsonAlias("projection-floor") @Nullable Double projectionFloor,
        @JsonAlias("min-signals-for-inference") @Nullable Integer minSignalsForInference,
        @JsonAlias("inference-cooldown") @Nullable Duration inferenceCooldown,
        @JsonAlias("max-signals-in-prompt") @Nullable Integer maxSignalsInPrompt,
        @JsonAlias("max-buffer-size") @Nullable Integer maxBufferSize,
        @JsonAlias("eviction-timeout") @Nullable Duration evictionTimeout,
        @JsonAlias("expected-tick-interval") @Nullable Duration expectedTickInterval,
        @JsonAlias("memory-domain") @Nullable String memoryDomain,
        @JsonAlias("case-type") @Nullable String caseType) {}
