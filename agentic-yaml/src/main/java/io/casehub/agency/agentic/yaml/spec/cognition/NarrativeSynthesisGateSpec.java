package io.casehub.agency.agentic.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record NarrativeSynthesisGateSpec(
        @Nullable Integer minNewReflections,
        @Nullable Double noveltyThreshold,
        @Nullable Duration quietPeriodBypass) {}
