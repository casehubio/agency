package io.casehub.agency.orchestration.listener;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class EventLogListenerTest {

    @Test
    void recordsRoutingDecisionWithReason() {
        var recorded = new ArrayList<Map.Entry<OrchestrationEventType, Map<String, Object>>>();
        var listener = new EventLogListener((type, payload) ->
                recorded.add(Map.entry(type, payload)));

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var selected = new RoutingDecision.Selected(List.of(agent), "domain match");

        listener.onRoutingDecision(selected, List.of());

        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getKey()).isEqualTo(OrchestrationEventType.ROUTING_DECISION);
        assertThat(recorded.get(0).getValue()).containsEntry("reason", "domain match");
    }

    @Test
    void recordsAgentResultWithOutputSummary() {
        var recorded = new ArrayList<Map.Entry<OrchestrationEventType, Map<String, Object>>>();
        var listener = new EventLogListener((type, payload) ->
                recorded.add(Map.entry(type, payload)));

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var result = new AgentResult(agent, "analysis complete", Duration.ofMillis(250),
                AgentResult.AgentResultStatus.SUCCESS);

        listener.onAgentResult(result);

        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getKey()).isEqualTo(OrchestrationEventType.AGENT_RESULT);
        assertThat(recorded.get(0).getValue())
                .containsEntry("status", "SUCCESS")
                .containsEntry("outputType", "String")
                .containsEntry("outputSummary", "analysis complete");
    }

    @Test
    void recordsAllEventTypes() {
        var types = new ArrayList<OrchestrationEventType>();
        var listener = new EventLogListener((type, payload) -> types.add(type));

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var model = io.casehub.agency.orchestration.pattern.Patterns.<String>supervisor()
                .agents(agent).task("test").build();

        listener.onExecutionStart(model);
        listener.onRoutingDecision(new RoutingDecision.Selected(List.of(agent)), List.of());
        listener.onActivation(agent, true);
        listener.onAgentDispatched(agent);
        listener.onAgentResult(AgentResult.success(agent, "ok"));
        listener.onAggregation(new io.casehub.agency.orchestration.aggregation.AggregationResult.Resolved(List.of()));
        listener.onTermination(new io.casehub.agency.orchestration.termination.TerminationDecision.Complete("done"));
        listener.onExecutionComplete(new io.casehub.agency.orchestration.execution.ExecutionResult.Completed("done"),
                java.time.Duration.ofMillis(100), 3);

        assertThat(types).containsExactly(
                OrchestrationEventType.EXECUTION_STARTED,
                OrchestrationEventType.ROUTING_DECISION,
                OrchestrationEventType.ACTIVATION_EVALUATED,
                OrchestrationEventType.AGENT_DISPATCHED,
                OrchestrationEventType.AGENT_RESULT,
                OrchestrationEventType.AGGREGATION_COMPLETED,
                OrchestrationEventType.TERMINATION_EVALUATED,
                OrchestrationEventType.EXECUTION_COMPLETED);
    }

    @Test
    void recordsFailureEvent() {
        var recorded = new ArrayList<Map.Entry<OrchestrationEventType, Map<String, Object>>>();
        var listener = new EventLogListener((type, payload) ->
                recorded.add(Map.entry(type, payload)));

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));

        listener.onFailure(agent, new RuntimeException("connection timeout"));

        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getKey()).isEqualTo(OrchestrationEventType.AGENT_FAILED);
        assertThat(recorded.get(0).getValue()).containsEntry("error", "connection timeout");
    }

    @Test
    void truncatesLongOutputSummary() {
        var recorded = new ArrayList<Map.Entry<OrchestrationEventType, Map<String, Object>>>();
        var listener = new EventLogListener((type, payload) ->
                recorded.add(Map.entry(type, payload)));

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var longOutput = "x".repeat(600);
        var result = new AgentResult(agent, longOutput, Duration.ofMillis(100),
                AgentResult.AgentResultStatus.SUCCESS);

        listener.onAgentResult(result);

        var summary = (String) recorded.get(0).getValue().get("outputSummary");
        assertThat(summary).hasSize(503); // 500 + "..."
        assertThat(summary).endsWith("...");
    }

    @Test
    void handlesNullOutputInAgentResult() {
        var recorded = new ArrayList<Map.Entry<OrchestrationEventType, Map<String, Object>>>();
        var listener = new EventLogListener((type, payload) ->
                recorded.add(Map.entry(type, payload)));

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var result = new AgentResult(agent, null, Duration.ofMillis(50),
                AgentResult.AgentResultStatus.SUCCESS);

        listener.onAgentResult(result);

        assertThat(recorded.get(0).getValue()).doesNotContainKey("outputType");
        assertThat(recorded.get(0).getValue()).doesNotContainKey("outputSummary");
    }

    @Test
    void recordsUnresolvableRoutingDecision() {
        var recorded = new ArrayList<Map.Entry<OrchestrationEventType, Map<String, Object>>>();
        var listener = new EventLogListener((type, payload) ->
                recorded.add(Map.entry(type, payload)));

        listener.onRoutingDecision(new RoutingDecision.Unresolvable("no agents"), List.of());

        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getValue())
                .containsEntry("decision", "Unresolvable")
                .containsEntry("reason", "no agents");
    }

    @Test
    void recordsTerminationFailedWithReason() {
        var recorded = new ArrayList<Map.Entry<OrchestrationEventType, Map<String, Object>>>();
        var listener = new EventLogListener((type, payload) ->
                recorded.add(Map.entry(type, payload)));

        listener.onTermination(new io.casehub.agency.orchestration.termination.TerminationDecision.Failed("max retries"));

        assertThat(recorded.get(0).getValue())
                .containsEntry("decision", "Failed")
                .containsEntry("reason", "max retries");
    }
}
