package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record CollectiveGoalConfigSpec(
        @JsonAlias("alignment-threshold") @Nullable Double alignmentThreshold,
        @JsonAlias("min-aligned-agents") @Nullable Integer minAlignedAgents,
        @Nullable Duration cooldown) {}
