# Design: Agency Specs REST Endpoint

**Issue:** casehubio/agency#368
**Branch:** issue-368-cognition-rest-endpoint

## Problem

The casehub-mosaic frontend has fully implemented pattern views (including the cognition-view with typed ConfigSpec interfaces for all 13 CognitionDefinition sections), but no backend endpoint serves this data. The agentic-yaml deployment module discovers YAML definitions at build time and produces build items, but nothing wires them to runtime beans or REST endpoints.

## Solution

A single `GET /api/agency/specs` endpoint that serves all agentic YAML spec data as a JSON object. The endpoint is auto-registered by the agentic-yaml deployment module — consumer apps get it for free on their classpath.

### Response shape

```json
{
  "patterns": [
    { "type": "supervisor", "name": "...", "agents": [...], ... },
    { "type": "debate", "name": "...", ... }
  ],
  "cognition": {
    "drive": { "axisWeights": {...}, "changeThreshold": 0.1, ... },
    "mood": { "baseline": {...}, ... },
    "personalityEvolution": { ... },
    ...
  },
  "promptOptimisation": {
    "pipelines": { ... }
  },
  "world": {
    "actions": [...],
    "entities": {...},
    ...
  }
}
```

Absent specs are `null` (e.g., if no `META-INF/cognition.yaml` exists, `cognition` is null).

### Field naming

`CognitionDefinition.personality` serializes as `personalityEvolution` via `@JsonProperty` to match the frontend `CognitionConfig` type. All other field names already match.

## Architecture

### Build time (agentic-yaml-deployment)

The existing `AgenticYamlProcessor` already produces four build items. A new `@BuildStep` consumes them and calls the recorder:

```
AgenticPatternsBuildItem ─┐
CognitionConfigBuildItem ──┤
PromptOptimisationBuildItem┤──► new @BuildStep ──► AgenticRecorder.createSpecsBean()
WorldConfigBuildItem ──────┘                              │
                                                          ▼
                                              AgencySpecsBean (runtime CDI bean)
```

### Runtime (agentic-yaml)

| Class | Role |
|-------|------|
| `AgencySpecsBean` | `@ApplicationScoped` CDI bean holding pre-built `AgencySpecs` record. Registered by recorder. |
| `AgencySpecs` | Immutable record: `patterns`, `cognition`, `promptOptimisation`, `world`. |
| `AgencySpecsResource` | JAX-RS `@Path("/api/agency/specs")` resource. `@GET` returns `AgencySpecs` as JSON. Injects `AgencySpecsBean`. |

### Package

Both new runtime classes go in `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/`:
- `AgencySpecs.java` — the response record
- `AgencySpecsBean.java` — CDI holder bean
- `AgencySpecsResource.java` — JAX-RS resource

The recorder method goes in the existing `AgenticRecorder.java`.

The new `@BuildStep` goes in the existing `AgenticYamlProcessor.java`.

## Components

### `AgencySpecs` (record)

```java
public record AgencySpecs(
    @Nullable List<PatternDefinition> patterns,
    @Nullable CognitionDefinition cognition,
    @Nullable PromptOptimisationDefinition promptOptimisation,
    @Nullable WorldDefinition world) {}
```

### `AgencySpecsBean` (CDI bean)

```java
@ApplicationScoped
public class AgencySpecsBean {
    private final AgencySpecs specs;
    // constructor, getter
}
```

Not a record because the recorder needs to call `new AgencySpecsBean(specs)` at runtime via bytecode recording, which requires a non-record class with Quarkus recorders.

### `AgencySpecsResource` (JAX-RS)

```java
@Path("/api/agency/specs")
@Produces(MediaType.APPLICATION_JSON)
public class AgencySpecsResource {
    @Inject AgencySpecsBean specsBean;

    @GET
    public AgencySpecs get() {
        return specsBean.specs();
    }
}
```

No authentication — spec data is static, non-sensitive configuration metadata. Consumer apps can layer security via their own filters if needed.

### `@JsonProperty` on `CognitionDefinition`

```java
public record CognitionDefinition(
    @Nullable DriveConfigSpec drive,
    @Nullable MoodConfigSpec mood,
    @JsonProperty("personalityEvolution")
    @Nullable PersonalityEvolutionConfigSpec personality,
    ...
```

### Recorder method

```java
// in AgenticRecorder.java
public AgencySpecsBean createSpecsBean(
        List<PatternDefinition> patterns,
        CognitionDefinition cognition,
        PromptOptimisationDefinition promptOptimisation,
        WorldDefinition world) {
    return new AgencySpecsBean(
        new AgencySpecs(
            patterns.isEmpty() ? null : patterns,
            cognition, promptOptimisation, world));
}
```

### Build step

```java
// in AgenticYamlProcessor.java
@BuildStep
@Record(ExecutionTime.STATIC_INIT)
SyntheticBeanBuildItem registerSpecsBean(
        AgenticRecorder recorder,
        AgenticPatternsBuildItem patterns,
        CognitionConfigBuildItem cognition,
        PromptOptimisationBuildItem promptOpt,
        WorldConfigBuildItem world) {
    var runtimeValue = recorder.createSpecsBean(
        patterns.patterns(),
        cognition.definition(),
        promptOpt.definition(),
        world.definition());
    return SyntheticBeanBuildItem.configure(AgencySpecsBean.class)
            .scope(ApplicationScoped.class)
            .runtimeValue(runtimeValue)
            .done();
}
```

Uses Quarkus `SyntheticBeanBuildItem` directly — no extra build item class needed.

## Testing

- **Unit test:** `AgencySpecsResourceTest` — construct the resource with a known `AgencySpecsBean`, verify JSON output matches expected shape. Test null fields (absent specs). Test `personalityEvolution` field name in serialized output.
- **Serialization test:** Verify `CognitionDefinition` with `@JsonProperty` still deserializes correctly from YAML (backward compatibility).

## Files changed

| File | Change |
|------|--------|
| `agentic-yaml/src/main/java/.../spec/cognition/CognitionDefinition.java` | Add `@JsonProperty("personalityEvolution")` |
| `agentic-yaml/src/main/java/.../runtime/AgencySpecs.java` | New record |
| `agentic-yaml/src/main/java/.../runtime/AgencySpecsBean.java` | New CDI bean |
| `agentic-yaml/src/main/java/.../runtime/AgencySpecsResource.java` | New JAX-RS resource |
| `agentic-yaml/src/main/java/.../runtime/AgenticRecorder.java` | Add `createSpecsBean()` |
| `agentic-yaml-deployment/src/main/java/.../deployment/AgenticYamlProcessor.java` | Add build step |
| `agentic-yaml/src/test/java/.../runtime/AgencySpecsResourceTest.java` | New test |
| `agentic-yaml/src/test/java/.../spec/cognition/CognitionDefinitionSerializationTest.java` | New test for @JsonProperty compat |

## References

- `agentic-yaml-deployment/AgenticYamlProcessor.java` — existing build steps that produce the 4 build items
- `agentic-yaml/runtime/AgenticRecorder.java` — existing recorder
- `agentic-yaml/spec/cognition/CognitionDefinition.java` — the record with 13 nullable sections
- mosaic-core `types/patterns/social.ts` — frontend `SocialPatternCategory` and `CognitionConfig`
- `packages/agent-viewer/src/views/social/cognition-view.ts` — frontend component consuming the data
- casehubio/casehub-mosaic#288 — typed ConfigSpec interfaces that this endpoint feeds
