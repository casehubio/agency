# Layered Event Summarisation — Design Spec

**Date:** 2026-07-07
**Issues:** #27 (extraction), #38 (domain-agnostic examples)
**Status:** Draft
**Repo:** casehubio/blocks

## Problem

quarkmind built a generic temporal abstraction framework for summarising
high-frequency event streams into progressively higher-level abstractions.
The framework lives in quarkmind under `io.casehub.agency.summarisation`,
explicitly packaged for migration to blocks. It is pure Java — zero CDI,
zero Quarkus dependencies — and has been validated by 3 summarisers and
3 window policies across quarkmind's SC2 domain.

The framework belongs in blocks because it meets two scope criteria: it
involves LLM-driven decision-making (async `Summariser` interface designed
for LLM-backed summarisation at upper levels) and uses classical AI at
lower levels (threshold/pattern detection, windowed accumulation with
configurable policies — the technology split between deterministic
lower layers and pluggable upper layers is the core design).

The extraction is blocked on two API improvements identified during
first-principles review, and on domain-agnostic example tests that prove
portability beyond SC2.

## Background

### Academic foundation

The hierarchical summarisation pattern has deep academic roots:

- **Clinical temporal abstraction** (Shahar, RESUME system, 1997+) — the
  canonical domain, with 30+ years of research on abstracting raw
  time-stamped patient data into episodes, phases, and narratives.
- **Chain of Summarization** (Ma et al., 2023, arXiv:2312.11865) — LLM
  agents for StarCraft II using single-frame compression, multi-frame
  aggregation, and CoT reasoning. Independently validates our architecture.
- **Multi-temporal-scale event detection** (MulTemS, 2025) — IoT sensor
  streams with hierarchical time-granularity detection.
- **Temporal GNNs for AML** (2024-2025) — financial transaction streams
  with multi-granularity scoring at transaction and actor levels.

### The hierarchy

```
Level 0 — Raw event stream (high frequency)
Level 1 — Classified events (threshold/pattern detection)
Level 2 — Episodes (bounded event sequences with lifecycle)
Level 3 — Phases (time-windowed classification)
Level 4 — Narratives (LLM-backed or rule-based summarisation)
```

Levels 0-2 are typically classical AI (CEP, rule-based). Levels 3-4 are
where LLM summarisation adds value. The framework is technology-split —
deterministic lower layers, pluggable `Summariser` interface for upper
layers.

## Design

### Package

```
io.casehub.agency.summarisation
```

Follows the existing blocks pattern (`blocks.channel`, `blocks.conversation`,
`blocks.routing`, `blocks.agentic`). New top-level package under `blocks`.

### Types extracted (7 source files)

| Type | Kind | Purpose |
|------|------|---------|
| `EventLevel` | Record | `(String name, int ordinal)` — identifies a level in the hierarchy |
| `LevelEvent<E>` | Record | `(E payload, long timestamp, EventLevel level)` — typed event at a specific level |
| `WindowPolicy` | Record | `(long maxAge, int maxCount)` — dual-trigger windowing (emit on either threshold). Compact constructor validates: `maxAge >= 0`, `maxCount >= 0`, at least one must be positive. |
| `EventAccumulator<E>` | Class | Collects events, tracks window state, `shouldEmit(now)`, `drain()`, `clear()`. Thread-safe — all public methods are synchronized. |
| `EventStreamBus<E>` | Class | Predicate-based pub/sub: `subscribe(Predicate, Consumer)`, `publish(LevelEvent)`, `clear()`. `CopyOnWriteArrayList`-backed — concurrent `publish()` and `subscribe()` are safe. |
| `Summariser<IN, OUT>` | Interface | `@FunctionalInterface`. `CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>>)`. Includes `ofSync()` factory. |
| `SummarisationRunner<IN, OUT>` | Class | Wires accumulator -> summariser -> output bus. API: `collect()`, `tick()`, `clear()`, `size()`. |

### Tests extracted (3 test files)

| Test | Coverage |
|------|----------|
| `EventAccumulatorTest` | 6 tests — timestamp trigger, count trigger, dual trigger, drain, clear, empty guard |
| `EventStreamBusTest` | 4 tests — pub/sub, predicate filtering, multiple subscribers, no-subscribers |
| `SummarisationRunnerTest` | 5 tests — windowing + publication, no-emit guard, level/timestamp wrapping, clear delegation, async error observability (failed `CompletionStage` propagation via `tick()` return) |

All plain JUnit 5 + AssertJ. No CDI, no Quarkus. Zero-dependency tests.

