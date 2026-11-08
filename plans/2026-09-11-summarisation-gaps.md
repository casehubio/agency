# Summarisation Gaps — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #254 — Summarisation gaps
**Issue group:** #248 (branch covers #246 epic)

**Goal:** Close 6 gaps in the summarisation-yaml surface: register 2
missing built-in types, add 3 config fields, implement keyed grouping
compiler branch.

**Architecture:** Fix gaps in existing modules (summarisation-yaml,
summarisation-yaml-deployment, cloudevents). No new modules. Keyed
grouping requires refactoring CompiledPipeline to support both
SummarisationRunner and KeyedSummarisationRunner via lambda wrappers.

**Tech Stack:** summarisation-api (KeyedSummarisationRunner,
KeyedAccumulator, VerbatimContentSummariser), summarisation-yaml
(PipelineCompiler, CompiledPipeline), ExpressionEngine (for keyed
expressions)

## Global Constraints

- Modules: summarisation-yaml, cloudevents (modify existing files)
- ExpressionEngine available via SummarisationRecorder
- PipelineCompiler.compile() signature change to accept ExpressionEngine
- CompiledPipeline stores lambda wrappers, not concrete runner types
- All existing PipelineCompilerTest tests must continue passing

---

## Batch 1: Registration fixes

### Task 1: Register field-extract and verbatim in SummarisationRecorder

**Files:**
- Modify: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/runtime/SummarisationRecorder.java`
- Test: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/SummariserRegistryTest.java` (create if not exists)

**Interfaces:**
- Consumes: `FieldExtractSummariser.create(config, jqCompiler)`, `VerbatimContentSummariser(Function<T, String>)`
- Produces: `"field-extract"` and `"verbatim"` types available via `SummariserRegistry.create()`

- [ ] **Step 1: Write failing test for field-extract registration**

```java
@Test
void registry_creates_field_extract() {
    var registry = recorder.createRegistry(expressionEngine);
    var config = Map.<String, Object>of("expression", "$.name");
    var summariser = registry.create("field-extract", config);
    assertThat(summariser).isNotNull();
}
```

Use a mock or stub `ExpressionEngine` that returns an identity function
for any expression. Verify the registry resolves "field-extract" without
throwing.

- [ ] **Step 2: Run test to verify fail**

Run: `mvn test -pl summarisation-yaml -Dtest=SummariserRegistryTest`
Expected: "Unknown summariser type: field-extract"

- [ ] **Step 3: Register field-extract in SummarisationRecorder**

In `createRegistry()`, add after the `"count"` registration:

```java
registry.register("field-extract", (SummariserFactory)
        config -> FieldExtractSummariser.create(config,
                expr -> {
                    var compiled = expressionEngine.compile(expr);
                    return node -> {
                        var result = compiled.apply(node);
                        return result instanceof java.util.List<?> l
                                ? l.stream().map(o -> (com.fasterxml.jackson.databind.JsonNode) o).toList()
                                : java.util.List.of((com.fasterxml.jackson.databind.JsonNode) result);
                    };
                }));
```

Note: Verify the exact `ExpressionEngine.compile()` return type and
`FieldExtractSummariser.create()` `jqCompiler` parameter type via
`ide_file_structure`. The expression engine may return `Function<Object, Object>`
which needs adapting to `Function<JsonNode, List<JsonNode>>`.

- [ ] **Step 4: Write failing test for verbatim registration**

```java
@Test
void registry_creates_verbatim() {
    var registry = recorder.createRegistry(expressionEngine);
    var config = Map.<String, Object>of("expression", "$.toString()");
    var summariser = registry.create("verbatim", config);
    assertThat(summariser).isNotNull();
}
```

- [ ] **Step 5: Register verbatim in SummarisationRecorder**

```java
registry.register("verbatim", (SummariserFactory) config -> {
    var expr = (String) config.getOrDefault("expression", "toString()");
    var compiled = expressionEngine.compile(expr);
    return new io.casehub.blocks.summarisation.VerbatimContentSummariser<>(
            item -> String.valueOf(compiled.apply(item))).asSummariser();
});
```

