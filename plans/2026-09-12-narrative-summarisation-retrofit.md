# Narrative Summarisation Retrofit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #259 — refactor: retrofit identity narratives onto enhanced summarisation pipeline
**Issue group:** #259

**Goal:** Introduce three new summarisation pipeline SPIs (StateStore, EmissionPolicy, OutputProcessor), then rewrite NarrativeSynthesiser as a ContentSummariser wired through SummarisationRunner.

**Architecture:** Part A adds three opt-in SPI extensions to SummarisationRunner via a new builder API. Part B decomposes NarrativeSynthesiser into four pipeline components (emission policy, content summariser, output processor, state store adapter) wired through a NarrativePipeline factory. The pull-based reflection flow becomes push-based via a ReflectionEventAdapter.

**Tech Stack:** Java 17, JUnit 5, Mockito, AssertJ. No Quarkus runtime in tests.

## Global Constraints

- All existing tests must pass unchanged (backward compat)
- SummarisationRunner existing constructors must remain and work identically
- NarrativeOrchestrator, NarrativeFragment hierarchy, GroupNarrativeOrchestrator — unchanged
- NarrativeSynthesisGate record — preserved (consumed by NarrativeEmissionPolicy)
- All new SPIs live in `summarisation-api` module (zero external deps)
- All narrative components live in `blocks` module
- Use `ide_insert_member` / `ide_replace_member` for code edits, not bash

---

## Batch 1: Pipeline SPI Foundation

### Task 1: SPI interfaces + WindowPolicyEmission + EventAccumulator.peekBuffer()

**Files:**
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/EmissionPolicy.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/StateStore.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/OutputProcessor.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/WindowPolicyEmission.java`
- Modify: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/EventAccumulator.java`
- Test: `summarisation-api/src/test/java/io/casehub/blocks/summarisation/EmissionPolicyTest.java`
- Test: `summarisation-api/src/test/java/io/casehub/blocks/summarisation/WindowPolicyEmissionTest.java`

**Interfaces:**
- Produces: `EmissionPolicy<IN, S>` with `shouldEmit(List<LevelEvent<IN>>, S, long)`, `anyOf()`, `allOf()`
- Produces: `StateStore<S>` with `load(String)`, `store(String, S)`
- Produces: `OutputProcessor<OUT, S>` with `process(List<OUT>, S)`
- Produces: `WindowPolicyEmission<IN, S>` (package-private adapter)
- Produces: `EventAccumulator.peekBuffer()` → `List<LevelEvent<E>>`

- [ ] **Step 1: Write EmissionPolicy test — anyOf composition**

```java
// File: summarisation-api/src/test/java/io/casehub/blocks/summarisation/EmissionPolicyTest.java
package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EmissionPolicyTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);

    @Test
    void anyOf_emitsWhenAnyPolicySaysYes() {
        EmissionPolicy<String, Object> never = (b, s, t) -> false;
        EmissionPolicy<String, Object> always = (b, s, t) -> true;
        var composite = EmissionPolicy.anyOf(List.of(never, always));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(composite.shouldEmit(buffered, null, 10)).isTrue();
    }

    @Test
    void anyOf_doesNotEmitWhenAllSayNo() {
        EmissionPolicy<String, Object> never1 = (b, s, t) -> false;
        EmissionPolicy<String, Object> never2 = (b, s, t) -> false;
        var composite = EmissionPolicy.anyOf(List.of(never1, never2));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(composite.shouldEmit(buffered, null, 10)).isFalse();
    }

    @Test
    void allOf_emitsOnlyWhenAllSayYes() {
        EmissionPolicy<String, Object> always = (b, s, t) -> true;
        EmissionPolicy<String, Object> never = (b, s, t) -> false;
        var composite = EmissionPolicy.allOf(List.of(always, never));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(composite.shouldEmit(buffered, null, 10)).isFalse();
    }

    @Test
    void allOf_emitsWhenAllAgree() {
        EmissionPolicy<String, Object> a1 = (b, s, t) -> true;
        EmissionPolicy<String, Object> a2 = (b, s, t) -> true;
        var composite = EmissionPolicy.allOf(List.of(a1, a2));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(composite.shouldEmit(buffered, null, 10)).isTrue();
    }

    @Test
    void shouldEmit_receivesStateAndTime() {
        EmissionPolicy<String, String> policy =
                (b, state, now) -> "go".equals(state) && now > 100;
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, "go", 200)).isTrue();
        assertThat(policy.shouldEmit(buffered, "stop", 200)).isFalse();
        assertThat(policy.shouldEmit(buffered, "go", 50)).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl summarisation-api -Dtest=EmissionPolicyTest`
