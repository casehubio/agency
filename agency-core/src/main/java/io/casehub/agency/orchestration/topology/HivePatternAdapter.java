package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.platform.agent.AgentProvider;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HivePatternAdapter {

    private final AgentProvider agentProvider;

    public HivePatternAdapter(AgentProvider agentProvider) {
        this.agentProvider = Objects.requireNonNull(agentProvider);
    }

    public CompletionStage<CoordinationFeedback> execute(
            OrchestrationPatternType patternType,
            String agentId,
            DecisionOutcome outcome,
            TopologyContext context) {
        if (outcome instanceof DecisionOutcome.ScopeGated) {
            return CompletableFuture.completedFuture(
                new CoordinationFeedback(agentId, outcome, FeedbackSignal.SCOPE_VIOLATION));
        }
        return switch (patternType) {
            case SUPERVISOR -> executeSupervisor(agentId, outcome, context);
            case DEBATE -> executeDebate(agentId, outcome, context);
            case VOTING -> executeVoting(agentId, outcome, context);
        };
    }

    private CompletionStage<CoordinationFeedback> executeSupervisor(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        return CompletableFuture.completedFuture(
            new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
    }

    private CompletionStage<CoordinationFeedback> executeDebate(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        return CompletableFuture.completedFuture(
            new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
    }

    private CompletionStage<CoordinationFeedback> executeVoting(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        return CompletableFuture.completedFuture(
            new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
    }

    public void onFeedback(OrchestrationPatternType patternType,
                           CoordinationFeedback feedback) {
    }

    public AgentProvider agentProvider() { return agentProvider; }
}
