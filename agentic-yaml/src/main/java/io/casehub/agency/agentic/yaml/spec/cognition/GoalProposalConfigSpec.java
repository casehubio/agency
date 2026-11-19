package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record GoalProposalConfigSpec(
        @JsonAlias("proposal-threshold") @Nullable Double proposalThreshold,
        @JsonAlias("relevance-threshold") @Nullable Double relevanceThreshold,
        @JsonAlias("max-drive-goals") @Nullable Integer maxDriveGoals,
        @JsonAlias("stale-after") @Nullable Duration staleAfter,
        @Nullable Duration cooldown,
        @JsonAlias("failure-abandonment-threshold") @Nullable Integer failureAbandonmentThreshold) {}
