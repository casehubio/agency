# Engine Adapter — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #255 — Engine adapter YAML surface
**Issue group:** #248 (branch covers #246 epic)

**Goal:** Close 5 engine adapter gaps in coverage matrix §21 by adding
spec records, registries, and supporting types.

**Architecture:** Extend existing sealed interfaces
(`ExecutionBackendSpec`, `ExecutionListenerSpec`), create new sealed
interfaces (`VerifierStrategySpec`, `CallerConfigSpec`), and compose them
in `PatternJudgmentConfigSpec`. All in `agentic-yaml` module.

**Tech Stack:** agentic-yaml spec/registry pattern, Jackson
`@JsonTypeInfo`/`@JsonSubTypes`, engine-api types (CallerConfig,
EvidenceRequirement, JudgmentVerifier)

## Global Constraints

- All specs in `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/`
- All registries in `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/`
- Tests in `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/`
- Direct reuse: `EvidenceRequirement`, `EvidenceType`, `JudgmentMode`, `OnThresholdReached`
- Adapted: `CallerConfig` → `CallerConfigSpec`, `QuorumConfig` → `QuorumConfigSpec`
- Existing `CandidateSetStrategySpec` reused for candidate groups/users

---

## Batch 1: Sealed interface extensions + VerifierStrategySpec

### Task 1: EngineHosted, Checkpointing, VerifierStrategySpec

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ExecutionBackendSpec.java`
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/ExecutionBackendRegistry.java`
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ExecutionListenerSpec.java`
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/ExecutionListenerRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/VerifierStrategySpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/VerifierStrategyRegistry.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/ExecutionInfraSpecSerializationTest.java`

**Interfaces:**
- Consumes: `EngineHostedBackend` (engine-adapter), `CheckpointingListener` (engine-adapter)
- Produces: `ExecutionBackendSpec.EngineHosted`, `ExecutionListenerSpec.Checkpointing`, `VerifierStrategySpec` sealed interface, `VerifierStrategyRegistry.resolve(spec) → String`

- [ ] **Step 1: Write failing tests for EngineHosted and Checkpointing deserialization**

Add to `ExecutionInfraSpecSerializationTest.ExecutionBackendSpecs`:

```java
@Test
void engineHosted() throws Exception {
    var spec = mapper.readValue("type: engine-hosted", ExecutionBackendSpec.class);
    assertThat(spec).isInstanceOf(ExecutionBackendSpec.EngineHosted.class);
}
```

Add to `ExecutionInfraSpecSerializationTest.ExecutionListenerSpecs`:

```java
@Test
void checkpointing() throws Exception {
    var spec = mapper.readValue("type: checkpointing", ExecutionListenerSpec.class);
    assertThat(spec).isInstanceOf(ExecutionListenerSpec.Checkpointing.class);
}
```

Add new nested class to `ExecutionInfraSpecSerializationTest`:

```java
@Nested
class VerifierStrategySpecs {
    @Test
    void llmEvaluation() throws Exception {
        var spec = mapper.readValue("type: llm-evaluation", VerifierStrategySpec.class);
        assertThat(spec).isInstanceOf(VerifierStrategySpec.LlmEvaluation.class);
    }

    @Test
    void schemaValidation() throws Exception {
        var spec = mapper.readValue("type: schema-validation", VerifierStrategySpec.class);
        assertThat(spec).isInstanceOf(VerifierStrategySpec.SchemaValidation.class);
    }
}
```

Add registry tests to `ExecutionInfraSpecSerializationTest.RegistryTests`:

```java
@Test
void executionBackendRegistryEngineHosted() {
    var registry = new ExecutionBackendRegistry(new EventConcurrencyPolicyRegistry());
    assertThatThrownBy(() -> registry.resolve(new ExecutionBackendSpec.EngineHosted()))
            .isInstanceOf(UnsupportedOperationException.class);
}

@Test
void executionListenerRegistryCheckpointing() {
    var registry = new ExecutionListenerRegistry();
    assertThatThrownBy(() -> registry.resolve(new ExecutionListenerSpec.Checkpointing(), null, null, null))
            .isInstanceOf(UnsupportedOperationException.class);
}

@Test
void verifierStrategyRegistry() {
    var registry = new VerifierStrategyRegistry();
    assertThat(registry.resolve(new VerifierStrategySpec.LlmEvaluation())).isEqualTo("llm-evaluation");
    assertThat(registry.resolve(new VerifierStrategySpec.SchemaValidation())).isEqualTo("schema-validation");
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `mvn test -pl agentic-yaml -Dtest=ExecutionInfraSpecSerializationTest`
Expected: Compilation errors (EngineHosted, Checkpointing, VerifierStrategySpec don't exist)

- [ ] **Step 3: Add EngineHosted to ExecutionBackendSpec**

Add `record EngineHosted() implements ExecutionBackendSpec {}` to the
sealed interface. Add `@Type(value = ExecutionBackendSpec.EngineHosted.class, name = "engine-hosted")`
to `@JsonSubTypes`.

- [ ] **Step 4: Add EngineHosted case to ExecutionBackendRegistry**

Add case to the switch in `resolve()`:

```java
case ExecutionBackendSpec.EngineHosted ignored ->
    throw new UnsupportedOperationException(
            "engine-hosted backend requires engine runtime — "
            + "use PatternWorkerFunction via CaseDefinition YAML");
```

- [ ] **Step 5: Add Checkpointing to ExecutionListenerSpec**

Add `record Checkpointing() implements ExecutionListenerSpec {}` to the
sealed interface. Add `@Type(value = ExecutionListenerSpec.Checkpointing.class, name = "checkpointing")`
to `@JsonSubTypes`.

- [ ] **Step 6: Add Checkpointing case to ExecutionListenerRegistry**

Add case to the switch in `resolve()`:

```java
case ExecutionListenerSpec.Checkpointing ignored ->
    throw new UnsupportedOperationException(
            "checkpointing listener requires engine runtime context — "
            + "resolved by PatternWorkerFunction, not the registry");
```

- [ ] **Step 7: Create VerifierStrategySpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = VerifierStrategySpec.LlmEvaluation.class, name = "llm-evaluation"),
        @Type(value = VerifierStrategySpec.SchemaValidation.class, name = "schema-validation")
})
public sealed interface VerifierStrategySpec {
    record LlmEvaluation() implements VerifierStrategySpec {}
    record SchemaValidation() implements VerifierStrategySpec {}
}
```

- [ ] **Step 8: Create VerifierStrategyRegistry**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.VerifierStrategySpec;

public class VerifierStrategyRegistry {

    public String resolve(VerifierStrategySpec spec) {
        return switch (spec) {
            case VerifierStrategySpec.LlmEvaluation ignored -> "llm-evaluation";
            case VerifierStrategySpec.SchemaValidation ignored -> "schema-validation";
        };
    }
}
```

- [ ] **Step 9: Run tests — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=ExecutionInfraSpecSerializationTest`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ExecutionBackendSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ExecutionListenerSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/VerifierStrategySpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/ExecutionBackendRegistry.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/ExecutionListenerRegistry.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/VerifierStrategyRegistry.java \
       agentic-yaml/src/test/
git commit -m "feat(#255): EngineHosted, Checkpointing, VerifierStrategySpec + registries Refs #255"
```

---

## Batch 2: CallerConfigSpec + QuorumConfigSpec

### Task 2: CallerConfigSpec, QuorumConfigSpec, CallerConfigRegistry

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/QuorumConfigSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/CallerConfigSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/CallerConfigRegistry.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/EngineAdapterSpecSerializationTest.java`

**Interfaces:**
- Consumes: `CandidateSetStrategySpec` (existing), `CallerConfig` (engine-api), `QuorumConfig` (engine-api), `CandidateSetSpec` (engine-api), `ExpressionEngine` (platform-api)
- Produces: `CallerConfigSpec` sealed interface (Human/Llm/A2A/Any), `QuorumConfigSpec` record, `CallerConfigRegistry.resolve(spec, expressionEngine) → CallerConfig`

- [ ] **Step 1: Write failing tests for CallerConfigSpec deserialization**

