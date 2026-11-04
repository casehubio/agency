package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.loop.DecisionOutcome;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface DecisionOutcomeExecutor {
    CompletionStage<CoordinationFeedback> execute(
        String agentId, DecisionOutcome outcome, TopologyContext context);
}
