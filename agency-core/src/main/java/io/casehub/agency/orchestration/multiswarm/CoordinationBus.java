package io.casehub.agency.orchestration.multiswarm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CoordinationBus<S> {

    private record Subscription<S>(Predicate<S> filter, Consumer<S> callback) {}

    private final CopyOnWriteArrayList<Subscription<S>> subscriptions = new CopyOnWriteArrayList<>();
    private final Object[] ringBuffer;
    private int writeIndex;
    private int count;
    private final int capacity;
    private final ExecutorService executor;

    public CoordinationBus(int replayCapacity) {
        if (replayCapacity < 0) throw new IllegalArgumentException("replayCapacity must be >= 0");
        this.capacity = replayCapacity;
        this.ringBuffer = new Object[Math.max(replayCapacity, 1)];
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void subscribe(Predicate<S> filter, Consumer<S> callback) {
        subscriptions.add(new Subscription<>(
                Objects.requireNonNull(filter),
                Objects.requireNonNull(callback)));
    }

    public void publish(S signal) {
        Objects.requireNonNull(signal);
        synchronized (ringBuffer) {
            if (capacity > 0) {
                ringBuffer[writeIndex % capacity] = signal;
                writeIndex++;
                if (count < capacity) count++;
            }
        }
        for (var sub : subscriptions) {
            if (sub.filter.test(signal)) {
                executor.submit(() -> sub.callback.accept(signal));
            }
        }
    }

    public void unsubscribe(Consumer<S> callback) {
        subscriptions.removeIf(s -> s.callback == callback);
    }

    @SuppressWarnings("unchecked")
    public List<S> replayBuffer() {
        synchronized (ringBuffer) {
            var result = new ArrayList<S>(count);
            int start = count < capacity ? 0 : writeIndex % capacity;
            for (int i = 0; i < count; i++) {
                result.add((S) ringBuffer[(start + i) % capacity]);
            }
            return result;
        }
    }
}
