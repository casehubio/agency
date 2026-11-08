# Summarisation YAML Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #233 — feat: summarisation YAML surface with composable runtime
**Issue group:** #231, #232, #233

**Goal:** Unify blocks' three separate batch-transform subsystems (temporal, content, observation) into one composable model. Extract to a zero-dependency API module with `StatefulSummariser` and generified `ContentSummariser<T, R>`. Add CloudEvent ingestion/emission adapters. Build a YAML pipeline surface with 5 built-in summariser types and a Quarkus build extension.

**Architecture:** Three new Maven modules in dependency order: `summarisation-api` (pure Java — unified SPI with `Summariser`, `StatefulSummariser`, `ContentSummariser<T, R>`), `cloudevents` (CE bridge with EventSink SPI), `summarisation-yaml` (canonical model, PipelineCompiler, built-in types) with a `summarisation-yaml-deployment` Quarkus build extension. YAML and Java are peer representations targeting immutable model records. The PipelineCompiler wires `SummarisationRunner` chains from the model. `ContentSummariser.asSummariser()` enables content summarisation as a pipeline citizen — no bridge adapter needed.

**Tech Stack:** Java 17, Maven, JUnit 5, Mockito, AssertJ, Jackson YAML, cloudevents-api, casehub-platform-api (ExpressionEngine SPI), Quarkus deployment API

## Global Constraints

- All new modules use `io.casehub` groupId, `0.2-SNAPSHOT` version, `casehub-blocks-parent` parent
- `summarisation-api` has zero external dependencies (jspecify only)
- `cloudevents` has no CDI/Quarkus dependency — plain Java
- `summarisation-yaml` runtime has no Quarkus dependency — plain Java + platform-api (provided)
- Package: `io.casehub.blocks.summarisation` for api; `io.casehub.blocks.summarisation.cloudevents` for bridge; `io.casehub.blocks.summarisation.yaml` for YAML surface
- All types use `@Nullable` from jspecify, not `@javax.annotation.Nullable`
- `mvn --batch-mode install` must pass after every batch

---

## Batch 1: Summarisation API Module (#231)

After this batch: unified summarisation SPI lives in `casehub-blocks-summarisation-api` — 10 extracted types + `StatefulSummariser` + generified `ContentSummariser<T, R>` + `VerbatimContentSummariser`. `LevelEvent` has tenancyId, key extractor is widened, `SummarisationRunner` manages state for `StatefulSummariser`. `ContentSummariserToSummariser` bridge deleted. All existing tests pass.

### Task 1: Create summarisation-api module and move types

**Files:**
- Create: `summarisation-api/pom.xml`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/LevelEvent.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/EventLevel.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/WindowPolicy.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/Summariser.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/Compactor.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/EventAccumulator.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/EventStreamBus.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/SummarisationRunner.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/KeyedAccumulator.java`
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/KeyedSummarisationRunner.java`
- Move tests: `EventAccumulatorTest`, `EventStreamBusTest`, `KeyedAccumulatorTest`, `KeyedSummarisationRunnerTest`, `SummarisationRunnerTest`, `SummariserTest`, `WindowPolicyTest` → `summarisation-api/src/test/java/...`
- Modify: `pom.xml` (parent) — add `summarisation-api` to `<modules>`
- Modify: `blocks/pom.xml` — add `casehub-blocks-summarisation-api` compile dependency
- Delete from blocks: the 10 source files (use `ide_refactor_safe_delete` after copy)
- Delete from blocks: `EventStreamBus.clear()` deprecated method (drop during extraction)

**Interfaces:**
- Produces: all 10 summarisation types in `io.casehub.blocks.summarisation` package, unchanged API (except EventStreamBus drops deprecated `clear()`)

- [ ] **Step 1: Create summarisation-api/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-parent</artifactId>
    <version>0.2-SNAPSHOT</version>
  </parent>

  <artifactId>casehub-blocks-summarisation-api</artifactId>
  <name>casehub-blocks-summarisation-api</name>
  <description>Pure-Java summarisation types — zero external dependencies</description>

  <dependencies>
    <dependency>
      <groupId>org.jspecify</groupId>
      <artifactId>jspecify</artifactId>
      <version>1.0.0</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Add module to parent pom.xml**

Add `<module>summarisation-api</module>` to the `<modules>` block in the parent pom, before `blocks`.

- [ ] **Step 3: Copy 10 source files to summarisation-api**

Use `ide_create_file` for each type. Copy the source content verbatim from the existing files in `blocks/blocks/src/main/java/io/casehub/blocks/summarisation/`. Drop the deprecated `clear()` method from `EventStreamBus` — only `clearSubscriptions()` is exported.

Files to copy: `LevelEvent.java`, `EventLevel.java`, `WindowPolicy.java`, `Summariser.java`, `Compactor.java`, `EventAccumulator.java`, `EventStreamBus.java`, `SummarisationRunner.java`, `KeyedAccumulator.java`, `KeyedSummarisationRunner.java`

- [ ] **Step 4: Move test files to summarisation-api**

Copy test files to `summarisation-api/src/test/java/io/casehub/blocks/summarisation/`: `EventAccumulatorTest.java`, `EventStreamBusTest.java`, `KeyedAccumulatorTest.java`, `KeyedSummarisationRunnerTest.java`, `SummarisationRunnerTest.java`, `SummariserTest.java`, `WindowPolicyTest.java`

- [ ] **Step 5: Add compile dependency in blocks/pom.xml**

```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-blocks-summarisation-api</artifactId>
  <version>${project.version}</version>
</dependency>
```

- [ ] **Step 6: Delete original source files from blocks**

Delete the 10 source files and 7 test files from `blocks/blocks/src/`. The blocks module now gets these types via the compile dependency.

- [ ] **Step 7: Build and verify**

Run: `mvn --batch-mode install -pl summarisation-api,blocks -am`
Expected: BUILD SUCCESS — blocks compiles against summarisation-api, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add summarisation-api/ pom.xml blocks/pom.xml blocks/src/
git commit -m "feat(#231): extract 10 summarisation types to casehub-blocks-summarisation-api

