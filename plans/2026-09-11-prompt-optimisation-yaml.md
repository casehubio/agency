# Prompt Optimisation YAML Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #250 — feat: Prompt optimisation — YAML surface for LLM tuning
**Issue group:** #246 (epic), #248, #249, #250

**Goal:** Make all 8 prompt optimisation capabilities expressible in YAML via spec records, registries, a compiler, and a deployment processor extension in agentic-yaml.

**Architecture:** Follow the per-domain root type pattern (PatternSpec/WorldDefinition/CognitionDefinition). Three sealed interface specs for named type registries (PromptOptimiserSpec, DiversityStrategySpec, ConfidenceScorerSpec), one adapted record (PromptSignatureSpec), three existing records reused directly (OptimiserConfig, SafetyConfig, FewShotExample). Root type `PromptOptimisationDefinition` discovered from `META-INF/prompt-optimisation.yaml`. Compiler produces `CompiledPromptOptimisation`.

**Tech Stack:** Java 17, Jackson YAML, JUnit 5, AssertJ, Quarkus deployment API (build items, recorder)

## Global Constraints

- All new source files in `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/`
- All new test files in `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/`
- Deployment files in `agentic-yaml-deployment/src/main/java/io/casehub/blocks/agentic/yaml/deployment/`
- Follow existing sealed interface + @JsonSubTypes pattern
- No new Maven dependencies — blocks (compile) and neocortex-memory-api (provided) already present
- Tests are plain JUnit 5 + AssertJ — no Quarkus test runtime

---

## Batch 1: Spec Records + Serialization Tests

### Task 1: PromptOptimiserSpec, DiversityStrategySpec, ConfidenceScorerSpec sealed interfaces

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/PromptOptimiserSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/DiversityStrategySpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ConfidenceScorerSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/PromptSignatureSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/PromptOptimisationPipelineSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/PromptOptimisationDefinition.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/PromptOptimisationSpecSerializationTest.java`

**Interfaces:**
- Produces: `PromptOptimiserSpec` (sealed: FewShot, Instruction), `DiversityStrategySpec` (sealed: TopN, OutcomeAware), `ConfidenceScorerSpec` (sealed: Arousal, Surprise, Composite + WeightedScorerEntry), `PromptSignatureSpec` (record), `PromptOptimisationPipelineSpec` (record), `PromptOptimisationDefinition` (record)

- [ ] **Step 1: Write PromptOptimiserSpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = PromptOptimiserSpec.FewShot.class, name = "few-shot"),
        @Type(value = PromptOptimiserSpec.Instruction.class, name = "instruction")
})
public sealed interface PromptOptimiserSpec {

    record FewShot(@Nullable DiversityStrategySpec diversity) implements PromptOptimiserSpec {}

    record Instruction() implements PromptOptimiserSpec {}
}
```

- [ ] **Step 2: Write DiversityStrategySpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

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

- [ ] **Step 3: Write ConfidenceScorerSpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.util.List;
import java.util.Objects;

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

- [ ] **Step 4: Write PromptSignatureSpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;
import java.util.Objects;

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

- [ ] **Step 5: Write PromptOptimisationPipelineSpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import io.casehub.blocks.prompt.FewShotExample;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.SafetyConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

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
```

- [ ] **Step 6: Write PromptOptimisationDefinition**

```java
package io.casehub.blocks.agentic.yaml.spec;

import java.util.Map;

