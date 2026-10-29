package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record CognitionDefinition(
        @Nullable DriveConfigSpec drive,
        @Nullable MoodConfigSpec mood,
        @JsonProperty("personalityEvolution") @JsonAlias("personality")
        @Nullable PersonalityEvolutionConfigSpec personality,
        @Nullable UserModelConfigSpec userModel,
        @Nullable StrategyLearningConfigSpec strategyLearning,
        @Nullable MentalModelConfigSpec mentalModel,
        @Nullable NarrativeConfigSpec narrative,
        @Nullable GoalProposalConfigSpec goalProposal,
        @Nullable GoalEscalationConfigSpec goalEscalation,
        @Nullable NormDetectionConfigSpec normDetection,
        @Nullable CollectiveGoalConfigSpec collectiveGoal,
        @Nullable RetentionConfigSpec retention,
        @Nullable CognitiveGoalConfigSpec cognitiveGoal) {}
