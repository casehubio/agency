package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record CognitionDefinition(
        @Nullable DriveConfigSpec drive,
        @Nullable MoodConfigSpec mood,
        @JsonProperty("personalityEvolution") @JsonAlias({"personality", "personality-evolution"})
        @Nullable PersonalityEvolutionConfigSpec personality,
        @JsonAlias("user-model") @Nullable UserModelConfigSpec userModel,
        @JsonAlias("strategy-learning") @Nullable StrategyLearningConfigSpec strategyLearning,
        @JsonAlias("mental-model") @Nullable MentalModelConfigSpec mentalModel,
        @Nullable NarrativeConfigSpec narrative,
        @JsonAlias("goal-proposal") @Nullable GoalProposalConfigSpec goalProposal,
        @JsonAlias("goal-escalation") @Nullable GoalEscalationConfigSpec goalEscalation,
        @JsonAlias("norm-detection") @Nullable NormDetectionConfigSpec normDetection,
        @JsonAlias("collective-goal") @Nullable CollectiveGoalConfigSpec collectiveGoal,
        @Nullable RetentionConfigSpec retention,
        @JsonAlias("cognitive-goal") @Nullable CognitiveGoalConfigSpec cognitiveGoal) {}
