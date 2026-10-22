# Prompt Optimisation — YAML Surface

**Issue:** casehubio/blocks#250
**Date:** 2026-09-11
**Scope:** 8 capabilities from coverage matrix §17 → spec records, registries, compiler, deployment processor extension

## Problem

The prompt optimisation framework (`blocks.prompt`) is fully functional in Java but has
no YAML surface. Consumers must construct `OptimiserConfig`, `SafetyConfig`,
`PromptSignature`, `FewShotExample`, `PromptOptimiser`, `DiversityStrategy`, and
`ConfidenceScorer` instances programmatically. This blocks YAML-only application
definition for any capability that uses prompt tuning.

## Solution

Add a `prompt-optimisation.yaml` domain to agentic-yaml — following the per-domain root
type pattern established by PatternSpec, WorldDefinition, and CognitionDefinition.

Three categories of work:
1. **Named type registries** — 3 new sealed interface specs + 3 registries
2. **Adapted records** — 1 new spec record for `PromptSignature` (Class<?> → String)
3. **Direct reuse** — 3 existing records used as-is (OptimiserConfig, SafetyConfig, FewShotExample)

All code goes in agentic-yaml. Zero new Maven dependencies — `blocks` is already compile,
`neocortex-memory-api` is already provided.

## YAML Structure

File: `META-INF/prompt-optimisation.yaml`

```yaml
pipelines:
  routing-prompt:
    signature:
      id: routing-prompt
      description: "LLM-based agent routing"
      baseSystemPrompt: "Evaluate which agent best fits the task..."
      inputType: io.casehub.agency.governance.routing.RoutingContext
      outputType: io.casehub.agency.governance.routing.RoutingDecision

    optimiser:
      type: few-shot
      diversity:
        type: outcome-aware
        weight: 0.3

    config:
      maxExamples: 5
      minQualityThreshold: 0.7
      minOutcomeCount: 50
      minVariantOutcomes: 20

    safety:
      qualityFloor: 0.3
      maxExperimentCycles: 5
      maxExperimentAge: P30D
      circuitBreakerThreshold: 5
      enabled: true

    confidenceScorer:
      type: composite
      scorers:
        - scorer:
            type: arousal
          weight: 0.6
        - scorer:
            type: surprise
          weight: 0.4

    examples:
      - input: "Emergency medical case"
        output: "medical-specialist"
        outcome: SUCCESS
        qualityScore: 0.95
      - input: "Routine document review"
        output: "general-agent"
        outcome: SUCCESS
        qualityScore: 0.85

  decomposition-prompt:
    signature:
      id: decomposition-prompt
      description: "LLM-based task decomposition"
      baseSystemPrompt: "Break the following task into subtasks..."

    optimiser:
      type: instruction

    config:
      maxExamples: 3
      minQualityThreshold: 0.8
      minOutcomeCount: 100
      minVariantOutcomes: 30
```

The root is a `Map<String, PromptOptimisationPipelineSpec>` keyed by pipeline name.
All fields except `signature` are optional — defaults are applied at compilation.

## Spec Records

### PromptOptimiserSpec (named type registry)

```java
package io.casehub.agency.core.yaml.spec;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = PromptOptimiserSpec.FewShot.class, name = "few-shot"),
    @Type(value = PromptOptimiserSpec.Instruction.class, name = "instruction")
})
public sealed interface PromptOptimiserSpec {

    record FewShot(@Nullable DiversityStrategySpec diversity)
            implements PromptOptimiserSpec {}

    record Instruction() implements PromptOptimiserSpec {}
}
```

- `FewShot` optionally nests a `DiversityStrategySpec`. When null, `TopNDiversityStrategy` is used (matching `FewShotOptimiser` no-arg constructor default).
- `Instruction` is stateless — `AgentProvider` is injected at runtime by the recorder.

### DiversityStrategySpec (named type registry)

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = DiversityStrategySpec.TopN.class, name = "top-n"),
    @Type(value = DiversityStrategySpec.OutcomeAware.class, name = "outcome-aware")
})
public sealed interface DiversityStrategySpec {

