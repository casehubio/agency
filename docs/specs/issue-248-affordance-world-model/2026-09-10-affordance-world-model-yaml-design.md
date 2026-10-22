# Affordance / World Model — YAML Surface

**Issue:** casehubio/agency#248
**Date:** 2026-09-10
**Scope:** 8 capabilities from coverage matrix §15 → spec records + compiler + deployment processor

## Problem

The affordance/world model types (`ObservableEntity`, `Affordance`, `ActionDescriptor`,
`ObservationSection`, `AnnotatedSection`, `ObservationPipeline`, `PerceptionFilter`,
`TieredObservationRenderer`) are pure data and compositional infrastructure. All are
YAML-expressible but have no YAML surface — consumers must construct them in Java.

## Solution

Spec records in `agentic-yaml`, a `WorldDefinition` root type, a `WorldCompiler`,
a named `ObservationFilterRegistry`, and a deployment processor that discovers
`world.yaml` on the classpath. Standalone file — not embedded in pattern specs.

## YAML Surface

```yaml
# world.yaml — discovered on classpath
actions:
  - type: MOVE
    description: Move to an adjacent room
    parameterFormat: <room-id>
  - type: TAKE
    description: Pick up a portable object
    parameterFormat: <object-id>
  - type: USE
    description: Use an inventory item on a target
    parameterFormat: <item-id> <target-id>

entities:
  poison:
    displayName: Rat Poison
    description: A dusty bottle
    affordances:
      - actionType: TAKE
        label: to pick up
  tea-service:
    displayName: Tea Service
    description: A silver set
    affordances:
      - actionType: USE
        acceptsItems: [rat-poison]

sections:
  - type: entity-group
    header: Visible Objects
    emptyMessage: Nothing here.
    entityRefs: [poison, tea-service]
  - type: entity-group
    header: Secret Room
    requiredTags: [perception-enhanced]
    resolutions:
      REDUCED:
        type: text
        header: Secret Room
        content: You sense something hidden.
    entities:
      - id: hidden-door
        displayName: Hidden Door
        affordances:
          - actionType: OPEN
  - type: text
    header: Current Location
    content: "Kitchen: A large room with copper pots."
  - type: item-list
    header: Your Goals
    items:
      - "[PRIMARY] Find the Doily Diamond"

pipeline:
  - type: perception

renderer:
  verbatimThreshold: 5
  groupedThreshold: 20
```

## Architecture

### Package

`io.casehub.agency.core.yaml.spec.world` — parallel to `.spec.cognition`.

### Root Definition

```java
public record WorldDefinition(
    @Nullable List<ActionDescriptorSpec> actions,
    @Nullable Map<String, EntitySpec> entities,
    @Nullable List<ObservationSectionSpec> sections,
    @Nullable List<ObservationFilterSpec> pipeline,
    @Nullable RendererSpec renderer) {}
```

Jackson deserializes `world.yaml` directly into `WorldDefinition`. All sections
optional — an empty `world.yaml` is valid (produces empty `CompiledWorld`).

### Spec Records

#### EntitySpec

```java
public record EntitySpec(
    String displayName,
    @Nullable String description,
    @Nullable List<AffordanceSpec> affordances) {}
```

No `id` field — when declared in the top-level `entities:` map, the map key is the ID.
When declared inline in a section, the entity must include an `id` field on the
`InlineEntitySpec` variant (see EntityGroupSpec below).

#### InlineEntitySpec

```java
public record InlineEntitySpec(
    String id,
    String displayName,
    @Nullable String description,
    @Nullable List<AffordanceSpec> affordances) {}
```

Used for inline entity declarations within sections. Carries its own `id`.

#### AffordanceSpec

```java
public record AffordanceSpec(
    String actionType,
    @Nullable String label,
    @Nullable String requiredItem,
    @Nullable List<String> acceptsItems) {}
```

Direct 1:1 mapping to the runtime `Affordance` record.

