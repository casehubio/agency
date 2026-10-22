package io.casehub.agency.core.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record CognitiveGoalConfigSpec(
        @Nullable Boolean enabled,
        @Nullable Double caseCreationThreshold,
        @Nullable Duration surfacingCooldown,
        @Nullable Double driveWeight,
        @Nullable Double minimumSurfacingPriority
) {}