    record TopN() implements DiversityStrategySpec {}

    record OutcomeAware(double weight) implements DiversityStrategySpec {
        public OutcomeAware {
            if (weight < 0 || weight > 1)
                throw new IllegalArgumentException("weight must be in [0, 1]");
        }
    }
}
```

### ConfidenceScorerSpec (named type registry)

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = ConfidenceScorerSpec.Arousal.class, name = "arousal"),
    @Type(value = ConfidenceScorerSpec.Surprise.class, name = "surprise"),
    @Type(value = ConfidenceScorerSpec.Composite.class, name = "composite")
})
public sealed interface ConfidenceScorerSpec {

    record Arousal() implements ConfidenceScorerSpec {}

    record Surprise() implements ConfidenceScorerSpec {}

    record Composite(List<WeightedScorerEntry> scorers) implements ConfidenceScorerSpec {
        public Composite {
            if (scorers == null || scorers.isEmpty())
                throw new IllegalArgumentException("at least one scorer required");
            scorers = List.copyOf(scorers);
        }
    }

    record WeightedScorerEntry(ConfidenceScorerSpec scorer, double weight) {
        public WeightedScorerEntry {
            Objects.requireNonNull(scorer, "scorer");
            if (weight <= 0) throw new IllegalArgumentException("weight must be positive");
        }
    }
}
```

`WeightedScorerEntry` nests `ConfidenceScorerSpec` recursively — a Composite can contain
other Composites. The YAML property is `type` (the Jackson discriminator for the nested
scorer spec), matching the outer discriminator pattern.

### PromptSignatureSpec (adapted record)

```java
public record PromptSignatureSpec(
        String id,
        @Nullable String description,
        String baseSystemPrompt,
        @Nullable String inputType,
        @Nullable String outputType) {

    public PromptSignatureSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(baseSystemPrompt, "baseSystemPrompt");
    }
}
```

`inputType`/`outputType` are fully-qualified class names as strings. The compiler resolves
them via `Class.forName()`. Both nullable — many consumers don't need type metadata.

### Directly Reused Records

These are already Jackson-compatible and used as-is in the YAML:

| Record | Package | Notes |
|--------|---------|-------|
| `OptimiserConfig` | `blocks.prompt` | 4 fields, compact constructor validation, `defaults()` factory |
| `SafetyConfig` | `blocks.prompt` | 5 fields including `Duration` (jackson-datatype-jsr310), `defaults()` factory |
| `FewShotExample` | `blocks.prompt` | 5 fields, `@Nullable annotation` |

No wrapper spec records — these types have zero domain dependencies and are already
pure Jackson-deserializable records.

### Root Aggregation Types

```java
public record PromptOptimisationPipelineSpec(
        PromptSignatureSpec signature,
        @Nullable PromptOptimiserSpec optimiser,
        @Nullable OptimiserConfig config,
        @Nullable SafetyConfig safety,
        @Nullable ConfidenceScorerSpec confidenceScorer,
        @Nullable List<FewShotExample> examples) {

    public PromptOptimisationPipelineSpec {
        Objects.requireNonNull(signature, "signature");
    }
}

public record PromptOptimisationDefinition(
        Map<String, PromptOptimisationPipelineSpec> pipelines) {

    public PromptOptimisationDefinition {
        if (pipelines == null || pipelines.isEmpty())
            throw new IllegalArgumentException("at least one pipeline required");
        pipelines = Map.copyOf(pipelines);
    }
}
```

## Compiled Output

```java
public record CompiledPromptOptimisation(
        Map<String, CompiledPipeline> pipelines) {

    public record CompiledPipeline(
            PromptSignature signature,
            PromptOptimiser optimiser,
            OptimiserConfig config,
            SafetyConfig safety,
            @Nullable ConfidenceScorer confidenceScorer,
            List<FewShotExample> examples) {}
}
```

The compiled output preserves the pipeline map structure. Each pipeline is fully resolved
with concrete instances ready for use.

