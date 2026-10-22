package io.casehub.agency.core.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record GoalProposalConfigSpec(
        @Nullable Double proposalThreshold,
        @Nullable Double relevanceThreshold,
        @Nullable Integer maxDriveGoals,
        @Nullable Duration staleAfter,
        @Nullable Duration cooldown,
        @Nullable Integer failureAbandonmentThreshold) {}
