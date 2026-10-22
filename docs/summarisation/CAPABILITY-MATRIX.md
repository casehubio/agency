# Summarisation Capability Matrix

Maps every summarisation capability to the example that demonstrates it and the tests that verify it.

## Modules

| Module | Artifact | What it provides |
|--------|----------|------------------|
| `summarisation-api` | `casehub-agency-summarisation-api` | Unified SPI: `Summariser`, `StatefulSummariser`, `ContentSummariser<T,R>`, pipeline infrastructure |
| `cloudevents` | `casehub-agency-cloudevents` | CloudEvent bridge: ingestion, emission, tick scheduling |
| `summarisation-yaml` | `casehub-agency-summarisation-yaml` | YAML surface: canonical model, PipelineCompiler, 5 built-in types, validation |
| `summarisation-yaml-deployment` | `casehub-agency-summarisation-yaml-deployment` | Quarkus build extension: YAML discovery, Jandex scan, recorder |
| `blocks` (existing) | `casehub-agency` | Extensions: `TieredContentSummariser`, `LlmContentSummariser`, observation pipeline |

## Examples

| Example | Module | Domain | What it demonstrates |
|---------|--------|--------|---------------------|
| **Stateful Running Average** | summarisation-api | Sensor readings | `StatefulSummariser` with framework-managed state, cross-batch accumulation, per-tenant isolation |
| **ContentSummariser Bridge** | summarisation-api | Text append | `ContentSummariser.asSummariser()` bridging content summarisation into pipeline infrastructure |
| **Keyed Warehouse Grouping** | summarisation-api | Logistics | `KeyedAccumulator` + `KeyedSummarisationRunner` — per-warehouse grouping with completion predicate and stale timeout, tenancy-aware key extraction |
| **CloudEvent Round-Trip** | cloudevents | Logistics | Full CE ingestion → pipeline → CE emission, tenancyId propagation, `PipelineTickScheduler` |
| **YAML Logistics Pipeline** | summarisation-yaml | Logistics hub | Declarative YAML pipeline: `threshold-classify` → `phase-detect` with CE emission, multi-tenant |
| **Built-in Types Gallery** | summarisation-yaml | Sensor monitoring | `pass-through` (identity rebatching), `count` (category frequencies), `field-extract` (nested document extraction) |
| **Java Logistics Pipeline** | blocks | Logistics hub | Tier 2 typed pipeline: `PackageScan` → `PackageAnomaly` → `HubPhase` → `HubNarrative` (L1-L4) |
| **Clinical Temporal Abstraction** | blocks | Vital signs | Tier 2 typed pipeline: `VitalReading` → `ClinicalEvent` → `CarePhase` → `ClinicalNarrative` |
| **Multi-Agent Observation** | blocks | Agent prompts | `ObservationAccumulator` with tiered rendering, `Compactor<E>` deduplication |
| **Channel Summarisation** | blocks | Messaging | `ChannelEventAdapter` + `ChannelEventPublisher` bridging channel messages to summarisation pipeline |
| **Tiered Channel Summary** | blocks | Messaging | `TieredContentSummariser` dispatching by batch size threshold |

## Capability → Example Matrix

### Core SPI (summarisation-api)

| Capability | Type | Stateful Avg | CE Bridge | Keyed WH | CE Round-Trip | YAML Logistics | Java Logistics | Clinical | Multi-Agent Obs | Unit Tests |
|-----------|------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| Stateless batch transform | `Summariser<IN,OUT>` | — | — | ✓ | ✓ | — | ✓ | ✓ | — | SummariserTest |
| Stateful batch transform | `StatefulSummariser<IN,OUT,S>` | ✓ | — | — | — | ✓ | — | — | — | StatefulSummariserTest |
| Framework state per partition | `SummarisationRunner` | ✓ | — | — | — | ✓ | — | — | — | SummarisationRunnerTest |
| Content summarisation SPI | `ContentSummariser<T,R>` | ✓ | — | — | — | — | — | — | — | ContentSummariserTest |
| `asSummariser()` bridge | `ContentSummariser.asSummariser()` | ✓ | — | — | — | — | — | — | — | ContentSummariserTest |
| Windowed accumulation | `EventAccumulator` | ✓ | — | — | ✓ | ✓ | ✓ | ✓ | — | EventAccumulatorTest |
| Keyed grouping | `KeyedAccumulator` | — | — | ✓ | — | — | — | — | — | KeyedAccumulatorTest |
| Keyed pipeline | `KeyedSummarisationRunner` | — | — | ✓ | — | — | — | — | — | KeyedSummarisationRunnerTest |
| Pub/sub event routing | `EventStreamBus<E>` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | EventStreamBusTest |
| Multi-tenant tenancyId | `LevelEvent.tenancyId()` | ✓ | — | ✓ | ✓ | ✓ | — | — | — | LevelEventTest |
| Pre-summarisation compaction | `Compactor<E>` | — | — | — | — | — | — | — | ✓ | — |