### API improvements (2 changes, made in quarkmind before extraction)

#### 1. Remove `EventConsumer`

**Current:** `EventConsumer<E>` interface with single method `Predicate<E> eventFilter()`.

**Problem:** No framework class references it. `EventStreamBus.subscribe()` takes
`Predicate<E>` directly. The sole consumer is quarkmind's `MomentConsumer extends
EventConsumer<GameMoment>`, which adds its own domain logic. The extends clause
provides zero value — `MomentConsumer` works identically as a standalone interface.

**Change:** Delete `EventConsumer.java` from the framework package. Update
`MomentConsumer` in quarkmind to remove the `extends` clause:

```java
// Before
public interface MomentConsumer extends EventConsumer<GameMoment> { ... }

// After
public interface MomentConsumer { ... }
```

**Impact:** quarkmind only. `MomentConsumer` retains its `eventFilter()` default
method — the method signature is unchanged, only the supertype is removed. No
other consumers exist. `EventConsumer.java` is not extracted to blocks.

**`eventFilter()` call sites:**

| Call site | Class | Post-change behaviour |
|-----------|-------|----------------------|
| `ensureSummarisationInitialized()` line 118 | `DroolsStrategyTask` | Calls `eventFilter()` on `this` (implements `MomentConsumer`). `eventFilter()` remains as a default method on `MomentConsumer` — works identically. |

No other `MomentConsumer` implementors exist. No code references
`EventConsumer` by its own type — all usage is through `MomentConsumer`.

#### 2. Change `SummarisationRunner.tick()` return type

**Current:** `public void tick(long now)`

**Problem:** The `CompletionStage` from `summarise()` is consumed with `thenAccept`
but no `exceptionally()` handler exists. The void return type means callers have
no mechanism to observe async errors even if they want to. The buffer is already
drained when a failure occurs — events are lost AND there is no API surface to
detect the loss. The Chain of Summarization paper (Ma et al.) confirms LLM
summarisers are a primary use case, not an edge case.

**Change:**

```java
// Before
public void tick(long now) {
    if (!accumulator.shouldEmit(now)) return;
    var batch = accumulator.drain();
    summariser.summarise(batch).thenAccept(results -> {
        for (var payload : results) {
            outputBus.publish(new LevelEvent<>(payload, now, outputLevel, null));
        }
    });
}

// After
public CompletionStage<Void> tick(long now) {
    if (!accumulator.shouldEmit(now))
        return CompletableFuture.completedFuture(null);
    var batch = accumulator.drain();
    return summariser.summarise(batch).thenAccept(results -> {
        for (var payload : results) {
            outputBus.publish(new LevelEvent<>(payload, now, outputLevel, null));
        }
    });
}
```

**Behavioural impact:**
- Sync callers: no change. The returned `CompletionStage` is already complete
  (sync summarisers wrap in `CompletableFuture.completedFuture()`). Existing
  fire-and-forget callers continue to work — they just ignore the return value.
- **API enablement, not a fix:** no existing caller currently observes the
  returned stage. quarkmind's `SummarisationLifecycle.tick()` ignores it
  (sync summarisers, already complete). This change exposes the information
  at the `SummarisationRunner` boundary so that future callers — or consumers
  using async summarisers — *can* chain `.exceptionally()`. It does not change
  any current behaviour.
- Batch loss on error is accepted: standard tumbling-window at-most-once
  semantics. The framework's job is to make failure *observable*, not to retry.

**Call site updates in quarkmind:**

`SummarisationLifecycle.tick()`:
```java
// Before
@Override
public void tick(long gameFrame) {
    phaseRunner.tick(gameFrame);
    arcRunner.tick(gameFrame);
}