#### ActionDescriptorSpec

```java
public record ActionDescriptorSpec(
    String type,
    String description,
    @Nullable String parameterFormat) {}
```

Field renamed: `actionType` → `type` for YAML brevity (consistent with other spec
records using `type` as the primary discriminator field name).

#### ObservationSectionSpec — sealed interface

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = ObservationSectionSpec.EntityGroupSpec.class, name = "entity-group"),
    @Type(value = ObservationSectionSpec.TextBlockSpec.class, name = "text"),
    @Type(value = ObservationSectionSpec.ItemListSpec.class, name = "item-list")
})
public sealed interface ObservationSectionSpec {

    String header();

    // Annotation properties — present on all variants (D3)
    @Nullable Set<String> requiredTags();
    @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions();
    @Nullable String interpretiveFrame();

    record EntityGroupSpec(
        String header,
        @Nullable String emptyMessage,
        @Nullable List<String> entityRefs,
        @Nullable List<InlineEntitySpec> entities,
        @Nullable Set<String> requiredTags,
        @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions,
        @Nullable String interpretiveFrame
    ) implements ObservationSectionSpec {}

    record TextBlockSpec(
        String header,
        String content,
        @Nullable Set<String> requiredTags,
        @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions,
        @Nullable String interpretiveFrame
    ) implements ObservationSectionSpec {}

    record ItemListSpec(
        String header,
        @Nullable String emptyMessage,
        @Nullable List<String> items,
        @Nullable Set<String> requiredTags,
        @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions,
        @Nullable String interpretiveFrame
    ) implements ObservationSectionSpec {}
}
```

Three variants matching the three `ObservationSection` permits. `EntityGroupSpec`
supports both `entityRefs` (references to top-level entities map) and `entities`
(inline declarations). Both may be present — compiler merges them.

Annotation properties (`requiredTags`, `resolutions`, `interpretiveFrame`) appear on
every variant. When any is non-null, the compiler wraps the compiled section in
`AnnotatedSection`.

#### ObservationFilterSpec — sealed interface

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = ObservationFilterSpec.PerceptionSpec.class, name = "perception")
})
public sealed interface ObservationFilterSpec {

    record PerceptionSpec() implements ObservationFilterSpec {}
}
```

Named filter registry pattern. `PerceptionSpec` has no fields — `PerceptionFilter`
is stateless. Future filter types add fields as needed.

#### RendererSpec

```java
public record RendererSpec(
    @Nullable Integer verbatimThreshold,
    @Nullable Integer groupedThreshold) {}
```

Only the threshold fields are YAML-expressible. The `eventRenderer`,
`groupKeyExtractor`, and `summariser` are `Function`/`Summariser` instances — code-only.
Coverage matrix stays "Partial" for TieredObservationRenderer.

Semantics: `verbatimThreshold` is required when the renderer section is present.
`groupedThreshold` is optional — when absent, the consumer gets 2-tier mode
(verbatim up to threshold, grouped above). When both are present, 3-tier mode
(verbatim → grouped → summarised). The compiler validates
`groupedThreshold > verbatimThreshold` when both are specified.

### Compiler

```java
public class WorldCompiler {

    private final ObservationFilterRegistry filterRegistry;

    public WorldCompiler(ObservationFilterRegistry filterRegistry) {
        this.filterRegistry = filterRegistry;
    }

    public CompiledWorld compile(WorldDefinition definition) {
        var entityMap = compileEntityMap(definition.entities());
        return new CompiledWorld(
            compileActions(definition.actions()),
            entityMap,
            compileSections(definition.sections(), entityMap),
            compilePipeline(definition.pipeline()),
            compileRenderer(definition.renderer()));
    }
}
```

#### Compilation steps

1. **Entity map** — `Map<String, EntitySpec>` → `Map<String, ObservableEntity>`.
   Each entry compiled: key becomes the `id`, `AffordanceSpec` → `Affordance`.

