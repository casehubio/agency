package io.casehub.agency.orchestration.execution;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.aggregation.AggregationResult;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.casehub.agency.orchestration.termination.TerminationDecision;

import java.time.Duration;
import java.util.List;

public interface ExecutionEventListener {
    default void onRoutingDecision(RoutingDecision decision,
                                   List<RoutingCandidate> candidates) {}
    default void onActivation(AgentRef agent, boolean activated) {}
    default void onAgentDispatched(AgentRef agent) {}
    default void onAgentResult(AgentResult result) {}
    default void onAggregation(AggregationResult result) {}
    default void onTermination(TerminationDecision decision) {}
    default void onStateTransition(ExecutionState from, ExecutionState to) {}
    default void onFailure(AgentRef agent, Throwable cause) {}
    default void onJudgment(io.casehub.agency.deliberation.judgment.JudgmentDecision decision) {}
    default void onExecutionStart(ExecutionModel<?> model) {}
    default void onExecutionComplete(ExecutionResult result, Duration executionDuration,
                                     int iterationCount) {}

    static String agentName(AgentRef agent) {return agent.name();}
}