Expected: compilation failure — EmissionPolicy does not exist

- [ ] **Step 3: Create EmissionPolicy interface**

```java
// File: summarisation-api/src/main/java/io/casehub/blocks/summarisation/EmissionPolicy.java
package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;
import java.util.List;

@FunctionalInterface
public interface EmissionPolicy<IN, S> {
    boolean shouldEmit(List<LevelEvent<IN>> buffered,
                       @Nullable S currentState,
                       long now);

    static <IN, S> EmissionPolicy<IN, S> anyOf(
            List<EmissionPolicy<IN, S>> policies) {
        return (buffered, state, now) ->
                policies.stream().anyMatch(p -> p.shouldEmit(buffered, state, now));
    }

    static <IN, S> EmissionPolicy<IN, S> allOf(
            List<EmissionPolicy<IN, S>> policies) {
        return (buffered, state, now) ->
                policies.stream().allMatch(p -> p.shouldEmit(buffered, state, now));
    }
}
```

- [ ] **Step 4: Create StateStore interface**

```java
// File: summarisation-api/src/main/java/io/casehub/blocks/summarisation/StateStore.java
package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;

public interface StateStore<S> {
    @Nullable S load(String partitionKey);
    void store(String partitionKey, S state);
}
```

- [ ] **Step 5: Create OutputProcessor interface**

```java
// File: summarisation-api/src/main/java/io/casehub/blocks/summarisation/OutputProcessor.java
package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;
import java.util.List;

@FunctionalInterface
public interface OutputProcessor<OUT, S> {
    List<OUT> process(List<OUT> outputs, @Nullable S currentState);
}
```

- [ ] **Step 6: Run EmissionPolicyTest — verify PASS**

Run: `mvn --batch-mode test -pl summarisation-api -Dtest=EmissionPolicyTest`
Expected: PASS (all 5 tests)

- [ ] **Step 7: Write WindowPolicyEmission test**

```java
// File: summarisation-api/src/test/java/io/casehub/blocks/summarisation/WindowPolicyEmissionTest.java
package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class WindowPolicyEmissionTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);

    @Test
    void emptyBuffer_returnsFalse() {
        var policy = new WindowPolicyEmission<>(WindowPolicy.ofCount(2));
        assertThat(policy.shouldEmit(List.of(), null, 10)).isFalse();
    }

    @Test
    void countThreshold_met() {
        var policy = new WindowPolicyEmission<>(WindowPolicy.ofCount(2));
        var buffered = List.of(
                new LevelEvent<>("a", 1, LEVEL, null),
                new LevelEvent<>("b", 2, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, null, 10)).isTrue();
    }

    @Test
    void countThreshold_notMet() {
        var policy = new WindowPolicyEmission<>(WindowPolicy.ofCount(3));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, null, 10)).isFalse();
    }

    @Test
    void ageThreshold_met() {
        var policy = new WindowPolicyEmission<>(WindowPolicy.ofAge(100));
        var buffered = List.of(new LevelEvent<>("a", 5, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, null, 110)).isTrue();
    }

    @Test
    void ageThreshold_notMet() {
        var policy = new WindowPolicyEmission<>(WindowPolicy.ofAge(100));
        var buffered = List.of(new LevelEvent<>("a", 50, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, null, 60)).isFalse();
    }

    @Test
    void ignoresState() {
        var policy = new WindowPolicyEmission<>(WindowPolicy.ofCount(1));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, "any-state", 10)).isTrue();
    }
}
```

- [ ] **Step 8: Create WindowPolicyEmission adapter**

```java
// File: summarisation-api/src/main/java/io/casehub/blocks/summarisation/WindowPolicyEmission.java
package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;
import java.util.List;

class WindowPolicyEmission<IN, S> implements EmissionPolicy<IN, S> {
    private final WindowPolicy policy;

    WindowPolicyEmission(WindowPolicy policy) {
        this.policy = policy;
    }

    @Override
    public boolean shouldEmit(List<LevelEvent<IN>> buffered,
                              @Nullable S currentState, long now) {
        if (buffered.isEmpty()) return false;
        if (policy.maxCount() > 0 && buffered.size() >= policy.maxCount())
            return true;
        if (policy.maxAge() > 0) {
            long oldest = buffered.get(0).timestamp();
            return (now - oldest) >= policy.maxAge();
        }
        return false;
    }
}
```