2. **Actions** — `List<ActionDescriptorSpec>` → `List<ActionDescriptor>`. 1:1 mapping,
   `type` field mapped to `actionType`.

3. **Sections** — `List<ObservationSectionSpec>` → `List<ObservationSection>`:
   - **EntityGroupSpec**: resolve `entityRefs` against entity map (error if ref not found),
     compile inline `entities` with their own IDs, merge both lists. Wrap in
     `AnnotatedSection` if annotation properties present.
   - **TextBlockSpec**: 1:1 mapping. Wrap if annotated.
   - **ItemListSpec**: 1:1 mapping. Wrap if annotated.
   - **Resolution map compilation**: each `Map<ResolutionTier, ObservationSectionSpec>`
     value is recursively compiled as a section (resolution sections can themselves
     be any section type).

4. **Pipeline** — `List<ObservationFilterSpec>` → `ObservationPipeline`.
   Each spec resolved via `ObservationFilterRegistry` to an `ObservationFilter` instance.

5. **Renderer** — `RendererSpec` → threshold values (stored in `CompiledWorld` for
   consumer use when constructing `TieredObservationRenderer` with their code-only
   functions).

#### Validation

The compiler validates:
- Entity refs resolve to entries in the top-level entity map
- No duplicate entity IDs (between top-level entries and inline entities within the same section)
- Filter types are registered in `ObservationFilterRegistry`
- `groupedThreshold > verbatimThreshold` when both specified
- Required fields present (actionType on affordances, header on sections, etc.)

### CompiledWorld

```java
public record CompiledWorld(
    List<ActionDescriptor> actions,
    Map<String, ObservableEntity> entities,
    List<ObservationSection> sections,
    @Nullable ObservationPipeline pipeline,
    @Nullable RendererThresholds rendererThresholds) {

    public record RendererThresholds(
        int verbatimThreshold,
        @Nullable Integer groupedThreshold) {}
}
```

### ObservationFilterRegistry

```java
public class ObservationFilterRegistry {

    private final Map<String, Supplier<ObservationFilter>> factories;

    public ObservationFilterRegistry() {
        this.factories = new HashMap<>();
        register("perception", PerceptionFilter::new);
    }

    public void register(String name, Supplier<ObservationFilter> factory) {
        factories.put(name, factory);
    }

    public ObservationFilter resolve(String name) {
        var factory = factories.get(name);
        if (factory == null)
            throw new IllegalArgumentException("Unknown filter type: " + name);
        return factory.get();
    }
}
```

### Deployment Processor

`WorldYamlProcessor` in `agentic-yaml-deployment`. Follows `SummarisationYamlProcessor`
and `AgenticYamlProcessor` patterns:

1. Scan classpath for `world.yaml` (`HotDeploymentWatchedFileBuildItem`)
2. Deserialize to `WorldDefinition` via Jackson YAML
3. Validate via `WorldCompiler` (build-time validation catches ref errors early)
4. Register `CompiledWorld` as `@DefaultBean` CDI bean via recorder
5. Register `ObservationFilterRegistry` as `@DefaultBean` CDI bean

### Recorder

```java
public class WorldRecorder {

    public ObservationFilterRegistry createFilterRegistry() {
        return new ObservationFilterRegistry();
    }

    public WorldCompiler createCompiler(ObservationFilterRegistry registry) {
        return new WorldCompiler(registry);
    }
}
```

## Type Inventory

