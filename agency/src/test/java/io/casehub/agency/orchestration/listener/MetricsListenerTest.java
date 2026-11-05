package io.casehub.agency.orchestration.listener;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.orchestration.execution.ExecutionResult;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsListenerTest {

    private InMemoryMetricReader reader;
    private MetricsListener listener;

    @BeforeEach
    void setUp() {
        reader = InMemoryMetricReader.create();
        var meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(reader)
                .build();
        listener = new MetricsListener(meterProvider.get("test"));
    }

    @Nested
    class AgentDuration {

        @Test
        void recordsAgentDurationFromResult() {
            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var result = new AgentResult(agent, "ok", Duration.ofMillis(150),
                    AgentResult.AgentResultStatus.SUCCESS);

            listener.onAgentResult(result);

            var metrics = reader.collectAllMetrics();
            var histogram = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.agent.duration"))
                    .findFirst();
            assertThat(histogram).isPresent();
        }
    }

    @Nested
    class RoutingDecisions {

        @Test
        void countsRoutingDecisions() {
            var decision = new RoutingDecision.Selected(List.of(), "test");
            listener.onRoutingDecision(decision, List.of());

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.routing.decisions"))
                    .findFirst();
            assertThat(counter).isPresent();
        }
    }

    @Nested
    class ActivationEvaluations {

        @Test
        void countsActivationEvaluations() {
            var agent = AgentRef.human(null);
            listener.onActivation(agent, true);
            listener.onActivation(agent, false);

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.activation.evaluations"))
                    .findFirst();
            assertThat(counter).isPresent();
        }
    }

    @Nested
    class AgentFailures {

        @Test
        void countsAgentFailures() {
            var agent = AgentRef.human(null);
            listener.onFailure(agent, new RuntimeException("oops"));

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.agent.failures"))
                    .findFirst();
            assertThat(counter).isPresent();
        }
    }

    @Nested
    class ExecutionLifecycle {

        @Test
        void recordsExecutionDurationAndIterations() {
            var result = new ExecutionResult.Completed("done");
            listener.onExecutionComplete(result, Duration.ofSeconds(5), 7);

            var metrics = reader.collectAllMetrics();
            var duration = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.execution.duration"))
                    .findFirst();
            var iterations = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.execution.iterations"))
                    .findFirst();
            assertThat(duration).isPresent();
            assertThat(iterations).isPresent();
        }

        @Test
        void cancelledExecutionRecordsMetrics() {
            var result = new ExecutionResult.Cancelled();
            listener.onExecutionComplete(result, Duration.ofMillis(200), 1);

            var metrics = reader.collectAllMetrics();
            var duration = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.execution.duration"))
                    .findFirst();
            assertThat(duration).isPresent();
        }
    }


    @Nested
    class DecisionLoopEvents {

        private static final java.util.UUID    CASE_ID  = java.util.UUID.randomUUID();
        private static final String            AGENT_ID = "test-agent";
        private static final java.time.Instant NOW      = java.time.Instant.now();

        @Test
        void loopTriggered_incrementsCounter() {
            var event = new io.casehub.agency.orchestration.loop.DecisionLoopEvent.LoopTriggered(
                    CASE_ID, AGENT_ID, io.casehub.api.model.coordination.DecisionScope.FULL_AUTONOMY,
                    io.casehub.api.model.coordination.CommunicationMode.BROADCAST, NOW);
            listener.onDecisionLoopEvent(event);

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                                 .filter(m -> m.getName().equals("casehub.agentic.loop.triggers"))
                                 .findFirst();
            assertThat(counter).isPresent();
        }

        @Test
        void reasoningCompleted_recordsDurationAndTokens() {
            var event = new io.casehub.agency.orchestration.loop.DecisionLoopEvent.ReasoningCompleted(
                    CASE_ID, AGENT_ID, "corr-1", 450, 200, NOW);
            listener.onDecisionLoopEvent(event);

            var metrics = reader.collectAllMetrics();
            var duration = metrics.stream()
                                  .filter(m -> m.getName().equals("casehub.agentic.loop.reasoning.duration"))
                                  .findFirst();
            var tokens = metrics.stream()
                                .filter(m -> m.getName().equals("casehub.agentic.loop.reasoning.tokens"))
                                .findFirst();
            assertThat(duration).isPresent();
            assertThat(tokens).isPresent();
        }

        @Test
        void budgetExhausted_incrementsCounter() {
            var event = new io.casehub.agency.orchestration.loop.DecisionLoopEvent.BudgetExhausted(
                    CASE_ID, AGENT_ID, "call budget exhausted",
                    new io.casehub.agency.orchestration.budget.BudgetPressure(0.99,
                                                                              io.casehub.agency.orchestration.budget.BudgetPriority.NORMAL), NOW);
            listener.onDecisionLoopEvent(event);

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                                 .filter(m -> m.getName().equals("casehub.agentic.loop.budget.exhaustions"))
                                 .findFirst();
            assertThat(counter).isPresent();
        }

        @Test
        void degraded_incrementsCounter() {
            var event = new io.casehub.agency.orchestration.loop.DecisionLoopEvent.Degraded(
                    CASE_ID, AGENT_ID, "reasoning", "latency exceeded", true, NOW);
            listener.onDecisionLoopEvent(event);

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                                 .filter(m -> m.getName().equals("casehub.agentic.loop.degradations"))
                                 .findFirst();
            assertThat(counter).isPresent();
        }

        @Test
        void observationGathered_noMetrics() {
            var event = new io.casehub.agency.orchestration.loop.DecisionLoopEvent.ObservationGathered(
                    CASE_ID, AGENT_ID, "signal.quality", NOW);
            listener.onDecisionLoopEvent(event);

            var metrics = reader.collectAllMetrics();
            assertThat(metrics.stream()
                              .filter(m -> m.getName().startsWith("casehub.agentic.loop"))
                              .toList()).isEmpty();
        }
    }

    @Nested
    class NoOpSafety {

        @Test
        void worksWithNoOpMeter() {
            var noOpListener = new MetricsListener(
                    io.opentelemetry.api.OpenTelemetry.noop().getMeter("test"));
            var agent = AgentRef.human(null);
            noOpListener.onActivation(agent, true);
            noOpListener.onAgentResult(AgentResult.success(agent, "ok"));
            noOpListener.onFailure(agent, new RuntimeException("oops"));
            noOpListener.onExecutionComplete(new ExecutionResult.Completed("ok"),
                    Duration.ZERO, 1);
            noOpListener.onDecisionLoopEvent(
                    new io.casehub.agency.orchestration.loop.DecisionLoopEvent.LoopTriggered(
                            java.util.UUID.randomUUID(), "a",
                            io.casehub.api.model.coordination.DecisionScope.FULL_AUTONOMY,
                            io.casehub.api.model.coordination.CommunicationMode.BROADCAST,
                            java.time.Instant.now()));
            // no exception = pass
        }
    }
}
