package io.casehub.agency.agentic.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record CollectiveGoalConfigSpec(
        @Nullable Double alignmentThreshold,
        @Nullable Integer minAlignedAgents,
        @Nullable Duration cooldown) {}