- [ ] **Step 9: Add peekBuffer() to EventAccumulator**

Add to `EventAccumulator.java` after the `drain()` method:

```java
public synchronized List<LevelEvent<E>> peekBuffer() {
    return List.copyOf(buffer);
}
```

- [ ] **Step 10: Run all tests — verify PASS**

Run: `mvn --batch-mode test -pl summarisation-api`
Expected: all existing + new tests PASS

- [ ] **Step 11: Commit**

```bash
git add summarisation-api/src/main/java/io/casehub/blocks/summarisation/EmissionPolicy.java summarisation-api/src/main/java/io/casehub/blocks/summarisation/StateStore.java summarisation-api/src/main/java/io/casehub/blocks/summarisation/OutputProcessor.java summarisation-api/src/main/java/io/casehub/blocks/summarisation/WindowPolicyEmission.java summarisation-api/src/main/java/io/casehub/blocks/summarisation/EventAccumulator.java summarisation-api/src/test/java/io/casehub/blocks/summarisation/EmissionPolicyTest.java summarisation-api/src/test/java/io/casehub/blocks/summarisation/WindowPolicyEmissionTest.java
git commit -m "feat(#259): add EmissionPolicy, StateStore, OutputProcessor SPIs + WindowPolicyEmission adapter

Refs #259"
```

### Task 2: SummarisationRunner builder + EmissionPolicy/StateStore/OutputProcessor integration

**Files:**
- Modify: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/SummarisationRunner.java`
- Test: `summarisation-api/src/test/java/io/casehub/blocks/summarisation/SummarisationRunnerBuilderTest.java`

**Interfaces:**
- Consumes: `EmissionPolicy<IN, S>`, `StateStore<S>`, `OutputProcessor<OUT, S>`, `WindowPolicyEmission`
- Produces: `SummarisationRunner.builder(summariser, outputBus, outputLevel)` → `Builder<IN, OUT>`
- Produces: `Builder.emissionPolicy()`, `.stateStore()`, `.stateKeyResolver()`, `.outputProcessor()`, `.compactor()`, `.onFailure()`, `.windowPolicy()`, `.build()`

- [ ] **Step 1: Write builder validation test**

```java
// File: summarisation-api/src/test/java/io/casehub/blocks/summarisation/SummarisationRunnerBuilderTest.java
package io.casehub.blocks.summarisation;

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
            @Override public String load(String key) { return stored.get(); }
            @Override public void store(String key, String state) { stored.set(state); }
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
            @Override public String load(String key) { return "pre-existing"; }
            @Override public void store(String key, String state) {}
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
            @Override public String load(String key) { keys.add("load:" + key); return null; }
            @Override public void store(String key, String state) { keys.add("store:" + key); }
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl summarisation-api -Dtest=SummarisationRunnerBuilderTest`
Expected: compilation failure — builder does not exist

- [ ] **Step 3: Implement SummarisationRunner builder + integration**

Modify `SummarisationRunner.java`. Add new fields, a package-private constructor for the builder, the `Builder` inner class, and modify `tick()` and `invokeSummariser()`. Keep all existing constructors unchanged.

The new fields:
```java
private final EmissionPolicy<IN, ?> emissionPolicy;
private final StateStore<?> stateStore;
private final Function<List<LevelEvent<IN>>, String> stateKeyResolver;
private final OutputProcessor<OUT, ?> outputProcessor;
```

The builder-based constructor (package-private):
```java
@SuppressWarnings("unchecked")
SummarisationRunner(Builder<IN, OUT> b) {
    this.accumulator = new EventAccumulator<>(WindowPolicy.ofCount(Integer.MAX_VALUE));
    this.compactor = b.compactor;
    this.summariser = b.summariser;
    this.outputBus = b.outputBus;
    this.outputLevel = b.outputLevel;
    this.onFailure = b.onFailure;
    this.emissionPolicy = b.emissionPolicy != null
            ? b.emissionPolicy
            : new WindowPolicyEmission<>(b.windowPolicy);
    this.stateStore = b.stateStore;
    this.stateKeyResolver = b.stateKeyResolver;
    this.outputProcessor = b.outputProcessor;
}
```

Modify `tick()` to handle emission policy path — when `emissionPolicy` is non-null, use `peekBuffer()` + policy check instead of `drainIfReady()`.

Modify `invokeSummariser()` to integrate state store (write-through cache, load fallback) and output processor (call after summarise, before publish). Use `stateKeyResolver` when set, fall back to tenancyId.

See spec §SPI 2 (lines 148-159) for tick() pseudocode, §SPI 1 (lines 61-77) for state store integration, and §SPI 3 (lines 193-200) for output processor integration.

Existing constructors must set `emissionPolicy = null`, `stateStore = null`, `stateKeyResolver = null`, `outputProcessor = null` — the legacy path (emissionPolicy == null) falls through to existing `drainIfReady()` behavior.

- [ ] **Step 4: Run all summarisation-api tests**

Run: `mvn --batch-mode test -pl summarisation-api`
Expected: all existing tests PASS + new builder tests PASS

- [ ] **Step 5: Commit**

```bash
git add summarisation-api/src/main/java/io/casehub/blocks/summarisation/SummarisationRunner.java summarisation-api/src/test/java/io/casehub/blocks/summarisation/SummarisationRunnerBuilderTest.java
git commit -m "feat(#259): add SummarisationRunner builder with EmissionPolicy, StateStore, OutputProcessor

