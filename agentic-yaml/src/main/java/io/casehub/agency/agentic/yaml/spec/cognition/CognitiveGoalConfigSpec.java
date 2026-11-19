package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record CognitiveGoalConfigSpec(
        @Nullable Boolean enabled,
        @JsonAlias("case-creation-threshold") @Nullable Double caseCreationThreshold,
        @JsonAlias("surfacing-cooldown") @Nullable Duration surfacingCooldown,
        @JsonAlias("drive-weight") @Nullable Double driveWeight,
        @JsonAlias("minimum-surfacing-priority") @Nullable Double minimumSurfacingPriority
) {}
