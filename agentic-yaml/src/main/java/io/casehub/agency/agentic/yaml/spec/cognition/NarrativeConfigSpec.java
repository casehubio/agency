package io.casehub.agency.core.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

public record NarrativeConfigSpec(
        @Nullable NarrativeSynthesisGateSpec synthesisGate,
        @Nullable Integer maxEpisodes,
        @Nullable Integer maxThemes,
        @Nullable Double themeSalienceFloor,
        @Nullable Integer maxReflectionsPerSynthesis,
        @Nullable String memoryDomain,
        @Nullable String caseType) {}
