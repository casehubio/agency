package io.casehub.agency.orchestration.listener;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.execution.ExecutionEventListener;
import io.casehub.agency.orchestration.execution.ExecutionResult;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class MetricsListener implements ExecutionEventListener {

    private static final AttributeKey<String> AGENT = AttributeKey.stringKey("agent");
    private static final AttributeKey<String> STATUS = AttributeKey.stringKey("status");
    private static final AttributeKey<String> DECISION_TYPE = AttributeKey.stringKey("decision_type");
    private static final AttributeKey<String> ACTIVATED = AttributeKey.stringKey("activated");
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");
    private static final AttributeKey<String> SCOPE   = AttributeKey.stringKey("scope");
    private static final AttributeKey<String> EVENT_TYPE = AttributeKey.stringKey("event_type");
    private static final AttributeKey<String> CAPABILITY = AttributeKey.stringKey("capability");


    private final DoubleHistogram agentDuration;
    private final LongCounter routingDecisions;
    private final LongCounter activationEvaluations;
    private final LongCounter agentFailures;
    private final DoubleHistogram executionDuration;
    private final DoubleHistogram executionIterations;
    private final LongCounter     loopTriggers;
    private final DoubleHistogram reasoningDuration;
    private final DoubleHistogram reasoningTokens;
    private final LongCounter     budgetExhaustions;
    private final LongCounter     loopDegradations;


    public MetricsListener(Meter meter) {
        Objects.requireNonNull(meter, "meter");

        this.agentDuration = meter.histogramBuilder("casehub.agentic.agent.duration")
                .setUnit("s")
                .setDescription("Agent invocation latency")
                .build();

        this.routingDecisions = meter.counterBuilder("casehub.agentic.routing.decisions")
                .setDescription("Routing decision count by type")
                .build();

        this.activationEvaluations = meter.counterBuilder("casehub.agentic.activation.evaluations")
                .setDescription("Activation evaluation count by agent and outcome")
                .build();

        this.agentFailures = meter.counterBuilder("casehub.agentic.agent.failures")
                .setDescription("Agent invocation exception count")
                .build();

        this.executionDuration = meter.histogramBuilder("casehub.agentic.execution.duration")
                .setUnit("s")
                .setDescription("Total execution wall-clock time")
                .build();

        this.executionIterations = meter.histogramBuilder("casehub.agentic.execution.iterations")
                .setUnit("{iteration}")
                .setDescription("Iteration count per execution")
                .build();

        this.loopTriggers = meter.counterBuilder("casehub.agentic.loop.triggers")
                .setDescription("Decision loop trigger count by agent and scope")
                .build();

        this.reasoningDuration = meter.histogramBuilder("casehub.agentic.loop.reasoning.duration")
                .setUnit("ms")
                .setDescription("LLM reasoning latency per invocation")
                .build();

        this.reasoningTokens = meter.histogramBuilder("casehub.agentic.loop.reasoning.tokens")
                .setUnit("{token}")
                .setDescription("Tokens consumed per LLM reasoning invocation")
                .build();

        this.budgetExhaustions = meter.counterBuilder("casehub.agentic.loop.budget.exhaustions")
                .setDescription("Budget exhaustion count by agent")
                .build();

        this.loopDegradations = meter.counterBuilder("casehub.agentic.loop.degradations")
                .setDescription("Decision loop degradation count by agent and capability")
                .build();
    }

    @Override
    public void onRoutingDecision(RoutingDecision decision, List<RoutingCandidate> candidates) {
        routingDecisions.add(1, Attributes.of(
                DECISION_TYPE, decision.getClass().getSimpleName()));
    }

    @Override
    public void onActivation(AgentRef agent, boolean activated) {
        activationEvaluations.add(1, Attributes.of(
                AGENT, ExecutionEventListener.agentName(agent),
                ACTIVATED, String.valueOf(activated)));
    }

    @Override
    public void onAgentResult(AgentResult result) {
        agentDuration.record(result.duration().toMillis() / 1000.0, Attributes.of(
                AGENT, ExecutionEventListener.agentName(result.agent()),
                STATUS, result.status().name()));
    }

    @Override
    public void onFailure(AgentRef agent, Throwable cause) {
        agentFailures.add(1, Attributes.of(
                AGENT, ExecutionEventListener.agentName(agent)));
    }

    @Override
    public void onExecutionComplete(ExecutionResult result, Duration executionDuration,
                                    int iterationCount) {
        var outcome = result.getClass().getSimpleName();
        this.executionDuration.record(executionDuration.toMillis() / 1000.0,
                Attributes.of(OUTCOME, outcome));
        this.executionIterations.record(iterationCount,
                Attributes.of(OUTCOME, outcome));
    }

    @Override
    public void onDecisionLoopEvent(io.casehub.agency.orchestration.loop.DecisionLoopEvent event) {
        switch (event) {
            case io.casehub.agency.orchestration.loop.DecisionLoopEvent.LoopTriggered e -> loopTriggers.add(1, Attributes.of(
                    AGENT, e.agentId(),
                    SCOPE, e.scope().name()));
            case io.casehub.agency.orchestration.loop.DecisionLoopEvent.ReasoningCompleted e -> {
                reasoningDuration.record(e.latencyMs(), Attributes.of(AGENT, e.agentId()));
                reasoningTokens.record(e.tokensConsumed(), Attributes.of(AGENT, e.agentId()));
            }
            case io.casehub.agency.orchestration.loop.DecisionLoopEvent.BudgetExhausted e -> budgetExhaustions.add(1, Attributes.of(AGENT, e.agentId()));
            case io.casehub.agency.orchestration.loop.DecisionLoopEvent.Degraded e -> loopDegradations.add(1, Attributes.of(
                    AGENT, e.agentId(),
                    CAPABILITY, e.capability()));
            default -> {}
        }
    }

}
