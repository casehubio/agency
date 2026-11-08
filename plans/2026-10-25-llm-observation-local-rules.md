# LLM Observation & Local Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #284 — LLM-driven observation & local rules
**Issue group:** #284

**Goal:** Build an LLM-backed interpretation layer that composes existing summarisation primitives to interpret engine environment state and dispatch observations to caller-registered handlers.

**Architecture:** Event-driven, alongside the engine tick loop (not inside it). LLM templates implement `ContentSummariser<E, R>` and plug into `SummarisationRunner` via `asSummariser()`. Handler dispatch routes LLM observations to pattern-matched handlers by patternId. All new types in `io.casehub.agency.stigmergy.llm`.

**Tech Stack:** Java 17, JUnit 5, Mockito, Jackson, agency summarisation-api, engine-api

## Global Constraints

- All new types in package `io.casehub.agency.stigmergy.llm`
- Main sources in `agency-core/src/main/java/`
- Test sources in `agency/src/test/java/`
- No Quarkus runtime — plain JUnit 5 with Mockito
- No dependency on `engine-common-core` internal types — use `EnvironmentWriter` SPI
- Use `StructuredAgentInvoker.InvocationMetadata` (nested record), not standalone
- `EmissionPolicy.window()` addition goes in `summarisation-api` module
- TDD: write failing test → verify fail → implement → verify pass → commit
- Use `ide_create_file` for new Java files, `ide_insert_member` / `ide_edit_member` for modifications

---

## Batch 1: Foundation — Records, SPIs, and EmissionPolicy bridge

### Task 1: LLM-facing records and engine-facing result records

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/LlmObservation.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/LlmObservationResponse.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/LlmRecommendation.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/LlmActionResponse.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/LlmInvocationPolicy.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/RecommendedAction.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/LlmObservationResult.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/ActionResult.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/InterpretationResult.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/EnvironmentWriter.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/ObservationHandler.java`
- Test: `agency/src/test/java/io/casehub/agency/stigmergy/llm/FoundationTypesTest.java`

**Interfaces:**
- Produces: `LlmObservation(String patternId, double confidence, List<String> evidence, List<String> derivedFrom, Map<String, Object> details, String reasoning)`
- Produces: `LlmObservationResponse(List<LlmObservation> observations)`
- Produces: `LlmRecommendation(String actionType, Map<String, Object> parameters, List<String> justifyingPatternIds, double confidence, String reasoning)`
- Produces: `LlmActionResponse(List<LlmRecommendation> actions)`
- Produces: `LlmInvocationPolicy(Duration expectedDuration, Duration warnAfter, Duration errorAfter)` with `LlmInvocationPolicy.of(Duration, Duration, Duration)`
- Produces: `RecommendedAction(RuleAction action, List<String> justifyingPatternIds, double confidence)`
- Produces: `LlmObservationResult(List<Observation> observations, List<LlmObservation> rawObservations, double confidence, String reasoning, Instant batchStart, Instant batchEnd, int batchEventCount, StructuredAgentInvoker.InvocationMetadata invocationMetadata)`
- Produces: `ActionResult(List<RecommendedAction> recommendedActions, double confidence, String reasoning, StructuredAgentInvoker.InvocationMetadata invocationMetadata)`
- Produces: `InterpretationResult(List<Observation> observations, List<RecommendedAction> recommendedActions, double confidence, String reasoning, Instant batchStart, Instant batchEnd, int batchEventCount, StructuredAgentInvoker.InvocationMetadata invocationMetadata)`
- Produces: `EnvironmentWriter` interface with `storeObservations(UUID, String, List<Observation>)`, `depositSignal(UUID, String, double, Duration, String)`, `writeContext(UUID, String, JsonNode)`
- Produces: `ObservationHandler` functional interface with `void handle(Observation, LlmObservation, ObservationHandlerContext)`

- [ ] **Step 1: Write failing tests for record construction and validation**

```java
package io.casehub.agency.stigmergy.llm;

