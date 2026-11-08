# LLM-Driven Observation & Local Rules — Design Spec

**Issue:** casehubio/agency#284
**Parent epic:** casehubio/engine#1104 (Hive Mind)
**Date:** 2026-10-25

## Summary

LLM-backed interpretation layer for the engine's stigmergy model. Agents use LLM reasoning to interpret full environment state and group events into semantic patterns — replacing static pattern matching with semantic understanding. Built as compositions of existing summarisation primitives, not a new framework. The LLM observes and groups; caller-registered handlers decide what to do with each pattern type.

## Relationship to Issue #284

Issue #284 specifies `LlmObserver` implementing `EnvironmentObserver` and `LlmRuleEvaluator` implementing `LocalRuleSet`. This spec deliberately diverges from that approach:

**`EnvironmentObserver.observe()` has a hardcoded 100ms timeout.** The engine's `CaseContextChangedEventHandler.observations()` wraps each observer call in `CompletableFuture.supplyAsync(...).orTimeout(100, MILLISECONDS).join()`. LLM calls take 30s–60min. An `LlmObserver` implementing `EnvironmentObserver` would timeout on every invocation — this is not configurable, the timeout is hardcoded in engine runtime.

**Deliverable mapping:**

| Issue #284 deliverable | This spec's equivalent |
|---|---|
| `LlmObserver` implementing `EnvironmentObserver` | `LlmSummarisers.observation()` — same interpretation function, event-driven instead of synchronous SPI |
| `LlmRuleEvaluator` implementing `LocalRuleSet` | Handler dispatch via `ObservationDispatcher` — caller-registered handlers react to observation patterns, deposit signals, write context |
| Context window management | `ObservationRenderer<E>` + `Compactor` — existing infrastructure reused for prompt assembly and deduplication |
| Observation caching | `InFlightGate` + `EventAccumulator` — emission suppressed during LLM processing, events stay in accumulator |
| Prompt templates | `LlmSummarisers` factory methods — observation, action, combined templates |

**Transparent fallback:** When the LLM is unavailable, `InFlightGate` suppresses emission and the `EventAccumulator` continues collecting events. No events are lost — they remain in the accumulator and are included in the next batch when the LLM becomes available. The engine's deterministic observers and local rules continue operating independently on every tick.

## Architecture

The LLM layer sits **alongside** the engine's synchronous tick loop, not inside it. The engine's `EnvironmentObserver` SPI is designed for bounded-latency deterministic observers (100ms timeout). LLM calls have unbounded latency (30s to 60min). The event-driven model avoids blocking the engine's sequential evaluation pipeline (`observations()` → `localRules()` → `convergenceDetection()`), which would stall the entire case for the duration of the LLM call.

The default composition mirrors the engine's two-phase pipeline:

1. **Observation phase** — accumulate context changes, send batch to LLM, produce `List<Observation>` with evidence and derivedFrom hierarchy
2. **Handler dispatch** — route each observation to caller-registered handlers by patternId, respecting derivedFrom ordering (leaves before derived)
3. **Write-back** — handlers deposit signals, write context, store observations — triggering a new engine tick

### Async Execution Model

LLM invocation is truly async via `CompletableFuture.supplyAsync()` on a caller-supplied `ExecutorService`. The `ContentSummariser.summarise()` returns an immediately-pending `CompletionStage` — the calling thread is never blocked.

`SummarisationRunner.tick()` is `synchronized` (prevents concurrent entry) but does NOT block waiting for the `CompletionStage` to complete — it returns `CompletionStage<Void>` immediately. The actual LLM call completes asynchronously on the `llmExecutor` thread, at which point `thenAccept` publishes the result to the `EventStreamBus`. This means handler dispatch (via bus subscribers) runs on the `llmExecutor` thread, not on the tick-calling thread.

**Concurrency control:** The `InFlightGate` coordinates the emission policy and summariser to prevent data loss. `SummarisationRunner.tickWithPolicy()` first checks `shouldEmit()` (which the gate suppresses when in-flight), then drains the accumulator, then invokes the summariser. By preventing emission at the policy level, the gate ensures the accumulator is never drained while a previous LLM call is still running. Events continue accumulating and are included in the next batch after the in-flight call completes.

### Tick-Trigger Mechanism

The engine dispatches a new `CaseContextChangedEvent` (triggering a new tick) when context keys are written via `WritableLayer.set()`. Signal deposits and observation storage do NOT trigger new events — signals and observations are read at the START of the next tick that occurs for other reasons.