Refs #259"
```

### Task 3: KeyedSummarisationRunner builder

**Files:**
- Modify: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/KeyedSummarisationRunner.java`
- Test: `summarisation-api/src/test/java/io/casehub/blocks/summarisation/KeyedSummarisationRunnerBuilderTest.java`

**Interfaces:**
- Consumes: `EmissionPolicy<IN, S>`, `StateStore<S>`, `OutputProcessor<OUT, S>`, `WindowPolicyEmission`
- Produces: `KeyedSummarisationRunner.builder(keyExtractor, completionTest, staleTimeout, summariser, outputBus, outputLevel)` → `Builder<K, IN, OUT>`

- [ ] **Step 1: Write builder test**

```java
// File: summarisation-api/src/test/java/io/casehub/blocks/summarisation/KeyedSummarisationRunnerBuilderTest.java
package io.casehub.blocks.summarisation;

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
            @Override public String load(String key) { return stored.get(); }
            @Override public void store(String key, String state) { stored.set(state); }
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl summarisation-api -Dtest=KeyedSummarisationRunnerBuilderTest`
Expected: compilation failure

- [ ] **Step 3: Implement KeyedSummarisationRunner builder**

Mirror the SummarisationRunner builder pattern. Add `Builder<K, IN, OUT>` inner class with `stateStore()`, `outputProcessor()`, `compactor()`, `onFailure()`. The keyed runner already has `keyState` and `evictState()` — add state store write-through and load fallback in `invokeSummariser()`, and output processor call before publish. The keyed runner does NOT use EmissionPolicy — it keeps its existing `completionTest` + `staleTimeout` on the accumulator. These are required builder parameters.

- [ ] **Step 4: Run all summarisation-api tests**

Run: `mvn --batch-mode test -pl summarisation-api`
Expected: all tests PASS

- [ ] **Step 5: Commit**

```bash
git add summarisation-api/src/main/java/io/casehub/blocks/summarisation/KeyedSummarisationRunner.java summarisation-api/src/test/java/io/casehub/blocks/summarisation/KeyedSummarisationRunnerBuilderTest.java
git commit -m "feat(#259): add KeyedSummarisationRunner builder with StateStore + OutputProcessor

Refs #259"
```

## Batch 2: Narrative Components

