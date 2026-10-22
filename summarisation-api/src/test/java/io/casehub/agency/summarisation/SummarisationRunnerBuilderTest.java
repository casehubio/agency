package io.casehub.agency.summarisation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummarisationRunnerBuilderTest {

    private static final EventLevel INPUT = new EventLevel("input", 0);
    private static final EventLevel OUTPUT = new EventLevel("output", 1);

    @Test
    void build_requiresEitherWindowPolicyOrEmissionPolicy() {
        Summariser<String, Integer> sum = Summariser.ofSync(b -> List.of(b.size()));
        var bus = new EventStreamBus<Integer>();
        assertThatThrownBy(() ->
                SummarisationRunner.builder(sum, bus, OUTPUT).build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void build_rejectsBothWindowPolicyAndEmissionPolicy() {
        Summariser<String, Integer> sum = Summariser.ofSync(b -> List.of(b.size()));
        var bus = new EventStreamBus<Integer>();
        assertThatThrownBy(() ->
                SummarisationRunner.builder(sum, bus, OUTPUT)
                        .windowPolicy(WindowPolicy.ofCount(1))
                        .emissionPolicy((b, s, t) -> true)
                        .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void build_withWindowPolicy_createsRunner() {
        Summariser<String, Integer> sum = Summariser.ofSync(b -> List.of(b.size()));
        var bus = new EventStreamBus<Integer>();
        var runner = SummarisationRunner.builder(sum, bus, OUTPUT)
                .windowPolicy(WindowPolicy.ofCount(1))
                .build();
        assertThat(runner).isNotNull();
    }

    @Test
    void build_withEmissionPolicy_createsRunner() {
        Summariser<String, Integer> sum = Summariser.ofSync(b -> List.of(b.size()));
        var bus = new EventStreamBus<Integer>();
        var runner = SummarisationRunner.builder(sum, bus, OUTPUT)
                .emissionPolicy((b, s, t) -> b.size() >= 1)
                .build();
        assertThat(runner).isNotNull();
    }

    @Test
    void emissionPolicy_controlsEmission() {
        Summariser<String, Integer> sum = Summariser.ofSync(b -> List.of(b.size()));
        var bus = new EventStreamBus<Integer>();
        List<Integer> received = new ArrayList<>();
        bus.subscribe(i -> true, e -> received.add(e.payload()));

        EmissionPolicy<String, Object> needsThree = (b, s, t) -> b.size() >= 3;
        var runner = SummarisationRunner.builder(sum, bus, OUTPUT)
                .emissionPolicy(needsThree)
                .build();

        runner.collect(new LevelEvent<>("a", 1, INPUT, null));
        runner.collect(new LevelEvent<>("b", 2, INPUT, null));
        runner.tick(10);
        assertThat(received).isEmpty();

        runner.collect(new LevelEvent<>("c", 3, INPUT, null));
        runner.tick(10);
        assertThat(received).containsExactly(3);
    }

    @Test
    void stateStore_writeThrough_loadOnCacheMiss() {
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

        var runner = SummarisationRunner.builder(cs.asSummariser(), bus, OUTPUT)
                .emissionPolicy((b, s, t) -> !b.isEmpty())
                .stateStore(store)
                .build();

        runner.collect(new LevelEvent<>("a", 1, INPUT, "t1"));
        runner.tick(10);
        assertThat(received).containsExactly("+1");
        assertThat(stored.get()).isEqualTo("+1");

        received.clear();
        runner.collect(new LevelEvent<>("b", 2, INPUT, "t1"));
        runner.tick(20);
        assertThat(received).containsExactly("+1+1");
        assertThat(stored.get()).isEqualTo("+1+1");
    }

    @Test
    void stateStore_loadFallbackOnCacheMiss() {
        StateStore<String> store = new StateStore<>() {
            @Override
            public String load(String key) { return "pre-existing"; }
            @Override
            public void store(String key, String state) {}
        };

        ContentSummariser<String, String> cs = (items, prev) ->
                CompletableFuture.completedFuture(prev + "+" + items.size());

        var bus = new EventStreamBus<String>();
        List<String> received = new ArrayList<>();
        bus.subscribe(i -> true, e -> received.add(e.payload()));

        var runner = SummarisationRunner.builder(cs.asSummariser(), bus, OUTPUT)
                .emissionPolicy((b, s, t) -> !b.isEmpty())
                .stateStore(store)
                .build();

        runner.collect(new LevelEvent<>("a", 1, INPUT, "t1"));
        runner.tick(10);
        assertThat(received).containsExactly("pre-existing+1");
    }

    @Test
    void outputProcessor_calledBeforePublish() {
        Summariser<String, Integer> sum = Summariser.ofSync(b -> List.of(b.size()));
        OutputProcessor<Integer, Object> doubler = (outputs, state) ->
                outputs.stream().map(n -> n * 2).toList();

        var bus = new EventStreamBus<Integer>();
        List<Integer> received = new ArrayList<>();
        bus.subscribe(i -> true, e -> received.add(e.payload()));

        var runner = SummarisationRunner.builder(sum, bus, OUTPUT)
                .emissionPolicy((b, s, t) -> !b.isEmpty())
                .outputProcessor(doubler)
                .build();

        runner.collect(new LevelEvent<>("a", 1, INPUT, null));
        runner.tick(10);
        assertThat(received).containsExactly(2);
    }

    @Test
    void stateKeyResolver_customPartitionKey() {
        var keys = new ArrayList<String>();
        StateStore<String> store = new StateStore<>() {
            @Override
            public String load(String key) { keys.add("load:" + key); return null; }
            @Override
            public void store(String key, String state) { keys.add("store:" + key); }
        };

        ContentSummariser<String, String> cs = (items, prev) ->
                CompletableFuture.completedFuture("result");

        var bus = new EventStreamBus<String>();
        var runner = SummarisationRunner.builder(cs.asSummariser(), bus, OUTPUT)
                .emissionPolicy((b, s, t) -> !b.isEmpty())
                .stateStore(store)
                .stateKeyResolver(batch -> "custom:" + batch.get(0).tenancyId())
                .build();

        runner.collect(new LevelEvent<>("a", 1, INPUT, "tenant-1"));
        runner.tick(10);
        assertThat(keys).contains("load:custom:tenant-1", "store:custom:tenant-1");
    }

    @Test
    void windowPolicy_viaBuilder_matchesLegacyBehavior() {
        Summariser<String, Integer> sum = Summariser.ofSync(b -> List.of(b.size()));
        var bus = new EventStreamBus<Integer>();
        List<Integer> received = new ArrayList<>();
        bus.subscribe(i -> true, e -> received.add(e.payload()));

        var runner = SummarisationRunner.builder(sum, bus, OUTPUT)
                .windowPolicy(WindowPolicy.ofCount(2))
                .build();

        runner.collect(new LevelEvent<>("a", 1, INPUT, null));
        runner.tick(5);
        assertThat(received).isEmpty();

        runner.collect(new LevelEvent<>("b", 2, INPUT, null));
        runner.tick(5);
        assertThat(received).containsExactly(2);
    }

    @Test
    void emissionPolicy_receivesCurrentState() {
        ContentSummariser<String, String> cs = (items, prev) ->
                CompletableFuture.completedFuture("state-" + items.size());

        var bus = new EventStreamBus<String>();
        List<String> policyStates = new ArrayList<>();

        EmissionPolicy<String, String> tracking = (b, s, t) -> {
            policyStates.add(s != null ? s : "null");
            return !b.isEmpty();
        };

        var runner = SummarisationRunner.builder(cs.asSummariser(), bus, OUTPUT)
                .emissionPolicy(tracking)
                .build();

        runner.collect(new LevelEvent<>("a", 1, INPUT, "t1"));
        runner.tick(10);
        assertThat(policyStates).containsExactly("null");

        runner.collect(new LevelEvent<>("b", 2, INPUT, "t1"));
        runner.tick(20);
        assertThat(policyStates).containsExactly("null", "state-1");
    }
}