Refs #231"
```

### Task 2: Add tenancyId to LevelEvent

**Files:**
- Modify: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/LevelEvent.java`
- Modify: all files in blocks that construct `LevelEvent` (add explicit `null` tenancyId)
- Modify: all test files that construct `LevelEvent`
- Test: `summarisation-api/src/test/java/io/casehub/blocks/summarisation/LevelEventTest.java` (new)

**Interfaces:**
- Produces: `LevelEvent<E>(E payload, long timestamp, EventLevel level, @Nullable String tenancyId)`

- [ ] **Step 1: Write failing test for tenancyId**

Create `LevelEventTest.java` in `summarisation-api/src/test/java/io/casehub/blocks/summarisation/`:

```java
package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LevelEventTest {

    static final EventLevel LEVEL = new EventLevel("test", 1);

    @Test
    void tenancyId_presentWhenProvided() {
        var event = new LevelEvent<>("payload", 100L, LEVEL, "tenant-1");
        assertThat(event.tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void tenancyId_nullForSingleTenant() {
        var event = new LevelEvent<>("payload", 100L, LEVEL, null);
        assertThat(event.tenancyId()).isNull();
    }

    @Test
    void existingComponentsPreserved() {
        var event = new LevelEvent<>("data", 42L, LEVEL, "t1");
        assertThat(event.payload()).isEqualTo("data");
        assertThat(event.timestamp()).isEqualTo(42L);
        assertThat(event.level()).isEqualTo(LEVEL);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl summarisation-api -Dtest=LevelEventTest`
Expected: FAIL — constructor expects 3 args, got 4

- [ ] **Step 3: Update LevelEvent record**

```java
package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;

public record LevelEvent<E>(E payload, long timestamp, EventLevel level, @Nullable String tenancyId) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl summarisation-api -Dtest=LevelEventTest`
Expected: PASS

- [ ] **Step 5: Fix all LevelEvent constructors in summarisation-api**

Update `SummarisationRunner.tick()`, `SummarisationRunner.flush()`, `KeyedSummarisationRunner.tick()`, `KeyedSummarisationRunner.flush()` — all `new LevelEvent<>(payload, now, outputLevel)` calls gain a 4th arg. Use `event.tenancyId()` from the input batch (all events in a tenant-homogeneous batch share the same tenancyId):

```java
// In SummarisationRunner.tick() and flush():
outputBus.publish(new LevelEvent<>(payload, now, outputLevel,
    finalBatch.isEmpty() ? null : finalBatch.get(0).tenancyId()));
```

Same pattern in `KeyedSummarisationRunner`.

- [ ] **Step 6: Fix all LevelEvent constructors in blocks source**

Search blocks source for `new LevelEvent<>` and add explicit `null` as 4th arg. Key files:
- `ChannelEventAdapter.java` — `new LevelEvent<>(extracted, ..., level)` → add `, null`
- `InnerLifeOrchestrator.java` — same pattern

- [ ] **Step 7: Fix all LevelEvent constructors in test files**

Search all test files for `new LevelEvent<>` and add explicit `null` as 4th arg. This covers ~90 call sites across logistics, clinical, channel, and unit test files.