To guarantee a tick fires after LLM write-back, the `EnvironmentWriter` implementation must always write a sentinel context key `llm.interpretation.lastWriteBack` (set to current timestamp) after processing all handler actions. This ensures at least one `WriteContext` action occurs, which triggers the engine tick. Without this, write-backs that only deposit signals or store observations would sit idle until something else triggers a tick.

### Thread Safety

Handler dispatch runs on the `llmExecutor` thread. `EventStreamBus` callbacks run synchronously on the thread that calls `publish()` — which is the `llmExecutor` thread (via `thenAccept` after the LLM `CompletionStage` completes). This means:

- `ObservationDispatcher.dispatch()` runs on `llmExecutor`
- `ObservationHandler` callbacks (including `EnvironmentWriter` calls) run on `llmExecutor`
- `EnvironmentWriter.writeContext()` triggers engine tick dispatch from the `llmExecutor` thread

**Requirements:**
- `EnvironmentWriter` implementations must be thread-safe — they are called from `llmExecutor`, not from engine virtual threads
- Handlers must not perform blocking I/O or long-running computation — they share the `llmExecutor` thread with LLM invocations
- The engine's `EventDispatcher` must accept events from non-engine threads — verified: `CaseContextChangedEventHandler` uses `evaluationSerializer.submit()` which queues work regardless of calling thread

```
Context Changes (engine events)
    │
    ├──→ Engine Tick Loop (synchronous, bounded, deterministic)
    │    ├── observers (ThresholdObserver, CorrelationObserver, etc.)
    │    ├── localRules (RuleCondition → RuleAction)
    │    └── convergenceDetection
    │
    └──→ LLM Interpretation Layer (async, unbounded, agency-owned)
         │
         ├── Phase 1: Observation
         │    ├── EventAccumulator (buffer context changes)
         │    ├── EmissionPolicy (change-gated: only invoke when watched keys changed)
         │    ├── Compactor (dedup redundant changes)
         │    ├── ContentSummariser template (LLM → List<Observation>)
         │    │   └── via CompletableFuture.supplyAsync(invokeText, llmExecutor)
         │    ├── InFlightGate (emission suppressed while previous call in-flight)
         │    └── EventStreamBus → Handler dispatch
         │
         ├── Phase 2: Handler dispatch (pattern-matched)
         │    ├── ObservationDispatcher routes by patternId
         │    ├── derivedFrom ordering (leaves before derived)
         │    ├── Caller-registered handlers per pattern type
         │    └── Handlers: deposit signals, write context, escalate, etc.
         │              │
         │              ▼ EnvironmentWriter SPI (provided by glue module)
         │
         └── sentinel context write ───► engine tick fires ───► deterministic rules react
```

## Core Principle: Composition via Builder Templates

No new runner types. The LLM interpretation layer is a **named configuration** of existing summarisation primitives. `LlmSummarisers` provides factory methods that produce pre-configured `ContentSummariser` implementations. These plug into the existing `SummarisationRunner` via `asSummariser()`.

End users can:
- Use named templates for common use cases (observation, action, combined, narration)
- Compose custom configurations from the same builder for novel use cases
- Mix LLM-backed and deterministic summarisers in the same pipeline

## New Types

### `LlmObservationResult` (record)

The output of an LLM observation phase. Carries detected patterns and batch correlation metadata.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record LlmObservationResult(
    List<Observation> observations,
    List<LlmObservation> rawObservations,
    double confidence,
    String reasoning,
    Instant batchStart,
    Instant batchEnd,
    int batchEventCount,
    StructuredAgentInvoker.InvocationMetadata invocationMetadata
) {}
```

Named `LlmObservationResult` to avoid collision with `io.casehub.agency.perception.observation.ObservationResult` — the existing type returned by `ObservationRenderer<E>.render()`. Both types appear in the same call chain (prompt assembly uses `ObservationRenderer`, which returns the perception `ObservationResult`; the template then produces `LlmObservationResult`).

The `rawObservations` list parallels `observations` at the same indices — the dispatcher uses `rawObservations` for topological sort (derivedFrom) and passes both the engine `Observation` and raw `LlmObservation` to each handler.

### `RecommendedAction` (record)

Wraps an engine `RuleAction` with LLM attribution — which observations justify this action.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record RecommendedAction(
    RuleAction action,
    List<String> justifyingPatternIds,
    double confidence
) {}
```

### `ActionResult` (record)

The output of an LLM action phase. Carries recommended actions with per-action attribution and batch correlation metadata.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record ActionResult(
    List<RecommendedAction> recommendedActions,
    double confidence,
    String reasoning,
    StructuredAgentInvoker.InvocationMetadata invocationMetadata
) {}
```

### `InterpretationResult` (record)

The output of a combined LLM call (observation + action in one pass). Used by the `combined()` template when callers explicitly opt into coupling.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record InterpretationResult(
    List<Observation> observations,
    List<RecommendedAction> recommendedActions,
    double confidence,
    String reasoning,
    Instant batchStart,
    Instant batchEnd,
    int batchEventCount,
    StructuredAgentInvoker.InvocationMetadata invocationMetadata
) {}
```