## Registries

### PromptOptimiserRegistry

```java
public class PromptOptimiserRegistry {

    private final DiversityStrategyRegistry diversityRegistry;

    public PromptOptimiserRegistry(DiversityStrategyRegistry diversityRegistry) {
        this.diversityRegistry = diversityRegistry;
    }

    public PromptOptimiser resolve(PromptOptimiserSpec spec,
                                    @Nullable AgentProvider agentProvider) {
        return switch (spec) {
            case PromptOptimiserSpec.FewShot fs -> {
                var diversity = fs.diversity() != null
                        ? diversityRegistry.resolve(fs.diversity())
                        : new TopNDiversityStrategy();
                yield new FewShotOptimiser(diversity);
            }
            case PromptOptimiserSpec.Instruction ignored -> {
                if (agentProvider == null)
                    throw new IllegalStateException(
                            "instruction optimiser requires AgentProvider");
                yield new InstructionOptimiser(agentProvider);
            }
        };
    }
}
```

`AgentProvider` is nullable — passed from the compiler, injected by the recorder at
runtime. Fail-fast when instruction type is specified without an available provider.

### DiversityStrategyRegistry

```java
public class DiversityStrategyRegistry {

    public DiversityStrategy resolve(DiversityStrategySpec spec) {
        return switch (spec) {
            case DiversityStrategySpec.TopN ignored -> new TopNDiversityStrategy();
            case DiversityStrategySpec.OutcomeAware oa ->
                    new OutcomeAwareDiversityStrategy(oa.weight());
        };
    }
}
```

### ConfidenceScorerRegistry

```java
public class ConfidenceScorerRegistry {

    public ConfidenceScorer resolve(ConfidenceScorerSpec spec) {
        return switch (spec) {
            case ConfidenceScorerSpec.Arousal ignored -> new ArousalScorer();
            case ConfidenceScorerSpec.Surprise ignored -> new SurpriseScorer();
            case ConfidenceScorerSpec.Composite c -> new CompositeConfidenceScorer(
                    c.scorers().stream()
                            .map(ws -> new WeightedScorer(resolve(ws.scorer()), ws.weight()))
                            .toList());
        };
    }
}
```

Recursive resolution — Composite delegates back to `resolve()` for each nested scorer.

## Compiler

```java
public class PromptOptimisationCompiler {

    private final PromptOptimiserRegistry optimiserRegistry;
    private final ConfidenceScorerRegistry confidenceScorerRegistry;
    private final @Nullable AgentProvider agentProvider;

    public PromptOptimisationCompiler(
            PromptOptimiserRegistry optimiserRegistry,
            ConfidenceScorerRegistry confidenceScorerRegistry,
            @Nullable AgentProvider agentProvider) {
        this.optimiserRegistry = optimiserRegistry;
        this.confidenceScorerRegistry = confidenceScorerRegistry;
        this.agentProvider = agentProvider;
    }

    public CompiledPromptOptimisation compile(PromptOptimisationDefinition definition) {
        var compiled = new LinkedHashMap<String, CompiledPromptOptimisation.CompiledPipeline>();
        for (var entry : definition.pipelines().entrySet()) {
            compiled.put(entry.getKey(), compilePipeline(entry.getValue()));
        }
        return new CompiledPromptOptimisation(compiled);
    }

    private CompiledPromptOptimisation.CompiledPipeline compilePipeline(
            PromptOptimisationPipelineSpec spec) {
        var signature = compileSignature(spec.signature());
        var optimiser = spec.optimiser() != null
                ? optimiserRegistry.resolve(spec.optimiser(), agentProvider)
                : new FewShotOptimiser();
        var config = spec.config() != null ? spec.config() : OptimiserConfig.defaults();
        var safety = spec.safety() != null ? spec.safety() : SafetyConfig.defaults();
        var confidenceScorer = spec.confidenceScorer() != null
                ? confidenceScorerRegistry.resolve(spec.confidenceScorer())
                : null;
        var examples = spec.examples() != null ? List.copyOf(spec.examples()) : List.<FewShotExample>of();

        return new CompiledPromptOptimisation.CompiledPipeline(
                signature, optimiser, config, safety, confidenceScorer, examples);
    }

    private PromptSignature compileSignature(PromptSignatureSpec spec) {
        Class<?> inputType = resolveClass(spec.inputType());
        Class<?> outputType = resolveClass(spec.outputType());
        return new PromptSignature(
                spec.id(), spec.description(), spec.baseSystemPrompt(),
                inputType, outputType);
    }

    private static @Nullable Class<?> resolveClass(@Nullable String className) {
        if (className == null) return null;
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown class: " + className, e);
        }
    }
}
```

