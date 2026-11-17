package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DegradationAwareSummariser<T, R> implements ContentSummariser<T, R> {

    private final ContentSummariser<T, R> delegate;
    private final ContentSummariser<T, R> heuristic;
    private final DegradationTrigger      trigger;
    private final Consumer<DegradedEvent> eventSink;
    private final String                  loopName;
    private final CollapsePolicy          collapsePolicy;
    private final AtomicBoolean previouslyDegraded = new AtomicBoolean(false);
    private final AtomicBoolean collapsed = new AtomicBoolean(false);
    private final AtomicInteger consecutiveDegradedCalls = new AtomicInteger();
    private volatile long degradedSinceMs;

    public DegradationAwareSummariser(ContentSummariser<T, R> delegate,
                                      ContentSummariser<T, R> heuristic,
                                      DegradationTrigger trigger,
                                      Consumer<DegradedEvent> eventSink,
                                      String loopName) {
        this(delegate, heuristic, trigger, eventSink, loopName, CollapsePolicy.never());
    }

    public DegradationAwareSummariser(ContentSummariser<T, R> delegate,
                                      ContentSummariser<T, R> heuristic,
                                      DegradationTrigger trigger,
                                      Consumer<DegradedEvent> eventSink,
                                      String loopName,
                                      CollapsePolicy collapsePolicy) {
        this.delegate = Objects.requireNonNull(delegate);
        this.heuristic = Objects.requireNonNull(heuristic);
        this.trigger = Objects.requireNonNull(trigger);
        this.eventSink = Objects.requireNonNull(eventSink);
        this.loopName = Objects.requireNonNull(loopName);
        this.collapsePolicy = Objects.requireNonNull(collapsePolicy);
    }

    @Override
    public CompletionStage<R> summarise(List<T> items, @Nullable R previous) {
        if (trigger.shouldDegrade()) {
            if (previouslyDegraded.compareAndSet(false, true)) {
                degradedSinceMs = System.currentTimeMillis();
                eventSink.accept(new DegradedEvent(loopName, "degradation trigger fired", true));
            }
            int calls = consecutiveDegradedCalls.incrementAndGet();
            long elapsed = System.currentTimeMillis() - degradedSinceMs;
            if (!collapsed.get() && collapsePolicy.isCollapsed(calls, elapsed)
                && collapsed.compareAndSet(false, true)) {
                eventSink.accept(new DegradedEvent(loopName, "collapse threshold reached", true, true));
            }
            return heuristic.summarise(items, previous);
        }
        if (previouslyDegraded.compareAndSet(true, false)) {
            consecutiveDegradedCalls.set(0);
            degradedSinceMs = 0;
            collapsed.set(false);
            eventSink.accept(new DegradedEvent(loopName, "degradation cleared", false));
        }
        return delegate.summarise(items, previous);
    }
}