### Task 4: ReflectionEventAdapter + NarrativeEmissionPolicy + NarrativeOutputProcessor + CbrStateStore

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/ReflectionEventAdapter.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeEmissionPolicy.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeOutputProcessor.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/CbrStateStore.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/ReflectionEventAdapterTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeEmissionPolicyTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeOutputProcessorTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/CbrStateStoreTest.java`

**Interfaces:**
- Consumes: `EmissionPolicy<IN, S>`, `OutputProcessor<OUT, S>`, `StateStore<S>` from Task 1; `ReflectionQueryStore`, `ReflectionEntry` from blocks; `EventStreamBus`, `LevelEvent`, `EventLevel` from summarisation-api; `NarrativeSynthesisGate`, `NarrativeState`, `NarrativeConfig`, `CbrNarrativeStore` from blocks
- Produces: `ReflectionEventAdapter.publishNewReflections(agentId, tenantId)`; `NarrativeEmissionPolicy` implementing `EmissionPolicy<ReflectionEntry, NarrativeState>`; `NarrativeOutputProcessor` implementing `OutputProcessor<NarrativeState, NarrativeState>`; `CbrStateStore` implementing `StateStore<NarrativeState>`

- [ ] **Step 1: Write ReflectionEventAdapter test**

```java
// File: blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/ReflectionEventAdapterTest.java
package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReflectionEventAdapterTest {

    private static final EventLevel LEVEL = new EventLevel("reflections", 0);

    @Test
    void publishNewReflections_publishesToBus() {
        var store = mock(ReflectionQueryStore.class);
        var r1 = new ReflectionEntry("agent-1", "t1", "insight-1",
                Instant.ofEpochMilli(100), List.of());
        when(store.findSince(eq("agent-1"), eq("t1"), any()))
                .thenReturn(List.of(r1));

        var bus = new EventStreamBus<ReflectionEntry>();
        List<ReflectionEntry> received = new ArrayList<>();
        bus.subscribe(e -> true, e -> received.add(e.payload()));

        var adapter = new ReflectionEventAdapter(store, bus, LEVEL);
        adapter.publishNewReflections("agent-1", "t1");

        assertThat(received).containsExactly(r1);
    }

    @Test
    void publishNewReflections_advancesWatermark() {
        var store = mock(ReflectionQueryStore.class);
        var r1 = new ReflectionEntry("agent-1", "t1", "insight-1",
                Instant.ofEpochMilli(100), List.of());
        when(store.findSince("agent-1", "t1", Instant.EPOCH))
                .thenReturn(List.of(r1));
        when(store.findSince("agent-1", "t1", Instant.ofEpochMilli(100)))
                .thenReturn(List.of());

        var bus = new EventStreamBus<ReflectionEntry>();
        List<ReflectionEntry> received = new ArrayList<>();
        bus.subscribe(e -> true, e -> received.add(e.payload()));

        var adapter = new ReflectionEventAdapter(store, bus, LEVEL);
        adapter.publishNewReflections("agent-1", "t1");
        assertThat(received).hasSize(1);

        adapter.publishNewReflections("agent-1", "t1");
        assertThat(received).hasSize(1);
    }

    @Test
    void publishNewReflections_emptyResult_noPublish() {
        var store = mock(ReflectionQueryStore.class);
        when(store.findSince(any(), any(), any())).thenReturn(List.of());

        var bus = new EventStreamBus<ReflectionEntry>();
        List<ReflectionEntry> received = new ArrayList<>();
        bus.subscribe(e -> true, e -> received.add(e.payload()));

        var adapter = new ReflectionEventAdapter(store, bus, LEVEL);
        adapter.publishNewReflections("agent-1", "t1");
        assertThat(received).isEmpty();
    }
}
```

- [ ] **Step 2: Write NarrativeEmissionPolicy test**

```java
// File: blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeEmissionPolicyTest.java
package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NarrativeEmissionPolicyTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);
    private final NarrativeSynthesisGate gate =
            new NarrativeSynthesisGate(3, 0.3, Duration.ofMinutes(60));

    private LevelEvent<ReflectionEntry> event(String insight) {
        return new LevelEvent<>(
                new ReflectionEntry("a1", "t1", insight,
                        Instant.ofEpochMilli(100), List.of()),
                100, LEVEL, "t1");
    }

    @Test
    void nullState_firstSynthesis_alwaysEmits() {
        var policy = new NarrativeEmissionPolicy(gate);
        assertThat(policy.shouldEmit(List.of(event("x")), null, 100))
                .isTrue();
    }

    @Test
    void emptyBuffer_neverEmits() {
        var policy = new NarrativeEmissionPolicy(gate);
        assertThat(policy.shouldEmit(List.of(), null, 100)).isFalse();
    }

    @Test
    void belowCountThreshold_doesNotEmit() {
        var policy = new NarrativeEmissionPolicy(gate);
        var state = narrativeState(Instant.ofEpochMilli(50));
        var buffered = List.of(event("a"), event("b"));
        assertThat(policy.shouldEmit(buffered, state, 100)).isFalse();
    }

    @Test
    void meetsCountThreshold_withNovelty_emits() {
        var policy = new NarrativeEmissionPolicy(gate);
        var state = narrativeState(Instant.ofEpochMilli(50));
        var buffered = List.of(
                event("completely novel content alpha"),
                event("entirely different beta"),
                event("brand new gamma"));
        assertThat(policy.shouldEmit(buffered, state, 100)).isTrue();
    }

    @Test
    void quietPeriodBypass_emitsRegardlessOfCount() {
        var policy = new NarrativeEmissionPolicy(gate);
        var longAgo = Instant.ofEpochMilli(0);
        var state = narrativeState(longAgo);
        long now = Duration.ofMinutes(61).toMillis();
        assertThat(policy.shouldEmit(List.of(event("x")), state, now))
                .isTrue();
    }

    private NarrativeState narrativeState(Instant synthesisedAt) {
        var episode = new IndividualEpisode("ep1", Instant.EPOCH, null,
                List.of(), "existing episode description", 0.5, List.of());
        return new NarrativeState("a1", "t1", NarrativeScope.INDIVIDUAL,
                List.of(episode), synthesisedAt, 5);
    }
}
```

- [ ] **Step 3: Write NarrativeOutputProcessor test**

```java
// File: blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeOutputProcessorTest.java
package io.casehub.blocks.agentic.social.narrative;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;