## Deployment Processor Extension

### Build Item

```java
public final class PromptOptimisationBuildItem extends SimpleBuildItem {

    private final PromptOptimisationDefinition definition;

    public PromptOptimisationBuildItem(PromptOptimisationDefinition definition) {
        this.definition = definition;
    }

    public PromptOptimisationDefinition definition() { return definition; }
}
```

### New @BuildStep in AgenticYamlProcessor

```java
@BuildStep
PromptOptimisationBuildItem processPromptOptimisation(
        ApplicationArchivesBuildItem archives) throws IOException {
    // Scan for META-INF/prompt-optimisation.yaml (or .yml)
    // Deserialize via Jackson to PromptOptimisationDefinition
    // Return build item (null if no file found)
}
```

### AgenticRecorder Extension

```java
@Record(ExecutionTime.RUNTIME_INIT)
@BuildStep
void registerPromptOptimisation(
        AgenticRecorder recorder,
        @Nullable PromptOptimisationBuildItem item) {
    if (item != null) {
        recorder.registerPromptOptimisation(item.definition());
    }
}
```

The recorder creates `PromptOptimisationCompiler` with CDI-resolved `AgentProvider`
(via `Instance<AgentProvider>` — optional, graceful when absent) and compiles the
definition into `CompiledPromptOptimisation`.

## Schema Generation

Extend `AgencySchemaGenerator` to include:
- `PromptOptimisationDefinition` (root)
- `PromptOptimisationPipelineSpec`
- `PromptOptimiserSpec` (sealed hierarchy)
- `DiversityStrategySpec` (sealed hierarchy)
- `ConfidenceScorerSpec` (sealed hierarchy with recursive WeightedScorerEntry)
- `PromptSignatureSpec`

Use existing victools SealedHierarchyModule with discriminator overrides extracted
from `@JsonSubTypes` annotations (per GE-20260909-865486).

## Coverage Matrix Update

After implementation, update `docs/yaml-coverage.md` §17:

| Capability | Status | Notes |
|-----------|--------|-------|
| OptimiserConfig | Done | Direct reuse — no spec wrapper |
| SafetyConfig | Done | Direct reuse — no spec wrapper |
| PromptOptimiser types | Done | PromptOptimiserSpec: few-shot / instruction |
| DiversityStrategy types | Done | DiversityStrategySpec: top-n / outcome-aware |
| FewShotExample | Done | Direct reuse — no spec wrapper |
| PromptVariant | Done | Declarative subset — examples + instructionDelta via pipeline spec |
| PromptSignature | Done | PromptSignatureSpec with string type refs |
| ConfidenceScorer types | Done | ConfidenceScorerSpec: arousal / surprise / composite |

## Test Plan

| Test | What it verifies |
|------|-----------------|
| `PromptOptimisationSpecSerializationTest` | Round-trip YAML serialization for all spec records |
| `PromptOptimiserRegistryTest` | FewShot (with/without diversity), Instruction (with/without AgentProvider) |
| `DiversityStrategyRegistryTest` | TopN, OutcomeAware with valid/invalid weight |
| `ConfidenceScorerRegistryTest` | Arousal, Surprise, Composite (including nested composite) |
| `PromptOptimisationCompilerTest` | Full pipeline compilation with defaults, overrides, Class.forName resolution |

## File Summary