Note: `VerbatimContentSummariser` is in summarisation-api. `asSummariser()`
bridges `ContentSummariser` to `StatefulSummariser`. Verify exact import
path and `asSummariser()` return type.

- [ ] **Step 6: Run all tests — verify pass**

Run: `mvn test -pl summarisation-yaml`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/runtime/SummarisationRecorder.java \
       summarisation-yaml/src/test/
git commit -m "feat(#254): register field-extract and verbatim summariser types Refs #254"
```

## Batch 2: Config field additions

### Task 2: SummaryMode, typePrefix, tickInterval config fields

**Files:**
- Modify: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/SummariserDefinition.java`
- Modify: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/SourceDefinition.java`
- Modify: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/PipelineDefinition.java`
- Test: existing YAML deserialization tests or new `ConfigFieldsTest.java`

**Interfaces:**
- Consumes: `SummaryMode` enum from blocks
- Produces: `SummariserDefinition.mode()`, `SourceDefinition.typePrefix()`, `PipelineDefinition.tickInterval()`

- [ ] **Step 1: Write failing test for SummaryMode field on SummariserDefinition**

```java
@Test
void summariserDefinition_deserialises_mode() throws Exception {
    var yaml = """
            type: llm
            mode: EDIT
            """;
    var def = mapper.readValue(yaml, SummariserDefinition.class);
    assertThat(def.mode()).isEqualTo(SummaryMode.EDIT);
}

@Test
void summariserDefinition_mode_defaults_to_null() throws Exception {
    var yaml = """
            type: count
            """;
    var def = mapper.readValue(yaml, SummariserDefinition.class);
    assertThat(def.mode()).isNull();
}
```

- [ ] **Step 2: Add `@Nullable SummaryMode mode` to SummariserDefinition**

The existing `SummariserDefinition` uses `@JsonCreator` + `@JsonAnySetter`.
Add `mode` as an explicit `@JsonProperty` parameter in the `fromJson` factory:

```java
@JsonCreator
static SummariserDefinition fromJson(
        @JsonProperty("type") String type,
        @JsonProperty("mode") @Nullable SummaryMode mode,
        @JsonAnySetter Map<String, Object> config) {
    return new SummariserDefinition(type, mode,
            config != null ? Collections.unmodifiableMap(config) : Map.of());
}
```

Update the record to include `@Nullable SummaryMode mode`:
```java
public record SummariserDefinition(String type, @Nullable SummaryMode mode,
                                    Map<String, Object> config) { ... }
```

Import `io.casehub.blocks.summarisation.SummaryMode`. Note: SummaryMode
is in the blocks main module — verify the summarisation-yaml module has
blocks as a dependency (it does via the parent).

- [ ] **Step 3: Write failing test for typePrefix on SourceDefinition**

```java
@Test
void sourceDefinition_deserialises_typePrefix() throws Exception {
    var yaml = """
            type: cloud-event
            type-prefix: io.casehub.sensor
            cloud-event-type: io.casehub.output
            """;
    var def = mapper.readValue(yaml, SourceDefinition.class);
    assertThat(def.typePrefix()).isEqualTo("io.casehub.sensor");
}
```

- [ ] **Step 4: Add `@Nullable String typePrefix` to SourceDefinition**

```java
public record SourceDefinition(
        @Nullable String type,
        @JsonProperty("cloud-event-type") @Nullable String cloudEventType,
        @JsonProperty("type-prefix") @Nullable String typePrefix) {}
```

- [ ] **Step 5: Write failing test for tickInterval on PipelineDefinition**

```java
@Test
void pipelineDefinition_deserialises_tickInterval() throws Exception {
    var yaml = """
            name: test
            tickInterval: PT1S
            source:
              type: direct
            levels: []
            """;
    var def = mapper.readValue(yaml, PipelineDefinition.class);
    assertThat(def.tickInterval()).isEqualTo(Duration.ofSeconds(1));
}
```