Create `EngineAdapterSpecSerializationTest.java`:

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngineAdapterSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
    }

    @Nested
    class CallerConfigSpecs {
        @Test
        void human_minimal() throws Exception {
            var yaml = "type: human";
            var spec = mapper.readValue(yaml, CallerConfigSpec.class);
            assertThat(spec).isInstanceOf(CallerConfigSpec.Human.class);
        }

        @Test
        void human_full() throws Exception {
            var yaml = """
                    type: human
                    candidateGroups:
                      type: static
                      groups:
                        - reviewers
                    title: Review needed
                    titleExpression: "'Review ' + caseId"
                    outcomes:
                      - APPROVED
                      - REJECTED
                    claimDeadlineHours: 48
                    scope: department
                    scopeExpression: "org.department"
                    priority: HIGH
                    templateRef: review-template
                    payloadType: com.example.ReviewPayload
                    quorum:
                      instances: 3
                      required: 2
                      onThresholdReached: KEEP
                      allowSameAssignee: false
                    """;
            var spec = mapper.readValue(yaml, CallerConfigSpec.class);
            var human = (CallerConfigSpec.Human) spec;
            assertThat(human.candidateGroups()).isInstanceOf(CandidateSetStrategySpec.Static.class);
            assertThat(human.title()).isEqualTo("Review needed");
            assertThat(human.titleExpression()).isEqualTo("'Review ' + caseId");
            assertThat(human.outcomes()).containsExactlyInAnyOrder("APPROVED", "REJECTED");
            assertThat(human.claimDeadlineHours()).isEqualTo(48);
            assertThat(human.payloadType()).isEqualTo("com.example.ReviewPayload");
            assertThat(human.quorum()).isNotNull();
            assertThat(human.quorum().instances()).isEqualTo(3);
            assertThat(human.quorum().required()).isEqualTo(2);
        }

        @Test
        void llm() throws Exception {
            var yaml = """
                    type: llm
                    modelId: gpt-4
                    systemPrompt: You are a reviewer
                    """;
            var spec = mapper.readValue(yaml, CallerConfigSpec.class);
            var llm = (CallerConfigSpec.Llm) spec;
            assertThat(llm.modelId()).isEqualTo("gpt-4");
            assertThat(llm.systemPrompt()).isEqualTo("You are a reviewer");
        }

        @Test
        void a2a() throws Exception {
            var yaml = """
                    type: a2a
                    endpoint: https://agent.example.com
                    skill: review
                    streaming: true
                    """;
            var spec = mapper.readValue(yaml, CallerConfigSpec.class);
            var a2a = (CallerConfigSpec.A2A) spec;
            assertThat(a2a.endpoint()).isEqualTo("https://agent.example.com");
            assertThat(a2a.skill()).isEqualTo("review");
            assertThat(a2a.streaming()).isTrue();
        }

        @Test
        void any() throws Exception {
            var spec = mapper.readValue("type: any", CallerConfigSpec.class);
            assertThat(spec).isInstanceOf(CallerConfigSpec.Any.class);
        }
    }

    @Nested
    class QuorumConfigSpecs {
        @Test
        void fullSpec() throws Exception {
            var yaml = """
                    instances: 5
                    required: 3
                    onThresholdReached: CANCEL
                    allowSameAssignee: true
                    """;
            var spec = mapper.readValue(yaml, QuorumConfigSpec.class);
            assertThat(spec.instances()).isEqualTo(5);
            assertThat(spec.required()).isEqualTo(3);
            assertThat(spec.onThresholdReached()).isEqualTo(io.casehub.api.model.OnThresholdReached.CANCEL);
            assertThat(spec.allowSameAssignee()).isTrue();
        }
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `mvn test -pl agentic-yaml -Dtest=EngineAdapterSpecSerializationTest`
Expected: Compilation errors (CallerConfigSpec, QuorumConfigSpec don't exist)

- [ ] **Step 3: Create QuorumConfigSpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import io.casehub.api.model.OnThresholdReached;
import org.jspecify.annotations.Nullable;

public record QuorumConfigSpec(
        int instances,
        int required,
        @Nullable OnThresholdReached onThresholdReached,
        boolean allowSameAssignee) {}
```

- [ ] **Step 4: Create CallerConfigSpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.Set;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = CallerConfigSpec.Human.class, name = "human"),
        @Type(value = CallerConfigSpec.Llm.class, name = "llm"),
        @Type(value = CallerConfigSpec.A2A.class, name = "a2a"),
        @Type(value = CallerConfigSpec.Any.class, name = "any")
})
public sealed interface CallerConfigSpec {

    record Human(
            @Nullable CandidateSetStrategySpec candidateGroups,
            @Nullable CandidateSetStrategySpec candidateUsers,
            @Nullable String title,
            @Nullable String titleExpression,
            @Nullable Set<String> outcomes,
            @Nullable Integer claimDeadlineHours,
            @Nullable String scope,
            @Nullable String scopeExpression,
            @Nullable String priority,
            @Nullable String templateRef,
            @Nullable String payloadType,
            @Nullable QuorumConfigSpec quorum
    ) implements CallerConfigSpec {}

    record Llm(
            @Nullable String modelId,
            @Nullable String modelName,
            @Nullable String systemPrompt
    ) implements CallerConfigSpec {}

    record A2A(
            String endpoint,
            @Nullable String skill,
            boolean streaming
    ) implements CallerConfigSpec {}

    record Any() implements CallerConfigSpec {}
}
```

- [ ] **Step 5: Create CallerConfigRegistry**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.api.spi.QuorumConfig;
import io.casehub.api.spi.judgment.CallerConfig;
import io.casehub.api.spi.routing.CandidateSetSpec;
import io.casehub.api.spi.routing.CandidateSetStrategy;
import io.casehub.blocks.agentic.yaml.spec.CallerConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.QuorumConfigSpec;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

public class CallerConfigRegistry {

    private final CandidateSetStrategyRegistry candidateRegistry;

    public CallerConfigRegistry(CandidateSetStrategyRegistry candidateRegistry) {
        this.candidateRegistry = candidateRegistry;
    }

    public CallerConfig resolve(CallerConfigSpec spec, @Nullable ExpressionEngine expressionEngine) {
        return switch (spec) {
            case CallerConfigSpec.Human h -> resolveHuman(h, expressionEngine);
            case CallerConfigSpec.Llm l -> new CallerConfig.Llm(l.modelId(), l.modelName(), l.systemPrompt());
            case CallerConfigSpec.A2A a -> new CallerConfig.A2A(a.endpoint(), a.skill(), a.streaming());
            case CallerConfigSpec.Any ignored -> new CallerConfig.Any();
        };
    }

    private CallerConfig.Human resolveHuman(CallerConfigSpec.Human h,
                                             @Nullable ExpressionEngine expressionEngine) {
        return new CallerConfig.Human(
                h.candidateGroups() != null
                        ? new CandidateSetSpec.Inline(candidateRegistry.resolve(h.candidateGroups()))
                        : null,
                h.candidateUsers() != null
                        ? new CandidateSetSpec.Inline(candidateRegistry.resolve(h.candidateUsers()))
                        : null,
                h.title(),
                h.titleExpression() != null && expressionEngine != null
                        ? expressionEngine.compile(h.titleExpression(), Object.class, Object.class)
                        : null,
                h.outcomes(),
                h.claimDeadlineHours(),
                h.scope(),
                h.scopeExpression() != null && expressionEngine != null
                        ? expressionEngine.compile(h.scopeExpression(), Object.class, Object.class)
                        : null,
                h.priority(),
                h.templateRef(),
                h.payloadType() != null ? resolveClass(h.payloadType()) : null,
                h.quorum() != null ? resolveQuorum(h.quorum()) : null);
    }

    private static Class<?> resolveClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown payload type: " + className, e);
        }
    }

    private static QuorumConfig resolveQuorum(QuorumConfigSpec q) {
        return new QuorumConfig(q.instances(), q.required(),
                q.onThresholdReached(), q.allowSameAssignee());
    }
}
```

Note: Verify `CandidateSetStrategyRegistry` exists and its `resolve()`
signature via `ide_file_structure` before writing the registry. The
registry takes a `CandidateSetStrategySpec` and returns a
`CandidateSetStrategy`.

- [ ] **Step 6: Run tests — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=EngineAdapterSpecSerializationTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/QuorumConfigSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/CallerConfigSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/CallerConfigRegistry.java \
       agentic-yaml/src/test/
git commit -m "feat(#255): CallerConfigSpec (full mirror) + QuorumConfigSpec + registry Refs #255"
```