import io.casehub.api.spi.observation.Observation;
import io.casehub.api.spi.observation.RuleAction;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class FoundationTypesTest {

    @Test
    void llmObservation_constructsWithAllFields() {
        var obs = new LlmObservation("risk-spike", 0.92,
            List.of("risk.score"), List.of(),
            Map.of("delta", 0.37), "Risk jumped");
        assertThat(obs.patternId()).isEqualTo("risk-spike");
        assertThat(obs.confidence()).isEqualTo(0.92);
        assertThat(obs.evidence()).containsExactly("risk.score");
        assertThat(obs.derivedFrom()).isEmpty();
    }

    @Test
    void llmObservation_derivedFromReferencesParent() {
        var derived = new LlmObservation("compound", 0.88,
            List.of("tx.amount"), List.of("risk-spike"),
            Map.of(), "Compound pattern");
        assertThat(derived.derivedFrom()).containsExactly("risk-spike");
    }

    @Test
    void llmInvocationPolicy_factoryMethod() {
        var policy = LlmInvocationPolicy.of(
            Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(5));
        assertThat(policy.expectedDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.warnAfter()).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.errorAfter()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void recommendedAction_wrapsRuleActionWithAttribution() {
        var action = new RecommendedAction(
            new RuleAction.DepositSignal("alert", 0.9, null),
            List.of("risk-spike"), 0.85);
        assertThat(action.justifyingPatternIds()).containsExactly("risk-spike");
        assertThat(action.action()).isInstanceOf(RuleAction.DepositSignal.class);
    }

    @Test
    void llmObservationResult_carriesParallelRawAndConverted() {
        var raw = new LlmObservation("p1", 0.9, List.of(), List.of(), Map.of(), "r");
        var engine = new Observation("p1", 0.9, Map.of(), Instant.now());
        var result = new LlmObservationResult(
            List.of(engine), List.of(raw), 0.9, "reasoning",
            Instant.now(), Instant.now(), 5,
            StructuredAgentInvoker.InvocationMetadata.EMPTY);
        assertThat(result.observations()).hasSize(1);
        assertThat(result.rawObservations()).hasSize(1);
        assertThat(result.rawObservations().get(0).patternId())
            .isEqualTo(result.observations().get(0).patternId());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl agency -Dtest=FoundationTypesTest -Dsurefire.failIfNoSpecifiedTests=false --batch-mode`
Expected: Compilation failure — classes don't exist

- [ ] **Step 3: Create all record and interface files**

Use `ide_create_file` for each. Records are small — 3-15 lines each. Key validation:
- `LlmInvocationPolicy`: compact constructor validates `expectedDuration != null`, `warnAfter != null`, `errorAfter != null`
- `LlmObservation`: compact constructor validates `patternId != null && !patternId.isBlank()`, `confidence >= 0.0 && confidence <= 1.0`
- `RecommendedAction`: compact constructor validates `action != null`, `confidence >= 0.0 && confidence <= 1.0`, defensive copies on lists

`EnvironmentWriter` is an interface with three methods. `ObservationHandler` is a `@FunctionalInterface`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl agency -Dtest=FoundationTypesTest --batch-mode`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/stigmergy/llm/
git add agency/src/test/java/io/casehub/agency/stigmergy/llm/FoundationTypesTest.java
git commit -m "feat(#284): add foundation records, SPIs, and LLM-facing types for stigmergy LLM layer"
```

### Task 2: EmissionPolicy.window() factory method

**Files:**
- Modify: `summarisation-api/src/main/java/io/casehub/agency/summarisation/EmissionPolicy.java`
- Test: `summarisation-api/src/test/java/io/casehub/agency/summarisation/EmissionPolicyTest.java`

**Interfaces:**
- Produces: `EmissionPolicy.window(WindowPolicy) → EmissionPolicy<IN, S>` — static factory wrapping `WindowPolicyEmission`

- [ ] **Step 1: Write failing test for window() factory**

Add to existing `EmissionPolicyTest`:

```java
@Test
void window_wrapsWindowPolicyAsEmissionPolicy() {
    var wp = WindowPolicy.ofCount(3);
    EmissionPolicy<String, Object> ep = EmissionPolicy.window(wp);
    var events = List.of(
        new LevelEvent<>("a", 1L, new EventLevel("l", 0), null),
        new LevelEvent<>("b", 2L, new EventLevel("l", 0), null));
    assertThat(ep.shouldEmit(events, null, 100L)).isFalse();
    events = List.of(
        new LevelEvent<>("a", 1L, new EventLevel("l", 0), null),
        new LevelEvent<>("b", 2L, new EventLevel("l", 0), null),
        new LevelEvent<>("c", 3L, new EventLevel("l", 0), null));
    assertThat(ep.shouldEmit(events, null, 100L)).isTrue();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl summarisation-api -Dtest=EmissionPolicyTest#window_wrapsWindowPolicyAsEmissionPolicy --batch-mode`
Expected: Compilation failure — `window()` method doesn't exist

- [ ] **Step 3: Add window() factory to EmissionPolicy**

Use `ide_insert_member` to add to `EmissionPolicy.java`:

```java
static <IN, S> EmissionPolicy<IN, S> window(WindowPolicy policy) {
    return new WindowPolicyEmission<>(policy);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl summarisation-api -Dtest=EmissionPolicyTest --batch-mode`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add summarisation-api/src/main/java/io/casehub/agency/summarisation/EmissionPolicy.java
git add summarisation-api/src/test/java/io/casehub/agency/summarisation/EmissionPolicyTest.java
git commit -m "feat(#284): add EmissionPolicy.window() factory method bridging WindowPolicy"
```

---

## Batch 2: Emission Infrastructure — ChangeGatedEmissionPolicy + InFlightGate

### Task 3: ChangeGatedEmissionPolicy and InFlightGate

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/ChangeGatedEmissionPolicy.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/InFlightGate.java`
- Test: `agency/src/test/java/io/casehub/agency/stigmergy/llm/ChangeGatedEmissionPolicyTest.java`
- Test: `agency/src/test/java/io/casehub/agency/stigmergy/llm/InFlightGateTest.java`

**Interfaces:**
- Consumes: `EmissionPolicy<E, S>` (summarisation-api), `ContentSummariser<E, R>` (summarisation-api), `LevelEvent<E>` (summarisation-api)
- Produces: `ChangeGatedEmissionPolicy<E> implements EmissionPolicy<E, Object>` — constructor: `(Set<String> watchedKeys, Function<LevelEvent<E>, Set<String>> changedKeyExtractor, EmissionPolicy<E, ?> delegate)`
- Produces: `InFlightGate<E, R>` — `summariser() → ContentSummariser<E, R>`, `gating(EmissionPolicy<E, S>) → EmissionPolicy<E, S>`

- [ ] **Step 1: Write failing tests for ChangeGatedEmissionPolicy**

```java
package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.summarisation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;

class ChangeGatedEmissionPolicyTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    record TestEvent(Set<String> changedKeys) {}

    Function<LevelEvent<TestEvent>, Set<String>> extractor =
        e -> e.payload().changedKeys();

    @Test
    void emits_whenWatchedKeyIntersectsChangedKeys() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.*"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("risk.score")), 1L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isTrue();
    }

    @Test
    void suppresses_whenNoWatchedKeyChanged() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.*"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("account.name")), 1L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isFalse();
    }

    @Test
    void suppresses_whenDelegateNotReady() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.*"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(5)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("risk.score")), 1L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isFalse();
    }

    @Test
    void prefixMatching_matchesDottedKeys() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("transaction.*"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("transaction.amount")), 1L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isTrue();
    }
}
```

- [ ] **Step 2: Write failing tests for InFlightGate**

```java
package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.summarisation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class InFlightGateTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    @Test
    void gating_allowsEmission_whenNothingInFlight() {
        ContentSummariser<String, String> delegate = (items, prev) ->
            CompletableFuture.completedFuture("done");
        var gate = new InFlightGate<>(delegate);
        EmissionPolicy<String, Object> gated =
            gate.gating(EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(new LevelEvent<>("a", 1L, LEVEL, null));
        assertThat(gated.shouldEmit(buffer, null, 100L)).isTrue();
    }

    @Test
    void gating_suppressesEmission_whenCallInFlight() {
        var pending = new CompletableFuture<String>();
        ContentSummariser<String, String> delegate = (items, prev) -> pending;
        var gate = new InFlightGate<>(delegate);

        gate.summariser().summarise(List.of("a"), null);

        EmissionPolicy<String, Object> gated =
            gate.gating(EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(new LevelEvent<>("b", 2L, LEVEL, null));
        assertThat(gated.shouldEmit(buffer, null, 200L)).isFalse();
    }

    @Test
    void gating_resumesEmission_afterCallCompletes() {
        var pending = new CompletableFuture<String>();
        ContentSummariser<String, String> delegate = (items, prev) -> pending;
        var gate = new InFlightGate<>(delegate);

        gate.summariser().summarise(List.of("a"), null);

        EmissionPolicy<String, Object> gated =
            gate.gating(EmissionPolicy.window(WindowPolicy.ofCount(1)));
        assertThat(gated.shouldEmit(
            List.of(new LevelEvent<>("b", 2L, LEVEL, null)), null, 200L)).isFalse();

        pending.complete("done");

        assertThat(gated.shouldEmit(
            List.of(new LevelEvent<>("c", 3L, LEVEL, null)), null, 300L)).isTrue();
    }

    @Test
    void summariser_delegatesToWrapped() {
        ContentSummariser<String, String> delegate = (items, prev) ->
            CompletableFuture.completedFuture("result:" + items.size());
        var gate = new InFlightGate<>(delegate);
        var result = gate.summariser().summarise(List.of("a", "b"), null)
            .toCompletableFuture().join();
        assertThat(result).isEqualTo("result:2");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn test -pl agency -Dtest="ChangeGatedEmissionPolicyTest,InFlightGateTest" -Dsurefire.failIfNoSpecifiedTests=false --batch-mode`
Expected: Compilation failure

- [ ] **Step 4: Implement ChangeGatedEmissionPolicy**

Use `ide_create_file`. Key logic:
- Constructor stores watchedKeys, changedKeyExtractor, delegate
- `shouldEmit()`: extract changed keys from ALL events in buffer (union), check prefix intersection with watchedKeys, then delegate to wrapped policy
- Prefix matching: `watchedKey.endsWith("*")` → check if any changed key starts with the prefix (minus the `*`)

- [ ] **Step 5: Implement InFlightGate**

Use `ide_create_file`. Key design:
- `volatile CompletionStage<?> inflight` field
- `summariser()` returns a `ContentSummariser` that tracks the pending stage
- `gating(policy)` returns an `EmissionPolicy` that checks `inflight != null && !inflight.toCompletableFuture().isDone()` → suppress

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn test -pl agency -Dtest="ChangeGatedEmissionPolicyTest,InFlightGateTest" --batch-mode`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/stigmergy/llm/ChangeGatedEmissionPolicy.java
git add agency-core/src/main/java/io/casehub/agency/stigmergy/llm/InFlightGate.java
git add agency/src/test/java/io/casehub/agency/stigmergy/llm/ChangeGatedEmissionPolicyTest.java
git add agency/src/test/java/io/casehub/agency/stigmergy/llm/InFlightGateTest.java
git commit -m "feat(#284): add ChangeGatedEmissionPolicy and InFlightGate for LLM emission control"
```

---

## Batch 3: Handler Dispatch — ObservationHandlerContext + ObservationDispatcher

### Task 4: ObservationHandlerContext with accumulated observation storage

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/ObservationHandlerContext.java`
- Test: `agency/src/test/java/io/casehub/agency/stigmergy/llm/ObservationHandlerContextTest.java`

**Interfaces:**
- Consumes: `EnvironmentWriter` (Task 1), `LlmObservationResult` (Task 1), `Observation` (engine-api), `RuleAction` (engine-api)
- Produces: `ObservationHandlerContext` — `depositSignal(String, double)`, `depositSignal(String, double, Duration)`, `writeContext(String, JsonNode)`, `storeObservation(Observation)`, package-private `flushObservations()`, `caseId()`, `agentId()`, `batchResult()`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.agency.stigmergy.llm;

import io.casehub.api.spi.observation.Observation;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservationHandlerContextTest {

    EnvironmentWriter writer = mock(EnvironmentWriter.class);
    UUID caseId = UUID.randomUUID();
    LlmObservationResult batchResult = new LlmObservationResult(
        List.of(), List.of(), 0.9, "r", Instant.now(), Instant.now(), 1,
        StructuredAgentInvoker.InvocationMetadata.EMPTY);

    ObservationHandlerContext ctx = new ObservationHandlerContext(
        caseId, "agent1", "tenant1", writer, batchResult);

    @Test
    void depositSignal_delegatesToWriter() {
        ctx.depositSignal("alert", 0.8);
        verify(writer).depositSignal(caseId, "alert", 0.8, null, "agent1");
    }

    @Test
    void storeObservation_accumulatesPending() {
        var obs1 = new Observation("p1", 0.9, Map.of(), Instant.now());
        var obs2 = new Observation("p2", 0.8, Map.of(), Instant.now());
        ctx.storeObservation(obs1);
        ctx.storeObservation(obs2);
        verifyNoInteractions(writer);
    }

    @Test
    void flushObservations_writesBatchToWriter() {
        var obs1 = new Observation("p1", 0.9, Map.of(), Instant.now());
        var obs2 = new Observation("p2", 0.8, Map.of(), Instant.now());
        ctx.storeObservation(obs1);
        ctx.storeObservation(obs2);
        ctx.flushObservations();
        verify(writer).storeObservations(eq(caseId), eq("agent1"),
            argThat(list -> list.size() == 2));
    }

    @Test
    void flushObservations_noOpWhenEmpty() {
        ctx.flushObservations();
        verify(writer, never()).storeObservations(any(), any(), any());
    }
}
```

- [ ] **Step 2: Run to verify fail, implement, verify pass**

- [ ] **Step 3: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/stigmergy/llm/ObservationHandlerContext.java
git add agency/src/test/java/io/casehub/agency/stigmergy/llm/ObservationHandlerContextTest.java
git commit -m "feat(#284): add ObservationHandlerContext with accumulated observation storage"
```

### Task 5: ObservationDispatcher with topological sort and handler precedence

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/ObservationDispatcher.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/EnvironmentWriteBackHandlers.java`
- Test: `agency/src/test/java/io/casehub/agency/stigmergy/llm/ObservationDispatcherTest.java`

**Interfaces:**
- Consumes: `ObservationHandler` (Task 1), `ObservationHandlerContext` (Task 4), `LlmObservationResult` (Task 1), `LlmObservation` (Task 1), `Observation` (engine-api)
- Produces: `ObservationDispatcher` — `Builder.on(String, ObservationHandler)`, `Builder.onDerived(ObservationHandler)`, `Builder.onUnhandled(ObservationHandler)`, `Builder.build()`, `dispatch(LlmObservationResult, ObservationHandlerContext)`
- Produces: `EnvironmentWriteBackHandlers` — `depositSignal(String)`, `storeObservation()`, `all()`

- [ ] **Step 1: Write failing tests for topological sort and handler precedence**

```java
package io.casehub.agency.stigmergy.llm;

import io.casehub.api.spi.observation.Observation;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservationDispatcherTest {

    EnvironmentWriter writer = mock(EnvironmentWriter.class);
    UUID caseId = UUID.randomUUID();

    LlmObservation rawLeaf = new LlmObservation("leaf", 0.9,
        List.of("key1"), List.of(), Map.of(), "leaf obs");
    LlmObservation rawDerived = new LlmObservation("derived", 0.85,
        List.of("key2"), List.of("leaf"), Map.of(), "derived obs");

    Observation engineLeaf = new Observation("leaf", 0.9, Map.of(), Instant.now());
    Observation engineDerived = new Observation("derived", 0.85, Map.of(), Instant.now());

    LlmObservationResult result = new LlmObservationResult(
        List.of(engineDerived, engineLeaf),
        List.of(rawDerived, rawLeaf),
        0.87, "reasoning", Instant.now(), Instant.now(), 3,
        StructuredAgentInvoker.InvocationMetadata.EMPTY);

    @Test
    void dispatch_respectsDerivedFromOrder_leavesFirst() {
        List<String> order = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .on("leaf", (obs, raw, ctx) -> order.add("leaf"))
            .on("derived", (obs, raw, ctx) -> order.add("derived"))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, result);
        dispatcher.dispatch(result, ctx);
        assertThat(order).containsExactly("leaf", "derived");
    }

    @Test
    void dispatch_specificHandler_takePrecedenceOverOnDerived() {
        List<String> hits = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .on("derived", (obs, raw, ctx) -> hits.add("specific"))
            .onDerived((obs, raw, ctx) -> hits.add("generic-derived"))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, result);
        dispatcher.dispatch(result, ctx);
        assertThat(hits).contains("specific");
        assertThat(hits).doesNotContain("generic-derived");
    }

    @Test
    void dispatch_unhandled_logsAndStores() {
        List<String> unhandled = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .onUnhandled((obs, raw, ctx) -> unhandled.add(obs.patternId()))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, result);
        dispatcher.dispatch(result, ctx);
        assertThat(unhandled).containsExactlyInAnyOrder("leaf", "derived");
    }

    @Test
    void dispatch_flushesObservationsAfterAllHandlers() {
        var dispatcher = ObservationDispatcher.builder()
            .on("leaf", (obs, raw, ctx) -> ctx.storeObservation(obs))
            .on("derived", (obs, raw, ctx) -> ctx.storeObservation(obs))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, result);
        dispatcher.dispatch(result, ctx);
        verify(writer).storeObservations(eq(caseId), eq("a1"),
            argThat(list -> list.size() == 2));
    }

    @Test
    void dispatch_handlesCycleGracefully() {
        var rawA = new LlmObservation("A", 0.9,
            List.of(), List.of("B"), Map.of(), "");
        var rawB = new LlmObservation("B", 0.9,
            List.of(), List.of("A"), Map.of(), "");
        var engA = new Observation("A", 0.9, Map.of(), Instant.now());
        var engB = new Observation("B", 0.9, Map.of(), Instant.now());
        var cycleResult = new LlmObservationResult(
            List.of(engA, engB), List.of(rawA, rawB),
            0.9, "", Instant.now(), Instant.now(), 1,
            StructuredAgentInvoker.InvocationMetadata.EMPTY);
        List<String> order = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .on("A", (obs, raw, ctx) -> order.add("A"))
            .on("B", (obs, raw, ctx) -> order.add("B"))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, cycleResult);
        dispatcher.dispatch(cycleResult, ctx);
        assertThat(order).containsExactlyInAnyOrder("A", "B");
    }
}
```

- [ ] **Step 2: Run to verify fail**

- [ ] **Step 3: Implement ObservationDispatcher**

Key logic:
- `dispatch()`: build adjacency map from `rawObservations` `derivedFrom`, topological sort (Kahn's algorithm), detect cycles → log ERROR + fall back to batch order for cycled nodes, iterate sorted list matching handlers by precedence (on > onDerived > onUnhandled), call `ctx.flushObservations()` at end
- Default `onUnhandled`: log WARNING + `ctx.storeObservation(obs)`

- [ ] **Step 4: Implement EnvironmentWriteBackHandlers**

Three static factory methods returning `ObservationHandler` lambdas:
- `depositSignal(prefix)`: `(obs, raw, ctx) -> ctx.depositSignal(prefix + ":" + obs.patternId(), obs.confidence())`
- `storeObservation()`: `(obs, raw, ctx) -> ctx.storeObservation(obs)`
- `all()`: combines both

- [ ] **Step 5: Run tests to verify pass**

- [ ] **Step 6: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/stigmergy/llm/ObservationDispatcher.java
git add agency-core/src/main/java/io/casehub/agency/stigmergy/llm/EnvironmentWriteBackHandlers.java
git add agency/src/test/java/io/casehub/agency/stigmergy/llm/ObservationDispatcherTest.java
git commit -m "feat(#284): add ObservationDispatcher with topological sort, handler precedence, cycle detection"
```

---

## Batch 4: LLM Templates — Conversion + LlmSummarisers + Integration

### Task 6: LLM-to-engine conversion and LlmSummarisers.observation() template

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/LlmResponseConverter.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/LlmSummarisers.java`
- Create: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/InterpretationBuilder.java`
- Test: `agency/src/test/java/io/casehub/agency/stigmergy/llm/LlmResponseConverterTest.java`
- Test: `agency/src/test/java/io/casehub/agency/stigmergy/llm/LlmSummarisersTest.java`

**Interfaces:**
- Consumes: `LlmObservation`, `LlmObservationResponse`, `LlmRecommendation`, `Observation`, `RuleAction`, `RecommendedAction`, `LlmInvocationPolicy`, `StructuredAgentInvoker` (agency-core invoke), `AgentProvider` (platform-agent-api), `ObservationRenderer<E>` (agency-core perception), `ContentSummariser<E, R>` (summarisation-api)
- Produces: `LlmResponseConverter.toObservations(LlmObservationResponse) → List<Observation>`
- Produces: `LlmResponseConverter.toRecommendedActions(LlmActionResponse) → List<RecommendedAction>`
- Produces: `LlmSummarisers.observation(AgentProvider, String, LlmInvocationPolicy, ObservationRenderer<E>, ExecutorService) → ContentSummariser<E, LlmObservationResult>`
- Produces: `LlmSummarisers.combined(AgentProvider, String, LlmInvocationPolicy, ObservationRenderer<E>, ExecutorService) → ContentSummariser<E, InterpretationResult>`
- Produces: `LlmSummarisers.action(AgentProvider, String, LlmInvocationPolicy, Supplier<String>, ExecutorService) → ContentSummariser<LlmObservationResult, ActionResult>`

- [ ] **Step 1: Write failing tests for LlmResponseConverter**

```java
package io.casehub.agency.stigmergy.llm;

import io.casehub.api.spi.observation.Observation;
import io.casehub.api.spi.observation.RuleAction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LlmResponseConverterTest {

    @Test
    void toObservations_convertsPatternIdAndConfidence() {
        var raw = new LlmObservation("risk-spike", 0.92,
            List.of("risk.score"), List.of(), Map.of("delta", 0.37), "jumped");
        var result = LlmResponseConverter.toObservations(
            new LlmObservationResponse(List.of(raw)));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).patternId()).isEqualTo("risk-spike");
        assertThat(result.get(0).confidence()).isEqualTo(0.92);
    }

    @Test
    void toObservations_packsEvidenceAndDerivedFromIntoDetails() {
        var raw = new LlmObservation("compound", 0.88,
            List.of("tx.amount"), List.of("risk-spike"),
            Map.of("threshold", "regulatory"), "compound pattern");
        var result = LlmResponseConverter.toObservations(
            new LlmObservationResponse(List.of(raw)));
        var details = result.get(0).details();
        assertThat(details).containsKey("evidence");
        assertThat(details).containsKey("derivedFrom");
        assertThat(details).containsKey("reasoning");
    }

    @Test
    void toRecommendedActions_convertsDepositSignal() {
        var rec = new LlmRecommendation("DepositSignal",
            Map.of("name", "alert", "strength", 0.9),
            List.of("risk-spike"), 0.85, "alert needed");
        var result = LlmResponseConverter.toRecommendedActions(
            new LlmActionResponse(List.of(rec)));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isInstanceOf(RuleAction.DepositSignal.class);
        var ds = (RuleAction.DepositSignal) result.get(0).action();
        assertThat(ds.name()).isEqualTo("alert");
        assertThat(ds.strength()).isEqualTo(0.9);
    }

    @Test
    void toRecommendedActions_dropsUnknownActionType() {
        var rec = new LlmRecommendation("FlyToMoon",
            Map.of(), List.of(), 0.5, "invalid");
        var result = LlmResponseConverter.toRecommendedActions(
            new LlmActionResponse(List.of(rec)));
        assertThat(result).isEmpty();
    }

    @Test
    void toRecommendedActions_dropsMissingRequiredParams() {
        var rec = new LlmRecommendation("DepositSignal",
            Map.of(), List.of(), 0.5, "missing name");
        var result = LlmResponseConverter.toRecommendedActions(
            new LlmActionResponse(List.of(rec)));
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Write failing tests for LlmSummarisers.observation()**

```java
package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.perception.observation.ObservationContext;
import io.casehub.agency.perception.observation.ObservationRenderer;
import io.casehub.agency.perception.observation.ObservationResult;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LlmSummarisersTest {

    @Test
    void observation_producesLlmObservationResult() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        var json = """
            {"observations":[{"patternId":"test","confidence":0.9,"evidence":[],"derivedFrom":[],"details":{},"reasoning":"test"}]}
            """;
        when(agentProvider.invoke(any())).thenReturn(
            io.smallrye.mutiny.Multi.createFrom().item(new AgentEvent.TextDelta(json)));

        ObservationRenderer<String> renderer = (events, obsCtx) ->
            CompletableFuture.completedFuture(
                ObservationResult.empty(0));

        var summariser = LlmSummarisers.observation(
            agentProvider,
            "Test system prompt",
            LlmInvocationPolicy.of(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30)),
            renderer,
            Executors.newVirtualThreadPerTaskExecutor());

        var result = summariser.summarise(List.of("event1"), null)
            .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result).isNotNull();
        assertThat(result.observations()).hasSize(1);
        assertThat(result.observations().get(0).patternId()).isEqualTo("test");
        assertThat(result.rawObservations()).hasSize(1);
    }
}
```

- [ ] **Step 3: Run to verify fail, implement all three files, verify pass**

Key implementation details for `LlmSummarisers.observation()`:
1. Returns a `ContentSummariser<E, LlmObservationResult>` lambda
2. Inside: renders batch via `ObservationRenderer`, builds system+user prompt, calls `CompletableFuture.supplyAsync(() -> StructuredAgentInvoker.invoke(agentProvider, config, LlmObservationResponse.class), llmExecutor)`
3. On success: converts via `LlmResponseConverter`, wraps in `LlmObservationResult` with batch metadata
4. On parse/agent error: logs, returns empty result

- [ ] **Step 4: Run full test suite**

Run: `mvn test -pl agency --batch-mode`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/stigmergy/llm/LlmResponseConverter.java
git add agency-core/src/main/java/io/casehub/agency/stigmergy/llm/LlmSummarisers.java
git add agency-core/src/main/java/io/casehub/agency/stigmergy/llm/InterpretationBuilder.java
git add agency/src/test/java/io/casehub/agency/stigmergy/llm/LlmResponseConverterTest.java
git add agency/src/test/java/io/casehub/agency/stigmergy/llm/LlmSummarisersTest.java
git commit -m "feat(#284): add LlmResponseConverter, LlmSummarisers templates, and InterpretationBuilder"
```

### Task 7: End-to-end integration test

**Files:**
- Test: `agency/src/test/java/io/casehub/agency/stigmergy/llm/LlmInterpretationIntegrationTest.java`

**Interfaces:**
- Consumes: All types from Tasks 1-6

- [ ] **Step 1: Write end-to-end test**

Full pipeline: `SummarisationRunner` → `InFlightGate` → `ChangeGatedEmissionPolicy` → `LlmSummarisers.observation()` → `EventStreamBus` → `ObservationDispatcher` → handlers → `EnvironmentWriter` mock. Verify:
- Events accumulate in runner
- Tick triggers LLM invocation (mock AgentProvider)
- Observations route to correct handlers by patternId
- derivedFrom ordering respected
- Handlers call EnvironmentWriter correctly
- Batch flush writes all stored observations

- [ ] **Step 2: Run to verify pass**

Run: `mvn test -pl agency -Dtest=LlmInterpretationIntegrationTest --batch-mode`

- [ ] **Step 3: Run full build**

Run: `mvn --batch-mode install`
Expected: All modules compile and all tests pass

- [ ] **Step 4: Commit**

```bash
git add agency/src/test/java/io/casehub/agency/stigmergy/llm/LlmInterpretationIntegrationTest.java
git commit -m "feat(#284): add end-to-end integration test for LLM interpretation pipeline"
```

---

## References

- [2026-10-25-llm-observation-local-rules-design.md] — design spec this plan implements
- [agency-core/src/main/java/io/casehub/agency/core/invoke/StructuredAgentInvoker.java] — LLM invocation utility
- [summarisation-api/src/main/java/io/casehub/agency/summarisation/EmissionPolicy.java] — emission gating SPI
- [summarisation-api/src/main/java/io/casehub/agency/summarisation/ContentSummariser.java] — processing SPI
- [summarisation-api/src/main/java/io/casehub/agency/summarisation/SummarisationRunner.java] — pipeline runner
- [agency-core/src/main/java/io/casehub/agency/perception/observation/ObservationRenderer.java] — prompt rendering SPI
- [engine api/src/main/java/io/casehub/api/spi/observation/Observation.java] — engine observation record
- [engine api/src/main/java/io/casehub/api/spi/observation/RuleAction.java] — engine action sealed interface
- [GitHub #284] — focal issue
- [GitHub engine#1104] — parent Hive Mind epic