- [ ] **Step 6: Add `@Nullable Duration tickInterval` to PipelineDefinition**

```java
public record PipelineDefinition(
        String name,
        SourceDefinition source,
        List<LevelDefinition> levels,
        @JsonProperty("tickInterval") @Nullable Duration tickInterval) {}
```

Register `JavaTimeModule` on the mapper if not already done (for Duration
deserialization).

- [ ] **Step 7: Fix compilation — update all PipelineDefinition constructor calls**

The existing `PipelineCompilerTest` creates `PipelineDefinition` with 3
args. Update all to include the 4th `tickInterval` arg (null):

```java
new PipelineDefinition("test", new SourceDefinition(null, null, null),
        List.of(...), null)
```

Similarly update `SourceDefinition` constructor calls to include `typePrefix`:

```java
new SourceDefinition(null, null, null)
```

- [ ] **Step 8: Run all tests — verify pass**

Run: `mvn test -pl summarisation-yaml`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/SummariserDefinition.java \
       summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/SourceDefinition.java \
       summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/PipelineDefinition.java \
       summarisation-yaml/src/test/
git commit -m "feat(#254): SummaryMode, typePrefix, tickInterval config fields Refs #254"
```

## Batch 3: Keyed grouping compiler

### Task 3: Implement keyed grouping in PipelineCompiler

**Files:**
- Modify: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/PipelineCompiler.java`
- Modify: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/CompiledPipeline.java`
- Modify: `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/runtime/SummarisationRecorder.java`
- Modify: `summarisation-yaml/src/test/java/io/casehub/blocks/summarisation/yaml/PipelineCompilerTest.java`

**Interfaces:**
- Consumes: `KeyedSummarisationRunner`, `KeyedAccumulator` from summarisation-api, `ExpressionEngine` from platform-api
- Produces: `PipelineCompiler.compile()` supports `GroupingDefinition.Keyed`

- [ ] **Step 1: Write failing test for keyed grouping compilation**

```java
@Test
void compiles_keyedGrouping() {
    var def = new PipelineDefinition("keyed-test",
            new SourceDefinition(null, null, null),
            List.of(new LevelDefinition("out",
                    new GroupingDefinition.Keyed("payload.category", "size() >= 3", 5000L),
                    new SummariserDefinition("pass-through", null, Map.of()),
                    null, List.of())),
            null);

    var registry = new SummariserRegistry();
    var pipeline = new PipelineCompiler().compile(def, registry, null, expressionEngine);

    var output = new ArrayList<LevelEvent<?>>();
    pipeline.outputBus("out").subscribe(e -> true, output::add);

    // Publish 3 events with same key
    pipeline.inputBus().publish(new LevelEvent<>(
            Map.of("category", (Object) "A", "value", (Object) 1), 100L, INPUT_LEVEL, null));
    pipeline.inputBus().publish(new LevelEvent<>(
            Map.of("category", (Object) "A", "value", (Object) 2), 200L, INPUT_LEVEL, null));
    pipeline.inputBus().publish(new LevelEvent<>(
            Map.of("category", (Object) "A", "value", (Object) 3), 300L, INPUT_LEVEL, null));
    pipeline.tick(400L).toCompletableFuture().join();

    assertThat(output).isNotEmpty();
}
```

Note: The exact expression evaluation depends on ExpressionEngine
implementation. Use the test `ExpressionEngine` (MvelExpressionEngine)
from the test dependencies.

- [ ] **Step 2: Run test to verify fail**

Run: `mvn test -pl summarisation-yaml -Dtest=PipelineCompilerTest#compiles_keyedGrouping`
Expected: Either compile error (signature changed) or UnsupportedOperationException

- [ ] **Step 3: Refactor CompiledPipeline to support both runner types**

Replace `List<SummarisationRunner<?, ?>>` with lambda wrappers:

```java
record LevelRunner(
        java.util.function.LongFunction<CompletionStage<Void>> ticker,
        java.util.function.Supplier<CompletionStage<Void>> flusher) {}
```

Update `tick()` and `flush()` to iterate over `LevelRunner` instances.
The `collect()` wiring stays in the compiler (via `subscribe()`).

- [ ] **Step 4: Update PipelineCompiler.compile() to accept ExpressionEngine**

Add `@Nullable ExpressionEngine expressionEngine` parameter:

```java
public <IN> CompiledPipeline<IN> compile(PipelineDefinition definition,
                                          SummariserRegistry registry,
                                          @Nullable EventSink<CloudEvent> emitterSink,
                                          @Nullable ExpressionEngine expressionEngine) {
```

For `GroupingDefinition.Keyed`:
```java
if (grouping instanceof GroupingDefinition.Keyed k) {
    if (expressionEngine == null)
        throw new IllegalStateException("Keyed grouping requires ExpressionEngine");
    var keyFn = expressionEngine.compile(k.keyExpression());
    Function<LevelEvent<Object>, Object> keyExtractor =
            event -> keyFn.apply(event.payload());
    var completionFn = expressionEngine.compile(k.completionExpression());
    Predicate<List<LevelEvent<Object>>> completionTest =
            events -> Boolean.TRUE.equals(completionFn.apply(events));
    var keyedRunner = new KeyedSummarisationRunner<>(
            keyExtractor, completionTest, k.staleTimeout(),
            summariser, outputBus, outputLevel);
    currentInput.subscribe(e -> true, keyedRunner::collect);
    levelRunners.add(new CompiledPipeline.LevelRunner(
            now -> keyedRunner.tick(now), keyedRunner::flush));
}
```

For `GroupingDefinition.Windowed`, wrap the existing `SummarisationRunner`:
```java
levelRunners.add(new CompiledPipeline.LevelRunner(
        now -> runner.tick(now), runner::flush));
```

- [ ] **Step 5: Update SummarisationRecorder.compilePipeline() to pass ExpressionEngine**

```java
public CompiledPipeline<Map<String, Object>> compilePipeline(
        PipelineDefinition definition,
        SummariserRegistry registry,
        ExpressionEngine expressionEngine) {
    return new PipelineCompiler().compile(definition, registry, null, expressionEngine);
}
```

- [ ] **Step 6: Update existing tests for new compile() signature**

Add `null` as the 4th arg (expressionEngine) to all existing
`new PipelineCompiler().compile(def, registry, sink)` calls:

```java
new PipelineCompiler().compile(def, registry, null, null)
```

- [ ] **Step 7: Run all tests — verify pass**

Run: `mvn test -pl summarisation-yaml`
Expected: PASS

- [ ] **Step 8: Update coverage matrix**

Update `docs/yaml-coverage.md` §9-13:
- Keyed grouping → Done
- field-extract registration → Done
- verbatim registration → Done
- SummaryMode → Done
- CloudEvent typePrefix → Done
- PipelineTickScheduler tickInterval → Done

- [ ] **Step 9: Commit**

```bash
git add summarisation-yaml/src/main/java/ summarisation-yaml/src/test/ docs/yaml-coverage.md
git commit -m "feat(#254): keyed grouping compiler, coverage matrix §9-13 Done Refs #254"
```

## References

- [2026-09-11-summarisation-gaps-design.md] — design spec
- `summarisation-yaml: PipelineCompiler.java:79` — UnsupportedOperationException
- `summarisation-yaml: CompiledPipeline.java` — runner storage
- `summarisation-yaml: runtime/SummarisationRecorder.java` — registry
- `summarisation-yaml: builtin/FieldExtractSummariser.java` — create() factory
- `summarisation-api: VerbatimContentSummariser.java` — asSummariser() bridge
- `summarisation-api: KeyedSummarisationRunner.java` — constructor
- `summarisation-api: KeyedAccumulator.java` — keyed grouping
- [GitHub #254](https://github.com/casehubio/blocks/issues/254)
