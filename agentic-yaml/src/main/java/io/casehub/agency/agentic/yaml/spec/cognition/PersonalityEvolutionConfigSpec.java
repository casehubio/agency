package io.casehub.agency.agentic.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

public record PersonalityEvolutionConfigSpec(
        @Nullable Double decayFactor,
        @Nullable Double l2Ceiling,
        @Nullable Double dampeningFactor) {}
