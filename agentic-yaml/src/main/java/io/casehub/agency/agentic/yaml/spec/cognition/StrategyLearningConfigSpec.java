package io.casehub.agency.core.yaml.spec.cognition;

import io.casehub.neocortex.memory.MemoryDomain;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record StrategyLearningConfigSpec(
        @Nullable Integer minSignalsForConversationCase,
        @Nullable Integer minCasesForReflection,
        @Nullable Integer maxReflectionSources,
        @Nullable Integer maxGuidelines,
        @Nullable Double defaultDimensionValue,
        @Nullable Integer maxBufferSize,
        @Nullable Duration staleStateTimeout,
        @Nullable MemoryDomain memoryDomain,
        @Nullable String engagementCaseType,
        @Nullable String profileCaseType) {}