public record PromptOptimisationDefinition(
        Map<String, PromptOptimisationPipelineSpec> pipelines) {

    public PromptOptimisationDefinition {
        if (pipelines == null || pipelines.isEmpty())
            throw new IllegalArgumentException("at least one pipeline required");
        pipelines = Map.copyOf(pipelines);
    }
}
```

- [ ] **Step 7: Write serialization test**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptOptimisationSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
    }

    @Nested
    class PromptOptimiserSpecs {
        @Test
        void fewShotWithoutDiversity() throws Exception {
            var spec = mapper.readValue("type: few-shot", PromptOptimiserSpec.class);
            assertThat(spec).isInstanceOf(PromptOptimiserSpec.FewShot.class);
            assertThat(((PromptOptimiserSpec.FewShot) spec).diversity()).isNull();
        }

        @Test
        void fewShotWithDiversity() throws Exception {
            var yaml = """
                    type: few-shot
                    diversity:
                      type: outcome-aware
                      weight: 0.3
                    """;
            var spec = mapper.readValue(yaml, PromptOptimiserSpec.class);
            assertThat(spec).isInstanceOf(PromptOptimiserSpec.FewShot.class);
            var fs = (PromptOptimiserSpec.FewShot) spec;
            assertThat(fs.diversity()).isInstanceOf(DiversityStrategySpec.OutcomeAware.class);
            assertThat(((DiversityStrategySpec.OutcomeAware) fs.diversity()).weight()).isEqualTo(0.3);
        }

        @Test
        void instruction() throws Exception {
            var spec = mapper.readValue("type: instruction", PromptOptimiserSpec.class);
            assertThat(spec).isInstanceOf(PromptOptimiserSpec.Instruction.class);
        }
    }

    @Nested
    class DiversityStrategySpecs {
        @Test
        void topN() throws Exception {
            var spec = mapper.readValue("type: top-n", DiversityStrategySpec.class);
            assertThat(spec).isInstanceOf(DiversityStrategySpec.TopN.class);
        }

        @Test
        void outcomeAware() throws Exception {
            var yaml = """
                    type: outcome-aware
                    weight: 0.5
                    """;
            var spec = mapper.readValue(yaml, DiversityStrategySpec.class);
            assertThat(spec).isInstanceOf(DiversityStrategySpec.OutcomeAware.class);
            assertThat(((DiversityStrategySpec.OutcomeAware) spec).weight()).isEqualTo(0.5);
        }

        @Test
        void outcomeAwareInvalidWeight() {
            assertThatThrownBy(() -> new DiversityStrategySpec.OutcomeAware(1.5))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ConfidenceScorerSpecs {
        @Test
        void arousal() throws Exception {
            var spec = mapper.readValue("type: arousal", ConfidenceScorerSpec.class);
            assertThat(spec).isInstanceOf(ConfidenceScorerSpec.Arousal.class);
        }

        @Test
        void surprise() throws Exception {
            var spec = mapper.readValue("type: surprise", ConfidenceScorerSpec.class);
            assertThat(spec).isInstanceOf(ConfidenceScorerSpec.Surprise.class);
        }

        @Test
        void composite() throws Exception {
            var yaml = """
                    type: composite
                    scorers:
                      - scorer:
                          type: arousal
                        weight: 0.6
                      - scorer:
                          type: surprise
                        weight: 0.4
                    """;
            var spec = mapper.readValue(yaml, ConfidenceScorerSpec.class);
            assertThat(spec).isInstanceOf(ConfidenceScorerSpec.Composite.class);
            var composite = (ConfidenceScorerSpec.Composite) spec;
            assertThat(composite.scorers()).hasSize(2);
            assertThat(composite.scorers().get(0).scorer()).isInstanceOf(ConfidenceScorerSpec.Arousal.class);
            assertThat(composite.scorers().get(0).weight()).isEqualTo(0.6);
        }

        @Test
        void compositeEmpty() {
            assertThatThrownBy(() -> new ConfidenceScorerSpec.Composite(java.util.List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class PipelineSpecs {
        @Test
        void fullPipeline() throws Exception {
            var yaml = """
                    signature:
                      id: test-prompt
                      baseSystemPrompt: "Test prompt"
                    optimiser:
                      type: few-shot
                    config:
                      maxExamples: 3
                      minQualityThreshold: 0.8
                      minOutcomeCount: 50
                      minVariantOutcomes: 20
                    safety:
                      qualityFloor: 0.3
                      maxExperimentCycles: 5
                      maxExperimentAge: P30D
                      circuitBreakerThreshold: 5
                      enabled: true
                    examples:
                      - input: test-input
                        output: test-output
                        outcome: SUCCESS
                        qualityScore: 0.9
                    """;
            var spec = mapper.readValue(yaml, PromptOptimisationPipelineSpec.class);
            assertThat(spec.signature().id()).isEqualTo("test-prompt");
            assertThat(spec.optimiser()).isInstanceOf(PromptOptimiserSpec.FewShot.class);
            assertThat(spec.config().maxExamples()).isEqualTo(3);
            assertThat(spec.safety().qualityFloor()).isEqualTo(0.3);
            assertThat(spec.examples()).hasSize(1);
        }

        @Test
        void minimalPipeline() throws Exception {
            var yaml = """
                    signature:
                      id: minimal
                      baseSystemPrompt: "Minimal"
                    """;
            var spec = mapper.readValue(yaml, PromptOptimisationPipelineSpec.class);
            assertThat(spec.signature().id()).isEqualTo("minimal");
            assertThat(spec.optimiser()).isNull();
            assertThat(spec.config()).isNull();
            assertThat(spec.safety()).isNull();
            assertThat(spec.examples()).isNull();
        }
    }

    @Nested
    class DefinitionSpecs {
        @Test
        void fullDefinition() throws Exception {
            var yaml = """
                    pipelines:
                      routing:
                        signature:
                          id: routing
                          baseSystemPrompt: "Route it"
                      decomposition:
                        signature:
                          id: decomp
                          baseSystemPrompt: "Decompose it"
                        optimiser:
                          type: instruction
                    """;
            var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
            assertThat(def.pipelines()).hasSize(2);
            assertThat(def.pipelines()).containsKeys("routing", "decomposition");
        }
    }
}
```