class NarrativeOutputProcessorTest {

    private final NarrativeConfig config = new NarrativeConfig(
            NarrativeSynthesisGate.defaults(), 3, 2, 0.2, 10,
            "domain", "case-type");

    @Test
    void pruneEpisodes_dropsOldestBeyondMax() {
        var processor = new NarrativeOutputProcessor(config);
        var episodes = IntStream.range(0, 5)
                .mapToObj(i -> new IndividualEpisode("ep" + i,
                        Instant.ofEpochSecond(i), null, List.of(),
                        "desc" + i, 0.5, List.of()))
                .map(e -> (NarrativeFragment) e)
                .toList();
        var state = new NarrativeState("a1", "t1",
                NarrativeScope.INDIVIDUAL, episodes, Instant.now(), 5);

        var result = processor.process(List.of(state), null);
        assertThat(result.get(0).episodes()).hasSize(3);
        assertThat(result.get(0).episodes().get(0).id()).isEqualTo("ep2");
    }

    @Test
    void pruneThemes_dropsLowestSalienceBeyondMax() {
        var processor = new NarrativeOutputProcessor(config);
        var themes = List.<NarrativeFragment>of(
                theme("t1", 0.9), theme("t2", 0.5), theme("t3", 0.7));
        var state = new NarrativeState("a1", "t1",
                NarrativeScope.INDIVIDUAL, themes, Instant.now(), 0);

        var result = processor.process(List.of(state), null);
        assertThat(result.get(0).themes()).hasSize(2);
        assertThat(result.get(0).themes().stream()
                .map(DerivedTheme::label).toList())
                .containsExactlyInAnyOrder("t1", "t3");
    }

    @Test
    void pruneThemes_removesBelowSalienceFloor() {
        var processor = new NarrativeOutputProcessor(config);
        var themes = List.<NarrativeFragment>of(
                theme("above", 0.5), theme("below", 0.1));
        var state = new NarrativeState("a1", "t1",
                NarrativeScope.INDIVIDUAL, themes, Instant.now(), 0);

        var result = processor.process(List.of(state), null);
        assertThat(result.get(0).themes()).hasSize(1);
        assertThat(result.get(0).themes().get(0).label()).isEqualTo("above");
    }

    private DerivedTheme theme(String label, double salience) {
        return new DerivedTheme(label, Instant.EPOCH, null, List.of(),
                label, salience, java.util.Map.of(), List.of());
    }
}
```

- [ ] **Step 4: Write CbrStateStore test**

```java
// File: blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/CbrStateStoreTest.java
package io.casehub.blocks.agentic.social.narrative;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CbrStateStoreTest {

    @Test
    void load_parsesPartitionKey() {
        var delegate = mock(CbrNarrativeStore.class);
        var state = new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL, List.of(), Instant.now(), 0);
        when(delegate.load("agent-1", "tenant-1")).thenReturn(state);

        var store = new CbrStateStore(delegate);
        var result = store.load("agent-1:tenant-1");
        assertThat(result).isSameAs(state);
    }

    @Test
    void store_delegatesToCbrStore() {
        var delegate = mock(CbrNarrativeStore.class);
        var state = new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL, List.of(), Instant.now(), 0);

        var store = new CbrStateStore(delegate);
        store.store("agent-1:tenant-1", state);
        verify(delegate).store(state);
    }
}
```

- [ ] **Step 5: Run tests to verify failure**

Run: `mvn --batch-mode test -pl blocks -Dtest=ReflectionEventAdapterTest,NarrativeEmissionPolicyTest,NarrativeOutputProcessorTest,CbrStateStoreTest`
Expected: compilation failure

- [ ] **Step 6: Implement all four classes**

Create each class following the spec code (spec §ReflectionEventAdapter lines 321-357, §NarrativeEmissionPolicy lines 372-418, §NarrativeOutputProcessor lines 489-529, §CbrStateStore lines 537-558). Extract `pruneEpisodes()` and `pruneThemes()` logic verbatim from `NarrativeSynthesiser.java` lines 421-438.

- [ ] **Step 7: Run tests — verify PASS**

Run: `mvn --batch-mode test -pl blocks -Dtest=ReflectionEventAdapterTest,NarrativeEmissionPolicyTest,NarrativeOutputProcessorTest,CbrStateStoreTest`
Expected: PASS

- [ ] **Step 8: Run all blocks tests**

Run: `mvn --batch-mode test -pl blocks`
Expected: all existing tests PASS

- [ ] **Step 9: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/ReflectionEventAdapter.java blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeEmissionPolicy.java blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeOutputProcessor.java blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/CbrStateStore.java blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/ReflectionEventAdapterTest.java blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeEmissionPolicyTest.java blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeOutputProcessorTest.java blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/CbrStateStoreTest.java
git commit -m "feat(#259): add narrative pipeline components — adapter, emission policy, output processor, state store

Refs #259"
```

