package io.casehub.agency.agentic.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record UserModelConfigSpec(
        @Nullable Integer minSignalsForSynthesis,
        @Nullable Duration synthesisCooldown,
        @Nullable Double decayRate,
        @Nullable Double positiveWeight,
        @Nullable Double negativeWeight,
        @Nullable RelationshipStageConfigSpec stageConfig,
        @Nullable Duration expectedTickInterval,
        @Nullable Duration evictionTimeout,
        @Nullable String memoryDomain,
        @Nullable String caseType,
        @Nullable Integer maxObservationsInPrompt) {}
