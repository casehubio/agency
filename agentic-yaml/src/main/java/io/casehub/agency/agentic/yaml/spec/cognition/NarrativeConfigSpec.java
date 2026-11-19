package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

public record NarrativeConfigSpec(
        @JsonAlias("synthesis-gate") @Nullable NarrativeSynthesisGateSpec synthesisGate,
        @JsonAlias("max-episodes") @Nullable Integer maxEpisodes,
        @JsonAlias("max-themes") @Nullable Integer maxThemes,
        @JsonAlias("theme-salience-floor") @Nullable Double themeSalienceFloor,
        @JsonAlias("max-reflections-per-synthesis") @Nullable Integer maxReflectionsPerSynthesis,
        @JsonAlias("memory-domain") @Nullable String memoryDomain,
        @JsonAlias("case-type") @Nullable String caseType) {}