### Task 5: NarrativeContentSummariser

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeContentSummariser.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeContentSummariserTest.java`

**Interfaces:**
- Consumes: `ContentSummariser<T, R>` from summarisation-api; `AgentProvider` from platform-agent-api; `NarrativeConfig`, `NarrativeState`, `ReflectionEntry`, `IndividualEpisode`, `DerivedTheme`, `NarrativeFragment`, `NarrativeScope`, `GroupEpisode`
- Produces: `NarrativeContentSummariser` implementing `ContentSummariser<ReflectionEntry, NarrativeState>`

- [ ] **Step 1: Write tests mirroring existing NarrativeSynthesiserTest assertions**

Key test cases (mirror `NarrativeSynthesiserTest` but test only the ContentSummariser contract — no gate logic, no pruning):
- `summarise_firstSynthesis_noExistingState`
- `summarise_incrementalMerge_preservesExistingEpisodes`
- `summarise_themesFullyReDerived`
- `summarise_llmFailure_returnsPrevious`
- `summarise_parseFailure_returnsPrevious`
- `summarise_partiallyInvalidResponse_writesValidItems`
- `summarise_reflectionIndexMapping`
- `summarise_maxReflectionsCapped`
- `summarise_emptyResult_returnsPrevious`
- `summarise_groupEpisodesIncludedInTagMatching`

Use `TestAgentProvider` pattern from existing tests — mock `AgentProvider` with canned JSON responses.

- [ ] **Step 2: Run test to verify failure**

Run: `mvn --batch-mode test -pl blocks -Dtest=NarrativeContentSummariserTest`
Expected: compilation failure

- [ ] **Step 3: Implement NarrativeContentSummariser**

Extract from `NarrativeSynthesiser.java`:
- `SYSTEM_PROMPT` constant (lines 38-55)
- `assembleUserPrompt()` method (lines 211-271)
- `parseResponse()` method (lines 279-352)
- `buildEpisodes()` method (lines 354-376)
- `buildThemes()` method (lines 378-419)
- Inner records `SynthesisResult`, `EpisodeSpec`, `ThemeSpec` (lines 273-277)

The `summarise()` method implements the ContentSummariser contract:
1. Cap reflections at `config.maxReflectionsPerSynthesis()`
2. Assemble prompt from `previous` state + `items` (reflections)
3. Invoke LLM via `agentProvider`
4. Parse response → build episodes + themes → merge with `previous`
5. Return new `NarrativeState` (without pruning)
6. On error: return `previous` (or null) — idempotent no-op downstream

Remove: gate logic, pruning, NarrativeStore writes, ReflectionQueryStore, locking.

- [ ] **Step 4: Run tests — verify PASS**

Run: `mvn --batch-mode test -pl blocks -Dtest=NarrativeContentSummariserTest`
Expected: PASS

- [ ] **Step 5: Run all blocks tests**

Run: `mvn --batch-mode test -pl blocks`
Expected: all existing + new tests PASS (old NarrativeSynthesiser still exists)

- [ ] **Step 6: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeContentSummariser.java blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeContentSummariserTest.java
git commit -m "feat(#259): add NarrativeContentSummariser — ContentSummariser<ReflectionEntry, NarrativeState>

Refs #259"
```

## Batch 3: Pipeline Wiring + Migration

