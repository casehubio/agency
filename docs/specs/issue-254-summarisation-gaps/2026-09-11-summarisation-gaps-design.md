# Summarisation Gaps — Keyed Grouping, Registration, CloudEvent Config

**Issue:** casehubio/agency#254
**Date:** 2026-09-11
**Scope:** 6 gaps in existing summarisation-yaml surface — compiler completions + registration wiring

## Problem

The summarisation-yaml surface has 6 known gaps: one compiler branch
throws UnsupportedOperationException, two built-in summariser types
are unregistered, and three config fields are missing from YAML
definitions.

## Solution

Fix each gap in place. No new modules or architectural changes.

## Gap 1: Keyed Grouping Compiler

`GroupingDefinition.Keyed` already parses from YAML with `keyExpression`,
`completionExpression`, `staleTimeout`. But `PipelineCompiler.toWindowPolicy()`
throws `UnsupportedOperationException("Keyed grouping not yet implemented")`.

**Fix:** When `GroupingDefinition.Keyed` is detected, skip `WindowPolicy`
creation and instead wire `KeyedAccumulator<K, E>` + `KeyedSummarisationRunner<K, IN, OUT>`:

- `keyExpression` → `ExpressionEngine.compile()` → `Function<LevelEvent<E>, K>` key extractor
- `completionExpression` → `ExpressionEngine.compile()` → `BiPredicate<K, List<LevelEvent<E>>>` completion predicate
- `staleTimeout` → stale timeout on `KeyedAccumulator`

`CompiledPipeline` needs to handle both flat (`SummarisationRunner`) and
keyed (`KeyedSummarisationRunner`) pipeline shapes. The existing
`CompiledPipeline.runner()` returns `SummarisationRunner` — either
generalize the return type or add a `keyedRunner()` accessor.

## Gap 2: field-extract Registration

`FieldExtractSummariser` exists in `builtin/` with `create(config, jqCompiler)`
factory. Not registered in `SummarisationRecorder.createRegistry()`.

**Fix:** Add `registry.register("field-extract", config -> FieldExtractSummariser.create(config, expressionEngine))` in `SummarisationRecorder`. The `ExpressionEngine`
parameter is already available in the method.

## Gap 3: verbatim Registration

`VerbatimContentSummariser<T>` exists in `summarisation-api`. Implements
`ContentSummariser<T, String>`, not `Summariser<IN, OUT>`. Needs
`asSummariser()` bridge.

**Fix:** Register as `"verbatim"` in `SummarisationRecorder`. The YAML
config specifies a rendering expression. Factory constructs
`VerbatimContentSummariser` with expression-based `Function<T, String>`
renderer, then wraps via `asSummariser()`.

## Gap 4: SummaryMode

`SummaryMode` enum (`APPEND` / `EDIT`) exists in blocks. Used by
`LlmContentSummariser`. Not referenced in summarisation-yaml.

**Fix:** Add `@Nullable SummaryMode mode` field to `SummariserDefinition`
(or the appropriate level definition). Direct reuse — enum deserializes
from YAML strings. Passed through to LLM summariser construction when
present.

## Gap 5: CloudEvent typePrefix Config

`CloudEventIngestionAdapter<E>` takes `typePrefix` for event filtering.
`SourceDefinition` has `cloudEventType` (emission) but no ingestion prefix.

**Fix:** Add `@Nullable String typePrefix` to `SourceDefinition`. When set,
`PipelineCompiler` wires a `CloudEventIngestionAdapter` with the prefix
for ingestion filtering.

## Gap 6: PipelineTickScheduler Config

`PipelineTickScheduler` takes `List<Tickable>` and `long intervalMs`.
No YAML config surface.

**Fix:** Add `@Nullable Duration tickInterval` to `PipelineDefinition`.
When set, `CompiledPipeline` exposes a `PipelineTickScheduler` instance.
Consumer calls `start()`/`stop()` on the compiled pipeline.

## Test Plan

| Test | What it verifies |
|------|-----------------|
| `PipelineCompilerTest` — keyed grouping | Keyed pipeline compiles without UnsupportedOperationException |
| `SummarisationRecorderTest` — field-extract | field-extract type resolves from registry |
| `SummarisationRecorderTest` — verbatim | verbatim type resolves from registry |
| `PipelineDefinitionTest` — SummaryMode | SummaryMode deserializes from YAML |
| `SourceDefinitionTest` — typePrefix | typePrefix field round-trips |
| `PipelineDefinitionTest` — tickInterval | tickInterval deserializes from Duration string |

## Coverage Matrix Update

§9-13: all 6 gaps → Done.

## References

- `summarisation-yaml: PipelineCompiler.java:79` — UnsupportedOperationException
- `summarisation-yaml: GroupingDefinition.java` — Keyed record
- `summarisation-yaml-deployment: SummarisationRecorder.java` — registry
- `summarisation-yaml: builtin/FieldExtractSummariser.java`
- `summarisation-api: VerbatimContentSummariser.java`
- `summarisation-api: KeyedAccumulator.java`, `KeyedSummarisationRunner.java`
- `blocks: SummaryMode.java`
- `cloudevents: CloudEventIngestionAdapter.java`
- `cloudevents: PipelineTickScheduler.java`
- `summarisation-yaml: SourceDefinition.java`, `PipelineDefinition.java`
- [GitHub #254](https://github.com/casehubio/agency/issues/254)