| # | Spec type | Fields | Maps to | Notes |
|---|-----------|--------|---------|-------|
| 1 | `WorldDefinition` | 5 | Root | Top-level YAML structure |
| 2 | `ActionDescriptorSpec` | 3 | `ActionDescriptor` | `type` → `actionType` rename |
| 3 | `EntitySpec` | 3 | `ObservableEntity` | ID from map key |
| 4 | `InlineEntitySpec` | 4 | `ObservableEntity` | ID from `id` field |
| 5 | `AffordanceSpec` | 4 | `Affordance` | 1:1 mapping |
| 6 | `EntityGroupSpec` | 7 | `ObservationSection.EntityGroup` + opt. `AnnotatedSection` | Ref + inline entities |
| 7 | `TextBlockSpec` | 5 | `ObservationSection.TextBlock` + opt. `AnnotatedSection` | |
| 8 | `ItemListSpec` | 5 | `ObservationSection.ItemList` + opt. `AnnotatedSection` | |
| 9 | `PerceptionSpec` | 0 | `PerceptionFilter` | Stateless |
| 10 | `RendererSpec` | 2 | `CompiledWorld.RendererThresholds` | Partial — code-only functions |
| 11 | `CompiledWorld` | 5 | Output | Includes `RendererThresholds` |
| 12 | `ObservationFilterRegistry` | — | Registry | Built-in: `perception` |

**Total: 12 types, ~38 fields.**

## Dependencies

`agentic-yaml` already depends on `casehub-agency` (which contains all runtime affordance
types). No new compile dependencies needed — all runtime types are already reachable.

## Testing

- **AffordanceSpec/EntitySpec**: YAML deserialization round-trip for all field combinations
- **ObservationSectionSpec**: polymorphic deserialization — each `type:` variant parsed correctly
- **EntityGroupSpec dual mode**: entityRefs resolved from map, inline entities compiled, both together merged
- **Annotation compilation**: section with requiredTags → wrapped in AnnotatedSection; without → bare section
- **Resolution map**: recursive section compilation for resolution alternatives
- **ObservationFilterSpec**: `perception` type resolved from registry; unknown type → error
- **RendererSpec**: null fields → no thresholds; partial → only specified fields; validation of groupedThreshold > verbatimThreshold
- **WorldCompiler validation**: unresolved entity ref → error, duplicate entity ID → error, unknown filter → error
- **End-to-end**: `world.yaml` fixture → `CompiledWorld` with expected entities, sections, pipeline, thresholds
- **Empty world**: empty `world.yaml` → valid empty `CompiledWorld`

## Coverage Matrix Update

After closing, update `docs/yaml-coverage.md` §15:

| Capability | Status before | Status after |
|-----------|--------------|-------------|
| ObservableEntity | Gap | Done |
| Affordance | Gap | Done |
| ActionDescriptor | Gap | Done |
| ObservationSection (EntityGroup/TextBlock/ItemList) | Gap | Done |
| AnnotatedSection (requiredTags, resolutions) | Gap | Done |
| ObservationPipeline (ordered filter chain) | Gap | Done |
| PerceptionFilter (agentTags for visibility) | Gap | Done |
| TieredObservationRenderer (tier thresholds) | Partial | Partial (thresholds Done; renderer code-only) |

## References

- `ObservableEntity.java` — runtime entity record
- `Affordance.java` — runtime affordance record
- `ActionDescriptor.java` — runtime action descriptor record
- `ObservationSection.java` — sealed interface with EntityGroup/TextBlock/ItemList
- `AnnotatedSection.java` — capability-gated decorator
- `ObservationPipeline.java` — filter composition
- `PerceptionFilter.java` — visibility gating filter
- `ObservationFilter.java` — @FunctionalInterface SPI
- `ResolutionTier.java` — FULL/REDUCED/SUMMARY enum
- `TieredObservationRenderer.java` — tiered rendering (thresholds YAML, functions code)
- `CognitionDefinition.java` — standalone YAML definition precedent (#247)
- `CognitionCompiler.java` — compiler with defaults-merge pattern (#247)
- `PatternSpec.java` — sealed interface + JsonTypeInfo precedent (#165)
- `SummariserRegistry` — named type registry precedent
- `SummarisationYamlProcessor.java` — deployment processor precedent
- `AffordanceRenderer design spec` — original #69 design (entity grounding chains)
- `docs/yaml-coverage.md` §15 — capability gap inventory