// After — sync summarisers, stage is already complete
@Override
public void tick(long gameFrame) {
    phaseRunner.tick(gameFrame);
    arcRunner.tick(gameFrame);
}
```

No change needed — `SummarisationTickable.tick()` returns void, and the
`CompletionStage` return value is safely ignored for sync summarisers.
If `SummarisationTickable` later needs to propagate async results, the
interface can be updated then. The important thing is that `SummarisationRunner`
now exposes the information.

### Design decisions retained

These were evaluated during first-principles review and confirmed as correct:

| Decision | Rationale |
|----------|-----------|
| `long timestamp` on `LevelEvent` (not `Instant`) | Works for both game frames and epoch millis. More general. |
| `Predicate<E>` for bus subscription filter (not `Predicate<LevelEvent<E>>`) | Each bus carries one event type. Filtering on `EventLevel` within a bus is pointless. |
| No unsubscribe on `EventStreamBus` | No consumer ever removes individual subscriptions. `clear()` handles full reset. |
| No error handling in `EventStreamBus.publish()` | Callers control their own callbacks. Document, don't enforce. |
| No back-pressure mechanism | In-process streaming framework, not distributed messaging. |
| Runner owns its `EventAccumulator` (encapsulated since quarkmind#232) | Consumers configure via `WindowPolicy`, never interact with accumulator directly. Direct accumulator use is a separate pattern (Pattern B). |
| `CompletionStage` for async (not `Uni`) | Pure Java, zero framework dependency. Consumers convert with `Uni.createFrom().completionStage()` if needed. |
| `CopyOnWriteArrayList` for bus subscriptions | Safe concurrent `publish()` and `subscribe()`. In-flight `publish()` iterates a snapshot — new subscriptions appear in subsequent publishes. Thread-safety of the overall pipeline depends on subscriber callbacks, which are safe when using the framework's `SummarisationRunner` (synchronized `EventAccumulator`). |
| Output event timestamps = assessment time | `tick(now)` stamps output events with the `now` parameter, not the time range of constituent events. Output timestamps reflect *when the summarisation was triggered*. Consumers needing constituent time ranges should embed them in their payload records (e.g. clinical `CarePhase.durationSeconds`). |

### Framework patterns

Two integration patterns for consuming the summarisation framework:

**Pattern A — SummarisationRunner pipeline.** Wire `EventAccumulator` →
`Summariser` → output `EventStreamBus` via `SummarisationRunner`. Best for
synchronous, fast summarisers (pure-Java heuristics, microsecond completion).
Supports async summarisers via `CompletionStage` return, but see thread-safety
note below.

**Pattern B — Direct EventAccumulator with external dispatch.** Use
`EventAccumulator` + `WindowPolicy` directly for accumulation, then dispatch
async work externally (e.g. via CaseHub Workers). Bypasses `SummarisationRunner`
and `Summariser` entirely. Best for LLM-backed summarisation where the async
call duration (1–5s) would create cross-thread pipeline hazards if wired
through `SummarisationRunner` at a non-terminal level. Proven by quarkmind's
commentary system (see `quarkmind/docs/superpowers/specs/2026-07-06-commentator-observer-llm-design.md`).

**When to use each:**

| Criterion | Pattern A | Pattern B |
|-----------|-----------|-----------|
| Summariser latency | Microseconds (sync heuristics) | Seconds (LLM calls) |
| Pipeline position | Any level | Any level |
| Thread-safety | Safe — `EventAccumulator` is synchronized | Safe — caller manages dispatch |
| Error observability | Via `tick()` return type (`CompletionStage<Void>`) | Caller's dispatch mechanism |
| Example | quarkmind's `GamePhaseSummariser`, `GameArcSummariser` | quarkmind's `CommentaryAccumulator` |

**Thread-safety note for multi-level async pipelines:** When an async
summariser at a non-terminal pipeline level completes on a background thread,
its `thenAccept` callback calls `outputBus.publish()`, which triggers
downstream `collect()` calls on the background thread. The downstream
`EventAccumulator`'s synchronized methods handle this safely — `collect()`
on the background thread and `tick()` on the main thread are mutually
exclusive. This is why `EventAccumulator` is synchronized: the bus-wired
pipeline creates implicit cross-thread access that callers cannot
synchronize externally.

### Dependencies

No new compile dependencies. The framework is pure Java — `CompletionStage`
and `CompletableFuture` are JDK. Blocks' existing test dependencies (JUnit 5,
AssertJ) are sufficient for all tests including examples.

### Example domains (#38)

Two domain examples prove the framework is genuinely portable. Both live
in the test source root under an `examples` package:

```
src/test/java/io/casehub/agency/summarisation/examples/
    clinical/    — clinical temporal abstraction
    logistics/   — logistics hub monitoring
