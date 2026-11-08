# Decision Narratives Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #241 — Decision narratives: extend narrative infrastructure for agent decision explanations
**Issue group:** #241, #259 (deferred — identity narrative retrofit)

**Goal:** Build a two-level summarisation pipeline that transforms platform decision signals into human-readable decision narratives.

**Architecture:** Decision signals flow as `LevelEvent<DecisionSignal>` through an L1 heuristic `KeyedSummarisationRunner` (groups by step, extracts key facts) and an L2 LLM-backed `KeyedSummarisationRunner` (groups by case, synthesises narrative with incremental state). Requires enhancing `KeyedSummarisationRunner` with `StatefulSummariser` support.

**Tech Stack:** Java 17, JUnit 5, Mockito, summarisation-api, platform-agent-api, engine-api

## Global Constraints

- All record compact constructors validate with `Objects.requireNonNull` and range checks
- Bounded doubles (scores, confidence) use `[0.0, 1.0]` range with `IllegalArgumentException`
- Collections use `List.copyOf()` / `Map.copyOf()` / `Set.copyOf()` in compact constructors
- `@Nullable` from `org.jspecify.annotations` — not `jakarta.annotation`
- No Quarkus runtime in tests — plain JUnit 5 + Mockito
- `KeyedSummarisationRunner` enhancement in `summarisation-api` module (pure Java, no new deps)
- All narrative types in `blocks` module, package `io.casehub.blocks.summarisation.narrative`
- `NarrativeSignalStrategy` SPI in `blocks` module (depends on engine-api observers)
- **Note:** Spec review incorrectly placed types in `summarisation-api` (§Package structure). `summarisation-api` has zero external deps (jspecify only). `DecisionNarrativeSummariser` depends on `AgentProvider` (`platform-agent-api`), so it goes in `blocks` — matching `LlmContentSummariser` precedent.

---

## Batch 1: Pipeline enhancement (summarisation-api)

### Task 1: StatefulSummariser support + eviction for KeyedSummarisationRunner

**Files:**
- Modify: `summarisation-api/src/main/java/io/casehub/blocks/summarisation/KeyedSummarisationRunner.java`
- Test: `summarisation-api/src/test/java/io/casehub/blocks/summarisation/KeyedSummarisationRunnerStatefulTest.java`

**Interfaces:**
- Consumes: `StatefulSummariser<IN, OUT, S>` (existing in summarisation-api), `ContentSummariser<T, R>.asSummariser()` (existing)
- Produces: `KeyedSummarisationRunner.evictState(K key)` — new public method for explicit state eviction; per-key `StatefulSummariser` state propagation in `tick()` and `flush()`

- [ ] **Step 1: Write failing test — stateful summariser receives previous state**

Create `KeyedSummarisationRunnerStatefulTest.java`:

```java
package io.casehub.blocks.summarisation;

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl summarisation-api -Dtest=KeyedSummarisationRunnerStatefulTest#tick_statefulSummariser_previousStatePassedOnSecondBatch`

Expected: FAIL — `stateReceived` is always null because `KeyedSummarisationRunner` doesn't detect `StatefulSummariser`.

- [ ] **Step 3: Implement StatefulSummariser support**

Modify `KeyedSummarisationRunner.java`:

1. Add field: `private final ConcurrentHashMap<K, Object> keyState = new ConcurrentHashMap<>();`
2. Add import: `java.util.concurrent.ConcurrentHashMap`
3. Extract `invokeSummariser` method from the inline lambda in `tick()` and `flush()`:

```java
@SuppressWarnings("unchecked")
private CompletionStage<Void> invokeSummariser(List<LevelEvent<IN>> batch, K key, long now) {
    String tenancyId = batch.isEmpty() ? null : batch.get(0).tenancyId();
    if (summariser instanceof StatefulSummariser<IN, OUT, ?> stateful) {
        var typedStateful = (StatefulSummariser<IN, OUT, Object>) stateful;
        Object prevState = keyState.get(key);
        return typedStateful.summarise(batch, prevState).thenAccept(result -> {
            if (result.newState() != null) {
                keyState.put(key, result.newState());
            }
            for (var payload : result.outputs()) {
                outputBus.publish(new LevelEvent<>(payload, now, outputLevel, tenancyId));
            }
        });
    }
    return summariser.summarise(batch).thenAccept(results -> {
        for (var payload : results) {
            outputBus.publish(new LevelEvent<>(payload, now, outputLevel, tenancyId));
        }
    });
}
```

4. Update `tick()` — extract the key from the first event of each group and pass to `invokeSummariser`:

```java
public synchronized CompletionStage<Void> tick(long now) {
    var groups = accumulator.drain(now);
    if (groups.isEmpty()) {return CompletableFuture.completedFuture(null);}
    @SuppressWarnings("unchecked")
    CompletableFuture<Void>[] futures = groups.stream()
                                              .map(group -> {
                                                  var batch = compactor != null ? compactor.compact(group) : group;
                                                  K key = batch.isEmpty() ? null : accumulator.keyExtractor().apply(batch.get(0));
                                                  return invokeSummariser(batch, key, now)
                                                      .handle((v, ex) -> {
                                                          if (ex != null) {
                                                              LOG.log(System.Logger.Level.WARNING,
                                                                      "Summarisation failed, batch size=" + batch.size(), ex);
                                                              if (onFailure != null) {
                                                                  onFailure.accept(batch);
                                                              }
                                                          }
                                                          return (Void) null;
                                                      }).toCompletableFuture();
                                              })
                                              .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(futures);
}
```

5. Update `flush()` — same pattern as `tick()`.

6. Expose `keyExtractor()` on `KeyedAccumulator` — add public getter:

```java
// In KeyedAccumulator.java
public Function<LevelEvent<E>, K> keyExtractor() {
    return keyExtractor;
}
```

7. Add `evictState` method:

```java
public void evictState(K key) {
    keyState.remove(key);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl summarisation-api -Dtest=KeyedSummarisationRunnerStatefulTest#tick_statefulSummariser_previousStatePassedOnSecondBatch`

Expected: PASS

- [ ] **Step 5: Write additional tests**

Add to `KeyedSummarisationRunnerStatefulTest.java`:

```java
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
```

- [ ] **Step 6: Run all tests**

Run: `mvn --batch-mode test -pl summarisation-api`

Expected: All tests pass, including existing `KeyedSummarisationRunnerTest`.

- [ ] **Step 7: Commit**

