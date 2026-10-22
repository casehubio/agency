package io.casehub.agency.summarisation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class KeyedSummarisationRunnerBuilderTest {

    private static final EventLevel INPUT = new EventLevel("input", 0);
    private static final EventLevel OUTPUT = new EventLevel("output", 1);

    @Test
    void build_createsRunner() {
        Summariser<String, Integer> sum = Summariser.ofSync(b -> List.of(b.size()));
        var bus = new EventStreamBus<Integer>();
        var runner = KeyedSummarisationRunner.<String, String, Integer>builder(
                        e -> e.payload(), g -> g.size() >= 1, 30_000L,
                        sum, bus, OUTPUT)
                .build();
        assertThat(runner).isNotNull();
    }

    @Test
    void stateStore_writeThroughWithKeyedState() {
        var stored = new AtomicReference<String>();
        StateStore<String> store = new StateStore<>() {
            @Override
            public String load(String key) { return stored.get(); }
            @Override
            public void store(String key, String state) { stored.set(state); }
        };

        ContentSummariser<String, String> cs = (items, prev) ->
                CompletableFuture.completedFuture(
                        (prev != null ? prev : "") + "+" + items.size());

        var bus = new EventStreamBus<String>();
        List<String> received = new ArrayList<>();
        bus.subscribe(i -> true, e -> received.add(e.payload()));

        var runner = KeyedSummarisationRunner.<String, String, String>builder(
                        e -> e.payload(), g -> g.size() >= 1, 30_000L,
                        cs.asSummariser(), bus, OUTPUT)
                .stateStore(store)
                .build();

        runner.collect(new LevelEvent<>("key1", 1, INPUT, "t1"));
        runner.tick(10);
        assertThat(received).containsExactly("+1");
        assertThat(stored.get()).isEqualTo("+1");
    }

    @Test
    void stateStore_loadFallbackOnCacheMiss() {
        StateStore<String> store = new StateStore<>() {
            @Override
            public String load(String key) { return "pre-loaded"; }
            @Override
            public void store(String key, String state) {}
        };

        ContentSummariser<String, String> cs = (items, prev) ->
                CompletableFuture.completedFuture(prev + "+" + items.size());

        var bus = new EventStreamBus<String>();
        List<String> received = new ArrayList<>();
        bus.subscribe(i -> true, e -> received.add(e.payload()));

        var runner = KeyedSummarisationRunner.<String, String, String>builder(
                        e -> e.payload(), g -> g.size() >= 1, 30_000L,
                        cs.asSummariser(), bus, OUTPUT)
                .stateStore(store)
                .build();

        runner.collect(new LevelEvent<>("key1", 1, INPUT, "t1"));
        runner.tick(10);
        assertThat(received).containsExactly("pre-loaded+1");
    }

    @Test
    void outputProcessor_appliedPerGroup() {
        Summariser<String, Integer> sum = Summariser.ofSync(b -> List.of(b.size()));
        OutputProcessor<Integer, Object> tripler = (outputs, state) ->
                outputs.stream().map(n -> n * 3).toList();

        var bus = new EventStreamBus<Integer>();
        List<Integer> received = new ArrayList<>();
        bus.subscribe(i -> true, e -> received.add(e.payload()));

        var runner = KeyedSummarisationRunner.<String, String, Integer>builder(
                        e -> e.payload(), g -> g.size() >= 1, 30_000L,
                        sum, bus, OUTPUT)
                .outputProcessor(tripler)
                .build();

        runner.collect(new LevelEvent<>("a", 1, INPUT, null));
        runner.tick(10);
        assertThat(received).containsExactly(3);
    }

    @Test
    void evictState_clearsKeyedStateAndStoreNotAffected() {
        var stored = new AtomicReference<String>();
        StateStore<String> store = new StateStore<>() {
            @Override
            public String load(String key) { return stored.get(); }
            @Override
            public void store(String key, String state) { stored.set(state); }
        };

        ContentSummariser<String, String> cs = (items, prev) ->
                CompletableFuture.completedFuture(
                        (prev != null ? prev : "") + "+" + items.size());

        var bus = new EventStreamBus<String>();
        List<String> received = new ArrayList<>();
        bus.subscribe(i -> true, e -> received.add(e.payload()));

        var runner = KeyedSummarisationRunner.<String, String, String>builder(
                        e -> e.payload(), g -> g.size() >= 1, 30_000L,
                        cs.asSummariser(), bus, OUTPUT)
                .stateStore(store)
                .build();

        runner.collect(new LevelEvent<>("key1", 1, INPUT, "t1"));
        runner.tick(10);
        assertThat(received).containsExactly("+1");

        runner.evictState("key1");

        received.clear();
        runner.collect(new LevelEvent<>("key1", 2, INPUT, "t1"));
        runner.tick(20);
        assertThat(received).containsExactly("+1+1");
    }
}