```

#### Clinical temporal abstraction

Based on Shahar's taxonomy — the canonical academic domain for temporal
abstraction. Maps directly to our level hierarchy.

**Domain records:**

| Record | Level | Fields |
|--------|-------|--------|
| `VitalReading` | L1 input | `type` (HR, BP, SpO2, RR, TEMP), `value`, `unit` |
| `ClinicalEvent` | L2 output | `category` (TACHYCARDIA, HYPOXEMIA, HYPERTENSION, ...), `severity` (MILD, MODERATE, CRITICAL), `description`, `rationale` |
| `CarePhase` | L3 output | `phase` (STABLE_MONITORING, ACUTE_DETERIORATION, RECOVERY, ...), `durationSeconds`, `rationale` |
| `ClinicalNarrative` | L4 output | `summary`, `timestamp` |

**Pipeline (fan-out at L2):**

```
VitalReading bus (L1)
    → ClinicalEventSummariser (sync, rule-based: threshold classification)
    → WindowPolicy(maxCount=5)
    → ClinicalEvent bus (L2)
        ├→ CarePhaseSummariser (sync, rule-based: windowed phase classification)
        │   → WindowPolicy(maxAge=900_000)  // 15 minutes
        │   → CarePhase bus (L3)
        │       → NarrativeSummariser (async, simulates LLM call)
        │       → WindowPolicy(maxCount=3)
        │       → ClinicalNarrative bus (L4)
        │
        └→ MedicationAlertAccumulator (Pattern B — direct EventAccumulator)
            → WindowPolicy(maxCount=3)
            → drains batch, checks for drug interaction patterns
```

**Tests demonstrate:**
- Full pipeline: L1 vitals -> L2 episodes -> L3 phases -> L4 narrative
- **Fan-out topology:** L2 bus feeds both the L3 runner and an independent accumulator
- Severity escalation: normal vitals -> abnormal -> critical episode detection
- Phase transitions: stable -> deterioration -> recovery
- Async summariser with `CompletionStage` (simulates LLM latency)
- Error handling via `tick()` return type (async failure scenario)
- Pattern B (direct `EventAccumulator`) alongside Pattern A (runner pipeline)
- Rationale traces at every level

#### Logistics hub monitoring

Different domain, different event shapes — proves the framework's type
parameters work with arbitrary payload types.

**Domain records:**

| Record | Level | Fields |
|--------|-------|--------|
| `PackageScan` | L1 input | `scanId`, `warehouseId`, `weight`, `destination`, `scanType` (INBOUND, OUTBOUND, TRANSFER) |
| `PackageAnomaly` | L2 output | `type` (MISROUTE, WEIGHT_MISMATCH, DELAY, DAMAGE), `severity`, `details`, `rationale` |
| `HubPhase` | L3 output | `phase` (NORMAL_FLOW, CONGESTION, RECOVERY), `durationSeconds`, `rationale` |
| `HubNarrative` | L4 output | `summary`, `timestamp` |

**Pipeline:**

```
PackageScan bus (L1)
    → AnomalyDetectorSummariser (sync, rule-based: pattern detection)
    → WindowPolicy(maxCount=10)
    → PackageAnomaly bus (L2)
        ├→ HubPhaseSummariser (sync, rule-based: congestion classification)
        │   → WindowPolicy(maxAge=300_000)  // 5 minutes
        │   → HubPhase bus (L3)
        │       → HubNarrativeSummariser (async, simulates LLM)
        │       → WindowPolicy(maxCount=2)
        │       → HubNarrative bus (L4)
        │
        └→ ComplianceAuditAccumulator (Pattern B — direct EventAccumulator)
            → WindowPolicy(maxAge=600_000)  // 10 minutes
            → drains batch for compliance event log
