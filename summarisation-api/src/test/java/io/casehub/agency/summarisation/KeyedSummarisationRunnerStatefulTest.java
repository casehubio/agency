package io.casehub.agency.summarisation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class KeyedSummarisationRunnerStatefulTest {

    private static final EventLevel INPUT = new EventLevel("input", 0);
    private static final EventLevel OUTPUT = new EventLevel("output", 1);

    @Test
    void tick_statefulSummariser_previousStatePassedOnSecondBatch() {
        var stateReceived = new AtomicReference<String>();

        ContentSummariser<String, String> content = (items, previous) -> {
            stateReceived.set(previous);
            var combined = (previous != null ? previous + "+" : "") + String.join(",", items);
            return CompletableFuture.completedFuture(combined);
        };

        var outputBus = new EventStreamBus<String>();
        var received = new ArrayList<String>();
        outputBus.subscribe(e -> true, e -> received.add(e.payload()));

        var runner = new KeyedSummarisationRunner<>(
                e -> e.payload().substring(0, 1),
                group -> group.size() >= 2,
                0,
                content.asSummariser(),
                outputBus, OUTPUT);

        runner.collect(new LevelEvent<>("a1", 1, INPUT, null));
        runner.collect(new LevelEvent<>("a2", 2, INPUT, null));
        runner.tick(10);

        assertThat(received).hasSize(1);
        assertThat(stateReceived.get()).isNull();

        runner.collect(new LevelEvent<>("a3", 3, INPUT, null));
        runner.collect(new LevelEvent<>("a4", 4, INPUT, null));
        runner.tick(20);

        assertThat(received).hasSize(2);
        assertThat(stateReceived.get()).isEqualTo("a1,a2");
    }

    @Test
    void tick_plainSummariser_backwardCompatible() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var received = new ArrayList<Integer>();
        outputBus.subscribe(e -> true, e -> received.add(e.payload()));

        var runner = new KeyedSummarisationRunner<>(
                e -> e.payload().substring(0, 1),
                group -> group.size() >= 2, 0,
                summariser, outputBus, OUTPUT);

        runner.collect(new LevelEvent<>("a1", 1, INPUT, null));
        runner.collect(new LevelEvent<>("a2", 2, INPUT, null));
        runner.tick(10);

        assertThat(received).containsExactly(2);
    }

    @Test
    void evictState_removesPerKeyState() {
        var stateReceived = new AtomicReference<String>();

        ContentSummariser<String, String> content = (items, previous) -> {
            stateReceived.set(previous);
            return CompletableFuture.completedFuture(String.join(",", items));
        };

        var outputBus = new EventStreamBus<String>();
        var runner = new KeyedSummarisationRunner<>(
                e -> e.payload().substring(0, 1),
                group -> group.size() >= 1, 0,
                content.asSummariser(),
                outputBus, OUTPUT);

        runner.collect(new LevelEvent<>("a1", 1, INPUT, null));
        runner.tick(10);

        assertThat(stateReceived.get()).isNull();

        runner.evictState("a");

        runner.collect(new LevelEvent<>("a2", 2, INPUT, null));
        runner.tick(20);

        assertThat(stateReceived.get()).isNull();
    }

    @Test
    void tick_multipleKeys_stateIsolated() {
        var states = new java.util.concurrent.ConcurrentHashMap<String, String>();

        ContentSummariser<String, String> content = (items, previous) -> {
            var key = items.get(0).substring(0, 1);
            states.put(key, previous != null ? previous : "null");
            return CompletableFuture.completedFuture(String.join(",", items));
        };

        var outputBus = new EventStreamBus<String>();
        var runner = new KeyedSummarisationRunner<>(
                e -> e.payload().substring(0, 1),
                group -> group.size() >= 1, 0,
                content.asSummariser(),
                outputBus, OUTPUT);

        runner.collect(new LevelEvent<>("a1", 1, INPUT, null));
        runner.collect(new LevelEvent<>("b1", 2, INPUT, null));
        runner.tick(10);

        runner.collect(new LevelEvent<>("a2", 3, INPUT, null));
        runner.tick(20);

        assertThat(states.get("a")).isEqualTo("a1");
        assertThat(states.get("b")).isEqualTo("null");
    }

    @Test
    void flush_statefulSummariser_statePassedAndUpdated() {
        var stateReceived = new AtomicReference<String>();

        ContentSummariser<String, String> content = (items, previous) -> {
            stateReceived.set(previous);
            return CompletableFuture.completedFuture(String.join(",", items));
        };

        var outputBus = new EventStreamBus<String>();
        var runner = new KeyedSummarisationRunner<>(
                e -> e.payload().substring(0, 1),
                group -> false, 0,
                content.asSummariser(),
                outputBus, OUTPUT);

        runner.collect(new LevelEvent<>("a1", 1, INPUT, null));
        runner.flush();

        assertThat(stateReceived.get()).isNull();

        runner.collect(new LevelEvent<>("a2", 2, INPUT, null));
        runner.flush();

        assertThat(stateReceived.get()).isEqualTo("a1");
    }
}