### `LlmInvocationPolicy` (record)

Caller-supplied latency expectations. Only the caller who builds the prompt knows the expected complexity.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record LlmInvocationPolicy(
    Duration expectedDuration,
    Duration warnAfter,
    Duration errorAfter
) {
    public static LlmInvocationPolicy of(
            Duration expected, Duration warn, Duration error) {
        return new LlmInvocationPolicy(expected, warn, error);
    }
}
```

The summariser template monitors actual invocation duration against these thresholds. Logs warnings and cancels/errors at the caller's specified limits.

### `LlmSummarisers` (factory class)

Named templates that produce pre-configured `ContentSummariser` implementations.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public final class LlmSummarisers {

    // Phase 1: Observation — interpret environment, produce Observation records
    public static <E> ContentSummariser<E, LlmObservationResult> observation(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy,
            ObservationRenderer<E> promptRenderer,
            ExecutorService llmExecutor) { ... }

    // Phase 2: Action — read observations + state, produce RuleAction recommendations
    public static ContentSummariser<LlmObservationResult, ActionResult> action(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy,
            Supplier<String> environmentSnapshot,
            ExecutorService llmExecutor) { ... }

    // Combined: observation + action in one call (explicit opt-in)
    public static <E> ContentSummariser<E, InterpretationResult> combined(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy,
            ObservationRenderer<E> promptRenderer,
            ExecutorService llmExecutor) { ... }

    // Builder for custom configurations
    public static <E, R> InterpretationBuilder<E, R> builder(AgentProvider agentProvider) { ... }
}
```

Each template internally:
1. Renders the batch into a prompt using the observation pipeline infrastructure (`ObservationRenderer<E>`, `AffordanceRenderer`, `CognitiveObservationSections`) — NOT a parallel rendering path
2. Prepends the system prompt with phase-specific instructions
3. If previous result exists, includes it for continuity
4. Invokes the LLM via `CompletableFuture.supplyAsync(() -> StructuredAgentInvoker.invoke(...), llmExecutor)` — returns immediately-pending `CompletionStage`
5. Monitors invocation duration against `LlmInvocationPolicy`
6. Wraps the response with batch correlation metadata

The `action()` template's `Supplier<String> environmentSnapshot` is called at invocation time and its output is included in the action prompt alongside the observation items. The caller provides a supplier that reads the current `CaseContext` and renders it as a prompt section.

### `InterpretationBuilder<E, R>` (builder)

For custom compositions beyond the named templates.

```java
public static class InterpretationBuilder<E, R> {
    InterpretationBuilder<E, R> systemPrompt(String prompt);
    InterpretationBuilder<E, R> invocationPolicy(LlmInvocationPolicy policy);
    InterpretationBuilder<E, R> promptRenderer(ObservationRenderer<E> renderer);
    InterpretationBuilder<E, R> model(String modelId);
    InterpretationBuilder<E, R> watchedKeys(Set<String> keys);
    InterpretationBuilder<E, R> llmExecutor(ExecutorService executor);
    ContentSummariser<E, R> build();
}
```

### `EnvironmentWriter` (SPI)

Write-back interface for depositing LLM results into the engine environment. The agency module defines this SPI; the engine runtime or glue module provides the implementation using internal types (`ObservationRegistry`, `SignalRegistry`).

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public interface EnvironmentWriter {
    void storeObservations(UUID caseId, String agentId, List<Observation> observations);
    void depositSignal(UUID caseId, String name, double strength,
                       Duration halfLife, String agentId);
    void writeContext(UUID caseId, String key, JsonNode value);
}
```

**Batch semantics for `storeObservations()`:** The engine's `ObservationRegistry.storeObservations()` is batch-replace — each call replaces the entire observation list for `(caseId, agentId)` via `.put(agentId, List.copyOf(obs))`. The `ObservationDispatcher` accumulates observations across all handler calls and makes a single `storeObservations()` call after dispatch completes (see §ObservationDispatcher). Signal deposits and context writes are fire-immediately — they don't have batch-replace semantics.

This SPI decouples the agency module from engine-internal types. `ObservationRegistry` (`io.casehub.engine.common.internal.observation`) and `SignalRegistry` (`io.casehub.engine.common.internal.signal`) are in `engine-common-core`'s `internal` packages — agency-core depends on `engine-api`, not `engine-common-core`. The implementation handles the sentinel context write (`llm.interpretation.lastWriteBack`) to guarantee tick triggering.

### `ObservationHandler` (functional interface)

Handler for a specific observation pattern type. The caller registers handlers keyed to the observation vocabulary defined in the system prompt.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
@FunctionalInterface
public interface ObservationHandler {
    void handle(Observation observation, LlmObservation raw, ObservationHandlerContext ctx);
}
```

