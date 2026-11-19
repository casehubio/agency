package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record UserModelConfigSpec(
        @JsonAlias("min-signals-for-synthesis") @Nullable Integer minSignalsForSynthesis,
        @JsonAlias("synthesis-cooldown") @Nullable Duration synthesisCooldown,
        @JsonAlias("decay-rate") @Nullable Double decayRate,
        @JsonAlias("positive-weight") @Nullable Double positiveWeight,
        @JsonAlias("negative-weight") @Nullable Double negativeWeight,
        @JsonAlias("stage-config") @Nullable RelationshipStageConfigSpec stageConfig,
        @JsonAlias("expected-tick-interval") @Nullable Duration expectedTickInterval,
        @JsonAlias("eviction-timeout") @Nullable Duration evictionTimeout,
        @JsonAlias("memory-domain") @Nullable String memoryDomain,
        @JsonAlias("case-type") @Nullable String caseType,
        @JsonAlias("max-observations-in-prompt") @Nullable Integer maxObservationsInPrompt) {}