---

## Batch 3: PatternJudgmentConfigSpec + schema + coverage

### Task 3: PatternJudgmentConfigSpec, registry, schema generation, coverage matrix

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/PatternJudgmentConfigSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/PatternJudgmentConfigRegistry.java`
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/schema/BlocksSchemaGenerator.java`
- Modify: `docs/yaml-coverage.md`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/EngineAdapterSpecSerializationTest.java`

**Interfaces:**
- Consumes: `CallerConfigSpec` (Task 2), `VerifierStrategySpec` (Task 1), `CallerConfigRegistry` (Task 2), `VerifierStrategyRegistry` (Task 1), `EvidenceRequirement` (engine-api, direct reuse), `PatternJudgmentConfig` (engine-adapter), `ExpressionEngine` (platform-api)
- Produces: `PatternJudgmentConfigSpec` record, `PatternJudgmentConfigRegistry.resolve(spec, expressionEngine) → PatternJudgmentConfig`

- [ ] **Step 1: Write failing tests for PatternJudgmentConfigSpec**

Add to `EngineAdapterSpecSerializationTest`:

```java
@Nested
class PatternJudgmentConfigSpecs {
    @Test
    void fullConfig() throws Exception {
        var yaml = """
                prompt: Evaluate the case evidence
                callerConfig:
                  type: human
                  title: Case review
                  outcomes:
                    - APPROVED
                    - REJECTED
                verifier:
                  type: llm-evaluation
                evidenceRequirements:
                  - key: case-notes
                    type: DOCUMENT
                    required: true
                  - key: risk-score
                    type: METRIC
                    required: false
                mode: INTEGRATED
                afterStep: true
                """;
        var spec = mapper.readValue(yaml, PatternJudgmentConfigSpec.class);
        assertThat(spec.prompt()).isEqualTo("Evaluate the case evidence");
        assertThat(spec.callerConfig()).isInstanceOf(CallerConfigSpec.Human.class);
        assertThat(spec.verifier()).isInstanceOf(VerifierStrategySpec.LlmEvaluation.class);
        assertThat(spec.evidenceRequirements()).hasSize(2);
        assertThat(spec.evidenceRequirements().get(0).key()).isEqualTo("case-notes");
        assertThat(spec.evidenceRequirements().get(0).type()).isEqualTo(
                io.casehub.api.spi.judgment.EvidenceType.DOCUMENT);
        assertThat(spec.mode()).isEqualTo(
                io.casehub.engine.agentic.judgment.PatternJudgmentConfig.JudgmentMode.INTEGRATED);
        assertThat(spec.afterStep()).isTrue();
    }

