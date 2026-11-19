package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

public record GoalEscalationConfigSpec(
        @JsonAlias("escalation-salience-threshold") @Nullable Double escalationSalienceThreshold,
        @JsonAlias("min-axis-alignment-weight") @Nullable Double minAxisAlignmentWeight,
        @JsonAlias("cross-axis-min-weight") @Nullable Double crossAxisMinWeight,
        @JsonAlias("min-cross-axis-count") @Nullable Integer minCrossAxisCount,
        @JsonAlias("escalation-cycles") @Nullable Integer escalationCycles,
        @JsonAlias("demotion-cycles") @Nullable Integer demotionCycles,
        @JsonAlias("max-primary-drive-goals") @Nullable Integer maxPrimaryDriveGoals) {}
