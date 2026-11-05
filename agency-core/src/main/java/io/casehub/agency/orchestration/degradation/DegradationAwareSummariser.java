package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DegradationAwareSummariser<T, R> implements ContentSummariser<T, R> {

    private final ContentSummariser<T, R> delegate;
    private final ContentSummariser<T, R> heuristic;
    private final DegradationTrigger trigger;
    private final Consumer<SwarmDegradedEvent> eventSink;
    private final String loopName;
    private final AtomicBoolean previouslyDegraded = new AtomicBoolean(false);

    public DegradationAwareSummariser(ContentSummariser<T, R> delegate,
                                      ContentSummariser<T, R> heuristic,
                                      DegradationTrigger trigger,
                                      Consumer<SwarmDegradedEvent> eventSink,
                                      String loopName) {
        this.delegate = Objects.requireNonNull(delegate);
        this.heuristic = Objects.requireNonNull(heuristic);
        this.trigger = Objects.requireNonNull(trigger);
        this.eventSink = Objects.requireNonNull(eventSink);
        this.loopName = Objects.requireNonNull(loopName);
    }

    @Override
    public CompletionStage<R> summarise(List<T> items, @Nullable R previous) {
        if (trigger.shouldDegrade()) {
            if (previouslyDegraded.compareAndSet(false, true)) {
                eventSink.accept(new SwarmDegradedEvent(loopName, "degradation trigger fired", true));
            }
            return heuristic.summarise(items, previous);
        }
        if (previouslyDegraded.compareAndSet(true, false)) {
            eventSink.accept(new SwarmDegradedEvent(loopName, "degradation cleared", false));
        }
        return delegate.summarise(items, previous);
    }
}
