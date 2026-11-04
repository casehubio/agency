package io.casehub.agency.orchestration.budget;

import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetAwareSummariserTest {

    static class StubSummariser implements ContentSummariser<String, String> {
        int callCount = 0;
        @Override
        public CompletionStage<String> summarise(List<String> items, @Nullable String previous) {
            callCount++;
            return CompletableFuture.completedFuture("result-" + callCount);
        }
    }

    private AutonomyBudgetPool pool(int maxCalls) {
        return new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(maxCalls, Long.MAX_VALUE, 0.0, maxCalls, Long.MAX_VALUE), "test");
    }

    @Test
    void summarise_delegatesWhenBudgetAvailable() throws Exception {
        var delegate = new StubSummariser();
        var p = pool(10);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        var summariser = new BudgetAwareSummariser<>(delegate, view);

        var result = summariser.summarise(List.of("input"), null).toCompletableFuture().get();
        assertThat(result).isEqualTo("result-1");
        assertThat(delegate.callCount).isEqualTo(1);
        assertThat(view.callsUsed()).isEqualTo(1);
    }

    @Test
    void summarise_returnsPreviousWhenDenied() throws Exception {
        var delegate = new StubSummariser();
        var p = pool(1);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        var summariser = new BudgetAwareSummariser<>(delegate, view);

        summariser.summarise(List.of("first"), null).toCompletableFuture().get();
        var result = summariser.summarise(List.of("second"), "prev").toCompletableFuture().get();
        assertThat(result).isEqualTo("prev");
        assertThat(delegate.callCount).isEqualTo(1);
    }

    @Test
    void summarise_returnsNullWhenDeniedWithNullPrevious() throws Exception {
        var delegate = new StubSummariser();
        var p = pool(1);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        var summariser = new BudgetAwareSummariser<>(delegate, view);

        summariser.summarise(List.of("exhaust"), null).toCompletableFuture().get();
        var result = summariser.summarise(List.of("denied"), null).toCompletableFuture().get();
        assertThat(result).isNull();
        assertThat(delegate.callCount).isEqualTo(1);
    }

    @Test
    void summarise_multipleCalls_tracksBudget() throws Exception {
        var delegate = new StubSummariser();
        var p = pool(3);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        var summariser = new BudgetAwareSummariser<>(delegate, view);

        summariser.summarise(List.of("a"), null).toCompletableFuture().get();
        summariser.summarise(List.of("b"), null).toCompletableFuture().get();
        summariser.summarise(List.of("c"), null).toCompletableFuture().get();
        var result = summariser.summarise(List.of("d"), "fallback").toCompletableFuture().get();

        assertThat(delegate.callCount).isEqualTo(3);
        assertThat(result).isEqualTo("fallback");
        assertThat(view.callsUsed()).isEqualTo(3);
    }

    @Test
    void summarise_emitsAcquisitionGrantedEvent() throws Exception {
        var delegate   = new StubSummariser();
        var p          = pool(10);
        var view       = p.createView("agent-1", BudgetPriority.NORMAL);
        var events     = new java.util.ArrayList<BudgetTelemetryEvent>();
        var summariser = new BudgetAwareSummariser<>(delegate, view, events::add, r -> 100L, "test-model");

        summariser.summarise(List.of("input"), null).toCompletableFuture().get();

        assertThat(events).hasSize(2);
        var acquisition = (BudgetTelemetryEvent.Acquisition) events.get(0);
        assertThat(acquisition.agentId()).isEqualTo("agent-1");
        assertThat(acquisition.topologyId()).isEqualTo("test");
        assertThat(acquisition.priority()).isEqualTo(BudgetPriority.NORMAL);
        assertThat(acquisition.granted()).isTrue();
        assertThat(acquisition.correlationId()).isNotNull();
    }

    @Test
    void summarise_emitsAcquisitionDeniedEvent() throws Exception {
        var delegate   = new StubSummariser();
        var p          = pool(1);
        var view       = p.createView("agent-1", BudgetPriority.NORMAL);
        var events     = new java.util.ArrayList<BudgetTelemetryEvent>();
        var summariser = new BudgetAwareSummariser<>(delegate, view, events::add, r -> 100L, "test-model");

        summariser.summarise(List.of("exhaust"), null).toCompletableFuture().get();
        summariser.summarise(List.of("denied"), null).toCompletableFuture().get();

        var denied = events.stream()
                           .filter(e -> e instanceof BudgetTelemetryEvent.Acquisition a && !a.granted())
                           .map(e -> (BudgetTelemetryEvent.Acquisition) e)
                           .findFirst().orElseThrow();
        assertThat(denied.agentId()).isEqualTo("agent-1");
        assertThat(denied.granted()).isFalse();
    }

    @Test
    void summarise_emitsOutcomeEventWithTokensAndLatency() throws Exception {
        var delegate   = new StubSummariser();
        var p          = pool(10);
        var view       = p.createView("agent-1", BudgetPriority.NORMAL);
        var events     = new java.util.ArrayList<BudgetTelemetryEvent>();
        var summariser = new BudgetAwareSummariser<>(delegate, view, events::add, r -> 150L, "claude-sonnet");

        summariser.summarise(List.of("input"), null).toCompletableFuture().get();

        var outcome = events.stream()
                            .filter(e -> e instanceof BudgetTelemetryEvent.Outcome)
                            .map(e -> (BudgetTelemetryEvent.Outcome) e)
                            .findFirst().orElseThrow();
        assertThat(outcome.modelId()).isEqualTo("claude-sonnet");
        assertThat(outcome.tokensConsumed()).isEqualTo(150L);
        assertThat(outcome.latencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(outcome.correlationId()).isNotNull();
    }

    @Test
    void summarise_recordsTokensOnView() throws Exception {
        var delegate   = new StubSummariser();
        var p          = pool(10);
        var view       = p.createView("agent-1", BudgetPriority.NORMAL);
        var summariser = new BudgetAwareSummariser<>(delegate, view, e -> {}, r -> 200L, "test-model");

        summariser.summarise(List.of("input"), null).toCompletableFuture().get();
        assertThat(view.tokensUsed()).isEqualTo(200L);

        summariser.summarise(List.of("more"), null).toCompletableFuture().get();
        assertThat(view.tokensUsed()).isEqualTo(400L);
    }

    @Test
    void summarise_outcomeCorrelationIdMatchesAcquisition() throws Exception {
        var delegate   = new StubSummariser();
        var p          = pool(10);
        var view       = p.createView("agent-1", BudgetPriority.NORMAL);
        var events     = new java.util.ArrayList<BudgetTelemetryEvent>();
        var summariser = new BudgetAwareSummariser<>(delegate, view, events::add, r -> 50L, "test");

        summariser.summarise(List.of("input"), null).toCompletableFuture().get();

        var acquisition = (BudgetTelemetryEvent.Acquisition) events.get(0);
        var outcome     = (BudgetTelemetryEvent.Outcome) events.get(1);
        assertThat(outcome.correlationId()).isEqualTo(acquisition.correlationId());
    }


}