    @Test
    void minimalConfig() throws Exception {
        var yaml = """
                prompt: Quick check
                callerConfig:
                  type: llm
                evidenceRequirements: []
                afterStep: false
                """;
        var spec = mapper.readValue(yaml, PatternJudgmentConfigSpec.class);
        assertThat(spec.prompt()).isEqualTo("Quick check");
        assertThat(spec.callerConfig()).isInstanceOf(CallerConfigSpec.Llm.class);
        assertThat(spec.verifier()).isNull();
        assertThat(spec.mode()).isNull();
    }
}
```

Add registry test:

```java
@Nested
class PatternJudgmentConfigRegistryTests {
    @Test
    void resolves_fullConfig() {
        var spec = new PatternJudgmentConfigSpec(
                "Review this",
                new CallerConfigSpec.Llm(null, null, "You are a reviewer"),
                new VerifierStrategySpec.SchemaValidation(),
                java.util.List.of(
                        new io.casehub.api.spi.judgment.EvidenceRequirement(
                                "notes", io.casehub.api.spi.judgment.EvidenceType.DOCUMENT)),
                io.casehub.engine.agentic.judgment.PatternJudgmentConfig.JudgmentMode.POST_STEP,
                true);
        var callerRegistry = new io.casehub.blocks.agentic.yaml.registry.CallerConfigRegistry(
                new io.casehub.blocks.agentic.yaml.registry.CandidateSetStrategyRegistry());
        var verifierRegistry = new io.casehub.blocks.agentic.yaml.registry.VerifierStrategyRegistry();
        var registry = new io.casehub.blocks.agentic.yaml.registry.PatternJudgmentConfigRegistry(
                callerRegistry, verifierRegistry);
        var config = registry.resolve(spec, null);
        assertThat(config.prompt()).isEqualTo("Review this");
        assertThat(config.callerConfig()).isInstanceOf(io.casehub.api.spi.judgment.CallerConfig.Llm.class);
        assertThat(config.verifierStrategy()).isEqualTo("schema-validation");
        assertThat(config.evidenceRequirements()).hasSize(1);
        assertThat(config.mode()).isEqualTo(
                io.casehub.engine.agentic.judgment.PatternJudgmentConfig.JudgmentMode.POST_STEP);
        assertThat(config.afterStep()).isTrue();
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `mvn test -pl agentic-yaml -Dtest=EngineAdapterSpecSerializationTest`
Expected: Compilation errors (PatternJudgmentConfigSpec doesn't exist)

- [ ] **Step 3: Create PatternJudgmentConfigSpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import io.casehub.api.spi.judgment.EvidenceRequirement;
import io.casehub.engine.agentic.judgment.PatternJudgmentConfig.JudgmentMode;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record PatternJudgmentConfigSpec(
        String prompt,
        CallerConfigSpec callerConfig,
        @Nullable VerifierStrategySpec verifier,
        List<EvidenceRequirement> evidenceRequirements,
        @Nullable JudgmentMode mode,
        boolean afterStep) {}
```

- [ ] **Step 4: Create PatternJudgmentConfigRegistry**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.PatternJudgmentConfigSpec;
import io.casehub.engine.agentic.judgment.PatternJudgmentConfig;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

public class PatternJudgmentConfigRegistry {

    private final CallerConfigRegistry callerRegistry;
    private final VerifierStrategyRegistry verifierRegistry;

    public PatternJudgmentConfigRegistry(CallerConfigRegistry callerRegistry,
                                          VerifierStrategyRegistry verifierRegistry) {
        this.callerRegistry = callerRegistry;
        this.verifierRegistry = verifierRegistry;
    }

    public PatternJudgmentConfig resolve(PatternJudgmentConfigSpec spec,
                                          @Nullable ExpressionEngine expressionEngine) {
        return new PatternJudgmentConfig(
                spec.prompt(),
                callerRegistry.resolve(spec.callerConfig(), expressionEngine),
                spec.verifier() != null ? verifierRegistry.resolve(spec.verifier()) : null,
                spec.evidenceRequirements(),
                spec.mode(),
                spec.afterStep());
    }
}
```

- [ ] **Step 5: Add new sealed types to BlocksSchemaGenerator**

Add `VerifierStrategySpec.class` and `CallerConfigSpec.class` to the
`List.of(...)` in `buildDiscriminatorOverrides()`.

- [ ] **Step 6: Run tests — verify pass**

Run: `mvn test -pl agentic-yaml`
Expected: PASS (all tests including schema generation)

- [ ] **Step 7: Update coverage matrix**

Update `docs/yaml-coverage.md` §21:
- PatternJudgmentConfig → Done
- EngineHostedBackend → Done
- CheckpointingListener → Done
- LlmEvaluationVerifier → Done
- SchemaValidationVerifier → Done

Update summary table: engine-adapter row from 0 Done / 5 Gap to 5 Done / 0 Gap.
Update total: Done +5, Gap -5.

- [ ] **Step 8: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/PatternJudgmentConfigSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/PatternJudgmentConfigRegistry.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/schema/BlocksSchemaGenerator.java \
       agentic-yaml/src/test/ \
       docs/yaml-coverage.md
git commit -m "feat(#255): PatternJudgmentConfigSpec + registry, schema generation, coverage matrix §21 Done Refs #255"
```

## References

- [2026-09-11-engine-adapter-design.md] — design spec
- `engine-adapter/src/main/java/io/casehub/engine/agentic/judgment/PatternJudgmentConfig.java` — runtime type
- `engine-adapter/src/main/java/io/casehub/engine/agentic/EngineHostedBackend.java` — runtime type
- `engine-adapter/src/main/java/io/casehub/engine/agentic/CheckpointingListener.java` — runtime type
- `engine-adapter/src/main/java/io/casehub/engine/agentic/judgment/LlmEvaluationVerifier.java` — runtime type
- `engine-adapter/src/main/java/io/casehub/engine/agentic/judgment/SchemaValidationVerifier.java` — runtime type
- `engine-api: CallerConfig.java` — mirrored by CallerConfigSpec
- `engine-api: QuorumConfig.java` — mirrored by QuorumConfigSpec
- `agentic-yaml: ExecutionBackendSpec.java` — extended with EngineHosted
- `agentic-yaml: ExecutionListenerSpec.java` — extended with Checkpointing
- `agentic-yaml: CandidateSetStrategySpec.java` — reused for candidate groups
- `agentic-yaml: BlocksSchemaGenerator.java:31-55` — discriminator registration
- [GitHub #255](https://github.com/casehubio/blocks/issues/255)