```bash
git add summarisation-api/src/main/java/io/casehub/blocks/summarisation/KeyedSummarisationRunner.java
git add summarisation-api/src/main/java/io/casehub/blocks/summarisation/KeyedAccumulator.java
git add summarisation-api/src/test/java/io/casehub/blocks/summarisation/KeyedSummarisationRunnerStatefulTest.java
git commit -m "feat(#241): add StatefulSummariser support + state eviction to KeyedSummarisationRunner

Per-key state via ConcurrentHashMap<K, Object>. Detects StatefulSummariser
via instanceof in tick/flush (mirrors SummarisationRunner pattern). Plain
Summariser path unchanged — backward compatible. evictState(K) for explicit
cleanup on case close.

Refs casehubio/blocks#241

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 2: Decision narrative types + summarisers (blocks module)

### Task 2: DecisionSignal sealed hierarchy + output records

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/DecisionSignal.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/RoutingDecision.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/CbrRetrieval.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/TrustAssessment.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/DeliberationOutcome.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/StepOutcome.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/SignalDigest.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/StepDecisionSummary.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/DecisionNarrative.java`
- Test: `blocks/src/test/java/io/casehub/blocks/summarisation/narrative/DecisionSignalTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `DecisionSignal` (sealed, 5 permits), `SignalDigest`, `StepDecisionSummary`, `DecisionNarrative` — consumed by Tasks 3–7

- [ ] **Step 1: Write validation tests for all record types**

Create `DecisionSignalTest.java`:

```java
package io.casehub.blocks.summarisation.narrative;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionSignalTest {

    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");

    @Test
    void routingDecision_validatesRequiredFields() {
        assertThatThrownBy(() -> new RoutingDecision(null, "step", NOW, "agent", "cbr", 0.5, List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void routingDecision_validatesScoreRange() {
        assertThatThrownBy(() -> new RoutingDecision("case1", "step", NOW, "agent", "cbr", 1.5, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void routingDecision_defensiveCopiesCandidateIds() {
        var ids = new java.util.ArrayList<>(List.of("a", "b"));
        var rd = new RoutingDecision("case1", "step", NOW, "agent", "cbr", 0.5, ids, null);
        ids.add("c");
        assertThat(rd.candidateIds()).hasSize(2);
    }

    @Test
    void stepOutcome_validatesRequiredFields() {
        assertThatThrownBy(() -> new StepOutcome("case1", "step", NOW, null, null, null, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void signalDigest_validatesConfidenceRange() {
        assertThatThrownBy(() -> new SignalDigest("type", "summary", Map.of(), -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decisionNarrative_defensiveCopies() {
        var steps = new java.util.ArrayList<>(List.of("s1"));
        var sources = new java.util.ArrayList<>(List.of("src1"));
        var dn = new DecisionNarrative("case1", steps, "explanation", sources, 0.8, NOW);
        steps.add("s2");
        sources.add("src2");
        assertThat(dn.stepNames()).hasSize(1);
        assertThat(dn.evidenceSources()).hasSize(1);
    }

    @Test
    void stepDecisionSummary_validatesRequiredFields() {
        assertThatThrownBy(() -> new StepDecisionSummary(null, "step", List.of(), NOW, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void decisionSignal_sealedHierarchyExhaustive() {
        DecisionSignal signal = new StepOutcome("c1", "s1", NOW, "COMPLETED", "w1", null, Duration.ofSeconds(1));
        var result = switch (signal) {
            case RoutingDecision r -> "routing";
            case CbrRetrieval c -> "cbr";
            case TrustAssessment t -> "trust";
            case DeliberationOutcome d -> "deliberation";
            case StepOutcome s -> "step";
        };
        assertThat(result).isEqualTo("step");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=io.casehub.blocks.summarisation.narrative.DecisionSignalTest`

Expected: FAIL — classes don't exist.

- [ ] **Step 3: Create all record types**

Create each file with the exact code from the spec:

`DecisionSignal.java`:
```java
package io.casehub.blocks.summarisation.narrative;

import java.time.Instant;

public sealed interface DecisionSignal
        permits RoutingDecision, CbrRetrieval, TrustAssessment,
                DeliberationOutcome, StepOutcome {
    String caseId();
    String stepName();
    Instant timestamp();
}
```

`RoutingDecision.java`:
```java
package io.casehub.blocks.summarisation.narrative;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RoutingDecision(
        String caseId, String stepName, Instant timestamp,
        String selectedAgentId, String strategyId, double score,
        List<String> candidateIds, @Nullable String reason
) implements DecisionSignal {
    public RoutingDecision {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(selectedAgentId);
        Objects.requireNonNull(strategyId);
        if (score < 0.0 || score > 1.0)
            throw new IllegalArgumentException("score must be in [0, 1]");
        candidateIds = List.copyOf(candidateIds);
    }
}
```

`CbrRetrieval.java`:
```java
package io.casehub.blocks.summarisation.narrative;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;

public record CbrRetrieval(
        String caseId, String stepName, Instant timestamp,
        int retrievedCount, double topSimilarity,
        @Nullable String topCaseOutcome, @Nullable String domain
) implements DecisionSignal {
    public CbrRetrieval {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        if (retrievedCount < 0)
            throw new IllegalArgumentException("retrievedCount must be >= 0");
        if (topSimilarity < 0.0 || topSimilarity > 1.0)
            throw new IllegalArgumentException("topSimilarity must be in [0, 1]");
    }
}
```

`TrustAssessment.java`:
```java
package io.casehub.blocks.summarisation.narrative;

import java.time.Instant;
import java.util.Objects;

public record TrustAssessment(
        String caseId, String stepName, Instant timestamp,
        String agentId, double trustScore, double threshold, boolean passed
) implements DecisionSignal {
    public TrustAssessment {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(agentId);
        if (trustScore < 0.0 || trustScore > 1.0)
            throw new IllegalArgumentException("trustScore must be in [0, 1]");
        if (threshold < 0.0 || threshold > 1.0)
            throw new IllegalArgumentException("threshold must be in [0, 1]");
    }
}
```

`DeliberationOutcome.java`:
```java
package io.casehub.blocks.summarisation.narrative;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DeliberationOutcome(
        String caseId, String stepName, Instant timestamp,
        String outcome, int rounds,
        @Nullable String convergenceState, List<String> participantIds
) implements DecisionSignal {
    public DeliberationOutcome {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(outcome);
        if (rounds < 0)
            throw new IllegalArgumentException("rounds must be >= 0");
        participantIds = List.copyOf(participantIds);
    }
}
```

`StepOutcome.java`:
```java
package io.casehub.blocks.summarisation.narrative;

import org.jspecify.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record StepOutcome(
        String caseId, String stepName, Instant timestamp,
        String status, @Nullable String workerId,
        @Nullable String errorMessage, Duration elapsed
) implements DecisionSignal {
    public StepOutcome {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(status);
        Objects.requireNonNull(elapsed);
    }
}
```

`SignalDigest.java`:
```java
package io.casehub.blocks.summarisation.narrative;

import java.util.Map;
import java.util.Objects;

public record SignalDigest(
        String signalType, String summary,
        Map<String, String> keyFacts, double confidence
) {
    public SignalDigest {
        Objects.requireNonNull(signalType);
        Objects.requireNonNull(summary);
        keyFacts = Map.copyOf(keyFacts);
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0, 1]");
    }
}
```

`StepDecisionSummary.java`:
```java
package io.casehub.blocks.summarisation.narrative;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record StepDecisionSummary(
        String caseId, String stepName,
        List<SignalDigest> signals, Instant from, Instant to
) {
    public StepDecisionSummary {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        signals = List.copyOf(signals);
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
    }
}
```

`DecisionNarrative.java`:
```java
package io.casehub.blocks.summarisation.narrative;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DecisionNarrative(
        String caseId, List<String> stepNames, String explanation,
        List<String> evidenceSources, double confidence, Instant producedAt
) {
    public DecisionNarrative {
        Objects.requireNonNull(caseId);
        stepNames = List.copyOf(stepNames);
        Objects.requireNonNull(explanation);
        evidenceSources = List.copyOf(evidenceSources);
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        Objects.requireNonNull(producedAt);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=io.casehub.blocks.summarisation.narrative.DecisionSignalTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/summarisation/narrative/
git add blocks/src/test/java/io/casehub/blocks/summarisation/narrative/DecisionSignalTest.java
git commit -m "feat(#241): DecisionSignal sealed hierarchy + output records

5 signal variants (RoutingDecision, CbrRetrieval, TrustAssessment,
DeliberationOutcome, StepOutcome), intermediate types (SignalDigest,
StepDecisionSummary), and output type (DecisionNarrative).

Refs casehubio/blocks#241

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 3: L1 heuristic summariser (DecisionSignalSummariser)

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/DecisionSignalSummariser.java`
- Test: `blocks/src/test/java/io/casehub/blocks/summarisation/narrative/DecisionSignalSummariserTest.java`

**Interfaces:**
- Consumes: `DecisionSignal` (sealed, from Task 2), `SignalDigest`, `StepDecisionSummary` (from Task 2)
- Produces: `DecisionSignalSummariser` implementing `Summariser.SyncSummariser<DecisionSignal, StepDecisionSummary>`

- [ ] **Step 1: Write failing tests**

Create `DecisionSignalSummariserTest.java`:

```java
package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionSignalSummariserTest {

    private static final Instant T1 = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-11T10:00:01Z");
    private static final EventLevel INPUT = new EventLevel("input", 0);

    private final DecisionSignalSummariser summariser = new DecisionSignalSummariser();

    @Test
    void emptyBatch_returnsEmpty() {
        assertThat(summariser.summarise(List.of())).isEmpty();
    }

    @Test
    void stepOutcome_producesDigestWithCorrectFields() {
        var signal = new StepOutcome("case1", "route-analyst", T1, "COMPLETED", "worker1", null, Duration.ofSeconds(2));
        var batch = List.of(new LevelEvent<DecisionSignal>(signal, T1.toEpochMilli(), INPUT, null));

        var result = summariser.summarise(batch);

        assertThat(result).hasSize(1);
        var summary = result.get(0);
        assertThat(summary.caseId()).isEqualTo("case1");
        assertThat(summary.stepName()).isEqualTo("route-analyst");
        assertThat(summary.signals()).hasSize(1);

        var digest = summary.signals().get(0);
        assertThat(digest.signalType()).isEqualTo("StepOutcome");
        assertThat(digest.keyFacts()).containsEntry("status", "COMPLETED");
        assertThat(digest.keyFacts()).containsEntry("worker", "worker1");
        assertThat(digest.confidence()).isEqualTo(1.0);
    }

    @Test
    void routingDecision_producesDigestWithScore() {
        var signal = new RoutingDecision("case1", "route", T1, "agent-3", "cbr", 0.87, List.of("agent-1", "agent-2", "agent-3"), null);
        var batch = List.of(new LevelEvent<DecisionSignal>(signal, T1.toEpochMilli(), INPUT, null));

        var result = summariser.summarise(batch);

        var digest = result.get(0).signals().get(0);
        assertThat(digest.signalType()).isEqualTo("RoutingDecision");
        assertThat(digest.keyFacts()).containsEntry("selected", "agent-3");
        assertThat(digest.keyFacts()).containsEntry("candidates", "3");
        assertThat(digest.confidence()).isEqualTo(0.87);
    }

    @Test
    void multipleSignals_singleSummaryWithAllDigests() {
        var routing = new RoutingDecision("case1", "step1", T1, "agent-3", "cbr", 0.87, List.of("a1", "a2", "a3"), null);
        var trust = new TrustAssessment("case1", "step1", T2, "agent-3", 0.85, 0.70, true);

        var batch = List.of(
                new LevelEvent<DecisionSignal>(routing, T1.toEpochMilli(), INPUT, null),
                new LevelEvent<DecisionSignal>(trust, T2.toEpochMilli(), INPUT, null));

        var result = summariser.summarise(batch);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).signals()).hasSize(2);
        assertThat(result.get(0).from()).isEqualTo(T1);
        assertThat(result.get(0).to()).isEqualTo(T2);
    }

    @Test
    void allFiveSignalTypes_exhaustiveSwitch() {
        List<DecisionSignal> signals = List.of(
                new RoutingDecision("c", "s", T1, "a", "strat", 0.5, List.of(), null),
                new CbrRetrieval("c", "s", T1, 3, 0.8, "COMPLETED", null),
                new TrustAssessment("c", "s", T1, "a", 0.9, 0.7, true),
                new DeliberationOutcome("c", "s", T1, "AGREED", 2, "CONSENSUS", List.of("p1")),
                new StepOutcome("c", "s", T1, "COMPLETED", "w", null, Duration.ofSeconds(1)));

        var batch = signals.stream()
                .map(s -> new LevelEvent<DecisionSignal>(s, T1.toEpochMilli(), INPUT, null))
                .toList();

        var result = summariser.summarise(batch);
        assertThat(result.get(0).signals()).hasSize(5);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=io.casehub.blocks.summarisation.narrative.DecisionSignalSummariserTest`

Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement DecisionSignalSummariser**

Create `DecisionSignalSummariser.java` using the exact code from the spec (see §L1 Heuristic Summariser).

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=io.casehub.blocks.summarisation.narrative.DecisionSignalSummariserTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/summarisation/narrative/DecisionSignalSummariser.java
git add blocks/src/test/java/io/casehub/blocks/summarisation/narrative/DecisionSignalSummariserTest.java
git commit -m "feat(#241): L1 heuristic summariser — DecisionSignalSummariser

Exhaustive sealed switch flattening all 5 DecisionSignal variants into
SignalDigest records with structured keyFacts. Synchronous, no LLM.

Refs casehubio/blocks#241

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 4: L2 LLM summariser (DecisionNarrativeSummariser)

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/DecisionNarrativeSummariser.java`
- Test: `blocks/src/test/java/io/casehub/blocks/summarisation/narrative/DecisionNarrativeSummariserTest.java`

**Interfaces:**
- Consumes: `StepDecisionSummary` (from Task 2), `DecisionNarrative` (from Task 2), `AgentProvider` (platform-agent-api)
- Produces: `DecisionNarrativeSummariser` implementing `ContentSummariser<StepDecisionSummary, DecisionNarrative>`

- [ ] **Step 1: Write failing tests**

Create `DecisionNarrativeSummariserTest.java`:

```java
package io.casehub.blocks.summarisation.narrative;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecisionNarrativeSummariserTest {

    private static final Instant T1 = Instant.parse("2026-09-11T10:00:00Z");

    @Test
    void summarise_withLlmResponse_producesNarrative() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        var jsonResponse = """
                {"explanation":"Agent analyst-3 was selected because CBR found 5 similar cases.","evidenceSources":["RoutingDecision","CbrRetrieval"],"confidence":0.85}""";
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(jsonResponse)));

        var summariser = new DecisionNarrativeSummariser(agentProvider);
        var step = new StepDecisionSummary("case1", "route-analyst",
                List.of(new SignalDigest("RoutingDecision", "Selected analyst-3", Map.of("selected", "analyst-3"), 0.87)),
                T1, T1.plusSeconds(1));

        var result = summariser.summarise(List.of(step), null).toCompletableFuture().get();

        assertThat(result.caseId()).isEqualTo("case1");
        assertThat(result.explanation()).contains("analyst-3");
        assertThat(result.evidenceSources()).contains("RoutingDecision");
        assertThat(result.confidence()).isEqualTo(0.85);
    }

    @Test
    void summarise_withPreviousNarrative_passedToPrompt() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        var jsonResponse = """
                {"explanation":"Extended narrative.","evidenceSources":["StepOutcome"],"confidence":0.9}""";
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(jsonResponse)));

        var summariser = new DecisionNarrativeSummariser(agentProvider);
        var previous = new DecisionNarrative("case1", List.of("step1"), "Previous explanation.", List.of("RoutingDecision"), 0.8, T1);
        var step = new StepDecisionSummary("case1", "step2",
                List.of(new SignalDigest("StepOutcome", "step2 COMPLETED", Map.of("status", "COMPLETED"), 1.0)),
                T1, T1.plusSeconds(2));

        var result = summariser.summarise(List.of(step), previous).toCompletableFuture().get();

        assertThat(result.stepNames()).contains("step2");
    }

    @Test
    void summarise_llmFailure_fallsBackToTemplate() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM unavailable")));

        var summariser = new DecisionNarrativeSummariser(agentProvider);
        var step = new StepDecisionSummary("case1", "route-analyst",
                List.of(new SignalDigest("RoutingDecision", "Selected analyst-3", Map.of("selected", "analyst-3"), 0.87)),
                T1, T1.plusSeconds(1));

        var result = summariser.summarise(List.of(step), null).toCompletableFuture().get();

        assertThat(result.caseId()).isEqualTo("case1");
        assertThat(result.explanation()).contains("Selected analyst-3");
        assertThat(result.confidence()).isEqualTo(0.87);
    }

    @Test
    void summarise_emptyLlmResponse_fallsBackToTemplate() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().empty());

        var summariser = new DecisionNarrativeSummariser(agentProvider);
        var step = new StepDecisionSummary("case1", "step1",
                List.of(new SignalDigest("StepOutcome", "step1 COMPLETED", Map.of("status", "COMPLETED"), 1.0)),
                T1, T1.plusSeconds(1));

        var result = summariser.summarise(List.of(step), null).toCompletableFuture().get();

        assertThat(result.explanation()).contains("step1 COMPLETED");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=io.casehub.blocks.summarisation.narrative.DecisionNarrativeSummariserTest`

Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement DecisionNarrativeSummariser**

Create `DecisionNarrativeSummariser.java`:

```java
package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import org.jspecify.annotations.Nullable;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class DecisionNarrativeSummariser
        implements ContentSummariser<StepDecisionSummary, DecisionNarrative> {

    private static final Logger LOG = Logger.getLogger(DecisionNarrativeSummariser.class.getName());

    static final String SYSTEM_PROMPT = """
            You are generating a concise decision narrative explaining why an \
            AI agent made specific decisions during case execution. Given the \
            step-level decision summaries and (optionally) the previous narrative, \
            produce a JSON response with:

            1. explanation — a 2-4 sentence human-readable explanation of the \
            decisions made. Focus on WHY: what evidence led to the routing choice, \
            what historical cases informed the approach, what trust levels influenced \
            agent selection.

            2. evidenceSources — array of signal types that contributed (e.g., \
            "RoutingDecision", "CbrRetrieval").

            3. confidence — a number [0.0, 1.0] reflecting overall decision confidence.

            If a previous narrative is provided, incorporate new steps into the \
            existing explanation rather than starting from scratch. Preserve prior \
            context and extend it.

            Respond with JSON only. No explanation outside the JSON.""";

    private final AgentProvider agentProvider;

    @Inject
    public DecisionNarrativeSummariser(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public CompletionStage<DecisionNarrative> summarise(
            List<StepDecisionSummary> items, @Nullable DecisionNarrative previous) {
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(previous != null ? previous
                    : new DecisionNarrative("unknown", List.of(), "No signals.", List.of(), 0.0, Instant.now()));
        }

        var caseId = items.get(0).caseId();
        var stepNames = items.stream().map(StepDecisionSummary::stepName).toList();
        var allStepNames = new ArrayList<String>();
        if (previous != null) {
            allStepNames.addAll(previous.stepNames());
        }
        allStepNames.addAll(stepNames);

        var userPrompt = assembleUserPrompt(items, previous);

        try {
            var responseText = agentProvider.invoke(AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt))
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().asList()
                    .await().indefinitely()
                    .stream().collect(Collectors.joining());

            if (responseText == null || responseText.isBlank()) {
                return CompletableFuture.completedFuture(templateFallback(items, previous, caseId, allStepNames));
            }

            return CompletableFuture.completedFuture(parseResponse(responseText, caseId, allStepNames));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM invocation failed for case " + caseId, e);
            return CompletableFuture.completedFuture(templateFallback(items, previous, caseId, allStepNames));
        }
    }

    String assembleUserPrompt(List<StepDecisionSummary> items, @Nullable DecisionNarrative previous) {
        var sb = new StringBuilder();
        sb.append("## Previous narrative\n");
        if (previous != null) {
            sb.append(previous.explanation()).append("\n\n");
        } else {
            sb.append("None — first synthesis\n\n");
        }

        sb.append("## New step summaries\n");
        int i = 1;
        for (var step : items) {
            sb.append(i++).append(". Step \"").append(step.stepName())
                    .append("\" (").append(step.from()).append(" → ").append(step.to()).append(")\n");
            for (var digest : step.signals()) {
                sb.append("   - ").append(digest.signalType()).append(": ").append(digest.summary());
                if (!digest.keyFacts().isEmpty()) {
                    sb.append(" [");
                    var entries = new ArrayList<>(digest.keyFacts().entrySet());
                    for (int j = 0; j < entries.size(); j++) {
                        if (j > 0) sb.append(", ");
                        sb.append(entries.get(j).getKey()).append("=").append(entries.get(j).getValue());
                    }
                    sb.append("]");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    DecisionNarrative parseResponse(String responseText, String caseId, List<String> stepNames) {
        try (var reader = Json.createReader(new StringReader(responseText))) {
            var json = reader.readObject();
            var explanation = json.getString("explanation", "");
            var sources = new ArrayList<String>();
            if (json.containsKey("evidenceSources")) {
                var arr = json.getJsonArray("evidenceSources");
                for (int i = 0; i < arr.size(); i++) {
                    sources.add(arr.getString(i));
                }
            }
            var confidence = json.getJsonNumber("confidence") != null
                    ? json.getJsonNumber("confidence").doubleValue() : 0.5;
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            return new DecisionNarrative(caseId, stepNames, explanation, sources, confidence, Instant.now());
        }
    }

    DecisionNarrative templateFallback(List<StepDecisionSummary> items,
                                        @Nullable DecisionNarrative previous,
                                        String caseId, List<String> stepNames) {
        var sb = new StringBuilder();
        if (previous != null) {
            sb.append(previous.explanation()).append(" ");
        }
        for (var step : items) {
            sb.append("Step ").append(step.stepName()).append(": ");
            for (var digest : step.signals()) {
                sb.append(digest.summary()).append(". ");
            }
        }
        var sources = items.stream()
                .flatMap(s -> s.signals().stream())
                .map(SignalDigest::signalType)
                .distinct().toList();
        var minConfidence = items.stream()
                .flatMap(s -> s.signals().stream())
                .mapToDouble(SignalDigest::confidence)
                .min().orElse(0.5);
        return new DecisionNarrative(caseId, stepNames, sb.toString().strip(), sources, minConfidence, Instant.now());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=io.casehub.blocks.summarisation.narrative.DecisionNarrativeSummariserTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/summarisation/narrative/DecisionNarrativeSummariser.java
git add blocks/src/test/java/io/casehub/blocks/summarisation/narrative/DecisionNarrativeSummariserTest.java
git commit -m "feat(#241): L2 LLM summariser — DecisionNarrativeSummariser

ContentSummariser<StepDecisionSummary, DecisionNarrative> with LLM-backed
synthesis via AgentProvider and template fallback on failure/empty response.
JSON response parsing via jakarta.json.

Refs casehubio/blocks#241

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 5: NarrativeSignalStrategy SPI + AbstractNarrativeSignalStrategy

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/NarrativeSignalStrategy.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/AbstractNarrativeSignalStrategy.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/narrative/DecisionNarrativePipeline.java`
- Test: `blocks/src/test/java/io/casehub/blocks/summarisation/narrative/AbstractNarrativeSignalStrategyTest.java`

**Interfaces:**
- Consumes: `DecisionSignal` (from Task 2), `EventStreamBus<DecisionSignal>`, `DecisionNarrativePipeline` (created here for eviction wiring)
- Produces: `NarrativeSignalStrategy` SPI (implemented by domain repos), `AbstractNarrativeSignalStrategy` (base class with `emit()` + case-close eviction), `DecisionNarrativePipeline` (factory/wiring class)

Note: `DecisionNarrativePipeline` is created in this task because `AbstractNarrativeSignalStrategy` needs it for `evictCaseState()`. Full pipeline wiring and integration testing are in Task 6.

- [ ] **Step 1: Write failing tests**

Create `AbstractNarrativeSignalStrategyTest.java`:

```java
package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AbstractNarrativeSignalStrategyTest {

    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");

    @Test
    void emit_publishesToSignalBus() {
        var signalBus = new EventStreamBus<DecisionSignal>();
        var received = new ArrayList<DecisionSignal>();
        signalBus.subscribe(e -> true, e -> received.add(e.payload()));

        var pipeline = mock(DecisionNarrativePipeline.class);
        var strategy = new TestSignalStrategy(signalBus, pipeline);

        var signal = new StepOutcome("case1", "step1", NOW, "COMPLETED", "w1", null, Duration.ofSeconds(1));
        strategy.emit(signal);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo(signal);
    }

    @Test
    void emit_setsCorrectEventLevel() {
        var signalBus = new EventStreamBus<DecisionSignal>();
        var received = new ArrayList<LevelEvent<DecisionSignal>>();
        signalBus.subscribe(e -> true, received::add);

        var pipeline = mock(DecisionNarrativePipeline.class);
        var strategy = new TestSignalStrategy(signalBus, pipeline);

        strategy.emit(new StepOutcome("c1", "s1", NOW, "COMPLETED", null, null, Duration.ofSeconds(1)));

        assertThat(received.get(0).level().name()).isEqualTo("decision-signal");
    }

    static class TestSignalStrategy extends AbstractNarrativeSignalStrategy {
        TestSignalStrategy(EventStreamBus<DecisionSignal> signalBus, DecisionNarrativePipeline pipeline) {
            super(signalBus, pipeline);
        }

        @Override
        protected @Nullable String extractTenancyId(DecisionSignal signal) {
            return null;
        }

        @Override
        public void onStepOutcome(Object event) {}
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=io.casehub.blocks.summarisation.narrative.AbstractNarrativeSignalStrategyTest`

Expected: FAIL — classes don't exist.

- [ ] **Step 3: Create NarrativeSignalStrategy, AbstractNarrativeSignalStrategy, and DecisionNarrativePipeline**

`NarrativeSignalStrategy.java`:
```java
package io.casehub.blocks.summarisation.narrative;

public interface NarrativeSignalStrategy {
    void onStepOutcome(Object event);
}
```

Note: Using `Object` for the event parameter rather than `StepOutcomeEvent`/`CaseOutcomeEvent` to avoid engine-api import in the SPI interface. Domain implementations cast to the concrete event type. This keeps the SPI in `blocks` without requiring consumers to have engine-api on the compile classpath if they only define the strategy.

`AbstractNarrativeSignalStrategy.java`:
```java
package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import org.jspecify.annotations.Nullable;

public abstract class AbstractNarrativeSignalStrategy implements NarrativeSignalStrategy {

    private static final EventLevel SIGNAL_LEVEL = new EventLevel("decision-signal", 0);

    private final EventStreamBus<DecisionSignal> signalBus;
    private final DecisionNarrativePipeline pipeline;

    protected AbstractNarrativeSignalStrategy(EventStreamBus<DecisionSignal> signalBus,
                                               DecisionNarrativePipeline pipeline) {
        this.signalBus = signalBus;
        this.pipeline = pipeline;
    }

    protected void emit(DecisionSignal signal) {
        signalBus.publish(new LevelEvent<>(
                signal, signal.timestamp().toEpochMilli(),
                SIGNAL_LEVEL, extractTenancyId(signal)));
    }

    public void onCaseClose(String caseId) {
        pipeline.evictCaseState(caseId);
    }

    protected abstract @Nullable String extractTenancyId(DecisionSignal signal);
}
```

`DecisionNarrativePipeline.java` (shell — full wiring in Task 6):
```java
package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.KeyedSummarisationRunner;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class DecisionNarrativePipeline {

    static final EventLevel L0_SIGNALS = new EventLevel("decision-signals", 0);
    static final EventLevel L1_STEPS = new EventLevel("step-summaries", 1);
    static final EventLevel L2_NARRATIVES = new EventLevel("decision-narratives", 2);

    private final EventStreamBus<DecisionSignal> signalBus;
    private final EventStreamBus<StepDecisionSummary> stepBus;
    private final EventStreamBus<DecisionNarrative> narrativeBus;
    private final KeyedSummarisationRunner<String, DecisionSignal, StepDecisionSummary> l1;
    private final KeyedSummarisationRunner<String, StepDecisionSummary, DecisionNarrative> l2;

    @Inject
    public DecisionNarrativePipeline(AgentProvider agentProvider) {
        this.signalBus = new EventStreamBus<>();
        this.stepBus = new EventStreamBus<>();
        this.narrativeBus = new EventStreamBus<>();

        this.l1 = new KeyedSummarisationRunner<>(
                e -> e.payload().caseId() + ":" + e.payload().stepName(),
                group -> group.stream().anyMatch(e -> e.payload() instanceof StepOutcome),
                30_000L,
                Summariser.ofSync(new DecisionSignalSummariser()),
                stepBus, L1_STEPS);

        this.l2 = new KeyedSummarisationRunner<>(
                e -> e.payload().caseId(),
                group -> group.size() >= 1,
                60_000L,
                new DecisionNarrativeSummariser(agentProvider).asSummariser(),
                narrativeBus, L2_NARRATIVES);

        signalBus.subscribe(e -> true, l1::collect);
        stepBus.subscribe(e -> true, l2::collect);
    }

    DecisionNarrativePipeline(EventStreamBus<DecisionSignal> signalBus,
                               EventStreamBus<StepDecisionSummary> stepBus,
                               EventStreamBus<DecisionNarrative> narrativeBus,
                               KeyedSummarisationRunner<String, DecisionSignal, StepDecisionSummary> l1,
                               KeyedSummarisationRunner<String, StepDecisionSummary, DecisionNarrative> l2) {
        this.signalBus = signalBus;
        this.stepBus = stepBus;
        this.narrativeBus = narrativeBus;
        this.l1 = l1;
        this.l2 = l2;
        signalBus.subscribe(e -> true, l1::collect);
        stepBus.subscribe(e -> true, l2::collect);
    }

    public EventStreamBus<DecisionSignal> signalBus() { return signalBus; }
    public EventStreamBus<DecisionNarrative> narrativeBus() { return narrativeBus; }

    public void tick(long now) {
        l1.tick(now);
        l2.tick(now);
    }

    public void evictCaseState(String caseId) {
        l2.evictState(caseId);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=io.casehub.blocks.summarisation.narrative.AbstractNarrativeSignalStrategyTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/summarisation/narrative/NarrativeSignalStrategy.java
git add blocks/src/main/java/io/casehub/blocks/summarisation/narrative/AbstractNarrativeSignalStrategy.java
git add blocks/src/main/java/io/casehub/blocks/summarisation/narrative/DecisionNarrativePipeline.java
git add blocks/src/test/java/io/casehub/blocks/summarisation/narrative/AbstractNarrativeSignalStrategyTest.java
git commit -m "feat(#241): NarrativeSignalStrategy SPI + pipeline factory

Observer-driven SPI with AbstractNarrativeSignalStrategy base class (emit +
case-close eviction). DecisionNarrativePipeline wires L1→L2 with EventStreamBus
subscription and tick-driven processing.

Refs casehubio/blocks#241

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 3: Pipeline integration + example (blocks module)

### Task 6: Pipeline integration test

**Files:**
- Test: `blocks/src/test/java/io/casehub/blocks/summarisation/narrative/DecisionNarrativePipelineTest.java`

**Interfaces:**
- Consumes: All types from Tasks 1–5
- Produces: End-to-end pipeline validation

- [ ] **Step 1: Write integration test**

Create `DecisionNarrativePipelineTest.java`:

```java
package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.KeyedSummarisationRunner;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionNarrativePipelineTest {

    private static final Instant T1 = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-11T10:00:01Z");

    private EventStreamBus<DecisionSignal> signalBus;
    private EventStreamBus<StepDecisionSummary> stepBus;
    private EventStreamBus<DecisionNarrative> narrativeBus;
    private DecisionNarrativePipeline pipeline;

    private List<StepDecisionSummary> capturedStepSummaries;
    private List<DecisionNarrative> capturedNarratives;

    @BeforeEach
    void setUp() {
        signalBus = new EventStreamBus<>();
        stepBus = new EventStreamBus<>();
        narrativeBus = new EventStreamBus<>();

        capturedStepSummaries = new ArrayList<>();
        capturedNarratives = new ArrayList<>();

        stepBus.subscribe(e -> true, e -> capturedStepSummaries.add(e.payload()));
        narrativeBus.subscribe(e -> true, e -> capturedNarratives.add(e.payload()));

        var l1 = new KeyedSummarisationRunner<>(
                (LevelEvent<DecisionSignal> e) -> e.payload().caseId() + ":" + e.payload().stepName(),
                group -> group.stream().anyMatch(e -> e.payload() instanceof StepOutcome),
                30_000L,
                Summariser.ofSync(new DecisionSignalSummariser()),
                stepBus, new EventLevel("step-summaries", 1));

        var templateSummariser = new TemplateFallbackSummariser();
        var l2 = new KeyedSummarisationRunner<>(
                (LevelEvent<StepDecisionSummary> e) -> e.payload().caseId(),
                group -> group.size() >= 1,
                60_000L,
                templateSummariser.asSummariser(),
                narrativeBus, new EventLevel("decision-narratives", 2));

        pipeline = new DecisionNarrativePipeline(signalBus, stepBus, narrativeBus, l1, l2);
    }

    @Test
    void endToEnd_signalsThroughPipeline_producesNarrative() {
        var routing = new RoutingDecision("case1", "route-analyst", T1, "analyst-3", "cbr", 0.87, List.of("a1", "a2", "a3"), null);
        var outcome = new StepOutcome("case1", "route-analyst", T2, "COMPLETED", "analyst-3", null, Duration.ofSeconds(1));

        signalBus.publish(new LevelEvent<>(routing, T1.toEpochMilli(), DecisionNarrativePipeline.L0_SIGNALS, null));
        signalBus.publish(new LevelEvent<>(outcome, T2.toEpochMilli(), DecisionNarrativePipeline.L0_SIGNALS, null));

        pipeline.tick(T2.toEpochMilli());
        pipeline.tick(T2.toEpochMilli() + 1);

        assertThat(capturedStepSummaries).hasSize(1);
        assertThat(capturedStepSummaries.get(0).signals()).hasSize(2);

        assertThat(capturedNarratives).hasSize(1);
        assertThat(capturedNarratives.get(0).caseId()).isEqualTo("case1");
    }

    @Test
    void multipleSteps_incrementalNarrative() {
        emitStepSignals("case1", "step1", T1);
        pipeline.tick(T1.toEpochMilli());
        pipeline.tick(T1.toEpochMilli() + 1);

        emitStepSignals("case1", "step2", T2);
        pipeline.tick(T2.toEpochMilli());
        pipeline.tick(T2.toEpochMilli() + 1);

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.get(1).stepNames()).contains("step1", "step2");
    }

    @Test
    void evictCaseState_resetsState() {
        emitStepSignals("case1", "step1", T1);
        pipeline.tick(T1.toEpochMilli());
        pipeline.tick(T1.toEpochMilli() + 1);

        pipeline.evictCaseState("case1");

        emitStepSignals("case1", "step2", T2);
        pipeline.tick(T2.toEpochMilli());
        pipeline.tick(T2.toEpochMilli() + 1);

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.get(1).stepNames()).containsExactly("step2");
    }

    @Test
    void differentCases_isolatedState() {
        emitStepSignals("case1", "step1", T1);
        emitStepSignals("case2", "step1", T1);
        pipeline.tick(T1.toEpochMilli());
        pipeline.tick(T1.toEpochMilli() + 1);

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.stream().map(DecisionNarrative::caseId).distinct().toList())
                .containsExactlyInAnyOrder("case1", "case2");
    }

    private void emitStepSignals(String caseId, String stepName, Instant t) {
        var outcome = new StepOutcome(caseId, stepName, t, "COMPLETED", "w1", null, Duration.ofSeconds(1));
        signalBus.publish(new LevelEvent<>(outcome, t.toEpochMilli(), DecisionNarrativePipeline.L0_SIGNALS, null));
    }

    static class TemplateFallbackSummariser
            implements io.casehub.blocks.summarisation.ContentSummariser<StepDecisionSummary, DecisionNarrative> {
        @Override
        public CompletableFuture<DecisionNarrative> summarise(
                List<StepDecisionSummary> items, DecisionNarrative previous) {
            var stepNames = new ArrayList<String>();
            if (previous != null) stepNames.addAll(previous.stepNames());
            items.forEach(s -> stepNames.add(s.stepName()));

            var sb = new StringBuilder();
            if (previous != null) sb.append(previous.explanation()).append(" ");
            items.forEach(s -> s.signals().forEach(d -> sb.append(d.summary()).append(". ")));

            var sources = items.stream()
                    .flatMap(s -> s.signals().stream())
                    .map(SignalDigest::signalType).distinct().toList();
            var confidence = items.stream()
                    .flatMap(s -> s.signals().stream())
                    .mapToDouble(SignalDigest::confidence).min().orElse(0.5);

            return CompletableFuture.completedFuture(
                    new DecisionNarrative(items.get(0).caseId(), stepNames, sb.toString().strip(),
                            sources, confidence, Instant.now()));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest=io.casehub.blocks.summarisation.narrative.DecisionNarrativePipelineTest`

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add blocks/src/test/java/io/casehub/blocks/summarisation/narrative/DecisionNarrativePipelineTest.java
git commit -m "feat(#241): pipeline integration test — end-to-end L1→L2

Verifies signal→step-summary→narrative flow, incremental state across
steps, state eviction on case close, and per-case isolation.

Refs casehubio/blocks#241

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 7: YAML-style example test (fsitrading-inspired)

**Files:**
- Create: `blocks/src/test/java/io/casehub/blocks/summarisation/examples/decision/DecisionNarrativePipelineExampleTest.java`

**Interfaces:**
- Consumes: `KeyedSummarisationRunner` (from summarisation-api), `EventStreamBus`, `LevelEvent`, `Summariser`, `ContentSummariser`
- Produces: Self-contained example demonstrating the pipeline pattern with trading domain signals

- [ ] **Step 1: Write example test**

Create `DecisionNarrativePipelineExampleTest.java`:

```java
package io.casehub.blocks.summarisation.examples.decision;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.KeyedSummarisationRunner;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionNarrativePipelineExampleTest {

    sealed interface TradingSignal {
        String tradeId();
        String step();
        Instant timestamp();
    }
    record AnalystRouting(String tradeId, String step, Instant timestamp,
                          String selectedAnalyst, double score) implements TradingSignal {}
    record HistoricalMatch(String tradeId, String step, Instant timestamp,
                           int matchCount, double topSimilarity) implements TradingSignal {}
    record TradeResult(String tradeId, String step, Instant timestamp,
                       String outcome, Duration elapsed) implements TradingSignal {}

    record TradeStepSummary(String tradeId, String step,
                            List<Map<String, String>> signalFacts, Instant from, Instant to) {}
    record TradeNarrative(String tradeId, List<String> steps, String explanation,
                          double confidence, Instant producedAt) {}

    static final EventLevel L0 = new EventLevel("trading-signals", 0);
    static final EventLevel L1 = new EventLevel("step-summaries", 1);
    static final EventLevel L2 = new EventLevel("narratives", 2);

    EventStreamBus<TradingSignal> signalBus;
    EventStreamBus<TradeStepSummary> stepBus;
    EventStreamBus<TradeNarrative> narrativeBus;
    KeyedSummarisationRunner<String, TradingSignal, TradeStepSummary> l1;
    KeyedSummarisationRunner<String, TradeStepSummary, TradeNarrative> l2;

    List<TradeStepSummary> capturedSteps;
    List<TradeNarrative> capturedNarratives;

    @BeforeEach
    void setUp() {
        signalBus = new EventStreamBus<>();
        stepBus = new EventStreamBus<>();
        narrativeBus = new EventStreamBus<>();

        capturedSteps = new ArrayList<>();
        capturedNarratives = new ArrayList<>();

        stepBus.subscribe(e -> true, e -> capturedSteps.add(e.payload()));
        narrativeBus.subscribe(e -> true, e -> capturedNarratives.add(e.payload()));

        Summariser.SyncSummariser<TradingSignal, TradeStepSummary> heuristic = batch -> {
            if (batch.isEmpty()) return List.of();
            var first = batch.get(0).payload();
            var facts = batch.stream().map(e -> switch (e.payload()) {
                case AnalystRouting r -> Map.of("type", "routing", "analyst", r.selectedAnalyst(), "score", String.valueOf(r.score()));
                case HistoricalMatch h -> Map.of("type", "historical", "matches", String.valueOf(h.matchCount()), "similarity", String.valueOf(h.topSimilarity()));
                case TradeResult t -> Map.of("type", "result", "outcome", t.outcome(), "elapsed", t.elapsed().toString());
            }).toList();
            return List.of(new TradeStepSummary(first.tradeId(), first.step(), facts,
                    batch.get(0).payload().timestamp(), batch.get(batch.size() - 1).payload().timestamp()));
        };

        ContentSummariser<TradeStepSummary, TradeNarrative> narrative = (items, previous) -> {
            var steps = new ArrayList<String>();
            if (previous != null) steps.addAll(previous.steps());
            items.forEach(s -> steps.add(s.step()));
            var sb = new StringBuilder();
            if (previous != null) sb.append(previous.explanation()).append(" ");
            items.forEach(s -> sb.append("Step ").append(s.step()).append(": ")
                    .append(s.signalFacts().size()).append(" signals. "));
            return CompletableFuture.completedFuture(
                    new TradeNarrative(items.get(0).tradeId(), steps, sb.toString().strip(), 0.85, Instant.now()));
        };

        l1 = new KeyedSummarisationRunner<>(
                e -> e.payload().tradeId() + ":" + e.payload().step(),
                group -> group.stream().anyMatch(e -> e.payload() instanceof TradeResult),
                5_000L,
                Summariser.ofSync(heuristic),
                stepBus, L1);

        l2 = new KeyedSummarisationRunner<>(
                e -> e.payload().tradeId(),
                group -> group.size() >= 1,
                10_000L,
                narrative.asSummariser(),
                narrativeBus, L2);

        signalBus.subscribe(e -> true, l1::collect);
        stepBus.subscribe(e -> true, l2::collect);
    }

    @Test
    void singleTrade_threeSteps_producesIncrementalNarrative() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit("trade-1", "select-analyst", t, new AnalystRouting("trade-1", "select-analyst", t, "analyst-3", 0.87));
        emit("trade-1", "select-analyst", t, new HistoricalMatch("trade-1", "select-analyst", t.plusMillis(10), 5, 0.92));
        emit("trade-1", "select-analyst", t, new TradeResult("trade-1", "select-analyst", t.plusMillis(20), "ROUTED", Duration.ofMillis(20)));
        tick(t);

        assertThat(capturedSteps).hasSize(1);
        assertThat(capturedSteps.get(0).signalFacts()).hasSize(3);

        tick(t.plusMillis(1));
        assertThat(capturedNarratives).hasSize(1);

        emit("trade-1", "execute", t.plusSeconds(1), new TradeResult("trade-1", "execute", t.plusSeconds(2), "FILLED", Duration.ofSeconds(1)));
        tick(t.plusSeconds(2));
        tick(t.plusSeconds(2).plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.get(1).steps()).containsExactly("select-analyst", "execute");
    }

    @Test
    void multipleTrades_isolatedPipelines() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit("trade-1", "step1", t, new TradeResult("trade-1", "step1", t, "OK", Duration.ofMillis(10)));
        emit("trade-2", "step1", t, new TradeResult("trade-2", "step1", t, "OK", Duration.ofMillis(10)));
        tick(t);
        tick(t.plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
        var tradeIds = capturedNarratives.stream().map(TradeNarrative::tradeId).toList();
        assertThat(tradeIds).containsExactlyInAnyOrder("trade-1", "trade-2");
    }

    @Test
    void multiTenant_signalsDontCrossContaminate() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        signalBus.publish(new LevelEvent<>(
                new TradeResult("trade-1", "step1", t, "OK", Duration.ofMillis(10)),
                t.toEpochMilli(), L0, "tenant-A"));
        signalBus.publish(new LevelEvent<>(
                new TradeResult("trade-2", "step1", t, "OK", Duration.ofMillis(10)),
                t.toEpochMilli(), L0, "tenant-B"));

        tick(t);
        tick(t.plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
    }

    @Test
    void staleTimeout_firesForIncompleteGroups() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit("trade-1", "step1", t, new AnalystRouting("trade-1", "step1", t, "analyst-1", 0.5));
        l1.tick(t.toEpochMilli() + 6_000);

        assertThat(capturedSteps).hasSize(1);
    }

    @Test
    void eviction_resetsL2State() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit("trade-1", "step1", t, new TradeResult("trade-1", "step1", t, "OK", Duration.ofMillis(10)));
        tick(t);
        tick(t.plusMillis(1));

        l2.evictState("trade-1");

        emit("trade-1", "step2", t.plusSeconds(1), new TradeResult("trade-1", "step2", t.plusSeconds(1), "OK", Duration.ofMillis(10)));
        tick(t.plusSeconds(1));
        tick(t.plusSeconds(1).plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.get(1).steps()).containsExactly("step2");
    }

    private void emit(String tradeId, String step, Instant t, TradingSignal signal) {
        signalBus.publish(new LevelEvent<>(signal, t.toEpochMilli(), L0, null));
    }

    private void tick(Instant t) {
        l1.tick(t.toEpochMilli());
        l2.tick(t.toEpochMilli());
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest=io.casehub.blocks.summarisation.examples.decision.DecisionNarrativePipelineExampleTest`

Expected: PASS

- [ ] **Step 3: Run full test suite**

Run: `mvn --batch-mode test`

Expected: All tests pass across all modules.

- [ ] **Step 4: Commit**

```bash
git add blocks/src/test/java/io/casehub/blocks/summarisation/examples/decision/DecisionNarrativePipelineExampleTest.java
git commit -m "feat(#241): fsitrading-inspired decision narrative pipeline example

Self-contained example with TradingSignal sealed hierarchy, two-level
pipeline (heuristic L1 + template L2), multi-tenant isolation,
incremental state, and stale timeout tests. Third pipeline example
alongside clinical and logistics.

Refs casehubio/blocks#241

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## References

- [2026-09-11-decision-narratives-design.md] — design spec this plan implements
- [decisions.md] — D1–D10 architectural decisions
- [KeyedSummarisationRunner.java] — grouping runner to enhance (StatefulSummariser support)
- [SummarisationRunner.java:112-132] — StatefulSummariser pattern to replicate
- [ContentSummariser.java] — `asSummariser()` bridge
- [KeyedAccumulator.java] — needs `keyExtractor()` getter
- [ClinicalPipelineTest.java] — multi-level pipeline example precedent
- [LogisticsPipelineTest.java] — second pipeline example precedent
- GE-20260825-ba18b3 — polymorphic sealed hierarchy serialization to CBR
- casehubio/blocks#241 — focal issue
- casehubio/blocks#259 — identity narrative retrofit (deferred)