- [ ] **Step 8: Run tests**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PromptOptimisationSpecSerializationTest`
Expected: All tests PASS

- [ ] **Step 9: Commit**

```bash
git add agentic-yaml/src/
git commit -m "feat(#250): PromptOptimiserSpec, DiversityStrategySpec, ConfidenceScorerSpec, PromptSignatureSpec, pipeline + definition records"
```

## Batch 2: Registries + Compiler

### Task 2: DiversityStrategyRegistry, PromptOptimiserRegistry, ConfidenceScorerRegistry

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/DiversityStrategyRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/PromptOptimiserRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/ConfidenceScorerRegistry.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/PromptOptimisationRegistryTest.java`

**Interfaces:**
- Consumes: `PromptOptimiserSpec`, `DiversityStrategySpec`, `ConfidenceScorerSpec` from Task 1
- Produces: `DiversityStrategyRegistry.resolve(DiversityStrategySpec) → DiversityStrategy`, `PromptOptimiserRegistry.resolve(PromptOptimiserSpec, @Nullable AgentProvider) → PromptOptimiser`, `ConfidenceScorerRegistry.resolve(ConfidenceScorerSpec) → ConfidenceScorer`

- [ ] **Step 1: Write failing registry tests**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.ConfidenceScorerSpec;
import io.casehub.blocks.agentic.yaml.spec.DiversityStrategySpec;
import io.casehub.blocks.agentic.yaml.spec.PromptOptimiserSpec;
import io.casehub.blocks.memory.ArousalScorer;
import io.casehub.blocks.memory.CompositeConfidenceScorer;
import io.casehub.blocks.memory.SurpriseScorer;
import io.casehub.blocks.prompt.optimiser.FewShotOptimiser;
import io.casehub.blocks.prompt.optimiser.InstructionOptimiser;
import io.casehub.blocks.prompt.optimiser.OutcomeAwareDiversityStrategy;
import io.casehub.blocks.prompt.optimiser.TopNDiversityStrategy;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PromptOptimisationRegistryTest {

    @Nested
    class DiversityStrategyRegistryTests {
        private final DiversityStrategyRegistry registry = new DiversityStrategyRegistry();

        @Test
        void topN() {
            var result = registry.resolve(new DiversityStrategySpec.TopN());
            assertThat(result).isInstanceOf(TopNDiversityStrategy.class);
        }

        @Test
        void outcomeAware() {
            var result = registry.resolve(new DiversityStrategySpec.OutcomeAware(0.5));
            assertThat(result).isInstanceOf(OutcomeAwareDiversityStrategy.class);
        }
    }

    @Nested
    class PromptOptimiserRegistryTests {
        private final PromptOptimiserRegistry registry =
                new PromptOptimiserRegistry(new DiversityStrategyRegistry());

        @Test
        void fewShotDefault() {
            var result = registry.resolve(new PromptOptimiserSpec.FewShot(null), null);
            assertThat(result).isInstanceOf(FewShotOptimiser.class);
        }

        @Test
        void fewShotWithDiversity() {
            var spec = new PromptOptimiserSpec.FewShot(
                    new DiversityStrategySpec.OutcomeAware(0.3));
            var result = registry.resolve(spec, null);
            assertThat(result).isInstanceOf(FewShotOptimiser.class);
        }

        @Test
        void instructionWithProvider() {
            var provider = mock(AgentProvider.class);
            var result = registry.resolve(new PromptOptimiserSpec.Instruction(), provider);
            assertThat(result).isInstanceOf(InstructionOptimiser.class);
        }

        @Test
        void instructionWithoutProviderThrows() {
            assertThatThrownBy(() ->
                    registry.resolve(new PromptOptimiserSpec.Instruction(), null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AgentProvider");
        }
    }

    @Nested
    class ConfidenceScorerRegistryTests {
        private final ConfidenceScorerRegistry registry = new ConfidenceScorerRegistry();

        @Test
        void arousal() {
            var result = registry.resolve(new ConfidenceScorerSpec.Arousal());
            assertThat(result).isInstanceOf(ArousalScorer.class);
        }

        @Test
        void surprise() {
            var result = registry.resolve(new ConfidenceScorerSpec.Surprise());
            assertThat(result).isInstanceOf(SurpriseScorer.class);
        }

        @Test
        void composite() {
            var spec = new ConfidenceScorerSpec.Composite(List.of(
                    new ConfidenceScorerSpec.WeightedScorerEntry(
                            new ConfidenceScorerSpec.Arousal(), 0.6),
                    new ConfidenceScorerSpec.WeightedScorerEntry(
                            new ConfidenceScorerSpec.Surprise(), 0.4)));
            var result = registry.resolve(spec);
            assertThat(result).isInstanceOf(CompositeConfidenceScorer.class);
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PromptOptimisationRegistryTest`
Expected: FAIL — registry classes don't exist

- [ ] **Step 3: Write DiversityStrategyRegistry**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.DiversityStrategySpec;
import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.optimiser.OutcomeAwareDiversityStrategy;
import io.casehub.blocks.prompt.optimiser.TopNDiversityStrategy;

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

- [ ] **Step 4: Write PromptOptimiserRegistry**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.PromptOptimiserSpec;
import io.casehub.blocks.prompt.PromptOptimiser;
import io.casehub.blocks.prompt.optimiser.FewShotOptimiser;
import io.casehub.blocks.prompt.optimiser.InstructionOptimiser;
import io.casehub.blocks.prompt.optimiser.TopNDiversityStrategy;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

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

- [ ] **Step 5: Write ConfidenceScorerRegistry**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.ConfidenceScorerSpec;
import io.casehub.blocks.memory.ArousalScorer;
import io.casehub.blocks.memory.CompositeConfidenceScorer;
import io.casehub.blocks.memory.ConfidenceScorer;
import io.casehub.blocks.memory.SurpriseScorer;
import io.casehub.blocks.memory.WeightedScorer;

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

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PromptOptimisationRegistryTest`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add agentic-yaml/src/
git commit -m "feat(#250): DiversityStrategyRegistry, PromptOptimiserRegistry, ConfidenceScorerRegistry"
```

### Task 3: PromptOptimisationCompiler + CompiledPromptOptimisation

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/CompiledPromptOptimisation.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/PromptOptimisationCompiler.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/compiler/PromptOptimisationCompilerTest.java`

**Interfaces:**
- Consumes: `PromptOptimisationDefinition`, `PromptOptimiserRegistry`, `ConfidenceScorerRegistry` from Tasks 1-2
- Produces: `PromptOptimisationCompiler.compile(PromptOptimisationDefinition) → CompiledPromptOptimisation`, `CompiledPromptOptimisation.pipelines() → Map<String, CompiledPipeline>`

- [ ] **Step 1: Write CompiledPromptOptimisation**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.memory.ConfidenceScorer;
import io.casehub.blocks.prompt.FewShotExample;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.PromptOptimiser;
import io.casehub.blocks.prompt.PromptSignature;
import io.casehub.blocks.prompt.SafetyConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record CompiledPromptOptimisation(Map<String, CompiledPipeline> pipelines) {

    public record CompiledPipeline(
            PromptSignature signature,
            PromptOptimiser optimiser,
            OptimiserConfig config,
            SafetyConfig safety,
            @Nullable ConfidenceScorer confidenceScorer,
            List<FewShotExample> examples) {}
}
```

- [ ] **Step 2: Write failing compiler tests**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.registry.ConfidenceScorerRegistry;
import io.casehub.blocks.agentic.yaml.registry.DiversityStrategyRegistry;
import io.casehub.blocks.agentic.yaml.registry.PromptOptimiserRegistry;
import io.casehub.blocks.agentic.yaml.spec.PromptOptimisationDefinition;
import io.casehub.blocks.memory.CompositeConfidenceScorer;
import io.casehub.blocks.prompt.optimiser.FewShotOptimiser;
import io.casehub.blocks.prompt.optimiser.InstructionOptimiser;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PromptOptimisationCompilerTest {

    private ObjectMapper mapper;
    private PromptOptimisationCompiler compiler;
    private PromptOptimisationCompiler compilerWithAgent;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        var diversityRegistry = new DiversityStrategyRegistry();
        var optimiserRegistry = new PromptOptimiserRegistry(diversityRegistry);
        var scorerRegistry = new ConfidenceScorerRegistry();
        compiler = new PromptOptimisationCompiler(optimiserRegistry, scorerRegistry, null);
        compilerWithAgent = new PromptOptimisationCompiler(
                optimiserRegistry, scorerRegistry, mock(AgentProvider.class));
    }

    @Test
    void compilesMinimalPipeline() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compiler.compile(def);
        assertThat(compiled.pipelines()).hasSize(1);
        var pipeline = compiled.pipelines().get("test");
        assertThat(pipeline.signature().id()).isEqualTo("test");
        assertThat(pipeline.optimiser()).isInstanceOf(FewShotOptimiser.class);
        assertThat(pipeline.config()).isEqualTo(
                io.casehub.blocks.prompt.OptimiserConfig.defaults());
        assertThat(pipeline.safety()).isEqualTo(
                io.casehub.blocks.prompt.SafetyConfig.defaults());
        assertThat(pipeline.confidenceScorer()).isNull();
        assertThat(pipeline.examples()).isEmpty();
    }

    @Test
    void compilesWithInstructionOptimiser() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                    optimiser:
                      type: instruction
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compilerWithAgent.compile(def);
        assertThat(compiled.pipelines().get("test").optimiser())
                .isInstanceOf(InstructionOptimiser.class);
    }

    @Test
    void compilesWithConfidenceScorer() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                    confidenceScorer:
                      type: composite
                      scorers:
                        - scorer:
                            type: arousal
                          weight: 0.7
                        - scorer:
                            type: surprise
                          weight: 0.3
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compiler.compile(def);
        assertThat(compiled.pipelines().get("test").confidenceScorer())
                .isInstanceOf(CompositeConfidenceScorer.class);
    }

    @Test
    void compilesWithExamples() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                    examples:
                      - input: in
                        output: out
                        outcome: SUCCESS
                        qualityScore: 0.9
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compiler.compile(def);
        assertThat(compiled.pipelines().get("test").examples()).hasSize(1);
        assertThat(compiled.pipelines().get("test").examples().get(0).input()).isEqualTo("in");
    }

    @Test
    void compilesSignatureWithTypeRefs() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                      inputType: java.lang.String
                      outputType: java.lang.Integer
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compiler.compile(def);
        assertThat(compiled.pipelines().get("test").signature().inputType())
                .isEqualTo(String.class);
        assertThat(compiled.pipelines().get("test").signature().outputType())
                .isEqualTo(Integer.class);
    }

    @Test
    void compilesMultiplePipelines() throws Exception {
        var yaml = """
                pipelines:
                  routing:
                    signature:
                      id: routing
                      baseSystemPrompt: "Route"
                  decomp:
                    signature:
                      id: decomp
                      baseSystemPrompt: "Decompose"
                    optimiser:
                      type: instruction
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        var compiled = compilerWithAgent.compile(def);
        assertThat(compiled.pipelines()).hasSize(2);
        assertThat(compiled.pipelines().get("routing").optimiser())
                .isInstanceOf(FewShotOptimiser.class);
        assertThat(compiled.pipelines().get("decomp").optimiser())
                .isInstanceOf(InstructionOptimiser.class);
    }

    @Test
    void invalidClassNameThrows() throws Exception {
        var yaml = """
                pipelines:
                  test:
                    signature:
                      id: test
                      baseSystemPrompt: "Test"
                      inputType: com.nonexistent.Fake
                """;
        var def = mapper.readValue(yaml, PromptOptimisationDefinition.class);
        assertThatThrownBy(() -> compiler.compile(def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("com.nonexistent.Fake");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PromptOptimisationCompilerTest`
Expected: FAIL — PromptOptimisationCompiler doesn't exist

- [ ] **Step 4: Write PromptOptimisationCompiler**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.yaml.registry.ConfidenceScorerRegistry;
import io.casehub.blocks.agentic.yaml.registry.PromptOptimiserRegistry;
import io.casehub.blocks.agentic.yaml.spec.PromptOptimisationDefinition;
import io.casehub.blocks.agentic.yaml.spec.PromptOptimisationPipelineSpec;
import io.casehub.blocks.agentic.yaml.spec.PromptSignatureSpec;
import io.casehub.blocks.prompt.FewShotExample;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.PromptSignature;
import io.casehub.blocks.prompt.SafetyConfig;
import io.casehub.blocks.prompt.optimiser.FewShotOptimiser;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;

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
        var examples = spec.examples() != null
                ? List.copyOf(spec.examples())
                : List.<FewShotExample>of();
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

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PromptOptimisationCompilerTest`
Expected: All tests PASS

- [ ] **Step 6: Run full agentic-yaml test suite**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: All tests PASS (no regressions)

- [ ] **Step 7: Commit**

```bash
git add agentic-yaml/src/
git commit -m "feat(#250): PromptOptimisationCompiler + CompiledPromptOptimisation"
```

## Batch 3: Deployment + Schema + Coverage Update

### Task 4: Deployment processor extension + schema generation + coverage matrix

**Files:**
- Create: `agentic-yaml-deployment/src/main/java/io/casehub/blocks/agentic/yaml/deployment/PromptOptimisationBuildItem.java`
- Modify: `agentic-yaml-deployment/src/main/java/io/casehub/blocks/agentic/yaml/deployment/AgenticYamlProcessor.java`
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/runtime/AgenticRecorder.java`
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/schema/BlocksSchemaGenerator.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/schema/SchemaGenerationTest.java` (extend)
- Modify: `docs/yaml-coverage.md` (update §17)

**Interfaces:**
- Consumes: `PromptOptimisationDefinition`, `PromptOptimisationCompiler` from Tasks 1-3

- [ ] **Step 1: Write PromptOptimisationBuildItem**

```java
package io.casehub.blocks.agentic.yaml.deployment;

import io.casehub.blocks.agentic.yaml.spec.PromptOptimisationDefinition;
import io.quarkus.builder.item.SimpleBuildItem;
import org.jspecify.annotations.Nullable;

public final class PromptOptimisationBuildItem extends SimpleBuildItem {

    private final @Nullable PromptOptimisationDefinition definition;

    public PromptOptimisationBuildItem(
            @Nullable PromptOptimisationDefinition definition) {
        this.definition = definition;
    }

    public @Nullable PromptOptimisationDefinition definition() {
        return definition;
    }
}
```

- [ ] **Step 2: Add @BuildStep to AgenticYamlProcessor**

Add this method to `AgenticYamlProcessor`:

```java
@BuildStep
PromptOptimisationBuildItem discoverPromptOptimisationConfig() throws IOException {
    ClassLoader cl       = Thread.currentThread().getContextClassLoader();
    URL         resource = cl.getResource("META-INF/prompt-optimisation.yaml");
    if (resource == null) {
        resource = cl.getResource("META-INF/prompt-optimisation.yml");
    }
    if (resource == null) {return new PromptOptimisationBuildItem(null);}
    try (InputStream in = resource.openStream()) {
        var definition = YAML.readValue(in,
                io.casehub.blocks.agentic.yaml.spec.PromptOptimisationDefinition.class);
        return new PromptOptimisationBuildItem(definition);
    }
}
```

Add the import for `PromptOptimisationBuildItem`.

- [ ] **Step 3: Extend AgenticRecorder**

Add this method to `AgenticRecorder`:

```java
public PromptOptimisationCompiler createPromptOptimisationCompiler(
        @Nullable AgentProvider agentProvider) {
    var diversityRegistry = new DiversityStrategyRegistry();
    var optimiserRegistry = new PromptOptimiserRegistry(diversityRegistry);
    var scorerRegistry = new ConfidenceScorerRegistry();
    return new PromptOptimisationCompiler(
            optimiserRegistry, scorerRegistry, agentProvider);
}
```

Add the required imports for `PromptOptimisationCompiler`, `DiversityStrategyRegistry`, `PromptOptimiserRegistry`, `ConfidenceScorerRegistry`, `AgentProvider`, and `@Nullable`.

- [ ] **Step 4: Extend BlocksSchemaGenerator**

Add the three new sealed interface types to the discriminator overrides list in `buildDiscriminatorOverrides()`:

```java
io.casehub.blocks.agentic.yaml.spec.PromptOptimiserSpec.class,
io.casehub.blocks.agentic.yaml.spec.DiversityStrategySpec.class,
io.casehub.blocks.agentic.yaml.spec.ConfidenceScorerSpec.class
```

Append these three entries after `TaskNodeSpec.class` in the `List.of(...)`.

- [ ] **Step 5: Write failing schema test**

Add to `SchemaGenerationTest`:

```java
@Test
void promptOptimiserSpecSchemaHasTypes() {
    JsonNode schema = generator.generate(PromptOptimiserSpec.class);
    assertThat(schema).isNotNull();
    var schemaText = schema.toString();
    assertThat(schemaText).contains("few-shot");
    assertThat(schemaText).contains("instruction");
}

@Test
void confidenceScorerSpecSchemaHasTypes() {
    JsonNode schema = generator.generate(ConfidenceScorerSpec.class);
    assertThat(schema).isNotNull();
    var schemaText = schema.toString();
    assertThat(schemaText).contains("arousal");
    assertThat(schemaText).contains("surprise");
    assertThat(schemaText).contains("composite");
}

@Test
void promptOptimisationDefinitionSchemaHasPipelines() {
    JsonNode schema = generator.generate(PromptOptimisationDefinition.class);
    assertThat(schema).isNotNull();
    var schemaText = schema.toString();
    assertThat(schemaText).contains("pipelines");
}
```

Add the imports for `PromptOptimiserSpec`, `ConfidenceScorerSpec`, and `PromptOptimisationDefinition`.

- [ ] **Step 6: Run schema tests**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=SchemaGenerationTest`
Expected: All tests PASS (including new ones)

- [ ] **Step 7: Update coverage matrix §17**

In `docs/yaml-coverage.md`, replace the §17 table with all statuses set to Done. Update the summary row for blocks in the totals table.

- [ ] **Step 8: Run full test suite**

Run: `mvn --batch-mode test`
Expected: All tests PASS across all modules

- [ ] **Step 9: Commit**

```bash
git add agentic-yaml/src/ agentic-yaml-deployment/src/ docs/yaml-coverage.md
git commit -m "feat(#250): deployment processor, schema generation, coverage matrix update for prompt optimisation YAML"
```

## References

- [2026-09-11-prompt-optimisation-yaml-design.md] — design spec this plan implements
- [agentic-yaml/src/main/java/.../spec/AggregationSpec.java] — sealed interface pattern reference
- [agentic-yaml/src/main/java/.../registry/ConflictResolutionRegistry.java] — registry pattern reference
- [agentic-yaml/src/main/java/.../compiler/NegotiationCompiler.java] — compiler pattern reference
- [agentic-yaml/src/test/java/.../spec/SecondarySpecSerializationTest.java] — test pattern reference
- [agentic-yaml/src/test/java/.../compiler/NegotiationCompilerTest.java] — compiler test pattern reference
- [agentic-yaml-deployment/src/main/java/.../AgenticYamlProcessor.java] — @BuildStep pattern
- [agentic-yaml-deployment/src/main/java/.../CognitionConfigBuildItem.java] — build item pattern
- [agentic-yaml/src/main/java/.../runtime/AgenticRecorder.java] — recorder pattern
- [agentic-yaml/src/main/java/.../schema/BlocksSchemaGenerator.java] — schema generation extension point
- [blocks/src/main/java/io/casehub/blocks/prompt/] — domain types being surfaced
- [blocks/src/main/java/io/casehub/blocks/memory/ConfidenceScorer.java] — memory scorer types
- [GE-20260909-865486] — victools SealedHierarchyModule discriminator mismatch
- [GitHub #250] — focal issue