- [ ] **Step 8: Build and verify**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS — all tests pass with 4-arg LevelEvent

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(#231): add tenancyId to LevelEvent — clean break, all callers explicit

Refs #231"
```

### Task 3: Widen KeyedAccumulator key extractor

**Files:**
- Modify: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/KeyedAccumulator.java`
- Modify: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/KeyedSummarisationRunner.java`
- Modify: `summarisation-api/src/test/java/io/casehub/blocks/summarisation/KeyedAccumulatorTest.java`
- Modify: `summarisation-api/src/test/java/io/casehub/blocks/summarisation/KeyedSummarisationRunnerTest.java`

**Interfaces:**
- Produces: `KeyedAccumulator(Function<LevelEvent<E>, K> keyExtractor, ...)` — was `Function<E, K>`
- Produces: `KeyedSummarisationRunner(Function<LevelEvent<IN>, K> keyExtractor, ...)` — same change

- [ ] **Step 1: Write failing test for new key extractor**

Add test to `KeyedAccumulatorTest.java`:

```java
@Test
void keyExtractor_accessesLevelEventFields() {
    var acc = new KeyedAccumulator<String, String>(
        event -> event.tenancyId() + ":" + event.payload(),
        group -> group.size() >= 2,
        5000L);

    acc.collect(new LevelEvent<>("a", 100L, LEVEL, "t1"));
    acc.collect(new LevelEvent<>("b", 200L, LEVEL, "t1"));

    var groups = acc.drain(300L);
    assertThat(groups).hasSize(1);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl summarisation-api -Dtest=KeyedAccumulatorTest#keyExtractor_accessesLevelEventFields`
Expected: FAIL — type mismatch, keyExtractor expects `Function<String, String>` not `Function<LevelEvent<String>, String>`

- [ ] **Step 3: Update KeyedAccumulator**

Change `Function<E, K> keyExtractor` → `Function<LevelEvent<E>, K> keyExtractor` in the constructor. Update `collect()`:

```java
public synchronized void collect(LevelEvent<E> event) {
    K key = Objects.requireNonNull(keyExtractor.apply(event),
        "keyExtractor returned null");
    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
    lastEventTime.put(key, event.timestamp());
}
```

- [ ] **Step 4: Update KeyedSummarisationRunner**

Same change: `Function<IN, K>` → `Function<LevelEvent<IN>, K>` in all 4 constructors.

- [ ] **Step 5: Fix existing tests**

Update all existing test key extractors: `e -> e.someField()` → `event -> event.payload().someField()` (or `event -> event.payload()` for simple cases).

- [ ] **Step 6: Build and verify**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(#231): widen KeyedAccumulator key extractor to Function<LevelEvent<E>, K>

Enables tenant partitioning via event.tenancyId() in key extraction.
Refs #231"
```

### Task 4: Add StatefulSummariser and enhance SummarisationRunner

**Files:**
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/StatefulSummariser.java`
- Modify: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/SummarisationRunner.java` — state management
- Modify: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/KeyedSummarisationRunner.java` — state management
- Test: `summarisation-api/src/test/java/io/casehub/blocks/summarisation/StatefulSummariserTest.java`

**Interfaces:**
- Produces: `StatefulSummariser<IN, OUT, S>` with `SummariseResult<OUT, S>` inner record
- Produces: Enhanced `SummarisationRunner` — detects `StatefulSummariser`, manages state per partition

- [ ] **Step 1: Write failing test for StatefulSummariser in SummarisationRunner**

```java
@Test
void runner_passesAndStoresState_forStatefulSummariser() {
    var stateLog = new ArrayList<String>();

    StatefulSummariser<String, String, String> summariser = (batch, prev) -> {
        stateLog.add("prev=" + prev);
        String newState = (prev == null ? "" : prev) + batch.size();
        return CompletableFuture.completedFuture(
            new SummariseResult<>(List.of("out"), newState));
    };

    var outputBus = new EventStreamBus<String>();
    var runner = new SummarisationRunner<>(
        WindowPolicy.ofCount(1), summariser, outputBus, LEVEL);

    runner.collect(new LevelEvent<>("a", 100L, LEVEL, null));
    runner.tick(200L).toCompletableFuture().join();

    runner.collect(new LevelEvent<>("b", 300L, LEVEL, null));
    runner.tick(400L).toCompletableFuture().join();

    assertThat(stateLog).containsExactly("prev=null", "prev=1");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl summarisation-api -Dtest=StatefulSummariserTest`
Expected: FAIL — `StatefulSummariser` doesn't exist

- [ ] **Step 3: Write StatefulSummariser interface**

```java
package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface StatefulSummariser<IN, OUT, S> extends Summariser<IN, OUT> {

    CompletionStage<SummariseResult<OUT, S>> summarise(
            List<LevelEvent<IN>> batch, @Nullable S previousState);

    @Override
    default CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>> batch) {
        return summarise(batch, null).thenApply(SummariseResult::outputs);
    }

    record SummariseResult<OUT, S>(List<OUT> outputs, @Nullable S newState) {}
}
```

- [ ] **Step 4: Enhance SummarisationRunner with state management**

In `SummarisationRunner.tick()` and `flush()`: detect if `summariser instanceof StatefulSummariser`, if so call the stateful overload with per-partition state from a `ConcurrentHashMap<String, Object>` keyed by tenancyId (from batch's first event, or "default" for null).

- [ ] **Step 5: Same enhancement for KeyedSummarisationRunner**

- [ ] **Step 6: Test multi-tenant state isolation**

```java
@Test
void runner_maintainsSeparateState_perTenant() {
    // Two tenants, each gets independent state
    StatefulSummariser<String, String, Integer> counter = (batch, prev) -> {
        int count = (prev == null ? 0 : prev) + batch.size();
        return CompletableFuture.completedFuture(
            new SummariseResult<>(List.of("count=" + count), count));
    };
    // Feed events for tenant-1 and tenant-2, verify independent counts
}
```

- [ ] **Step 7: Build and verify**

Run: `mvn --batch-mode install -pl summarisation-api`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add summarisation-api/
git commit -m "feat(#231): add StatefulSummariser SPI with framework-managed state per partition

SummarisationRunner detects StatefulSummariser and manages state keyed by tenancyId.
Refs #231"
```

### Task 5: Generify ContentSummariser and move to summarisation-api

**Files:**
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/ContentSummariser.java` (generified)
- Create: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/VerbatimContentSummariser.java`
- Delete: `blocks/blocks/src/main/java/io/casehub/blocks/summarisation/ContentSummariser.java`
- Delete: `blocks/blocks/src/main/java/io/casehub/blocks/summarisation/VerbatimContentSummariser.java`
- Delete: `blocks/blocks/src/main/java/io/casehub/blocks/summarisation/ContentSummariserToSummariser.java`
- Modify: all ContentSummariser consumers in blocks — add second type parameter
- Test: `summarisation-api/src/test/java/io/casehub/blocks/summarisation/ContentSummariserTest.java`

**Interfaces:**
- Produces: `ContentSummariser<T, R>` with `asSummariser()` → `StatefulSummariser<T, R, R>`
- Produces: `VerbatimContentSummariser<T>` implements `ContentSummariser<T, String>`

- [ ] **Step 1: Write failing test for asSummariser() bridge**

```java
@Test
void asSummariser_carriesPreviousState() {
    ContentSummariser<String, String> cs = (items, prev) ->
        CompletableFuture.completedFuture(
            (prev == null ? "" : prev + " ") + String.join(",", items));

    StatefulSummariser<String, String, String> s = cs.asSummariser();

    var batch1 = List.of(new LevelEvent<>("a", 100L, LEVEL, null));
    var r1 = s.summarise(batch1, null).toCompletableFuture().join();
    assertThat(r1.outputs()).containsExactly("a");
    assertThat(r1.newState()).isEqualTo("a");

    var batch2 = List.of(new LevelEvent<>("b", 200L, LEVEL, null));
    var r2 = s.summarise(batch2, r1.newState()).toCompletableFuture().join();
    assertThat(r2.outputs()).containsExactly("a b");
    assertThat(r2.newState()).isEqualTo("a b");
}
```

- [ ] **Step 2: Write ContentSummariser<T, R> in summarisation-api**

```java
package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ContentSummariser<T, R> {
    CompletionStage<R> summarise(List<T> items, @Nullable R previous);

    default StatefulSummariser<T, R, R> asSummariser() {
        return (batch, prev) -> {
            var items = batch.stream().map(LevelEvent::payload).toList();
            return summarise(items, prev)
                .thenApply(out -> new StatefulSummariser.SummariseResult<>(List.of(out), out));
        };
    }
}
```

- [ ] **Step 3: Write VerbatimContentSummariser in summarisation-api**

Pure Java — renders items as bullet list via `Function<T, String>`. Implements `ContentSummariser<T, String>`.

- [ ] **Step 4: Delete old files from blocks**

Delete: `ContentSummariser.java`, `VerbatimContentSummariser.java`, `ContentSummariserToSummariser.java` from blocks. Delete `ContentSummariserToSummariserTest.java`.

- [ ] **Step 5: Update all ContentSummariser consumers in blocks**

Use `ide_search_text` to find all `ContentSummariser<` references. Add second type parameter:
- `ContentSummariser<Message>` → `ContentSummariser<Message, SummaryResult>`
- `ContentSummariser<T>` → `ContentSummariser<T, SummaryResult>` (in generic contexts)

Key files: `HeuristicMessageSummariser.java`, `ChannelSummariser.java`, `ThreadSummaryObserver.java`, `MemoryHygieneOrchestrator.java`, `StrategyLearningOrchestrator.java`, `TieredContentSummariser.java`, `LlmContentSummariser.java`

- [ ] **Step 6: Update TieredContentSummariser**

Add second type parameter: `TieredContentSummariser<T>` → `TieredContentSummariser<T, R>`. Mechanical — the delegates are `ContentSummariser<T, R>` now.

- [ ] **Step 7: Replace ContentSummariserToSummariser usages**

Search for `ContentSummariserToSummariser` usages. Replace with `contentSummariser.asSummariser()`.

- [ ] **Step 8: Build and verify**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS — all tests pass with generified ContentSummariser

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(#231): unify ContentSummariser<T,R> into summarisation-api with asSummariser() bridge

Generified result type breaks qhorus coupling. ContentSummariserToSummariser deleted —
replaced by ContentSummariser.asSummariser() which carries state.
Refs #231"
```

---

## Batch 2: CloudEvent Bridge (#232)

After this batch: `casehub-blocks-cloudevents` module exists with `EventSink`, `CloudEventIngestionAdapter`, `CloudEventEmitter`, and `PipelineTickScheduler`, all with full test coverage.

### Task 6: CloudEvent module with ingestion and emission

**Files:**
- Create: `cloudevents/pom.xml`
- Create: `cloudevents/src/main/java/io/casehub/blocks/summarisation/cloudevents/EventSink.java`
- Create: `cloudevents/src/main/java/io/casehub/blocks/summarisation/cloudevents/CloudEventIngestionAdapter.java`
- Create: `cloudevents/src/main/java/io/casehub/blocks/summarisation/cloudevents/CloudEventEmitter.java`
- Create: `cloudevents/src/main/java/io/casehub/blocks/summarisation/cloudevents/PipelineTickScheduler.java`
- Test: `cloudevents/src/test/java/io/casehub/blocks/summarisation/cloudevents/CloudEventIngestionAdapterTest.java`
- Test: `cloudevents/src/test/java/io/casehub/blocks/summarisation/cloudevents/CloudEventEmitterTest.java`
- Test: `cloudevents/src/test/java/io/casehub/blocks/summarisation/cloudevents/PipelineTickSchedulerTest.java`
- Modify: `pom.xml` (parent) — add `cloudevents` module

**Interfaces:**
- Consumes: `LevelEvent<E>`, `EventStreamBus<E>`, `SummarisationRunner<IN, OUT>` from summarisation-api
- Produces: `EventSink<T>` (`@FunctionalInterface void emit(T event)`), `CloudEventIngestionAdapter<E>`, `CloudEventEmitter<E>`, `PipelineTickScheduler`

- [ ] **Step 1: Create cloudevents/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-parent</artifactId>
    <version>0.2-SNAPSHOT</version>
  </parent>

  <artifactId>casehub-blocks-cloudevents</artifactId>
  <name>casehub-blocks-cloudevents</name>
  <description>CloudEvent-Summarisation bridge — ingestion, emission, tick scheduling</description>

  <dependencies>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-blocks-summarisation-api</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.cloudevents</groupId>
      <artifactId>cloudevents-api</artifactId>
    </dependency>
    <dependency>
      <groupId>io.cloudevents</groupId>
      <artifactId>cloudevents-core</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.jspecify</groupId>
      <artifactId>jspecify</artifactId>
      <version>1.0.0</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Add module to parent pom**

Add `<module>cloudevents</module>` after `summarisation-api`.

- [ ] **Step 3: Write EventSink**

```java
package io.casehub.blocks.summarisation.cloudevents;

@FunctionalInterface
public interface EventSink<T> {
    void emit(T event);
}
```

- [ ] **Step 4: Write failing test for CloudEventIngestionAdapter**

Test: receives CloudEvent, filters by type, deserializes payload, extracts tenancyId, publishes LevelEvent to bus.

```java
@Test
void ingests_matchingCloudEvent_publishesLevelEvent() {
    var bus = new EventStreamBus<Map<String, Object>>();
    var captured = new ArrayList<LevelEvent<Map<String, Object>>>();
    bus.subscribe(e -> true, captured::add);

    var adapter = new CloudEventIngestionAdapter<Map<String, Object>>(
        bus, LEVEL, "io.casehub.test.*", Map.class);

    CloudEvent ce = buildCloudEvent("io.casehub.test.scan",
        "{\"packageId\": \"PKG-1\"}", "tenant-1");
    adapter.accept(ce);

    assertThat(captured).hasSize(1);
    assertThat(captured.get(0).tenancyId()).isEqualTo("tenant-1");
    assertThat(captured.get(0).payload()).containsEntry("packageId", "PKG-1");
}

@Test
void ignores_nonMatchingCloudEventType() {
    // ... type "io.other.event" does not match pattern "io.casehub.test.*"
}
```

- [ ] **Step 5: Implement CloudEventIngestionAdapter**

Filters by type pattern (simple prefix match on `cloudEvent.getType()`), deserializes JSON data to `E`, extracts `tenancyid` extension, publishes `new LevelEvent<>(payload, timestamp, level, tenancyId)` to the bus.

- [ ] **Step 6: Write failing test for CloudEventEmitter**

Test: subscribes to bus, wraps LevelEvent as CloudEvent with type URI and tenancyId extension, calls EventSink.

```java
@Test
void emits_cloudEventFromLevelEvent() {
    var bus = new EventStreamBus<Map<String, Object>>();
    var emitted = new ArrayList<CloudEvent>();

    new CloudEventEmitter<>(bus, emitted::add,
        "io.casehub.test.output.v1", e -> true);

    bus.publish(new LevelEvent<>(Map.of("key", "val"), 100L, LEVEL, "tenant-1"));

    assertThat(emitted).hasSize(1);
    assertThat(emitted.get(0).getType()).isEqualTo("io.casehub.test.output.v1");
    assertThat(emitted.get(0).getExtension("tenancyid")).isEqualTo("tenant-1");
}
```

- [ ] **Step 7: Implement CloudEventEmitter**

Subscribes to `EventStreamBus<E>`, wraps each `LevelEvent<E>` payload as a CloudEvent with configured type URI and tenancyId extension, fires via `EventSink<CloudEvent>`.

- [ ] **Step 8: Write failing test for PipelineTickScheduler**

```java
@Test
void ticks_allRunners_atInterval() throws Exception {
    var tickCount = new AtomicInteger();
    var runner = mock(SummarisationRunner.class);
    when(runner.tick(anyLong())).thenAnswer(inv -> {
        tickCount.incrementAndGet();
        return CompletableFuture.completedFuture(null);
    });

    var scheduler = new PipelineTickScheduler(List.of(runner), 50L);
    scheduler.start();
    Thread.sleep(200);
    scheduler.stop();

    assertThat(tickCount.get()).isGreaterThanOrEqualTo(2);
}
```

- [ ] **Step 9: Implement PipelineTickScheduler**

Uses `ScheduledExecutorService` with configurable interval. `start()` begins ticking, `stop()` shuts down gracefully (flush all runners).

- [ ] **Step 10: Build and verify**

Run: `mvn --batch-mode install -pl summarisation-api,cloudevents -am`
Expected: BUILD SUCCESS

- [ ] **Step 11: Commit**

```bash
git add cloudevents/ pom.xml
git commit -m "feat(#232): CloudEvent bridge — EventSink, ingestion, emission, tick scheduler

Plain Java, no CDI dependency. Consumer provides CDI wiring as lambda.
Refs #232"
```

---

## Batch 3: YAML Canonical Model and Registry (#233 foundation)

After this batch: canonical model records exist, YAML deserializes to them via Jackson, SummariserRegistry works with `pass-through` built-in. No compiler yet.

### Task 7: Canonical model records and YAML deserialization

**Files:**
- Create: `summarisation-yaml/pom.xml`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/PipelineDefinition.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/SourceDefinition.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/LevelDefinition.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/GroupingDefinition.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/SummariserDefinition.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/EmitDefinition.java`
- Test: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/PipelineDefinitionTest.java`
- Modify: `pom.xml` (parent) — add `summarisation-yaml` module

**Interfaces:**
- Consumes: `WindowPolicy` from summarisation-api
- Produces: `PipelineDefinition`, `SourceDefinition`, `LevelDefinition`, `GroupingDefinition` (sealed: `Windowed`, `Keyed`), `SummariserDefinition`, `EmitDefinition`

- [ ] **Step 1: Create summarisation-yaml/pom.xml**

Dependencies: `casehub-blocks-summarisation-api`, `casehub-blocks-cloudevents`, `casehub-platform-api` (provided), `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`, `org.jspecify:jspecify`, test deps.

- [ ] **Step 2: Write failing test — YAML deserializes to PipelineDefinition**

```java
@Test
void parsesMinimalPipeline() throws Exception {
    String yaml = """
        pipeline:
          name: test-pipeline
          source:
            cloud-event-type: io.test.event.v1
          levels:
            - name: output
              grouping:
                type: windowed
                count: 10
              summariser:
                type: pass-through
        """;

    var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
    assertThat(def.name()).isEqualTo("test-pipeline");
    assertThat(def.levels()).hasSize(1);
    assertThat(def.levels().get(0).grouping()).isInstanceOf(GroupingDefinition.Windowed.class);
}
```

- [ ] **Step 3: Implement model records**

All records with Jackson annotations for YAML deserialization. `GroupingDefinition` sealed interface with `@JsonTypeInfo(use = NAME, property = "type")` discriminator.

- [ ] **Step 4: Test keyed grouping deserialization**

```java
@Test
void parsesKeyedGrouping() throws Exception {
    String yaml = """
        pipeline:
          name: keyed-test
          source:
            cloud-event-type: io.test.v1
          levels:
            - name: per-hub
              grouping:
                type: keyed
                key: "warehouseId"
                completion: "size >= 10"
                stale-timeout: 90000
              summariser:
                type: pass-through
        """;

    var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
    var grouping = def.levels().get(0).grouping();
    assertThat(grouping).isInstanceOf(GroupingDefinition.Keyed.class);
    var keyed = (GroupingDefinition.Keyed) grouping;
    assertThat(keyed.keyExpression()).isEqualTo("warehouseId");
    assertThat(keyed.staleTimeout()).isEqualTo(90_000L);
}
```

- [ ] **Step 5: Build and verify**

Run: `mvn --batch-mode test -pl summarisation-yaml`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add summarisation-yaml/ pom.xml
git commit -m "feat(#233): canonical model records with Jackson YAML deserialization

Refs #233"
```

### Task 8: SummariserRegistry and pass-through built-in

**Files:**
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/SummariserRegistry.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/SummariserFactory.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/SummariserTypeId.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/builtin/PassThroughSummariser.java`
- Test: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/SummariserRegistryTest.java`

**Interfaces:**
- Produces: `SummariserRegistry` (`register(String, SummariserFactory<?>)`, `create(String, Map<String, Object>)`), `SummariserFactory<E>` (`@FunctionalInterface`), `@SummariserTypeId` annotation, `PassThroughSummariser`

- [ ] **Step 1: Write failing test for registry**

```java
@Test
void registry_createsRegisteredSummariser() {
    var registry = new SummariserRegistry();
    registry.register("pass-through", config -> Summariser.ofSync(batch ->
        batch.stream().map(LevelEvent::payload).toList()));

    Summariser<String, String> s = registry.create("pass-through", Map.of());
    assertThat(s).isNotNull();
}

@Test
void registry_throwsOnUnknownType() {
    var registry = new SummariserRegistry();
    assertThatThrownBy(() -> registry.create("unknown", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Implement SummariserRegistry, SummariserFactory, annotation**

- [ ] **Step 3: Write and test PassThroughSummariser**

```java
@Test
void passThrough_returnsPayloadsUnchanged() {
    var summariser = new PassThroughSummariser<Map<String, Object>>();
    var batch = List.of(
        new LevelEvent<>(Map.of("a", 1), 100L, LEVEL, null),
        new LevelEvent<>(Map.of("b", 2), 200L, LEVEL, null));

    var result = summariser.summarise(batch).toCompletableFuture().join();
    assertThat(result).containsExactly(Map.of("a", 1), Map.of("b", 2));
}
```

- [ ] **Step 4: Register pass-through as default built-in**

`SummariserRegistry` constructor registers `pass-through` automatically.

- [ ] **Step 5: Build and verify**

Run: `mvn --batch-mode test -pl summarisation-yaml`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add summarisation-yaml/
git commit -m "feat(#233): SummariserRegistry with pass-through built-in

Refs #233"
```

---

## Batch 4: PipelineCompiler and Built-in Types (#233 core)

After this batch: `PipelineCompiler` wires full pipelines. All 5 built-in summariser types work with tests. Batch aggregates computed for expression context.

### Task 9: PipelineCompiler and CompiledPipeline

**Files:**
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/PipelineCompiler.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/CompiledPipeline.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/BatchContext.java`
- Test: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/PipelineCompilerTest.java`

**Interfaces:**
- Consumes: `PipelineDefinition`, `SummariserRegistry`, `EventStreamBus`, `SummarisationRunner`, `KeyedSummarisationRunner`, `WindowPolicy`, `EventSink`, `CloudEventIngestionAdapter`, `CloudEventEmitter`
- Produces: `PipelineCompiler` (`compile(PipelineDefinition, SummariserRegistry, ExpressionEngineRegistry, @Nullable EventSink<CloudEvent>)`), `CompiledPipeline<IN>` (`inputBus()`, `tick(long)`, `flush()`)

- [ ] **Step 1: Write failing test — compile minimal pipeline**

```java
@Test
void compiles_singleLevelPassThrough() {
    var def = new PipelineDefinition("test",
        new SourceDefinition(null, null),
        List.of(new LevelDefinition("out",
            new GroupingDefinition.Windowed(WindowPolicy.ofCount(2)),
            new SummariserDefinition("pass-through", Map.of()),
            null, List.of())));

    var registry = new SummariserRegistry();
    var pipeline = new PipelineCompiler().compile(def, registry, null, null);

    var output = new ArrayList<>();
    // Subscribe to internal output for test verification
    pipeline.inputBus().publish(new LevelEvent<>(Map.of("a", 1), 100L,
        new EventLevel("input", 0), null));
    pipeline.inputBus().publish(new LevelEvent<>(Map.of("b", 2), 200L,
        new EventLevel("input", 0), null));

    pipeline.tick(300L).toCompletableFuture().join();

    assertThat(pipeline.name()).isEqualTo("test");
}
```

- [ ] **Step 2: Implement PipelineCompiler**

Creates `EventStreamBus` instances per level, resolves summariser types from registry, wires `SummarisationRunner` or `KeyedSummarisationRunner` per level based on `GroupingDefinition` type, chains buses (level N output → level N+1 input), creates `CloudEventEmitter` for levels with `emit:` blocks.

Automatic tenant partitioning: for multi-tenant pipelines (when events carry tenancyId), interpose `KeyedAccumulator` with tenancyId as key at each windowed level. Single-tenant (null tenancyId) skips this.

- [ ] **Step 3: Implement CompiledPipeline**

```java
public class CompiledPipeline<IN> {
    private final String name;
    private final EventStreamBus<IN> inputBus;
    private final List<Tickable> runners;

    public String name() { return name; }
    public EventStreamBus<IN> inputBus() { return inputBus; }

    public CompletionStage<Void> tick(long now) {
        var futures = runners.stream()
            .map(r -> r.tick(now).toCompletableFuture())
            .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public CompletionStage<Void> flush() {
        var futures = runners.stream()
            .map(r -> r.flush().toCompletableFuture())
            .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }
}
```

- [ ] **Step 4: Test multi-level pipeline compilation**

Test 2-level pipeline: input → pass-through L1 → pass-through L2. Verify events flow through both levels.

- [ ] **Step 5: Test CloudEvent emission wiring**

Test pipeline with `emit:` block — verify `EventSink` receives CloudEvents.

- [ ] **Step 6: Build and verify**

Run: `mvn --batch-mode test -pl summarisation-yaml`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add summarisation-yaml/
git commit -m "feat(#233): PipelineCompiler wires model to SummarisationRunner chains

Refs #233"
```

### Task 10: threshold-classify and count built-ins with batch aggregates

**Files:**
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/builtin/ThresholdClassifySummariser.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/builtin/CountSummariser.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/BatchContext.java`
- Test: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/builtin/ThresholdClassifySummariserTest.java`
- Test: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/builtin/CountSummariserTest.java`
- Test: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/BatchContextTest.java`

**Interfaces:**
- Consumes: `Summariser<E, OUT>`, `CompiledExpression<C, R>` from platform-api
- Produces: `ThresholdClassifySummariser` (per-event MVEL3 predicates), `CountSummariser` (per-category counts), `BatchContext` (pre-computed aggregates)

- [ ] **Step 1: Write failing test for BatchContext**

```java
@Test
void computesAggregates_fromDeclaredFields() {
    var events = List.of(
        Map.of("severity", "HIGH", "weight", 55.0),
        Map.of("severity", "LOW", "weight", 10.0),
        Map.of("severity", "HIGH", "weight", 60.0));

    var ctx = BatchContext.compute(events, List.of("severity", "weight"));

    assertThat(ctx.get("size")).isEqualTo(3);
    assertThat(((Map<?, ?>) ((Map<?, ?>) ctx.get("counts")).get("severity")).get("HIGH")).isEqualTo(2);
    assertThat(ctx.get("sums")).asInstanceOf(InstanceOfAssertFactories.MAP)
        .extractingByKey("weight").isEqualTo(125.0);
}
```

- [ ] **Step 2: Implement BatchContext**

Static `compute(List<Map<String, Object>>, List<String> aggregateFields)` → `Map<String, Object>` with `size`, `counts`, `sums`, `avgs`, `batch` keys.

- [ ] **Step 3: Write failing test for threshold-classify**

```java
@Test
void classifies_eventsMatchingRules() {
    var config = Map.of("rules", List.of(
        Map.of("name", "heavy", "when", "weight > 50.0",
               "category", "WEIGHT_MISMATCH", "severity", "HIGH")));

    var summariser = ThresholdClassifySummariser.create(config, expressionEngine);

    var batch = List.of(
        new LevelEvent<>(Map.of("weight", 55.0, "id", "P1"), 100L, LEVEL, null),
        new LevelEvent<>(Map.of("weight", 10.0, "id", "P2"), 200L, LEVEL, null));

    var result = summariser.summarise(batch).toCompletableFuture().join();

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("category", "WEIGHT_MISMATCH");
    assertThat(result.get(0)).containsEntry("severity", "HIGH");
    assertThat(result.get(0)).containsEntry("id", "P1");
}
```

- [ ] **Step 4: Implement ThresholdClassifySummariser**

Per-event: iterates batch, evaluates each rule's `when` MVEL3 expression against each event's payload. Output: one Map per event-rule match. Rule config fields (except `name` and `when`) merged with input payload.

- [ ] **Step 5: Write failing test for count**

```java
@Test
void counts_categoriesInBatch() {
    var config = Map.of("category-field", "severity");
    var summariser = CountSummariser.create(config);

    var batch = List.of(
        new LevelEvent<>(Map.of("severity", "HIGH"), 100L, LEVEL, null),
        new LevelEvent<>(Map.of("severity", "LOW"), 200L, LEVEL, null),
        new LevelEvent<>(Map.of("severity", "HIGH"), 300L, LEVEL, null));

    var result = summariser.summarise(batch).toCompletableFuture().join();
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("HIGH", 2).containsEntry("LOW", 1);
}
```

- [ ] **Step 6: Implement CountSummariser**

- [ ] **Step 7: Register both in SummariserRegistry defaults**

- [ ] **Step 8: Build and verify**

Run: `mvn --batch-mode test -pl summarisation-yaml`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add summarisation-yaml/
git commit -m "feat(#233): threshold-classify, count built-ins with batch aggregate context

Refs #233"
```

### Task 11: phase-detect and field-extract built-ins

**Files:**
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/builtin/PhaseDetectSummariser.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/builtin/FieldExtractSummariser.java`
- Test: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/builtin/PhaseDetectSummariserTest.java`
- Test: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/builtin/FieldExtractSummariserTest.java`

**Interfaces:**
- Produces: `PhaseDetectSummariser` (stateful per-partition state machine), `FieldExtractSummariser` (JQ transformation)

- [ ] **Step 1: Write failing test for phase-detect — basic transition**

```java
@Test
void emitsOnTransition_silentWhenStable() {
    var config = Map.of(
        "initial", "NORMAL",
        "states", List.of("NORMAL", "CONGESTION"),
        "transitions", List.of(
            Map.of("from", "NORMAL", "to", "CONGESTION",
                   "when", "counts.severity.HIGH >= 3")));

    var summariser = PhaseDetectSummariser.create(config, expressionEngine);

    // Batch with 2 HIGH — no transition
    var batch1 = createBatch(Map.of("severity", "HIGH"), 2,
                             Map.of("severity", "LOW"), 3);
    var r1 = summariser.summarise(batch1).toCompletableFuture().join();
    assertThat(r1).isEmpty();

    // Batch with 3 HIGH — transition fires
    var batch2 = createBatch(Map.of("severity", "HIGH"), 3,
                             Map.of("severity", "LOW"), 1);
    var r2 = summariser.summarise(batch2).toCompletableFuture().join();
    assertThat(r2).hasSize(1);
    assertThat(r2.get(0)).containsEntry("from", "NORMAL")
                         .containsEntry("to", "CONGESTION");
}
```

- [ ] **Step 2: Test phase-detect — self-transition, terminal state, first-match-wins**

- [ ] **Step 3: Implement PhaseDetectSummariser**

Per-partition state machine. State stored in `ConcurrentHashMap<String, String>` keyed by partition ID (tenancyId or "default"). Evaluates transitions in YAML declaration order (first match wins). Emits only on state change. `BatchContext.compute()` provides the expression context.

- [ ] **Step 4: Write failing test for field-extract**

```java
@Test
void extractsFields_viaJQ() {
    var config = Map.of("expression", ".anomalies | length");
    var summariser = FieldExtractSummariser.create(config, expressionEngine);

    var payload = Map.of("anomalies", List.of("a", "b", "c"));
    var batch = List.of(new LevelEvent<>(payload, 100L, LEVEL, null));

    var result = summariser.summarise(batch).toCompletableFuture().join();
    assertThat(result).hasSize(1);
}
```

- [ ] **Step 5: Implement FieldExtractSummariser**

Uses platform `JQExpressionEngine` via `CompiledExpression<JsonNode, List<JsonNode>>`. Converts input Map to JsonNode, evaluates JQ, converts results back to Map.

- [ ] **Step 6: Register both in SummariserRegistry defaults**

- [ ] **Step 7: Build and verify**

Run: `mvn --batch-mode test -pl summarisation-yaml`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add summarisation-yaml/
git commit -m "feat(#233): phase-detect (stateful state machine) and field-extract (JQ) built-ins

Refs #233"
```

---

## Batch 5: Deployment Module and Integration (#233 build-time)

After this batch: Quarkus deployment module discovers YAML pipelines, validates, registers beans. End-to-end integration test with logistics pipeline in YAML.

### Task 12: Build-time validation

**Files:**
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/PipelineValidator.java`
- Test: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/PipelineValidatorTest.java`

**Interfaces:**
- Consumes: `PipelineDefinition`, `SummariserRegistry`
- Produces: `PipelineValidator` (`validate(PipelineDefinition, SummariserRegistry) → List<ValidationError>`)

- [ ] **Step 1: Write failing tests for each validation rule**

```java
@Test
void rejects_unknownSummariserType() {
    var def = pipeline(level("l1", summariser("nonexistent")));
    var errors = new PipelineValidator().validate(def, new SummariserRegistry());
    assertThat(errors).anyMatch(e -> e.message().contains("nonexistent"));
}

@Test
void rejects_unreachablePhaseDetectState() {
    var config = Map.of("initial", "A", "states", List.of("A", "B", "C"),
        "transitions", List.of(Map.of("from", "A", "to", "B", "when", "true")));
    // C is unreachable
    var def = pipeline(level("l1", summariser("phase-detect", config)));
    var errors = new PipelineValidator().validate(def, registry);
    assertThat(errors).anyMatch(e -> e.message().contains("unreachable") && e.message().contains("C"));
}

@Test
void rejects_missingInitialState() { ... }

@Test
void rejects_bothWindowedAndKeyed() { ... }

@Test
void rejects_missingRulesForThresholdClassify() { ... }
```

- [ ] **Step 2: Implement PipelineValidator**

- [ ] **Step 3: Build and verify**

- [ ] **Step 4: Commit**

```bash
git add summarisation-yaml/
git commit -m "feat(#233): PipelineValidator — build-time validation rules

Refs #233"
```

### Task 13: Quarkus deployment module

**Files:**
- Create: `summarisation-yaml-deployment/pom.xml`
- Create: `summarisation-yaml-deployment/src/main/java/io/casehub/blocks/summarisation/yaml/deployment/SummarisationYamlProcessor.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/runtime/SummarisationRecorder.java`
- Create: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/runtime/PipelineTickBean.java`
- Modify: `pom.xml` (parent) — add `summarisation-yaml-deployment` module

**Interfaces:**
- Consumes: `PipelineCompiler`, `PipelineValidator`, `SummariserRegistry`, `PipelineDefinition`
- Produces: Quarkus build extension that discovers YAML, validates, registers `CompiledPipeline` beans, wires `@Scheduled` tick

- [ ] **Step 1: Create summarisation-yaml-deployment/pom.xml**

Dependencies: `summarisation-yaml`, `quarkus-core-deployment`, `quarkus-arc-deployment`.

- [ ] **Step 2: Implement SummarisationYamlProcessor**

```java
@BuildStep
void discoverPipelines(BuildProducer<..> pipelines) {
    // Scan META-INF/summarisation/*.yaml
    // Parse each to PipelineDefinition
    // Validate via PipelineValidator
    // Fail build on validation errors
}

@BuildStep
@Record(RUNTIME_INIT)
void registerPipelines(SummarisationRecorder recorder, ...) {
    // Jandex scan for @SummariserTypeId
    // Record: populate registry, compile pipelines, register beans
}
```

- [ ] **Step 3: Implement SummarisationRecorder**

Runtime recorder that populates `SummariserRegistry` from Jandex-discovered types, compiles each `PipelineDefinition`, and returns `CompiledPipeline` beans.

- [ ] **Step 4: Implement PipelineTickBean**

`@ApplicationScoped` bean with `@Scheduled(every = "${casehub.summarisation.tick-interval-ms:1000}ms")` that ticks all injected `CompiledPipeline` instances.

- [ ] **Step 5: Build and verify**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add summarisation-yaml-deployment/ summarisation-yaml/ pom.xml
git commit -m "feat(#233): Quarkus deployment module — YAML discovery, validation, recorder

Refs #233"
```

### Task 14: End-to-end integration test

**Files:**
- Create: `summarisation-yaml/src/test/resources/META-INF/summarisation/logistics-hub.yaml`
- Create: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/LogisticsIntegrationTest.java`

**Interfaces:**
- Consumes: all components from Tasks 5-11

- [ ] **Step 1: Write logistics-hub.yaml**

Full pipeline matching the spec's YAML grammar example: scans → anomalies (threshold-classify) → phases (phase-detect) with CloudEvent emission at both levels.

- [ ] **Step 2: Write integration test**

Parse YAML, compile pipeline, feed events, verify:
1. threshold-classify produces anomalies from weight > 50 events
2. phase-detect transitions from NORMAL_FLOW to CONGESTION when 3+ HIGH severity anomalies
3. CloudEvent emission produces correctly typed events with tenancyId

- [ ] **Step 3: Test multi-tenant isolation**

Feed events for tenant-1 and tenant-2. Verify each tenant's phase-detect state is independent.

- [ ] **Step 4: Build full project**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS — all modules compile, all tests pass

- [ ] **Step 5: Commit**

```bash
git add summarisation-yaml/
git commit -m "feat(#233): end-to-end integration test — logistics pipeline in YAML

Multi-tenant isolation verified.
Refs #233"
```

---

## Deferred Issues

File these before implementation begins:

1. `feat: YAML compactor block with built-in dedup strategy` — Ref: spec §Compaction
2. `feat: per-pipeline tick interval configuration` — Ref: spec §Build-time Processing step 5

## References

- [2026-09-05-summarisation-yaml-surface-design.md] — design spec this plan implements
- [decisions.md] — 12 design decisions (D1-D12)
- `blocks/blocks/src/main/java/io/casehub/blocks/summarisation/` — existing types to extract
- `blocks/blocks/src/test/java/io/casehub/blocks/summarisation/examples/logistics/` — reference pipeline pattern
- `io.casehub.platform.api.expression.ExpressionEngine` — platform expression SPI
- [GE-20260813-keyed-summarisation-runner-api] — KeyedSummarisationRunner API constraint
- GitHub #231, #232, #233
