package io.casehub.agency.core.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record MentalModelConfigSpec(
        @Nullable Duration beliefHalfLife,
        @Nullable Duration desireHalfLife,
        @Nullable Duration intentionHalfLife,
        @Nullable Double confidenceFloor,
        @Nullable Double projectionFloor,
        @Nullable Integer minSignalsForInference,
        @Nullable Duration inferenceCooldown,
        @Nullable Integer maxSignalsInPrompt,
        @Nullable Integer maxBufferSize,
        @Nullable Duration evictionTimeout,
        @Nullable Duration expectedTickInterval,
        @Nullable String memoryDomain,
        @Nullable String caseType) {}
