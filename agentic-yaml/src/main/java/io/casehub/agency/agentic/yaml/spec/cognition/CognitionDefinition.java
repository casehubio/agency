package io.casehub.agency.agentic.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

public record CognitionDefinition(
        @Nullable DriveConfigSpec drive,
        @Nullable MoodConfigSpec mood,
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