| Path | What it is |
|------|-----------|
| `agentic-yaml/src/main/java/.../spec/PromptOptimiserSpec.java` | Sealed interface: FewShot, Instruction |
| `agentic-yaml/src/main/java/.../spec/DiversityStrategySpec.java` | Sealed interface: TopN, OutcomeAware |
| `agentic-yaml/src/main/java/.../spec/ConfidenceScorerSpec.java` | Sealed interface: Arousal, Surprise, Composite + WeightedScorerEntry |
| `agentic-yaml/src/main/java/.../spec/PromptSignatureSpec.java` | Record: id, description, baseSystemPrompt, inputType, outputType |
| `agentic-yaml/src/main/java/.../spec/PromptOptimisationPipelineSpec.java` | Record: aggregates signature, optimiser, config, safety, scorer, examples |
| `agentic-yaml/src/main/java/.../spec/PromptOptimisationDefinition.java` | Record: root type — Map<String, PipelineSpec> |
| `agentic-yaml/src/main/java/.../registry/PromptOptimiserRegistry.java` | Registry: spec → PromptOptimiser |
| `agentic-yaml/src/main/java/.../registry/DiversityStrategyRegistry.java` | Registry: spec → DiversityStrategy |
| `agentic-yaml/src/main/java/.../registry/ConfidenceScorerRegistry.java` | Registry: spec → ConfidenceScorer |
| `agentic-yaml/src/main/java/.../compiler/PromptOptimisationCompiler.java` | Compiler: definition → CompiledPromptOptimisation |
| `agentic-yaml/src/main/java/.../compiler/CompiledPromptOptimisation.java` | Compiled output record |
| `agentic-yaml-deployment/.../AgenticYamlProcessor.java` | Extended: new @BuildStep |
| `agentic-yaml-deployment/.../PromptOptimisationBuildItem.java` | Build item for deployment |
| `agentic-yaml/src/main/java/.../runtime/AgenticRecorder.java` | Extended: register compiler |

## References

- `blocks/src/main/java/io/casehub/agency/prompt/OptimiserConfig.java` — reused directly
- `blocks/src/main/java/io/casehub/agency/prompt/SafetyConfig.java` — reused directly
- `blocks/src/main/java/io/casehub/agency/prompt/FewShotExample.java` — reused directly
- `blocks/src/main/java/io/casehub/agency/prompt/PromptSignature.java` — adapted (Class<?> → String)
- `blocks/src/main/java/io/casehub/agency/prompt/PromptOptimiser.java` — interface, id-based
- `blocks/src/main/java/io/casehub/agency/prompt/optimiser/FewShotOptimiser.java` — constructor takes DiversityStrategy
- `blocks/src/main/java/io/casehub/agency/prompt/optimiser/InstructionOptimiser.java` — constructor takes AgentProvider
- `blocks/src/main/java/io/casehub/agency/prompt/DiversityStrategy.java` — functional interface
- `blocks/src/main/java/io/casehub/agency/prompt/optimiser/TopNDiversityStrategy.java` — no-arg
- `blocks/src/main/java/io/casehub/agency/prompt/optimiser/OutcomeAwareDiversityStrategy.java` — takes diversityWeight
- `blocks/src/main/java/io/casehub/agency/memory/ConfidenceScorer.java` — functional interface
- `blocks/src/main/java/io/casehub/agency/memory/ArousalScorer.java` — no-arg
- `blocks/src/main/java/io/casehub/agency/memory/SurpriseScorer.java` — no-arg
- `blocks/src/main/java/io/casehub/agency/memory/CompositeConfidenceScorer.java` — takes List<WeightedScorer>
- `agentic-yaml/pom.xml` — neocortex-memory-api already provided scope
- `agentic-yaml-deployment/src/main/java/.../AgenticYamlProcessor.java` — per-domain @BuildStep pattern
- `GE-20260909-865486` — victools SealedHierarchyModule discriminator mismatch gotcha
- `docs/yaml-coverage.md` §17 — 8 capabilities all Gap