### CloudEvent Bridge (cloudevents)

| Capability | Type | CE Round-Trip | YAML Logistics | Unit Tests |
|-----------|------|:---:|:---:|:---|
| CloudEvent ingestion | `CloudEventIngestionAdapter<E>` | ✓ | — | CloudEventIngestionAdapterTest |
| CloudEvent emission | `CloudEventEmitter<E>` | ✓ | ✓ | CloudEventEmitterTest |
| TenancyId round-trip | CE extension propagation | ✓ | ✓ | CloudEventIngestionAdapterTest |
| Tick scheduling | `PipelineTickScheduler` | ✓ | — | PipelineTickSchedulerTest |
| Emission SPI | `EventSink<T>` | ✓ | ✓ | — |

### YAML Surface (summarisation-yaml)

| Capability | Type | YAML Logistics | Built-in Gallery | Unit Tests |
|-----------|------|:---:|:---:|:---|
| YAML deserialization | `PipelineDefinition` records | ✓ | ✓ | PipelineDefinitionTest |
| Polymorphic grouping | `GroupingDefinition` sealed | ✓ | ✓ | PipelineDefinitionTest |
| Summariser config capture | `SummariserDefinition` @JsonAnySetter | ✓ | ✓ | PipelineDefinitionTest |
| Pipeline compilation | `PipelineCompiler` | ✓ | ✓ | PipelineCompilerTest |
| Per-level CloudEvent emission | `EmitDefinition` | ✓ | — | PipelineCompilerTest |
| Pipeline validation | `PipelineValidator` | ✓ | — | PipelineValidatorTest |
| Summariser registry | `SummariserRegistry` | ✓ | ✓ | SummariserRegistryTest |

### Built-in Summariser Types

| Type | Stateful | YAML Logistics | Built-in Gallery | Unit Tests |
|------|:---:|:---:|:---:|:---|
| `pass-through` | No | — | ✓ | SummariserRegistryTest |
| `threshold-classify` | No | ✓ | — | ThresholdClassifySummariserTest |
| `count` | No | — | ✓ | CountSummariserTest |
| `phase-detect` | Yes | ✓ | — | PhaseDetectSummariserTest |
| `field-extract` | No | — | ✓ | FieldExtractSummariserTest |
| Batch aggregate context | — | ✓ | — | BatchContextTest |

## Coverage Summary

| Category | Total | In Examples | In Unit Tests Only |
|----------|-------|-------------|---------------------|
| Core SPI capabilities | 11 | 11 | 0 |
| CloudEvent bridge | 5 | 5 | 0 |
| YAML surface | 7 | 7 | 0 |
| Built-in types | 6 | 6 | 0 |
| **Total** | **29** | **29** | **0** |

## How to Run

```bash
# All summarisation tests
mvn test -pl summarisation-api,cloudevents,summarisation-yaml

# Examples only
mvn test -pl summarisation-api -Dtest=StatefulSummariserExampleTest,KeyedGroupingExampleTest
mvn test -pl cloudevents -Dtest=CloudEventRoundTripExampleTest
mvn test -pl summarisation-yaml -Dtest=YamlLogisticsPipelineTest,BuiltInTypesExampleTest

# Java API examples (Tier 2)
mvn test -pl blocks -Dtest=LogisticsPipelineTest,ClinicalPipelineTest

# Built-in type tests
mvn test -pl summarisation-yaml -Dtest="*SummariserTest,BatchContextTest"
```
