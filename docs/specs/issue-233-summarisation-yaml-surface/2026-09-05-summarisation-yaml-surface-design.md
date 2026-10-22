# Summarisation YAML Surface with Composable Runtime

**Issues:** #231, #232, #233
**Design spec:** casehubio/casehub-desiredstate#74 — Summarisation→RAS Integration (this spec resolves the blocks side; Option 1 — loose coupling via CloudEvents. desiredstate#74 is closed; no child issues required on the desiredstate side.)
**Date:** 2026-09-05

## Overview

Three new Maven modules that layer a declarative YAML pipeline surface over a unified summarisation model. The extraction (#231) creates a zero-dependency API module with a unified SPI: `Summariser` (stateless), `StatefulSummariser` (stateful with framework-managed state), and `ContentSummariser` (simplified API for content summarisation, composable into pipelines via `.asSummariser()`). The CloudEvent bridge (#232) provides ingestion and emission adapters. The YAML surface (#233) adds a canonical model, pipeline compiler, and five built-in summariser types. This consolidation eliminates three separate batch-transform subsystems (temporal, content, observation) into one composable model.

## Module Architecture

```
casehub-agency-summarisation-api  (zero external deps, pure Java)
        ↑
        ├── casehub-agency  (existing — retains ContentSummariser + observation)
        ├── casehub-agency-cloudevents  (api + cloudevents-api)
        └── casehub-agency-summarisation-yaml  (api + cloudevents + platform-api)
                └── casehub-agency-summarisation-yaml-deployment  (Quarkus build extension)
```

| Module | Artifact ID | Dependencies | Contents |
|---|---|---|---|
| `summarisation-api/` | `casehub-agency-summarisation-api` | jspecify only | 10 extracted types + LevelEvent with tenancyId + StatefulSummariser + ContentSummariser<T,R> + VerbatimContentSummariser |
| `cloudevents/` | `casehub-agency-cloudevents` | summarisation-api, cloudevents-api | Ingestion adapter, emitter, EventSink SPI, tick scheduler |
| `summarisation-yaml/` | `casehub-agency-summarisation-yaml` | summarisation-api, cloudevents, platform-api (provided) | Canonical model, PipelineCompiler, SummariserRegistry, 5 built-in types |
| `summarisation-yaml-deployment/` | `casehub-agency-summarisation-yaml-deployment` | summarisation-yaml, quarkus-core-deployment | Jandex scan, YAML validation, recorder, CDI bean registration |

## Module 1: Summarisation API (#231)

### Extracted Types

All move from `blocks/blocks/src/main/java/io/casehub/agency/summarisation/` to `summarisation-api/` in the same package (`io.casehub.agency.summarisation`). Split package — acceptable without JPMS.

| Type | Kind | Changes |
|---|---|---|
| `LevelEvent<E>` | record | Gains `@Nullable String tenancyId` as 4th component (D1) |
| `EventLevel` | record | None |
| `WindowPolicy` | record | None |
| `Summariser<IN, OUT>` | interface | None (includes `SyncSummariser` inner interface) |
| `Compactor<E>` | interface | None |
| `EventAccumulator<E>` | class | None |
| `EventStreamBus<E>` | class | Drops deprecated `clear()` — only `clearSubscriptions()` exported |
| `SummarisationRunner<IN, OUT>` | class | None |
| `KeyedAccumulator<K, E>` | class | Key extractor widened: `Function<E, K>` → `Function<LevelEvent<E>, K>` (D12) |
| `KeyedSummarisationRunner<K, IN, OUT>` | class | Same key extractor change as KeyedAccumulator (D12) |
| `StatefulSummariser<IN, OUT, S>` | interface | NEW — stateful variant of Summariser with framework-managed state (D13) |
| `SummariseResult<OUT, S>` | record | NEW — output + new state, inner type of StatefulSummariser |
| `ContentSummariser<T, R>` | interface | MOVED from blocks, generified: `R` replaces qhorus `SummaryResult` (D14). `asSummariser()` bridges to `StatefulSummariser<T, R, R>` |
| `VerbatimContentSummariser<T>` | class | MOVED from blocks — pure Java, implements `ContentSummariser<T, String>` |

### Unified Summarisation Model (D13, D14)

Blocks previously had three separate batch-transform subsystems:

| Subsystem | SPI | Statefulness | Accumulation |
|---|---|---|---|
| Temporal | `Summariser<IN, OUT>` | Hidden (phase-detect) | `EventAccumulator` |
| Content | `ContentSummariser<T>` | `previous` parameter | Ad-hoc |
| Observation | `ObservationRenderer<E>` | None | `ObservationAccumulator` (reimplements EventAccumulator) |

Unified model — one composable hierarchy:

```java
// Stateless batch transform (existing, unchanged)
@FunctionalInterface
interface Summariser<IN, OUT> {
    CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>> batch);
}

// Stateful batch transform (NEW — D13)
interface StatefulSummariser<IN, OUT, S> extends Summariser<IN, OUT> {
    CompletionStage<SummariseResult<OUT, S>> summarise(
        List<LevelEvent<IN>> batch, @Nullable S previousState);

    default CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>> batch) {
        return summarise(batch, null).thenApply(SummariseResult::outputs);
    }

    record SummariseResult<OUT, S>(List<OUT> outputs, @Nullable S newState) {}
}

// Content summarisation — simplified API, composable into pipelines (D14)
@FunctionalInterface
interface ContentSummariser<T, R> {
    CompletionStage<R> summarise(List<T> items, @Nullable R previous);

    default StatefulSummariser<T, R, R> asSummariser() {
        return (batch, prev) -> {
            var items = batch.stream().map(LevelEvent::payload).toList();
            return summarise(items, prev)
                .thenApply(out -> new SummariseResult<>(List.of(out), out));
        };
    }
}
```

**Framework state management:** `SummarisationRunner` detects `StatefulSummariser` and manages state per partition (keyed by tenancyId or "default"). State stored in `ConcurrentHashMap<String, S>`. On each tick, the runner passes the previous state to `summarise(batch, state)` and stores the returned new state.

**What this enables:**
- `phase-detect` implements `StatefulSummariser<E, Map, String>` — framework manages state per partition instead of ad-hoc internal ConcurrentHashMap
- `ContentSummariser.asSummariser()` makes content summarisation a pipeline citizen with state — replaces `ContentSummariserToSummariser` bridge (which lost the `previous` context)
- `ObservationAccumulator` can wrap `EventAccumulator` instead of reimplementing buffering
- Future: `llm-summarise` YAML built-in type wrapping `ContentSummariser`

### What Stays in blocks

`TieredContentSummariser<T, R>` (gains second type param), `SummaryMode`, `LlmContentSummariser<T>` (implements `ContentSummariser<T, SummaryResult>`), `HeuristicMessageSummariser` (implements `ContentSummariser<Message, SummaryResult>`), channel summary integration (`ChannelSummariser`, `ThreadSummaryObserver`, `NoOpThreadSummaryStore`). Entire `observation` package — depends on qhorus-api and platform-agent-api. `ObservationAccumulator` consolidated to wrap `EventAccumulator` internally.

### What is Deleted

`ContentSummariserToSummariser` — replaced by `ContentSummariser.asSummariser()`. No bridge adapter needed.

### Breaking Changes

1. **LevelEvent** — 3-arg → 4-arg canonical constructor. All callers pass tenancyId explicitly (null for single-tenant). No convenience factory — compiler-enforced decision at every call site (D1).
2. **KeyedAccumulator / KeyedSummarisationRunner** — key extractor takes `LevelEvent<E>` not `E`. Existing callers add `.payload()`: `e -> e.transactionId()` becomes `event -> event.payload().transactionId()` (D12).
3. **`casehub-agency` gains compile dependency on `casehub-agency-summarisation-api`** — types remaining in blocks (`ChannelEventAdapter`, `ChannelEventPublisher`) reference extracted types (`Summariser`, `LevelEvent`, `EventLevel`, `EventStreamBus`). A new `<dependency>` entry in `blocks/pom.xml` is required.
4. **ContentSummariser<T> → ContentSummariser<T, R>** — generified result type (D14). Migration: add second type parameter. `ContentSummariser<Message>` → `ContentSummariser<Message, SummaryResult>`. ~10 consumer files, mechanical via `ide_refactor_rename` / find-replace.
5. **SummarisationRunner** — enhanced with state management for `StatefulSummariser`. Backward compatible for stateless `Summariser` implementations.

### Downstream Migration

| Consumer | Call sites | Migration |
|---|---|---|
| quarkmind | ~6 production | LevelEvent constructor + key extractor |
| blocks (content summariser consumers) | ~10 files | `ContentSummariser<T>` → `ContentSummariser<T, SummaryResult>` |
| blocks tests | ~90 test | LevelEvent constructor |
| logistics/clinical examples | ~20 test | LevelEvent constructor |
| blocks `ContentSummariserToSummariser` | 1 file + consumers | DELETE — replaced by `.asSummariser()` |

## Module 2: CloudEvent Bridge (#232)

### Components

All plain Java — no CDI, no Quarkus dependency.

| Type | Kind | What it does |
|---|---|---|
| `EventSink<T>` | `@FunctionalInterface` | `void emit(T event)` — consumer provides CDI wiring (D2) |
| `CloudEventIngestionAdapter<E>` | class | Filters CloudEvents by type pattern, deserializes payload to `E`, wraps as `LevelEvent<E>` with tenancyId from CE extension, publishes to `EventStreamBus<E>` |
| `CloudEventEmitter<E>` | class | Subscribes to output `EventStreamBus<E>`, wraps `LevelEvent<E>` as CloudEvent with configurable type URI + tenancyId extension, fires via `EventSink<CloudEvent>` |
| `PipelineTickScheduler` | class | Drives `tick(now)` on runners at configurable interval. Tick interval is a deployment concern, not pipeline definition (D11) |

### TenancyId Flow and Tenant Isolation

Tenant isolation is a framework guarantee, not a consumer responsibility.

**Ingestion:** CloudEvent arrives with `tenancyid` extension → ingestion adapter extracts → `new LevelEvent<>(payload, timestamp, level, tenancyId)`.

**Automatic tenant partitioning:** The `PipelineCompiler` interposes tenant partitioning at each pipeline level. Each tenant's events are accumulated and summarised independently — the YAML author never thinks about tenancy.

- **Windowed grouping:** each tenant gets its own `EventAccumulator` instance. Window triggers (count, age) are evaluated per-tenant.
- **Keyed grouping:** each tenant gets its own `KeyedAccumulator` instance. Key groups are inherently tenant-scoped.
- **Single-tenant (null tenancyId):** all events share a single default partition. Zero overhead.

**TenancyId propagation:** Runners propagate tenancyId from input batch to output events. Since tenant partitioning guarantees each batch is tenant-homogeneous, the batch's tenancyId is applied to all output `LevelEvent`s via the 4-arg constructor.

**Emission:** CloudEventEmitter reads `event.tenancyId()` → sets on outbound CloudEvent as `tenancyid` extension.

### Consumer Wiring (~30 lines CDI)

```java
// Consumer provides the CDI bridge
new CloudEventEmitter<>(outputBus, ce -> cdiEvent.fireAsync(ce), typeUri);
```

## Module 3: Summarisation YAML Surface (#233)

### Canonical Model

The pipeline definition is a tree of immutable records. Both YAML (Jackson deserialization) and Java (direct construction) target these. The `PipelineCompiler` consumes them.

```java
record PipelineDefinition(
    String name,
    SourceDefinition source,
    List<LevelDefinition> levels) {}

record SourceDefinition(
    String type,                         // Java class name (Tier 2) or null (Tier 1 → Map)
    @Nullable String cloudEventType) {}  // CE type pattern for ingestion

record LevelDefinition(
    String name,
    GroupingDefinition grouping,
    SummariserDefinition summariser,
    @Nullable EmitDefinition emit,
    List<String> aggregateFields) {}     // explicit fields for pre-computation (D10)

sealed interface GroupingDefinition {
    record Windowed(WindowPolicy policy) implements GroupingDefinition {}
    record Keyed(String keyExpression,
                 String completionExpression,
                 long staleTimeout) implements GroupingDefinition {}
}

record SummariserDefinition(
    String type,                         // built-in name or @SummariserTypeId
    Map<String, Object> config) {}       // type-specific config

record EmitDefinition(
    String cloudEventType) {}            // type URI for outbound CloudEvents
```

### DSL Parity

YAML and Java are peer representations of the same canonical model (D13). The model records are the source of truth — YAML deserializes to them via Jackson, Java constructs them directly. A future `yaml-core` auto-binding capability could generate YAML deserialization from a Java builder API automatically, but the current design does not depend on this.

### YAML Grammar

Levels implicitly chain — level N's output feeds level N+1's input (D3). Per-level optional CloudEvent emission (D5).

```yaml
pipeline:
  name: logistics-hub
  source:
    type: io.example.PackageScan
    cloud-event-type: io.casehub.logistics.scan.v1
  levels:
    - name: anomalies
      grouping:
        type: windowed
        count: 10
      summariser:
        type: threshold-classify
        rules:
          - name: weight-mismatch
            when: "weight > 50.0"
            category: WEIGHT_MISMATCH
            severity: HIGH
      emit:
        cloud-event-type: io.casehub.logistics.anomaly.v1

    - name: phases
      grouping:
        type: windowed
        age: 300000
      aggregate-fields: [severity]
      summariser:
        type: phase-detect
        initial: NORMAL_FLOW
        states: [NORMAL_FLOW, CONGESTION, RECOVERY]
        transitions:
          - from: NORMAL_FLOW
            to: CONGESTION
            when: "counts.severity.HIGH >= 3"
          - from: CONGESTION
            to: RECOVERY
            when: "counts.severity.HIGH == 0"
      emit:
        cloud-event-type: io.casehub.logistics.phase.v1
```

### Grouping Modes

A `grouping:` block with a `type:` discriminator (D4). Mutually exclusive modes:

| Type | Fields | Maps to |
|---|---|---|
| `windowed` | `age` (ms) and/or `count` | `WindowPolicy` → `SummarisationRunner` |
| `keyed` | `key` (expression), `completion` (expression), `stale-timeout` (ms) | `KeyedAccumulator` → `KeyedSummarisationRunner` |

### Five Built-in Summariser Types

| Type ID | Stateful? | What it does | Config |
|---|---|---|---|
| `threshold-classify` | No | Per-event MVEL3 boolean predicates. Iterates batch, evaluates each rule against each event's payload. Output: 0..N classifications per batch (one per event-rule match). | `rules: [{name, when, category}]` |
| `phase-detect` | Yes (per-partition) | State machine. Emit-on-transition-only, first-match-wins, explicit initial state (D8). See lifecycle details below. | `initial`, `states`, `transitions: [{from, to, when}]` |
| `count` | No | Per-category counts within the batch | `category-field` |
| `field-extract` | No | JQ document transformation. Tier 1 only (`E = Map`) — JQ operates on JSON documents, not typed POJOs (D7). See §Generic Payload Model. | `expression` (JQ string) |
| `pass-through` | No | Identity rebatching — level exists for grouping/emission, not transformation | (none) |

All implement `Summariser<E, OUT>`. `phase-detect` state is framework-managed, per-partition (tenant × key group), in-memory. State lost on restart — persistence is Tier 2 territory (D8).

#### Built-in Output Schemas (Tier 1)

For Tier 1 (`E = Map<String, Object>`), each built-in summariser produces output Maps with defined keys. These schemas are the contract between levels — the next level's expressions and aggregate-fields operate on these keys.

**threshold-classify:** One output Map per event-rule match.

| Key | Type | Source |
|---|---|---|
| `ruleName` | `String` | From the rule's `name` field |
| `category` | `String` | From the rule's `category` field |
| *(user-defined)* | `Object` | Any additional key-value pairs in the rule config (e.g., `severity: HIGH`) |
| *(input fields)* | `Object` | All fields from the matched input event's payload, merged at lower precedence (rule fields win on collision) |

Rule config fields `name` and `when` are reserved (identification and predicate). All other key-value pairs in a rule are literal output fields. This enables arbitrary classification metadata without a separate `output:` block.

**phase-detect:** Zero or one output Map per batch (on transition only).

| Key | Type | Source |
|---|---|---|
| `from` | `String` | Previous state |
| `to` | `String` | New state |
| `phase` | `String` | Alias for `to` (convenience for downstream expressions) |

**count:** One output Map per batch.

| Key | Type | Source |
|---|---|---|
| *(category values)* | `Integer` | Keys are distinct values of `category-field`; values are occurrence counts |

**field-extract:** One output Map per JQ result node. Each `JsonNode` in the JQ result list is converted to `Map<String, Object>`.

**pass-through:** Same Map as input — the input event's payload is returned unchanged.

#### phase-detect Lifecycle

**State granularity:** One state machine instance per partition. With automatic tenant partitioning, each tenant has its own state. With keyed grouping, each key group within a tenant has its own state machine.

**First-batch behavior:** The state machine starts at `initial`. On the first batch, transition rules are evaluated against the initial state. If a transition fires, it is a genuine state change that produces output. If no transition fires, state stays at initial with zero output. The initial state assignment is not itself a transition.

**No-match behavior:** If no transition's `when` predicate matches the current batch, state stays unchanged. No output is produced. Downstream levels see nothing for this tick — expected behavior for stable states.

**Self-transitions:** A transition where `from == to` IS a valid transition and DOES produce output when matched. This enables periodic "heartbeat" emissions in a stable state.

**Terminal states:** States reachable from initial but with no outbound transitions are valid. They represent stable end states (e.g., SHUTDOWN). The pipeline stops emitting after reaching a terminal state. Build-time validation reports terminal states as an info-level note, not an error.

#### Compaction

`Compactor<E>` is supported in the Java API (Tier 2) but has no YAML surface representation. Compaction logic is inherently domain-specific — deduplication strategies (by field, by time window, by priority) vary across use cases and cannot be expressed generically in YAML without creating a mini-language. Tier 2 Java-constructed pipelines have full compaction support via `SummarisationRunner` and `KeyedSummarisationRunner` constructors. Future YAML compaction support is deferred. **Deferred issue (file before implementation):** `feat: YAML compactor block with built-in dedup strategy` — analyse common compaction patterns from real YAML pipeline usage; design a `compactor:` block in the level definition with at least a `dedup-by-field` built-in strategy. Ref: this spec §Compaction, `MultiAgentObservationPipelineTest.compact()` as the reference implementation pattern.

### Expression Engine

Expressions evaluated via platform `ExpressionEngine` SPI — `CompiledExpression<C, R>` with `R eval(C)` (D9). This is a new precedent: typed compilation via `CompiledExpression` rather than the less-typed `ExpressionEvaluator` marker used by RAS situations.

| Expression position | Language | Context type | Result type |
|---|---|---|---|
| `threshold-classify` `when` | MVEL3 | Event payload (individual event) | `Boolean` |
| `phase-detect` `when` | MVEL3 | Pre-computed batch aggregates | `Boolean` |
| `keyed` `key` | MVEL3 | Event payload | `Object` |
| `keyed` `completion` | MVEL3 | Key group context Map (see below) | `Boolean` |
| `field-extract` `expression` | JQ | `JsonNode` (Tier 1 only) | `List<JsonNode>` |

**Keyed completion context:** The `PipelineCompiler` wraps the key group in a `Map<String, Object>` for MVEL evaluation (raw `List` cannot be used as MVEL context — `Introspector.getBeanInfo(List.class)` does not expose `size` as a JavaBean property, only `empty` from `isEmpty()`).

| Key | Type | Description |
|---|---|---|
| `size` | `int` | Number of events in the key group |
| `events` | `List<Map<String, Object>>` | Event payloads (Tier 1) |

Examples: `size >= 10` (count-based), `events[size - 1].status == 'COMPLETE'` (sentinel event).

Pre-computed batch aggregates (D10): `size`, `counts.<field>.<value>`, `sums.<field>`, `avgs.<field>`. Only declared `aggregate-fields` are pre-computed — bounded, no speculative enumeration. Used by `phase-detect`; not used by `threshold-classify` (which evaluates per-event, not per-batch).

**Non-numeric field handling:** `counts.<field>.<value>` is always computed (counts occurrences of each distinct value — works for any type). `sums.<field>` and `avgs.<field>` silently skip non-numeric values: for a field with no numeric values, `sums` is `0` and `avgs` is `null`. No runtime error. This means `aggregate-fields: [severity]` where severity is a string produces valid `counts.severity.HIGH` but `sums.severity` is `0` — which is harmless because `phase-detect` expressions typically use `counts`, not `sums`/`avgs`, for categorical fields.

### SummariserRegistry and Tier 2

```java
class SummariserRegistry {
    void register(String typeId, SummariserFactory<?> factory);
    <IN, OUT> Summariser<IN, OUT> create(String typeId, Map<String, Object> config);
}

@Retention(RUNTIME) @Target(TYPE)
@interface SummariserTypeId { String value(); }
```

Single registry, dual population (D6):
- **Build time:** Jandex discovers `@SummariserTypeId` classes, recorder populates registry
- **Runtime:** callers populate via `registry.register()` directly

### PipelineCompiler

```java
class PipelineCompiler {
    CompiledPipeline compile(
        PipelineDefinition definition,
        SummariserRegistry registry,
        ExpressionEngineRegistry expressions,
        @Nullable EventSink<CloudEvent> emitterSink);
}
```

Plain Java, no Quarkus dependency (D6). Dual-use: deployment module recorder (static path) and application code (dynamic path).

`CompiledPipeline` owns the wired object graph — buses, runners, emitters, tenant partitions.

```java
class CompiledPipeline<IN> {
    String name();
    EventStreamBus<IN> inputBus();
    CompletionStage<Void> tick(long now);
    CompletionStage<Void> flush();
}
```

- Generic over `IN` — Tier 1: `CompiledPipeline<Map<String, Object>>`, Tier 2: `CompiledPipeline<DomainType>`
- `inputBus()` — the entry point. `CloudEventIngestionAdapter` subscribes here; Java callers publish directly.
- `tick(long now)` — drives all runners across all tenant partitions. Returns `CompletionStage.allOf()` over all runner ticks.
- `flush()` — unconditional drain at shutdown. Returns `CompletionStage<Void>` for graceful shutdown coordination.
- Intermediate buses are internal — external observation is via per-level CloudEvent emission (`emit:` block) or Tier 2 direct construction.
- The `@Scheduled` tick driver is wired by the deployment module, not part of `CompiledPipeline`.

### Build-time Processing (Deployment Module)

1. Classpath scan for `META-INF/summarisation/*.yaml`
2. `@SummariserTypeId` registry scan via Jandex
3. Validation: unknown summariser types, unreachable states (phase-detect), cross-level type consistency, mutual exclusivity (windowed vs keyed)
4. `@Record(RUNTIME_INIT)` recorder: constructs `PipelineCompiler`, compiles all discovered pipelines, registers `CompiledPipeline` beans. **Multi-pipeline qualification:** when multiple pipelines are discovered, beans are qualified with `@PipelineQualifier(name)` using `PipelineDefinition.name`. Two-pass approach (count first, then qualify): single-pipeline applications retain `@Default` for unqualified injection; qualifiers are only added when multiple pipelines exist. This avoids the desiredstate#110 gotcha where adding a CDI qualifier silently removes `@Default`.
5. `@Scheduled` tick driver registration with configurable interval:
   - Config property: `casehub.summarisation.tick-interval-ms` (long, milliseconds)
   - Default: `1000` (1 second)
   - Global — applies to all compiled pipelines in the application. Per-pipeline intervals are a future concern. **Deferred issue (file if needed):** `feat: per-pipeline tick interval configuration` — allow different pipelines in the same application to tick at different rates. Ref: this spec §Build-time Processing step 5.

### Validation Rules

| Rule | What it catches |
|---|---|
| Unknown summariser type | Type ID not in registry (built-in or @SummariserTypeId) |
| Unreachable states | phase-detect state not reachable from initial via any transition path |
| Grouping mutual exclusivity | Both `windowed` and `keyed` fields present |
| Missing required config | phase-detect without `initial`, threshold-classify without `rules` |
| Expression syntax | MVEL3/JQ parse errors at build time |

## Generic Payload Model

The pipeline is generic throughout — `LevelEvent<E>` (D7). Tier 1 (YAML-only) uses `E = Map<String, Object>` from JSON deserialization. Tier 2 uses domain types. MVEL3 property access handles both transparently.

JQ (`field-extract`) requires `JsonNode` — Tier 1 only. For Tier 1 (`E = Map<String, Object>`), the platform `JQExpressionEngine` handles serialization transparently via `MapAdaptedJQExpression` (`ObjectMapper.valueToTree()`). For Tier 2 (domain types), `field-extract` is not supported — use MVEL3-based summariser types (`threshold-classify`, `phase-detect`) which handle typed POJOs natively, or Java-constructed summarisers. This is a design decision: JQ is a JSON document transformation tool, and automating POJO→JsonNode serialization would create a leaky abstraction where field renames break silently at runtime (D7 trade-off).

## References

- `blocks/blocks/src/main/java/io/casehub/agency/summarisation/LevelEvent.java` — current 3-arg record
- `blocks/blocks/src/main/java/io/casehub/agency/summarisation/SummarisationRunner.java` — runner implementation
- `blocks/blocks/src/main/java/io/casehub/agency/summarisation/KeyedAccumulator.java` — key extractor signature
- `blocks/blocks/src/test/java/io/casehub/agency/summarisation/examples/logistics/LogisticsPipelineTest.java` — pipeline wiring pattern
- `io.casehub.platform.api.expression.ExpressionEngine` — platform expression SPI (javap analysis)
- `io.casehub.platform.api.expression.CompiledExpression` — generic `R eval(C)` contract
- casehubio/casehub-desiredstate#74 — RAS integration scope
- [GE-20260813-keyed-summarisation-runner-api] — KeyedSummarisationRunner uses completionTest, not WindowPolicy
- [GE-20260813-partitioned-observation-single-typed] — PartitionedObservationService is single-typed
