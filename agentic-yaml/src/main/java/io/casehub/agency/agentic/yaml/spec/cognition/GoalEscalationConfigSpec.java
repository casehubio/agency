package io.casehub.agency.agentic.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

public record GoalEscalationConfigSpec(
        @Nullable Double escalationSalienceThreshold,
        @Nullable Double minAxisAlignmentWeight,
        @Nullable Double crossAxisMinWeight,
        @Nullable Integer minCrossAxisCount,
        @Nullable Integer escalationCycles,
        @Nullable Integer demotionCycles,
        @Nullable Integer maxPrimaryDriveGoals) {}
