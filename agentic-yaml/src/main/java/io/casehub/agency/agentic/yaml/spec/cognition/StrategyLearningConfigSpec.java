package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.casehub.neocortex.memory.MemoryDomain;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record StrategyLearningConfigSpec(
        @JsonAlias("min-signals-for-conversation-case") @Nullable Integer minSignalsForConversationCase,
        @JsonAlias("min-cases-for-reflection") @Nullable Integer minCasesForReflection,
        @JsonAlias("max-reflection-sources") @Nullable Integer maxReflectionSources,
        @JsonAlias("max-guidelines") @Nullable Integer maxGuidelines,
        @JsonAlias("default-dimension-value") @Nullable Double defaultDimensionValue,
        @JsonAlias("max-buffer-size") @Nullable Integer maxBufferSize,
        @JsonAlias("stale-state-timeout") @Nullable Duration staleStateTimeout,
        @JsonAlias("memory-domain") @Nullable MemoryDomain memoryDomain,
        @JsonAlias("engagement-case-type") @Nullable String engagementCaseType,
        @JsonAlias("profile-case-type") @Nullable String profileCaseType) {}