```

**Tests demonstrate:**
- Full pipeline with different event shapes than clinical
- Count-based windowing (L1->L2) vs time-based windowing (L2->L3)
- Independent `EventAccumulator` usage without `SummarisationRunner` (Pattern B)
- Multi-level subscription: single consumer on L2 + L3 buses
- Structured domain records with typed fields (not flat strings)

### Both examples validate

| Aspect | How |
|--------|-----|
| Type safety | Different generic parameters across two domains |
| Sync + async | `Summariser.ofSync()` at L1-L2, raw `CompletionStage` at L3-L4 |
| Count + time windowing | `maxCount` triggers at lower levels, `maxAge` at upper levels |
| Rationale traces | Every output record carries human-readable reasoning |
| Error observability | Async failure test using `tick()` return type |
| Fan-out topology | L2 bus feeds both a `SummarisationRunner` (Pattern A) and a direct `EventAccumulator` (Pattern B) in both examples |
| Pattern A (runner pipeline) | `SummarisationRunner` wires accumulator → summariser → output bus |
| Pattern B (independent accumulator) | Direct `EventAccumulator` + `WindowPolicy` without `SummarisationRunner` |
| Multi-bus subscription | Consumer subscribing to multiple level buses simultaneously |

## Extraction workflow

### Phase 1: Improve in quarkmind (on main)

Quarkmind commits on main are approved for consolidation work (CLAUDE.md
"Cross-Repo Consolidation Commits"). Each improvement is a separate commit
with tests run between.

1. Remove `EventConsumer.java`, update `MomentConsumer` — commit, run tests
2. Remove dead `@Inject @Any Instance<MomentConsumer> consumers` field from `MomentBroker`, update Javadoc — commit, run tests
3. Change `tick()` return type, add async error test to `SummarisationRunnerTest` — commit, run tests
4. Make `EventAccumulator` thread-safe (synchronized `collect`/`shouldEmit`/`drain`/`clear`/`size`), add `WindowPolicy` compact constructor validation, update `EventStreamBus` class Javadoc to reflect that concurrent `publish()` and `subscribe()` are safe (`CopyOnWriteArrayList`-backed) — commit, run tests
5. Update issue #27 body to reflect file count change (8→7 after `EventConsumer` removal)

### Phase 2: Extract to blocks (on issue-27 branch)

6. Create `io.casehub.agency.summarisation` package in blocks
7. Copy 7 source files + 3 test files from quarkmind
8. Run blocks tests to validate — commit

### Phase 3: Wire quarkmind to blocks (on quarkmind main)

9. Add `casehub-agency` as a compile dependency in quarkmind's `pom.xml`
10. Remove the 7 source files + 3 test files from quarkmind
11. Run quarkmind's full test suite to validate the dependency switch — commit

### Phase 4: Domain-agnostic examples (#38, on blocks branch)

12. Clinical domain records + pipeline + tests (including fan-out at L2)
13. Logistics domain records + pipeline + tests (including Pattern B branch)
14. Run full blocks test suite — commit

### Phase 5: Documentation

15. Update blocks CLAUDE.md with new package documentation
16. Update blocks ARC42STORIES.MD §5 with `io.casehub.agency.summarisation` package entry
17. Update quarkmind ARC42STORIES.MD to note `casehub-agency` dependency for generic framework types
18. Update #38 issue as complete

## Related issues

- [#40](https://github.com/casehubio/blocks/issues/40) — qhorus channel
  integration for summarisation (bidirectional bridge). The summarisation
  hierarchy maps naturally to qhorus's Space -> Channel -> Topic -> Thread
  hierarchy. Correlation chains map to episodes, topics map to phases,
  channels carry the event stream. Should develop in parallel with qhorus#328.
- [#41](https://github.com/casehubio/blocks/issues/41) — generalised
  summarisation pipeline viewer. Concept mockup at
  `docs/assets/summarisation-viewer-concept.svg`.

## Consumers

| Repo | Current usage | Post-extraction |
|------|--------------|-----------------|
| quarkmind | Source (framework + SC2 domain summarisers) | Depends on blocks, keeps SC2-specific `GamePhaseSummariser`, `GameArcSummariser`, `SummarisationLifecycle` |
| Future: IoT | — | L1 sensor readings -> L2 anomalies -> L3 operational phases -> L4 maintenance narratives |
| Future: AML | — | L1 transactions -> L2 behavioural anomalies -> L3 investigation phases -> L4 SAR narratives |
| Future: clinical | — | L1 vitals -> L2 clinical episodes -> L3 care phases -> L4 clinical narratives |

## Acceptance criteria

- [ ] `EventConsumer` removed from quarkmind, `MomentConsumer` updated
- [ ] `MomentBroker` dead `consumers` field removed, Javadoc corrected
- [ ] `tick()` returns `CompletionStage<Void>`, all quarkmind tests pass
- [ ] 7 source + 3 test files extracted to blocks, all pass
- [ ] quarkmind depends on blocks, local copies removed, all quarkmind tests pass
- [ ] Clinical example: full L1->L4 pipeline with sync + async summarisers
- [ ] Logistics example: full L1->L4 pipeline with different event shapes
- [ ] Both examples demonstrate rationale traces, windowing modes, error observability
- [ ] Clinical example demonstrates fan-out topology (Pattern A + Pattern B from L2 bus)
- [ ] `WindowPolicy` rejects invalid configurations (both triggers zero or negative)
- [ ] `EventAccumulator` is thread-safe (synchronized public methods)
- [ ] CLAUDE.md updated with summarisation package documentation
- [ ] Blocks ARC42STORIES.MD §5 updated with summarisation package entry
- [ ] Quarkmind ARC42STORIES.MD updated to note `casehub-agency` dependency
- [ ] Issue #27 body updated to reflect `EventConsumer` removal (8→7 files)
- [ ] Zero new compile dependencies added to blocks