### Task 6: NarrativePipeline + delete NarrativeSynthesiser + end-to-end test

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativePipeline.java`
- Delete: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeSynthesiser.java` (use `ide_refactor_safe_delete`)
- Delete: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeSynthesiserTest.java` (use `ide_refactor_safe_delete`)
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativePipelineTest.java`

**Interfaces:**
- Consumes: All components from Tasks 1-5
- Produces: `NarrativePipeline.tick(agentId, tenantId)`, `NarrativePipeline.narrativeBus()`

- [ ] **Step 1: Write end-to-end pipeline test**

```java
// File: blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativePipelineTest.java
package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.blocks.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NarrativePipelineTest {

    @Test
    void tick_endToEnd_adapterToSummariserToOutput() {
        // Setup: mock ReflectionQueryStore returns reflections
        var reflectionStore = mock(ReflectionQueryStore.class);
        var reflections = List.of(
                new ReflectionEntry("agent-1", "t1", "learned about X",
                        Instant.ofEpochMilli(100), List.of()),
                new ReflectionEntry("agent-1", "t1", "discovered Y",
                        Instant.ofEpochMilli(200), List.of()),
                new ReflectionEntry("agent-1", "t1", "mastered Z",
                        Instant.ofEpochMilli(300), List.of()),
                new ReflectionEntry("agent-1", "t1", "understood W",
                        Instant.ofEpochMilli(400), List.of()),
                new ReflectionEntry("agent-1", "t1", "explored V",
                        Instant.ofEpochMilli(500), List.of()));
        when(reflectionStore.findSince(eq("agent-1"), eq("t1"), any()))
                .thenReturn(reflections);

        // Setup: mock CbrNarrativeStore (no existing state)
        var cbrStore = mock(CbrNarrativeStore.class);
        when(cbrStore.load(any(), any())).thenReturn(null);

        // Setup: ContentSummariser with canned response
        // (use a test implementation that returns a simple NarrativeState)
        var config = NarrativeConfig.defaults();
        // ... build pipeline with test summariser, capture output bus events

        // Assert: output bus receives a NarrativeState
        // Assert: state store was written to
    }
}
```

The full test will use a `TestContentSummariser` that returns a canned `NarrativeState` to avoid LLM dependency. Wire the pipeline manually (not via CDI) for unit testing.

- [ ] **Step 2: Create NarrativePipeline**

Follow spec §NarrativePipeline (lines 607-661). The class is `@ApplicationScoped`, takes CDI-injected dependencies, wires all components in the constructor.

For unit testing, provide a package-private constructor that accepts pre-built components.

- [ ] **Step 3: Run pipeline test — verify PASS**

Run: `mvn --batch-mode test -pl blocks -Dtest=NarrativePipelineTest`
Expected: PASS

- [ ] **Step 4: Delete NarrativeSynthesiser + NarrativeSynthesiserTest**

Use `ide_refactor_safe_delete` on both files. Verify no production callers (confirmed: zero references).

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test`
Expected: all tests PASS across all modules. Key checks:
- `NarrativeOrchestratorTest` — PASS (unchanged)
- `SummarisationRunnerTest` — PASS (backward compat)
- `KeyedSummarisationRunnerTest` — PASS
- `KeyedSummarisationRunnerStatefulTest` — PASS
- All new tests — PASS

- [ ] **Step 6: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativePipeline.java blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativePipelineTest.java
git commit -m "feat(#259): add NarrativePipeline, delete NarrativeSynthesiser — retrofit complete

Closes #259"
```

## References

- [2026-09-12-narrative-summarisation-retrofit-design.md] — design spec this plan implements
- [decisions.md] — D1-D7 architectural decisions
- [SummarisationRunner.java] — current runner implementation
- [KeyedSummarisationRunner.java] — keyed runner with evictState
- [EventAccumulator.java] — buffer with WindowPolicy-based shouldEmit
- [ContentSummariser.java:13] — asSummariser() bridge
- [NarrativeSynthesiser.java] — current implementation being refactored
- [NarrativeOrchestrator.java] — compositor (unchanged)
- [NarrativeSynthesisGate.java] — gate config (preserved)
- [CbrNarrativeStore.java] — CBR persistence (wrapped by CbrStateStore)
- [DecisionNarrativePipeline.java] — factory pattern precedent (#241)
- [SummarisationRunnerTest.java] — existing test patterns
- [GitHub #259] — focal issue
- [GitHub #241] — dependency (decision narratives, landed)
