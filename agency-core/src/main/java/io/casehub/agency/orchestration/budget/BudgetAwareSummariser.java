package io.casehub.agency.orchestration.budget;

import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

public class BudgetAwareSummariser<T, R> implements ContentSummariser<T, R> {

    private final ContentSummariser<T, R>        delegate;
    private final AgentBudgetView                view;
    private final Consumer<BudgetTelemetryEvent> eventSink;
    private final ToLongFunction<R>              tokenEstimator;
    private final String                         modelId;

    public BudgetAwareSummariser(ContentSummariser<T, R> delegate, AgentBudgetView view) {
        this(delegate, view, e -> {}, r -> 0L, "unknown");
    }

    public BudgetAwareSummariser(ContentSummariser<T, R> delegate, AgentBudgetView view,
                                 Consumer<BudgetTelemetryEvent> eventSink,
                                 ToLongFunction<R> tokenEstimator,
                                 String modelId) {
        this.delegate       = delegate;
        this.view           = view;
        this.eventSink      = eventSink;
        this.tokenEstimator = tokenEstimator;
        this.modelId        = modelId;
    }

    @Override
    public CompletionStage<R> summarise(List<T> items, @Nullable R previous) {
        long        startNanos = System.nanoTime();
        BudgetGrant grant      = view.tryAcquire();

        if (grant instanceof BudgetGrant.Denied denied) {
            eventSink.accept(new BudgetTelemetryEvent.Acquisition(
                    UUID.randomUUID().toString(),
                    view.agentId(), view.topologyId(), view.priority(),
                    denied.pressure(), false, Instant.now()));
            return CompletableFuture.completedFuture(previous);
        }

        var granted = (BudgetGrant.Granted) grant;
        eventSink.accept(new BudgetTelemetryEvent.Acquisition(
                granted.correlationId(),
                view.agentId(), view.topologyId(), view.priority(),
                granted.pressure(), true, Instant.now()));

        return delegate.summarise(items, previous)
                       .whenComplete((result, error) -> {
                           long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
                           long tokens = (error == null && result != null)
                                         ? tokenEstimator.applyAsLong(result) : 0L;
                           view.recordTokens(granted.correlationId(), tokens);
                           eventSink.accept(new BudgetTelemetryEvent.Outcome(
                                   granted.correlationId(), modelId, tokens, latencyMs, Instant.now()));
                       });
    }
}