### `ObservationHandlerContext`

Context provided to observation handlers — gives access to environment write-back operations via the `EnvironmentWriter` SPI. Signal deposits and context writes fire immediately. Observation storage is accumulated — `storeObservation()` adds to a pending list that the `ObservationDispatcher` flushes as a single batch after all handlers complete (matching the engine's batch-replace API).

```java
public class ObservationHandlerContext {

    private final UUID caseId;
    private final String agentId;
    private final String tenancyId;
    private final EnvironmentWriter writer;
    private final LlmObservationResult batchResult;
    private final List<Observation> pendingObservations = new ArrayList<>();

    public ObservationHandlerContext(UUID caseId, String agentId, String tenancyId,
                                     EnvironmentWriter writer, LlmObservationResult batchResult) {
        // ...
    }

    public void depositSignal(String name, double strength) {
        writer.depositSignal(caseId, name, strength, null, agentId);
    }
    public void depositSignal(String name, double strength, Duration halfLife) {
        writer.depositSignal(caseId, name, strength, halfLife, agentId);
    }
    public void writeContext(String key, JsonNode value) {
        writer.writeContext(caseId, key, value);
    }
    public void storeObservation(Observation observation) {
        pendingObservations.add(observation);
    }

    // Package-private — called by ObservationDispatcher after all handlers complete
    void flushObservations() {
        if (!pendingObservations.isEmpty()) {
            writer.storeObservations(caseId, agentId, List.copyOf(pendingObservations));
        }
    }

    public UUID caseId() { return caseId; }
    public String agentId() { return agentId; }
    public LlmObservationResult batchResult() { return batchResult; }
}
```

### `ObservationDispatcher` (handler routing)

Routes observations to pattern-matched handlers. The prompt vocabulary and handler registration form a closed contract.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public class ObservationDispatcher {

    public static Builder builder() { return new Builder(); }

    public void dispatch(LlmObservationResult result, ObservationHandlerContext ctx) {
        // 1. Topological sort on derivedFrom DAG
        // 2. For each observation (leaves first), match handler by precedence:
        //    on(patternId) > onDerived() > onUnhandled()
        // 3. Invoke matched handler
        // 4. After all handlers: ctx.flushObservations() — single batch write
    }

    public static class Builder {
        Builder on(String patternId, ObservationHandler handler);
        Builder onDerived(ObservationHandler handler);  // fallback for derived obs without specific on() match
        Builder onUnhandled(ObservationHandler handler);
        ObservationDispatcher build();
    }
}
```

The dispatcher respects the `derivedFrom` hierarchy — leaf observations are dispatched before derived observations, so a handler for "compound-risk" can assume "velocity-anomaly" has already been processed.

**Handler precedence:** For each observation, exactly one handler fires. Precedence order:
1. `on(patternId)` — specific match by patternId (most specific)
2. `onDerived()` — any observation with non-empty `derivedFrom` that did NOT match a specific `on()` handler
3. `onUnhandled()` — any observation that matched neither `on()` nor `onDerived()`

An observation like `"high-value-high-risk"` with `derivedFrom: ["risk-spike"]` that has a specific `on("high-value-high-risk", ...)` handler will fire only that handler — `onDerived()` will NOT fire for it.

**Post-dispatch flush:** After all handlers complete, the dispatcher calls `ctx.flushObservations()` to batch-write all pending observations via `EnvironmentWriter.storeObservations()`. This matches the engine's batch-replace semantics — `ObservationRegistry.storeObservations()` replaces the entire observation list for `(caseId, agentId)` on each call.

**derivedFrom ordering:** Topological sort on the observation DAG:
- External references (patternIds not in the current batch) → treated as satisfied (produced in a previous batch)
- Missing references (patternIds that don't exist anywhere) → treated as satisfied, logged at WARNING
- Self-references (observation's own patternId in its derivedFrom) → ignored, logged at WARNING
- Cycles (A derivedFrom B, B derivedFrom A) → detected via cycle check, logged at ERROR; cycled observations dispatched in batch order

**Default unhandled behavior:** If `onUnhandled` is not registered, unhandled observations are logged at WARNING and stored via `ctx.storeObservation()`. The default ensures no LLM finding is silently dropped.

**Error handling in templates:** `StructuredAgentInvoker.invoke()` returns sealed `InvocationResult<T>` — `Success`, `ParseError`, or `AgentError`. Templates handle:
- `Success` → validate each observation individually, filter invalid (missing patternId or confidence), log WARNING per filtered observation, produce result with only valid observations
- `ParseError` → log WARNING with raw response text, return empty result (previous result preserved via StatefulSummariser continuity)
- `AgentError` → log ERROR, return empty result, `LlmInvocationPolicy` tracks the failure

**Evidence model acknowledgment:** `evidence` entries are free-form LLM citations from a rendered prompt. There is no structural validation that citations reference actual input data — the LLM cites what it chooses. Handlers should treat evidence as informational context, not as verified provenance.

### `EnvironmentWriteBackHandlers` (pre-built handlers)

Pre-built `ObservationHandler` implementations for common write-back patterns.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public final class EnvironmentWriteBackHandlers {

    public static ObservationHandler depositSignal(String signalNamePrefix) { ... }
    public static ObservationHandler storeObservation() { ... }
    public static ObservationHandler all() { ... }  // store + deposit
}
```

### `ChangeGatedEmissionPolicy<E>` (EmissionPolicy implementation)

Custom `EmissionPolicy` that gates LLM invocation on watched-key changes. Composes with `WindowPolicy`-based time/count gating.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public class ChangeGatedEmissionPolicy<E> implements EmissionPolicy<E, Object> {

    public ChangeGatedEmissionPolicy(
            Set<String> watchedKeys,
            Function<LevelEvent<E>, Set<String>> changedKeyExtractor,
            EmissionPolicy<E, ?> delegate) { ... }

    @Override
    public boolean shouldEmit(List<LevelEvent<E>> buffer, Object state, long now) {
        // Check delegate (time/count window) AND watched-key intersection
    }
}
```

### `InFlightGate<E, R>` (concurrency gate)

Coordinates the emission policy and summariser to prevent data loss during async LLM invocation. Provides two views: a gating emission policy (suppresses emission while in-flight) and a tracking summariser (records in-flight status when invoked).

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public class InFlightGate<E, R> {

    private final ContentSummariser<E, R> delegate;
    private volatile CompletionStage<?> inflight;

    public InFlightGate(ContentSummariser<E, R> delegate) {
        this.delegate = delegate;
    }

    /** Returns a summariser that tracks in-flight status. */
    public ContentSummariser<E, R> summariser() {
        return (items, previous) -> {
            var stage = delegate.summarise(items, previous);
            inflight = stage;
            return stage;
        };
    }

    /** Returns an emission policy that suppresses emission while in-flight. */
    public <S> EmissionPolicy<E, S> gating(EmissionPolicy<E, S> policy) {
        return (buffer, state, now) -> {
            if (inflight != null && !inflight.toCompletableFuture().isDone()) return false;
            return policy.shouldEmit(buffer, state, now);
        };
    }
}
```

**Why not a ContentSummariser wrapper?** `SummarisationRunner.tickWithPolicy()` drains the `EventAccumulator` BEFORE invoking the summariser. A `ContentSummariser` wrapper that skips invocation (returning the previous result) would discard the drained events — they cannot be returned to the accumulator. By gating at the emission policy level, the drain never happens while a previous call is in-flight.

**Thread safety:** `inflight` is `volatile` — the emission policy (called from the tick thread) and summariser (completing on `llmExecutor`) see consistent state. The gate does not need synchronization because the runner's `tick()` is already `synchronized`.

## Structured JSON Output & Conversion

The LLM produces structured JSON conforming to LLM-facing response records. Templates convert these into engine-facing records. This decouples the prompt schema from engine internals and enables per-item attribution.

### LLM-Facing Records (what the LLM produces)

**Package:** `io.casehub.agency.stigmergy.llm`

```java
// Observation phase JSON output
record LlmObservationResponse(List<LlmObservation> observations) {}

record LlmObservation(
    String patternId,
    double confidence,
    List<String> evidence,
    List<String> derivedFrom,
    Map<String, Object> details,
    String reasoning
) {}

// Action phase JSON output
record LlmActionResponse(List<LlmRecommendation> actions) {}

record LlmRecommendation(
    String actionType,                 // "DepositSignal", "WriteContext", etc.
    Map<String, Object> parameters,    // action-type-specific params
    List<String> justifyingPatternIds, // which observations justify this
    double confidence,
    String reasoning
) {}
```

### Conversion: LLM Records → Engine Records

Templates handle this conversion internally:

**`LlmObservation` → `Observation`** (engine record):
- `patternId` → `Observation.patternId()`
- `confidence` → `Observation.confidence()`
- `evidence` + `derivedFrom` + `details` + `reasoning` → `Observation.details()` (serialized as `Map<String, JsonNode>`)
- `Instant.now()` → `Observation.timestamp()`
- `derivedFrom` enables hierarchical observations: leaf observations have empty `derivedFrom`, derived observations reference other `patternId`s they build on

**`LlmRecommendation` → `RecommendedAction`**:
- `actionType` + `parameters` → `RuleAction` variant (e.g., `actionType: "DepositSignal"`, `parameters: {name: "anomaly", strength: 0.85}` → `new RuleAction.DepositSignal("anomaly", 0.85, null)`)
- `justifyingPatternIds` → `RecommendedAction.justifyingPatternIds()`
- `confidence` → `RecommendedAction.confidence()`

The conversion from `actionType` + `parameters` to the correct `RuleAction` sealed variant uses a switch on `actionType`:
- `"DepositSignal"` → `new RuleAction.DepositSignal(name, strength, halfLife)`
- `"WriteContext"` → `new RuleAction.WriteContext(key, value)`
- `"RegisterInterest"` → `new RuleAction.RegisterInterest(declaration)`
- `"DeregisterInterest"` → `new RuleAction.DeregisterInterest(interestId)`
- `"Leave"` → `new RuleAction.Leave()`

**Conversion error handling:** Individual conversion failures do not fail the batch:
- Unknown `actionType` → drop the action, log WARNING with the raw `LlmRecommendation`
- Missing required parameters (e.g., `DepositSignal` without `name`) → drop the action, log WARNING
- Wrong parameter types (e.g., `strength` is a string) → attempt coercion (string → double), drop with WARNING if coercion fails
- The `ActionResult` includes only successfully converted actions. The `reasoning` field preserves the LLM's intent for debugging dropped actions.

### Prompt Assembly & Key Identification

Context changes are presented to the LLM with identifiable structure so the LLM can cite them as evidence. The `Compactor` ensures each key appears at most once per batch (latest value wins), keeping the prompt focused:

```
Environment state (5 keys changed since last evaluation):
  risk.score: 0.82 (was: 0.45)
  transaction.amount: 150000
  transaction.currency: "USD"
  counterparty.jurisdiction: "high-risk"
  account.balance: 3200000

Active signals:
  velocity-alert: strength 0.7 (deposited 30s ago)
```

The LLM cites what it based each observation on. `evidence` is free-form — could be key names, event descriptions, or quotes from the prompt. `derivedFrom` gives structural hierarchy between observations:
```json
{
  "observations": [
    {
      "patternId": "risk-spike",
      "confidence": 0.92,
      "evidence": ["risk.score"],
      "derivedFrom": [],
      "details": {"riskDelta": 0.37, "previousValue": 0.45},
      "reasoning": "Risk score jumped 82% in a single evaluation cycle"
    },
    {
      "patternId": "high-value-high-risk",
      "confidence": 0.88,
      "evidence": ["transaction.amount", "counterparty.jurisdiction"],
      "derivedFrom": ["risk-spike"],
      "details": {"threshold": "regulatory"},
      "reasoning": "Risk spike coincides with high-value transaction to high-risk jurisdiction"
    }
  ]
}
```

### Attribution Trace

The full causal chain from environment change to deposited signal, with hierarchical observations:

```
context keys (risk.score)
    │  LlmObservation.evidence
    ▼
Observation (patternId: "risk-spike", confidence: 0.92, derivedFrom: [])
    │  LlmObservation.derivedFrom
    ▼
context keys (transaction.amount, counterparty.jurisdiction) + derivedFrom: ["risk-spike"]
    │  LlmObservation.evidence + derivedFrom
    ▼
Observation (patternId: "high-value-high-risk", confidence: 0.88)
    │  ObservationHandler → ObservationHandlerContext
    ▼
Handler deposits signal: DepositSignal("aml:escalate", 0.88)
    │  EnvironmentWriter.writeContext sentinel
    ▼
Engine tick fires → deterministic rules react
```

## Usage Example — Handler-Driven (Default)

```java
// 1. Build the observation summariser
ContentSummariser<CaseContextChangedEvent, LlmObservationResult> observer =
    LlmSummarisers.observation(
        agentProvider,
        "You are analyzing financial transaction patterns. "
            + "Detect these patterns: risk-spike, high-value-high-risk, velocity-anomaly. "
            + "Report as observations with confidence scores and evidence.",
        LlmInvocationPolicy.of(
            Duration.ofSeconds(30),    // expected
            Duration.ofMinutes(1),     // warn
            Duration.ofMinutes(5)),    // error
        transactionObservationRenderer,
        llmExecutor);

// 2. Register handlers — keyed to the vocabulary in the prompt
var dispatcher = ObservationDispatcher.builder()
    .on("risk-spike", (obs, raw, ctx) -> {
        ctx.depositSignal("alert:risk-spike", obs.confidence());
        ctx.storeObservation(obs);
    })
    .on("velocity-anomaly", (obs, raw, ctx) -> {
        ctx.depositSignal("alert:velocity", obs.confidence());
        ctx.storeObservation(obs);
    })
    .on("high-value-high-risk", (obs, raw, ctx) -> {
        ctx.depositSignal("escalate:aml", obs.confidence());
        ctx.storeObservation(obs);
        escalateToHumanReviewer(ctx.caseId(), obs);
    })
    .onUnhandled((obs, raw, ctx) -> {
        log.warnf("Unexpected pattern: %s (confidence: %.2f)", obs.patternId(), obs.confidence());
        ctx.storeObservation(obs);
    })
    .build();

// 3. Wire the pipeline with InFlightGate (prevents drain-before-skip data loss)
var obsBus = new EventStreamBus<LlmObservationResult>();
var gate = new InFlightGate<>(observer);

// Extract changed keys from engine's CaseContextChangedEvent payload
Function<LevelEvent<CaseContextChangedEvent>, Set<String>> keyExtractor =
    e -> contextHistoryBuffer.computeChangedKeys(
        e.payload().instance().getUuid(),
        e.payload().contextSnapshot().layer(ContextLayer.WORKING).asJsonNode());

var runner = SummarisationRunner.builder(
        gate.summariser().asSummariser(), obsBus, OBSERVATION_LEVEL)
    .emissionPolicy(gate.gating(new ChangeGatedEmissionPolicy<>(
        Set.of("transaction.*", "risk.*"),
        keyExtractor,
        EmissionPolicy.window(WindowPolicy.of(5_000, 10)))))
    .compactor(contextChangeCompactor)
    .build();

// 4. Connect dispatcher to bus
//    dispatcher.dispatch() calls each handler, then flushes pending observations
//    as a single batch via EnvironmentWriter.storeObservations()
obsBus.subscribe(e -> true, event -> {
    var handlerCtx = new ObservationHandlerContext(
        caseId, agentId, tenancyId, environmentWriter, event.payload());
    dispatcher.dispatch(event.payload(), handlerCtx);
});

// 5. Feed events and tick (on dedicated thread, NOT the engine tick thread)
runner.collect(new LevelEvent<>(contextChangedEvent, timestamp, INPUT_LEVEL, tenancyId));
runner.tick(now);
```

**Note:** `EmissionPolicy.window(WindowPolicy)` is a new public factory method that wraps `WindowPolicy` into an `EmissionPolicy`. `WindowPolicyEmission` is package-private in `summarisation-api` — this factory provides the public bridge. This is a minor addition to `summarisation-api`, not a change to the existing API shape.

## Relationship to Existing Patterns

| Pattern | Uses | Role |
|---------|------|------|
| `ContentSummariser<T,R>` | Directly implements | The processing SPI — batch + previous → result |
| `.asSummariser()` | Bridge | Converts to `StatefulSummariser` for the runner |
| `SummarisationRunner` | Unchanged | Handles accumulation, windowing, compaction, ticking |
| `EventStreamBus` | Unchanged | Output routing (handler dispatch subscribers) |
| `EventAccumulator` | Via runner | Event buffering |
| `WindowPolicy` / `EmissionPolicy` | Via runner | Emission gating (change-gated for LLM) |
| `Compactor` | Via runner | Pre-LLM deduplication |
| `StructuredAgentInvoker` | Internal to template | LLM invocation with structured output |
| `StructuredAgentInvoker.InvocationMetadata` | Captured | Token usage, cost, duration (nested record) |
| `ObservationRenderer<E>` | Prompt assembly | Existing observation rendering for LLM prompts |
| `AffordanceRenderer` | Prompt assembly | Entity/action grounding chains |
| `CognitiveObservationSections` | Prompt assembly | Goals, beliefs, trust, norms sections |

## What This Does NOT Do

- Does NOT implement `EnvironmentObserver` — event-driven, not synchronous SPI (see §Relationship to Issue #284)
- Does NOT change `SummarisationRunner` — composes existing infrastructure
- Does NOT create a parallel "digest" concept — named templates for `ContentSummariser`
- Does NOT hardcode timeouts — caller supplies `LlmInvocationPolicy`
- Does NOT change the engine — writes back via `EnvironmentWriter` SPI
- Does NOT create parallel rendering infrastructure — integrates with existing observation pipeline
- Does NOT depend on engine-internal types — `EnvironmentWriter` SPI abstracts `ObservationRegistry` / `SignalRegistry`

## Testing Strategy

Plain JUnit 5 with Mockito (no Quarkus runtime in agency tests):

1. **Result record construction and validation** — `LlmObservationResult`, `ActionResult`, `InterpretationResult` semantics
2. **`LlmInvocationPolicy` monitoring** — test warning/error thresholds against mock durations
3. **`LlmSummarisers` templates** — mock `AgentProvider`, verify prompt assembly, response parsing, async invocation on executor
4. **Handler dispatch** — verify `ObservationDispatcher` routes by patternId, respects derivedFrom ordering
5. **Combined template** — verify single-call mode produces both observations and actions
6. **Prompt rendering integration** — verify `ObservationRenderer` / `AffordanceRenderer` output in prompts
7. **`EnvironmentWriter` mock** — verify handlers call correct write-back operations
8. **`InFlightGate`** — verify emission suppressed when previous `CompletionStage` is pending, emission resumes after completion
9. **`ChangeGatedEmissionPolicy`** — verify watched-key intersection gating, no invocation on irrelevant changes
10. **End-to-end composition** — full runner pipeline with mock LLM, verify accumulation → processing → handler dispatch → write-back
11. **Previous result continuity** — verify `StatefulSummariser` state management across ticks
12. **Compactor integration** — verify redundant context changes are removed before LLM invocation
13. **Write-back failure logging** — verify `EnvironmentWriter` failures are logged at WARNING with batch correlation metadata
14. **Sentinel write verification** — verify `EnvironmentWriter` implementation writes `llm.interpretation.lastWriteBack` after all handler actions, ensuring ticks fire even for signal-only or observation-only write-backs
15. **InFlightGate data preservation** — verify events remain in accumulator when gate suppresses emission, and are included in the next batch after in-flight call completes
16. **Conversion error resilience** — verify unknown `actionType`, missing parameters, and wrong parameter types are individually dropped with WARNING logs, not batch failures

## Dependencies

**Compile:** `casehub-agency-summarisation-api` (EventAccumulator, WindowPolicy, EmissionPolicy, ContentSummariser, SummarisationRunner, EventStreamBus, LevelEvent), `casehub-engine-api` (Observation, RuleAction)
**Provided:** `casehub-platform-agent-api` (AgentProvider, AgentSessionConfig)
**Internal:** `StructuredAgentInvoker`, `StructuredAgentInvoker.InvocationMetadata` (agency-core invoke package — `InvocationMetadata` is a nested public record), `ObservationRenderer`, `AffordanceRenderer`, `CognitiveObservationSections` (agency-core perception packages)

**NOT a dependency:** `casehub-engine-common-core` — `ObservationRegistry` and `SignalRegistry` are in `internal` packages. The `EnvironmentWriter` SPI abstracts these; the implementation is provided by the glue module that has access to engine internals.

**New addition to `summarisation-api`:** `EmissionPolicy.window(WindowPolicy)` factory method — wraps `WindowPolicy` into `EmissionPolicy` via the existing package-private `WindowPolicyEmission`.

## References

- engine `CaseContextChangedEventHandler.observations()` — tick loop architecture, 100ms hardcoded timeout, virtual threads
- engine `CaseContextChangedEventHandler.evaluateAndDispatch()` — sequential pipeline: observations → localRules → convergenceDetection
- engine `CaseContextChangedEventHandler.localRules()` — batched `WriteContext` actions dispatch `CaseContextChangedEvent` (tick trigger), `DepositSignal` deposits to `SignalRegistry` without event dispatch
- engine `EnvironmentObserver`, `Observation`, `ObservationContext` — SPIs we integrate with (not implement)
- engine `LocalRule`, `RuleAction`, `RuleSpace` — action model for LLM recommendations
- agency `ContentSummariser<T,R>`, `SummarisationRunner` — composition primitives
- agency `EmissionPolicy<IN,S>` — composable emission gating
- agency `ObservationRenderer<E>`, `AffordanceRenderer`, `CognitiveObservationSections` — prompt assembly
- agency `StructuredAgentInvoker` — LLM invocation utility (synchronous `invokeText()`, wrapped in `supplyAsync` by templates)
- agency `LlmContentSummariser` — existing pattern: synchronous `invokeText()` wrapped in `completedFuture()` (this spec improves on this with `supplyAsync`)
- casehubio/engine#1104 — parent Hive Mind epic
- casehubio/engine#1105 — Environment observation SPI (closed)
- casehubio/engine#1109 — Local rule evaluation (closed)
