# Agentic YAML Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #165 — Agentic Pattern YAML Expansion

**Goal:** Make all agentic orchestration patterns YAML-expressible via typed spec records, strategy registries, and a pattern compiler that resolves YAML to `ExecutionModel`.

**Architecture:** New `agentic-yaml` Maven module (parallel to `summarisation-yaml`). Layer 1: sealed record hierarchies with `@JsonTypeInfo` discriminators define the YAML shape. Layer 2: strategy registries resolve type names to runtime implementations via exhaustive switch on sealed subtypes. Layer 3: `PatternCompiler` wires spec records into `ExecutionModel` for all 8 topologies. Layer 4: `ExpressionEngine` SPI compiles MVEL predicates from YAML. Layer 5: victools generates JSON/YAML schema from spec records. Quarkus deployment module discovers and validates classpath YAML at build time.

**Tech Stack:** Java 17, Jackson YAML, MVEL3 (via platform `ExpressionEngine`), victools 4.36.0 (via `casehub-platform-schema-generator`), Quarkus deployment API, JUnit 5 + AssertJ

## Global Constraints

- Maven modules: `casehub-blocks-agentic-yaml` (runtime), `casehub-blocks-agentic-yaml-deployment` (build extension)
- `io.casehub` groupId, `0.2-SNAPSHOT` version, `casehub-blocks-parent` parent
- Package root: `io.casehub.blocks.agentic.yaml`
- Sealed interfaces use `@JsonTypeInfo(use = Id.NAME, property = "type")` with `@JsonSubTypes` — same pattern as `GroupingDefinition` in summarisation-yaml
- Spec records are pure data — no functional interfaces, no runtime type imports
- Spec record subtypes are inner records of their sealed interface (e.g., `RoutingSpec.FirstMatch`)
- `@Nullable` from `org.jspecify.annotations`, not `javax.annotation`
- ExpressionEngine from `casehub-platform-api` (provided scope), MvelExpressionEngine in test scope
- `mvn --batch-mode install` must pass after every batch
- All commits reference `Refs #165`

---

## File Map

### New files in `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/`

| Path | Responsibility |
|------|---------------|
| `spec/RoutingSpec.java` | Sealed: FirstMatch, RoundRobin, Sequential, LlmSelected, SelectAll |
| `spec/TerminationSpec.java` | Sealed: MaxIterations, GoalReached, JudgeConvergence, AllAgreed, Supervisor, Contested, Convergence, SinglePass, AgentCount |
| `spec/AggregationSpec.java` | Sealed: PassThrough, CollectAll, MajorityVote, Auction |
| `spec/ActivationSpec.java` | Sealed: OnDispatch, MaxIterationsGuard |
| `spec/DecompositionSpec.java` | Sealed: Identity, Static, Llm, Hybrid, Heuristic, Goap, CapabilityDependency, ForwardReasoning |
| `spec/AgentRefSpec.java` | Sealed: Worker, Channel, Human, External, Composed |
| `spec/PatternSpec.java` | Sealed: Supervisor, Debate, Loop, Parallel, Voting, Conditional, Sequence, Htn |
| `spec/BranchSpec.java` | Record: condition expression + agent ref for ConditionalSpec |
| `spec/TaskNodeSpec.java` | Sealed: Primitive, Compound — HTN task tree |
| `spec/MethodSpec.java` | Record: decomposition method (guard + tasks) for StaticSpec |
| `spec/JudgmentSpec.java` | Record + inner sealed TriggerSpec, CallerSpec, AgreementSpec |
| `spec/NegotiationSpec.java` | Record: parties, acceptance policy, termination |
| `spec/ConversationSpec.java` | Record: turn policy, epistemic rule, convergence policy |
| `spec/AcceptancePolicySpec.java` | Sealed: Unanimous, Majority, Threshold |
| `spec/TurnPolicySpec.java` | Sealed: RoundRobin, Addressed, PointAddressed, Free |
| `spec/EpistemicRuleSpec.java` | Sealed: ExplicitAcknowledgement, TacitAcceptance, CommitmentResolution |
| `spec/ConvergencePolicySpec.java` | Sealed: Structural, CommonGroundRatio, Composite |
| `spec/ConflictResolutionSpec.java` | Sealed: Priority, Specificity, Recency, MostRestrictive, Escalation |
| `registry/RoutingStrategyRegistry.java` | resolve(RoutingSpec) → RoutingStrategy |
| `registry/TerminationConditionRegistry.java` | resolve(TerminationSpec) → TerminationCondition |
| `registry/AggregationStrategyRegistry.java` | resolve(AggregationSpec) → AggregationStrategy |
| `registry/ActivationRuleRegistry.java` | resolve(ActivationSpec) → ActivationRule |
| `registry/DecompositionStrategyRegistry.java` | resolve(DecompositionSpec) → DecompositionStrategy |
| `registry/ConflictResolutionRegistry.java` | resolve(ConflictResolutionSpec) → ConflictResolutionStrategy |
| `registry/AcceptancePolicyRegistry.java` | resolve(AcceptancePolicySpec) → AcceptancePolicy |
| `registry/TurnPolicyRegistry.java` | resolve(TurnPolicySpec) → TurnPolicy |
| `registry/EpistemicRuleRegistry.java` | resolve(EpistemicRuleSpec) → EpistemicRule |
| `registry/JudgmentTriggerRegistry.java` | resolve(TriggerSpec) → JudgmentTrigger |
| `registry/CallerStrategyRegistry.java` | resolve(CallerSpec) → CallerStrategy |
| `compiler/PatternCompiler.java` | compile(PatternSpec) → ExecutionModel |
| `schema/BlocksSchemaGenerator.java` | victools schema from spec records |
| `runtime/AgenticRecorder.java` | @Recorder — registers built-in types |

### New files in `agentic-yaml-deployment/`

| Path | Responsibility |
|------|---------------|
| `src/main/java/.../deployment/AgenticYamlProcessor.java` | @BuildStep — YAML discovery + validation |

### Modified files

| Path | Change |
|------|--------|
| `pom.xml` (parent) | Add `agentic-yaml`, `agentic-yaml-deployment` modules |
| `blocks/src/main/java/.../routing/SelectAllRouting.java` | New: trivial routing strategy returning all candidates |

### Test files in `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/`

| Path | What it tests |
|------|--------------|
| `spec/StrategySpecSerializationTest.java` | YAML round-trip for all strategy specs |
| `spec/PatternSpecSerializationTest.java` | YAML round-trip for all 8 pattern topologies |
| `spec/SecondarySpecSerializationTest.java` | YAML round-trip for negotiation, conversation, normative |
| `registry/RegistryTest.java` | All registries resolve correctly |
| `compiler/PatternCompilerTest.java` | YAML → ExecutionModel per topology |
| `schema/SchemaGenerationTest.java` | victools generates valid schema |

### Test resources in `agentic-yaml/src/test/resources/patterns/`

One YAML fixture per topology: `supervisor.yaml`, `debate.yaml`, `loop.yaml`, `parallel.yaml`, `voting.yaml`, `conditional.yaml`, `sequence.yaml`, `htn.yaml`

---

## Batch 1: Module Scaffolding + Spec Records

After this batch: `agentic-yaml` module compiles. All spec records defined. YAML round-trip serialization tests pass for every strategy, agent, and pattern spec.

### Task 1: Maven Module Setup

**Files:**
- Create: `agentic-yaml/pom.xml`
- Create: `agentic-yaml-deployment/pom.xml`
- Modify: `pom.xml` (parent) — add modules

**Interfaces:**
- Produces: compilable empty modules that downstream tasks populate

- [ ] **Step 1: Create `agentic-yaml/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-parent</artifactId>
    <version>0.2-SNAPSHOT</version>
  </parent>

  <artifactId>casehub-blocks-agentic-yaml</artifactId>
  <name>casehub-blocks-agentic-yaml</name>
  <description>YAML surface for agentic orchestration patterns — spec records, registries, compiler</description>

  <dependencies>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-blocks</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.dataformat</groupId>
      <artifactId>jackson-dataformat-yaml</artifactId>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.datatype</groupId>
      <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jspecify</groupId>
      <artifactId>jspecify</artifactId>
    </dependency>

    <!-- Provided -->
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-platform-api</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-core</artifactId>
      <scope>provided</scope>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-platform-expression</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Create `agentic-yaml-deployment/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-parent</artifactId>
    <version>0.2-SNAPSHOT</version>
  </parent>

  <artifactId>casehub-blocks-agentic-yaml-deployment</artifactId>
  <name>casehub-blocks-agentic-yaml-deployment</name>
  <description>Quarkus deployment extension for agentic YAML — build-time discovery and validation</description>

  <dependencies>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-blocks-agentic-yaml</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-arc-deployment</artifactId>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <annotationProcessorPaths>
            <path>
              <groupId>io.quarkus</groupId>
              <artifactId>quarkus-extension-processor</artifactId>
              <version>${quarkus.version}</version>
            </path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Add modules to parent pom.xml**

Add `<module>agentic-yaml</module>` and `<module>agentic-yaml-deployment</module>` to the `<modules>` block in the parent pom, after `summarisation-yaml-deployment`.

- [ ] **Step 4: Verify compilation**

Run: `mvn --batch-mode compile -pl agentic-yaml,agentic-yaml-deployment -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```
feat(#165): scaffold agentic-yaml Maven modules Refs #165
```

---

### Task 2: Strategy Spec Records + Agent Spec

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/RoutingSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/TerminationSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/AggregationSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ActivationSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/DecompositionSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/MethodSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/TaskNodeSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/AgentRefSpec.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/StrategySpecSerializationTest.java`

**Interfaces:**
- Produces: `RoutingSpec` (5 subtypes), `TerminationSpec` (9), `AggregationSpec` (4), `ActivationSpec` (2), `DecompositionSpec` (8), `AgentRefSpec` (5) — all Jackson-deserializable from YAML
- Produces: `MethodSpec`, `TaskNodeSpec` — supporting records for decomposition/HTN specs

- [ ] **Step 1: Write the serialization test (all strategy specs)**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategySpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
    }

    @Nested
    class RoutingSpecs {
        @Test
        void firstMatchWithGuard() throws Exception {
            var yaml = """
                    type: first-match
                    guard: "priority > 5"
                    """;
            var spec = mapper.readValue(yaml, RoutingSpec.class);
            assertThat(spec).isInstanceOf(RoutingSpec.FirstMatch.class);
            assertThat(((RoutingSpec.FirstMatch) spec).guard()).isEqualTo("priority > 5");
        }

        @Test
        void roundRobin() throws Exception {
            var spec = mapper.readValue("type: round-robin", RoutingSpec.class);
            assertThat(spec).isInstanceOf(RoutingSpec.RoundRobin.class);
        }

        @Test
        void sequential() throws Exception {
            var spec = mapper.readValue("type: sequential", RoutingSpec.class);
            assertThat(spec).isInstanceOf(RoutingSpec.Sequential.class);
        }

        @Test
        void llmSelected() throws Exception {
            var spec = mapper.readValue("type: llm-selected", RoutingSpec.class);
            assertThat(spec).isInstanceOf(RoutingSpec.LlmSelected.class);
        }

        @Test
        void selectAll() throws Exception {
            var spec = mapper.readValue("type: select-all", RoutingSpec.class);
            assertThat(spec).isInstanceOf(RoutingSpec.SelectAll.class);
        }
    }

    @Nested
    class TerminationSpecs {
        @Test
        void maxIterations() throws Exception {
            var yaml = """
                    type: max-iterations
                    iterations: 20
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.MaxIterations.class);
            assertThat(((TerminationSpec.MaxIterations) spec).iterations()).isEqualTo(20);
        }

        @Test
        void goalReached() throws Exception {
            var yaml = """
                    type: goal-reached
                    when: "qualityScore > 0.9"
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.GoalReached.class);
            assertThat(((TerminationSpec.GoalReached) spec).when()).isEqualTo("qualityScore > 0.9");
        }

        @Test
        void singlePass() throws Exception {
            var spec = mapper.readValue("type: single-pass", TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.SinglePass.class);
        }

        @Test
        void agentCount() throws Exception {
            var spec = mapper.readValue("type: agent-count", TerminationSpec.class);
            assertThat(spec).isInstanceOf(TerminationSpec.AgentCount.class);
        }
    }

    @Nested
    class AggregationSpecs {
        @Test
        void passThrough() throws Exception {
            var spec = mapper.readValue("type: pass-through", AggregationSpec.class);
            assertThat(spec).isInstanceOf(AggregationSpec.PassThrough.class);
        }

        @Test
        void majorityVote() throws Exception {
            var spec = mapper.readValue("type: majority-vote", AggregationSpec.class);
            assertThat(spec).isInstanceOf(AggregationSpec.MajorityVote.class);
        }
    }

    @Nested
    class ActivationSpecs {
        @Test
        void onDispatch() throws Exception {
            var spec = mapper.readValue("type: on-dispatch", ActivationSpec.class);
            assertThat(spec).isInstanceOf(ActivationSpec.OnDispatch.class);
        }

        @Test
        void maxIterationsGuard() throws Exception {
            var yaml = """
                    type: max-iterations
                    maxIterations: 5
                    """;
            var spec = mapper.readValue(yaml, ActivationSpec.class);
            assertThat(spec).isInstanceOf(ActivationSpec.MaxIterationsGuard.class);
            assertThat(((ActivationSpec.MaxIterationsGuard) spec).maxIterations()).isEqualTo(5);
        }
    }

    @Nested
    class DecompositionSpecs {
        @Test
        void identity() throws Exception {
            var spec = mapper.readValue("type: identity", DecompositionSpec.class);
            assertThat(spec).isInstanceOf(DecompositionSpec.Identity.class);
        }

        @Test
        void llmWithMaxDepth() throws Exception {
            var yaml = """
                    type: llm
                    maxDepth: 3
                    """;
            var spec = mapper.readValue(yaml, DecompositionSpec.class);
            assertThat(spec).isInstanceOf(DecompositionSpec.Llm.class);
            assertThat(((DecompositionSpec.Llm) spec).maxDepth()).isEqualTo(3);
        }
    }

    @Nested
    class AgentRefSpecs {
        @Test
        void workerAgent() throws Exception {
            var yaml = """
                    type: worker
                    name: analyst
                    description: "Analyses input data"
                    capabilities:
                      - data-analysis
                      - pattern-recognition
                    """;
            var spec = mapper.readValue(yaml, AgentRefSpec.class);
            assertThat(spec).isInstanceOf(AgentRefSpec.Worker.class);
            assertThat(spec.name()).isEqualTo("analyst");
            assertThat(spec.description()).isEqualTo("Analyses input data");
            assertThat(spec.capabilities()).containsExactly("data-analysis", "pattern-recognition");
        }

        @Test
        void externalAgent() throws Exception {
            var yaml = """
                    type: external
                    name: ext-service
                    description: "External API"
                    """;
            var spec = mapper.readValue(yaml, AgentRefSpec.class);
            assertThat(spec).isInstanceOf(AgentRefSpec.External.class);
            assertThat(spec.name()).isEqualTo("ext-service");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=StrategySpecSerializationTest`
Expected: COMPILATION FAILURE (spec classes don't exist yet)

- [ ] **Step 3: Write `RoutingSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = RoutingSpec.FirstMatch.class, name = "first-match"),
        @Type(value = RoutingSpec.RoundRobin.class, name = "round-robin"),
        @Type(value = RoutingSpec.Sequential.class, name = "sequential"),
        @Type(value = RoutingSpec.LlmSelected.class, name = "llm-selected"),
        @Type(value = RoutingSpec.SelectAll.class, name = "select-all")
})
public sealed interface RoutingSpec {

    record FirstMatch(@Nullable String guard) implements RoutingSpec {}

    record RoundRobin() implements RoutingSpec {}

    record Sequential() implements RoutingSpec {}

    record LlmSelected() implements RoutingSpec {}

    record SelectAll() implements RoutingSpec {}
}
```

- [ ] **Step 4: Write `TerminationSpec.java`**

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
        @Type(value = TerminationSpec.MaxIterations.class, name = "max-iterations"),
        @Type(value = TerminationSpec.GoalReached.class, name = "goal-reached"),
        @Type(value = TerminationSpec.JudgeConvergence.class, name = "judge-convergence"),
        @Type(value = TerminationSpec.AllAgreed.class, name = "all-agreed"),
        @Type(value = TerminationSpec.Supervisor.class, name = "supervisor"),
        @Type(value = TerminationSpec.Contested.class, name = "contested"),
        @Type(value = TerminationSpec.Convergence.class, name = "convergence"),
        @Type(value = TerminationSpec.SinglePass.class, name = "single-pass"),
        @Type(value = TerminationSpec.AgentCount.class, name = "agent-count")
})
public sealed interface TerminationSpec {

    record MaxIterations(int iterations) implements TerminationSpec {}

    record GoalReached(String when) implements TerminationSpec {}

    record JudgeConvergence(AgentRefSpec judge, int maxIterations) implements TerminationSpec {}

    record AllAgreed(@Nullable Set<String> resolvedStatuses) implements TerminationSpec {}

    record Supervisor(String role, @Nullable String signalType) implements TerminationSpec {}

    record Contested(int maxDisputeRounds) implements TerminationSpec {}

    record Convergence(double threshold) implements TerminationSpec {}

    record SinglePass() implements TerminationSpec {}

    record AgentCount() implements TerminationSpec {}
}
```

- [ ] **Step 5: Write `AggregationSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = AggregationSpec.PassThrough.class, name = "pass-through"),
        @Type(value = AggregationSpec.CollectAll.class, name = "collect-all"),
        @Type(value = AggregationSpec.MajorityVote.class, name = "majority-vote"),
        @Type(value = AggregationSpec.Auction.class, name = "auction")
})
public sealed interface AggregationSpec {

    record PassThrough() implements AggregationSpec {}

    record CollectAll() implements AggregationSpec {}

    record MajorityVote() implements AggregationSpec {}

    record Auction(@Nullable String auctionType) implements AggregationSpec {}
}
```

- [ ] **Step 6: Write `ActivationSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ActivationSpec.OnDispatch.class, name = "on-dispatch"),
        @Type(value = ActivationSpec.MaxIterationsGuard.class, name = "max-iterations")
})
public sealed interface ActivationSpec {

    record OnDispatch() implements ActivationSpec {}

    record MaxIterationsGuard(int maxIterations) implements ActivationSpec {}
}
```

- [ ] **Step 7: Write `TaskNodeSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = TaskNodeSpec.Primitive.class, name = "primitive"),
        @Type(value = TaskNodeSpec.Compound.class, name = "compound")
})
public sealed interface TaskNodeSpec {

    String name();

    record Primitive(String name, @Nullable String executor) implements TaskNodeSpec {}

    record Compound(String name, List<TaskNodeSpec> subtasks) implements TaskNodeSpec {}
}
```

- [ ] **Step 8: Write `MethodSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

public record MethodSpec(
        String name,
        @Nullable String guard,
        @Nullable Double estimatedCost,
        @Nullable Duration estimatedDuration,
        List<TaskNodeSpec> tasks
) {}
```

- [ ] **Step 9: Write `DecompositionSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = DecompositionSpec.Identity.class, name = "identity"),
        @Type(value = DecompositionSpec.Static.class, name = "static"),
        @Type(value = DecompositionSpec.Llm.class, name = "llm"),
        @Type(value = DecompositionSpec.Hybrid.class, name = "hybrid"),
        @Type(value = DecompositionSpec.Heuristic.class, name = "heuristic"),
        @Type(value = DecompositionSpec.Goap.class, name = "goap"),
        @Type(value = DecompositionSpec.CapabilityDependency.class, name = "capability-dependency"),
        @Type(value = DecompositionSpec.ForwardReasoning.class, name = "forward-reasoning")
})
public sealed interface DecompositionSpec {

    record Identity() implements DecompositionSpec {}

    record Static(List<MethodSpec> methods) implements DecompositionSpec {}

    record Llm(@Nullable Integer maxDepth) implements DecompositionSpec {}

    record Hybrid(@Nullable Integer maxDepth) implements DecompositionSpec {}

    record Heuristic(@Nullable String name) implements DecompositionSpec {}

    record Goap() implements DecompositionSpec {}

    record CapabilityDependency() implements DecompositionSpec {}

    record ForwardReasoning() implements DecompositionSpec {}
}
```

- [ ] **Step 10: Write `AgentRefSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = AgentRefSpec.Worker.class, name = "worker"),
        @Type(value = AgentRefSpec.Channel.class, name = "channel"),
        @Type(value = AgentRefSpec.Human.class, name = "human"),
        @Type(value = AgentRefSpec.External.class, name = "external"),
        @Type(value = AgentRefSpec.Composed.class, name = "composed")
})
public sealed interface AgentRefSpec {

    String name();
    @Nullable String description();
    @Nullable List<String> capabilities();

    record Worker(String name, @Nullable String description,
                  @Nullable List<String> capabilities) implements AgentRefSpec {}

    record Channel(String name, String channelId, @Nullable String description,
                   @Nullable List<String> capabilities) implements AgentRefSpec {}

    record Human(String name, @Nullable String description,
                 @Nullable List<String> capabilities) implements AgentRefSpec {}

    record External(String name, @Nullable String description,
                    @Nullable List<String> capabilities) implements AgentRefSpec {}

    record Composed(String name, @Nullable String description,
                    @Nullable List<String> capabilities,
                    PatternSpec pattern) implements AgentRefSpec {}
}
```

- [ ] **Step 11: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=StrategySpecSerializationTest`
Expected: ALL PASS

- [ ] **Step 12: Commit**

```
feat(#165): strategy + agent spec records with YAML serialization Refs #165
```

---

### Task 3: Pattern Spec + Judgment Spec

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/PatternSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/BranchSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/JudgmentSpec.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/PatternSpecSerializationTest.java`
- Create: `agentic-yaml/src/test/resources/patterns/supervisor.yaml`
- Create: `agentic-yaml/src/test/resources/patterns/debate.yaml`
- Create: `agentic-yaml/src/test/resources/patterns/loop.yaml`
- Create: `agentic-yaml/src/test/resources/patterns/parallel.yaml`
- Create: `agentic-yaml/src/test/resources/patterns/voting.yaml`
- Create: `agentic-yaml/src/test/resources/patterns/conditional.yaml`
- Create: `agentic-yaml/src/test/resources/patterns/sequence.yaml`
- Create: `agentic-yaml/src/test/resources/patterns/htn.yaml`

**Interfaces:**
- Consumes: `RoutingSpec`, `TerminationSpec`, `AggregationSpec`, `ActivationSpec`, `DecompositionSpec`, `AgentRefSpec`, `TaskNodeSpec` from Task 2
- Produces: `PatternSpec` (8 subtypes), `BranchSpec`, `JudgmentSpec` (with inner sealed `TriggerSpec`, `CallerSpec`, `AgreementSpec`)

- [ ] **Step 1: Write the YAML fixtures**

`agentic-yaml/src/test/resources/patterns/supervisor.yaml`:
```yaml
type: supervisor
routing:
  type: first-match
  guard: "priority > 5"
termination:
  - type: max-iterations
    iterations: 20
aggregation:
  type: collect-all
agents:
  - type: worker
    name: analyst
    description: "Analyses input data"
    capabilities:
      - data-analysis
  - type: worker
    name: reviewer
```

`agentic-yaml/src/test/resources/patterns/debate.yaml`:
```yaml
type: debate
maxRounds: 7
judge:
  type: worker
  name: arbiter
  description: "Resolves disputed points"
agents:
  - type: worker
    name: proponent
  - type: worker
    name: opponent
```

`agentic-yaml/src/test/resources/patterns/loop.yaml`:
```yaml
type: loop
maxIterations: 15
exitCondition: "qualityScore > 0.9"
agents:
  - type: worker
    name: improver
```

`agentic-yaml/src/test/resources/patterns/parallel.yaml`:
```yaml
type: parallel
agents:
  - type: worker
    name: researcher-1
  - type: worker
    name: researcher-2
  - type: worker
    name: researcher-3
```

`agentic-yaml/src/test/resources/patterns/voting.yaml`:
```yaml
type: voting
agents:
  - type: worker
    name: evaluator-1
  - type: worker
    name: evaluator-2
  - type: worker
    name: evaluator-3
```

`agentic-yaml/src/test/resources/patterns/conditional.yaml`:
```yaml
type: conditional
branches:
  - condition: "category == 'medical'"
    agent:
      type: worker
      name: medical-expert
  - condition: "category == 'legal'"
    agent:
      type: worker
      name: legal-expert
```

`agentic-yaml/src/test/resources/patterns/sequence.yaml`:
```yaml
type: sequence
agents:
  - type: worker
    name: step-1
  - type: worker
    name: step-2
  - type: worker
    name: step-3
```

`agentic-yaml/src/test/resources/patterns/htn.yaml`:
```yaml
type: htn
rootTask:
  type: compound
  name: main-task
  subtasks:
    - type: primitive
      name: gather-data
      executor: data-agent
    - type: compound
      name: process
      subtasks:
        - type: primitive
          name: clean
          executor: cleaner-agent
        - type: primitive
          name: analyse
          executor: analyst-agent
agents:
  - type: worker
    name: data-agent
  - type: worker
    name: cleaner-agent
  - type: worker
    name: analyst-agent
```

- [ ] **Step 2: Write the serialization test**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PatternSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
    }

    @ParameterizedTest
    @ValueSource(strings = {"supervisor", "debate", "loop", "parallel",
            "voting", "conditional", "sequence", "htn"})
    void deserializesAllTopologies(String topology) throws IOException {
        var yaml = readFixture(topology);
        var spec = mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec).isNotNull();
        assertThat(spec.agents()).isNotEmpty();
    }

    @Test
    void supervisorFields() throws IOException {
        var spec = loadPattern("supervisor", PatternSpec.Supervisor.class);
        assertThat(spec.routing()).isInstanceOf(RoutingSpec.FirstMatch.class);
        assertThat(((RoutingSpec.FirstMatch) spec.routing()).guard()).isEqualTo("priority > 5");
        assertThat(spec.termination()).hasSize(1);
        assertThat(spec.termination().get(0)).isInstanceOf(TerminationSpec.MaxIterations.class);
        assertThat(spec.aggregation()).isInstanceOf(AggregationSpec.CollectAll.class);
        assertThat(spec.agents()).hasSize(2);
    }

    @Test
    void debateFields() throws IOException {
        var spec = loadPattern("debate", PatternSpec.Debate.class);
        assertThat(spec.maxRounds()).isEqualTo(7);
        assertThat(spec.judge()).isNotNull();
        assertThat(spec.judge().name()).isEqualTo("arbiter");
    }

    @Test
    void loopFields() throws IOException {
        var spec = loadPattern("loop", PatternSpec.Loop.class);
        assertThat(spec.maxIterations()).isEqualTo(15);
        assertThat(spec.exitCondition()).isEqualTo("qualityScore > 0.9");
    }

    @Test
    void conditionalBranches() throws IOException {
        var spec = loadPattern("conditional", PatternSpec.Conditional.class);
        assertThat(spec.branches()).hasSize(2);
        assertThat(spec.branches().get(0).condition()).isEqualTo("category == 'medical'");
        assertThat(spec.branches().get(0).agent().name()).isEqualTo("medical-expert");
    }

    @Test
    void htnRootTask() throws IOException {
        var spec = loadPattern("htn", PatternSpec.Htn.class);
        assertThat(spec.rootTask()).isInstanceOf(TaskNodeSpec.Compound.class);
        var root = (TaskNodeSpec.Compound) spec.rootTask();
        assertThat(root.name()).isEqualTo("main-task");
        assertThat(root.subtasks()).hasSize(2);
    }

    @Test
    void composedAgentWithNestedPattern() throws IOException {
        var yaml = """
                type: supervisor
                agents:
                  - type: composed
                    name: research-team
                    pattern:
                      type: parallel
                      agents:
                        - type: worker
                          name: web-researcher
                        - type: worker
                          name: db-researcher
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var composed = (AgentRefSpec.Composed) spec.agents().get(0);
        assertThat(composed.pattern()).isInstanceOf(PatternSpec.Parallel.class);
        assertThat(composed.pattern().agents()).hasSize(2);
    }

    @Test
    void judgmentSpec() throws IOException {
        var yaml = """
                type: supervisor
                judgment:
                  trigger:
                    type: iteration-based
                    every: 3
                  caller:
                    type: single
                    callerName: quality-judge
                agents:
                  - type: worker
                    name: worker-1
                """;
        var spec = (PatternSpec.Supervisor) mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.judgment()).isNotNull();
        assertThat(spec.judgment().trigger()).isInstanceOf(JudgmentSpec.TriggerSpec.IterationBased.class);
        assertThat(((JudgmentSpec.TriggerSpec.IterationBased) spec.judgment().trigger()).every()).isEqualTo(3);
    }

    private <T extends PatternSpec> T loadPattern(String name, Class<T> type) throws IOException {
        var spec = mapper.readValue(readFixture(name), PatternSpec.class);
        assertThat(spec).isInstanceOf(type);
        return type.cast(spec);
    }

    private String readFixture(String name) throws IOException {
        try (var is = getClass().getResourceAsStream("/patterns/" + name + ".yaml")) {
            if (is == null) throw new IOException("Missing fixture: " + name + ".yaml");
            return new String(is.readAllBytes());
        }
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PatternSpecSerializationTest`
Expected: COMPILATION FAILURE (PatternSpec, BranchSpec, JudgmentSpec don't exist)

- [ ] **Step 4: Write `BranchSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

public record BranchSpec(String condition, AgentRefSpec agent) {}
```

- [ ] **Step 5: Write `JudgmentSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record JudgmentSpec(
        TriggerSpec trigger,
        CallerSpec caller,
        @Nullable AgreementSpec agreement
) {
    @JsonTypeInfo(use = Id.NAME, property = "type")
    @JsonSubTypes({
            @Type(value = TriggerSpec.AlwaysYield.class, name = "always-yield"),
            @Type(value = TriggerSpec.NeverYield.class, name = "never-yield"),
            @Type(value = TriggerSpec.IterationBased.class, name = "iteration-based"),
            @Type(value = TriggerSpec.ConfidenceThreshold.class, name = "confidence-threshold")
    })
    public sealed interface TriggerSpec {
        record AlwaysYield() implements TriggerSpec {}
        record NeverYield() implements TriggerSpec {}
        record IterationBased(int every) implements TriggerSpec {}
        record ConfidenceThreshold(double threshold, String extractor) implements TriggerSpec {}
    }

    @JsonTypeInfo(use = Id.NAME, property = "type")
    @JsonSubTypes({
            @Type(value = CallerSpec.Single.class, name = "single"),
            @Type(value = CallerSpec.FanOut.class, name = "fan-out"),
            @Type(value = CallerSpec.EscalationChain.class, name = "escalation-chain")
    })
    public sealed interface CallerSpec {
        record Single(String callerName) implements CallerSpec {}
        record FanOut(List<String> callerNames, AgreementSpec agreement) implements CallerSpec {}
        record EscalationChain(List<String> callerNames) implements CallerSpec {}
    }

    @JsonTypeInfo(use = Id.NAME, property = "type")
    @JsonSubTypes({
            @Type(value = AgreementSpec.Unanimous.class, name = "unanimous"),
            @Type(value = AgreementSpec.Majority.class, name = "majority"),
            @Type(value = AgreementSpec.Threshold.class, name = "threshold")
    })
    public sealed interface AgreementSpec {
        record Unanimous() implements AgreementSpec {}
        record Majority() implements AgreementSpec {}
        record Threshold(int minAgreements) implements AgreementSpec {}
    }
}
```

- [ ] **Step 6: Write `PatternSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.casehub.blocks.agentic.FailurePolicy;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = PatternSpec.Supervisor.class, name = "supervisor"),
        @Type(value = PatternSpec.Debate.class, name = "debate"),
        @Type(value = PatternSpec.Loop.class, name = "loop"),
        @Type(value = PatternSpec.Parallel.class, name = "parallel"),
        @Type(value = PatternSpec.Voting.class, name = "voting"),
        @Type(value = PatternSpec.Conditional.class, name = "conditional"),
        @Type(value = PatternSpec.Sequence.class, name = "sequence"),
        @Type(value = PatternSpec.Htn.class, name = "htn")
})
public sealed interface PatternSpec {

    @Nullable RoutingSpec routing();
    @Nullable List<TerminationSpec> termination();
    @Nullable AggregationSpec aggregation();
    @Nullable ActivationSpec activation();
    @Nullable DecompositionSpec decomposition();
    @Nullable FailurePolicy failurePolicy();
    @Nullable String task();
    List<AgentRefSpec> agents();
    @Nullable JudgmentSpec judgment();

    record Supervisor(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment
    ) implements PatternSpec {}

    record Debate(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable AgentRefSpec judge,
            int maxRounds
    ) implements PatternSpec {}

    record Loop(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            int maxIterations,
            @Nullable String exitCondition
    ) implements PatternSpec {}

    record Parallel(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment
    ) implements PatternSpec {}

    record Voting(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment
    ) implements PatternSpec {}

    record Conditional(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            List<BranchSpec> branches
    ) implements PatternSpec {}

    record Sequence(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment
    ) implements PatternSpec {}

    record Htn(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            TaskNodeSpec rootTask
    ) implements PatternSpec {}
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PatternSpecSerializationTest`
Expected: ALL PASS

- [ ] **Step 8: Run full module test suite**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: ALL PASS

- [ ] **Step 9: Commit**

```
feat(#165): PatternSpec + JudgmentSpec with 8 topology fixtures Refs #165
```

---

## Batch 2: Registries + Compiler

After this batch: YAML → `ExecutionModel` works for all 8 topologies. Strategy registries resolve spec records to runtime implementations. MVEL expressions compile via `ExpressionEngine`.

### Task 4: Strategy Registries + SelectAllRouting

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/routing/SelectAllRouting.java`
- Create: `blocks/src/test/java/io/casehub/blocks/agentic/routing/SelectAllRoutingTest.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/RoutingStrategyRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/TerminationConditionRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/AggregationStrategyRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/ActivationRuleRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/DecompositionStrategyRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/JudgmentTriggerRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/CallerStrategyRegistry.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/RegistryTest.java`

**Interfaces:**
- Consumes: all spec records from Tasks 2-3, runtime SPI types from blocks
- Produces: registry classes with `resolve(Spec) → RuntimeType` methods

- [ ] **Step 1: Write the failing test for SelectAllRouting**

```java
package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SelectAllRoutingTest {

    @Test
    void selectsAllCandidates() {
        var routing = new SelectAllRouting<>();
        var candidates = List.of(
                new RoutingCandidate(mock(AgentRef.class), null),
                new RoutingCandidate(mock(AgentRef.class), null),
                new RoutingCandidate(mock(AgentRef.class), null)
        );
        var context = new RoutingStrategy.RoutingContext<>("task", candidates, null);
        var decision = routing.route(context);
        assertThat(decision).isInstanceOf(RoutingStrategy.RoutingDecision.Selected.class);
        var selected = (RoutingStrategy.RoutingDecision.Selected<?>) decision;
        assertThat(selected.candidates()).hasSize(3);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=SelectAllRoutingTest`
Expected: COMPILATION FAILURE (SelectAllRouting doesn't exist)

- [ ] **Step 3: Write `SelectAllRouting.java`**

```java
package io.casehub.blocks.agentic.routing;

public class SelectAllRouting<T> implements RoutingStrategy<T> {

    @Override
    public RoutingDecision<T> route(RoutingContext<T> context) {
        return new RoutingDecision.Selected<>(context.candidates());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest=SelectAllRoutingTest`
Expected: PASS

- [ ] **Step 5: Write the registry test**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.activation.MaxIterationsGuard;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.aggregation.MajorityVote;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.blocks.agentic.routing.SelectAllRouting;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.yaml.spec.*;
import io.casehub.engine.plan.IdentityDecomposition;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryTest {

    @Nested
    class RoutingTests {
        private final RoutingStrategyRegistry registry = new RoutingStrategyRegistry();

        @Test
        void roundRobin() {
            var result = registry.resolve(new RoutingSpec.RoundRobin(), null);
            assertThat(result).isInstanceOf(RoundRobinRouting.class);
        }

        @Test
        void sequential() {
            var result = registry.resolve(new RoutingSpec.Sequential(), null);
            assertThat(result).isInstanceOf(SequentialRouting.class);
        }

        @Test
        void selectAll() {
            var result = registry.resolve(new RoutingSpec.SelectAll(), null);
            assertThat(result).isInstanceOf(SelectAllRouting.class);
        }
    }

    @Nested
    class TerminationTests {
        private final TerminationConditionRegistry registry = new TerminationConditionRegistry();

        @Test
        void maxIterations() {
            var result = registry.resolve(new TerminationSpec.MaxIterations(10), null);
            assertThat(result).isInstanceOf(MaxIterationsTermination.class);
        }

        @Test
        void singlePass() {
            var result = registry.resolve(new TerminationSpec.SinglePass(), null);
            assertThat(result).isInstanceOf(MaxIterationsTermination.class);
        }
    }

    @Nested
    class AggregationTests {
        private final AggregationStrategyRegistry registry = new AggregationStrategyRegistry();

        @Test
        void passThrough() {
            var result = registry.resolve(new AggregationSpec.PassThrough());
            assertThat(result).isInstanceOf(PassThrough.class);
        }

        @Test
        void collectAll() {
            var result = registry.resolve(new AggregationSpec.CollectAll());
            assertThat(result).isInstanceOf(CollectAll.class);
        }

        @Test
        void majorityVote() {
            var result = registry.resolve(new AggregationSpec.MajorityVote());
            assertThat(result).isInstanceOf(MajorityVote.class);
        }
    }

    @Nested
    class ActivationTests {
        private final ActivationRuleRegistry registry = new ActivationRuleRegistry();

        @Test
        void onDispatch() {
            var result = registry.resolve(new ActivationSpec.OnDispatch());
            assertThat(result).isInstanceOf(OnExplicitDispatch.class);
        }

        @Test
        void maxIterationsGuard() {
            var result = registry.resolve(new ActivationSpec.MaxIterationsGuard(5));
            assertThat(result).isInstanceOf(MaxIterationsGuard.class);
        }
    }

    @Nested
    class DecompositionTests {
        private final DecompositionStrategyRegistry registry = new DecompositionStrategyRegistry();

        @Test
        void identity() {
            var result = registry.resolve(new DecompositionSpec.Identity(), null);
            assertThat(result).isInstanceOf(IdentityDecomposition.class);
        }
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=RegistryTest`
Expected: COMPILATION FAILURE (registry classes don't exist)

- [ ] **Step 7: Write `RoutingStrategyRegistry.java`**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.LlmSelectedRouting;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.blocks.agentic.routing.RoutingStrategy;
import io.casehub.blocks.agentic.routing.SelectAllRouting;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.yaml.spec.RoutingSpec;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

public class RoutingStrategyRegistry {

    @SuppressWarnings("unchecked")
    public <T> RoutingStrategy<T> resolve(RoutingSpec spec, @Nullable ExpressionEngine engine) {
        return switch (spec) {
            case RoutingSpec.FirstMatch fm -> {
                if (fm.guard() != null && engine != null) {
                    var compiled = engine.compile(fm.guard(), Object.class, Boolean.class);
                    yield new FirstMatchRouting<>(c -> (Boolean) compiled.evaluate(c));
                }
                yield new FirstMatchRouting<>(c -> true);
            }
            case RoutingSpec.RoundRobin rr -> new RoundRobinRouting<>();
            case RoutingSpec.Sequential seq -> new SequentialRouting<>();
            case RoutingSpec.LlmSelected llm ->
                    throw new UnsupportedOperationException(
                            "llm-selected routing requires AgentProvider — use CDI injection");
            case RoutingSpec.SelectAll sa -> new SelectAllRouting<>();
        };
    }
}
```

- [ ] **Step 8: Write `TerminationConditionRegistry.java`**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.termination.GoalReached;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.yaml.spec.TerminationSpec;
import io.casehub.blocks.conversation.orchestration.AllAgreedTermination;
import io.casehub.blocks.conversation.orchestration.ContestedEscalation;
import io.casehub.blocks.conversation.orchestration.SupervisorTermination;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public class TerminationConditionRegistry {

    @SuppressWarnings("unchecked")
    public <T> TerminationCondition<T> resolve(TerminationSpec spec,
                                                @Nullable ExpressionEngine engine) {
        return switch (spec) {
            case TerminationSpec.MaxIterations mi ->
                    new MaxIterationsTermination<>(mi.iterations());
            case TerminationSpec.GoalReached gr -> {
                if (engine == null) throw new IllegalStateException(
                        "goal-reached requires ExpressionEngine for predicate compilation");
                var compiled = engine.compile(gr.when(), Object.class, Boolean.class);
                yield (TerminationCondition<T>) new GoalReached<>(ctx ->
                        (Boolean) compiled.evaluate(ctx));
            }
            case TerminationSpec.JudgeConvergence jc ->
                    throw new UnsupportedOperationException(
                            "judge-convergence resolved by PatternCompiler (needs agent ref)");
            case TerminationSpec.AllAgreed aa ->
                    (TerminationCondition<T>) new AllAgreedTermination(
                            aa.resolvedStatuses() != null ? aa.resolvedStatuses()
                                    : Set.of("RESOLVED", "ACCEPTED"));
            case TerminationSpec.Supervisor sv ->
                    sv.signalType() != null
                            ? (TerminationCondition<T>) new SupervisorTermination(sv.role(), sv.signalType())
                            : (TerminationCondition<T>) new SupervisorTermination(sv.role());
            case TerminationSpec.Contested ct ->
                    (TerminationCondition<T>) new ContestedEscalation(ct.maxDisputeRounds());
            case TerminationSpec.Convergence cv ->
                    throw new UnsupportedOperationException(
                            "convergence termination requires state extraction functions");
            case TerminationSpec.SinglePass sp -> new MaxIterationsTermination<>(1);
            case TerminationSpec.AgentCount ac ->
                    throw new UnsupportedOperationException(
                            "agent-count resolved by PatternCompiler (needs agent list)");
        };
    }
}
```

- [ ] **Step 9: Write `AggregationStrategyRegistry.java`**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.aggregation.AggregationStrategy;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.aggregation.MajorityVote;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.yaml.spec.AggregationSpec;

public class AggregationStrategyRegistry {

    @SuppressWarnings("unchecked")
    public <T> AggregationStrategy<T> resolve(AggregationSpec spec) {
        return switch (spec) {
            case AggregationSpec.PassThrough pt -> new PassThrough<>();
            case AggregationSpec.CollectAll ca -> new CollectAll<>();
            case AggregationSpec.MajorityVote mv -> new MajorityVote<>();
            case AggregationSpec.Auction au ->
                    throw new UnsupportedOperationException(
                            "auction requires BidExtractor — use CDI injection");
        };
    }
}
```

- [ ] **Step 10: Write `ActivationRuleRegistry.java`**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.activation.ActivationRule;
import io.casehub.blocks.agentic.activation.MaxIterationsGuard;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.yaml.spec.ActivationSpec;

public class ActivationRuleRegistry {

    @SuppressWarnings("unchecked")
    public <T> ActivationRule<T> resolve(ActivationSpec spec) {
        return switch (spec) {
            case ActivationSpec.OnDispatch od -> new OnExplicitDispatch<>();
            case ActivationSpec.MaxIterationsGuard mig ->
                    new MaxIterationsGuard<>(mig.maxIterations());
        };
    }
}
```

- [ ] **Step 11: Write `DecompositionStrategyRegistry.java`**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.decomposition.CapabilityDependencyDecomposition;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.yaml.spec.DecompositionSpec;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

public class DecompositionStrategyRegistry {

    @SuppressWarnings("unchecked")
    public <T> DecompositionStrategy<T> resolve(DecompositionSpec spec,
                                                 @Nullable ExpressionEngine engine) {
        return switch (spec) {
            case DecompositionSpec.Identity id -> new IdentityDecomposition<>();
            case DecompositionSpec.Static st ->
                    throw new UnsupportedOperationException(
                            "static decomposition with YAML methods not yet implemented");
            case DecompositionSpec.Llm llm ->
                    throw new UnsupportedOperationException(
                            "llm decomposition requires AgentProvider — use CDI injection");
            case DecompositionSpec.Hybrid hy ->
                    throw new UnsupportedOperationException(
                            "hybrid decomposition requires AgentProvider — use CDI injection");
            case DecompositionSpec.Heuristic he ->
                    throw new UnsupportedOperationException(
                            "heuristic decomposition requires DecompositionHeuristic — use CDI injection");
            case DecompositionSpec.Goap goap -> new CapabilityDependencyDecomposition<>();
            case DecompositionSpec.CapabilityDependency cd ->
                    new CapabilityDependencyDecomposition<>();
            case DecompositionSpec.ForwardReasoning fr ->
                    throw new UnsupportedOperationException(
                            "forward-reasoning requires stateCopier function — Java only");
        };
    }
}
```

- [ ] **Step 12: Write `JudgmentTriggerRegistry.java`**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.judgment.JudgmentTrigger;
import io.casehub.blocks.agentic.yaml.spec.JudgmentSpec.TriggerSpec;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

public class JudgmentTriggerRegistry {

    @SuppressWarnings("unchecked")
    public <T> JudgmentTrigger<T> resolve(TriggerSpec spec,
                                           @Nullable ExpressionEngine engine) {
        return switch (spec) {
            case TriggerSpec.AlwaysYield ay -> JudgmentTrigger.alwaysYield();
            case TriggerSpec.NeverYield ny -> JudgmentTrigger.neverYield();
            case TriggerSpec.IterationBased ib -> JudgmentTrigger.iterationBased(ib.every());
            case TriggerSpec.ConfidenceThreshold ct -> {
                if (engine == null) throw new IllegalStateException(
                        "confidence-threshold requires ExpressionEngine");
                var compiled = engine.compile(ct.extractor(), Object.class, Double.class);
                yield JudgmentTrigger.confidenceBelow(ct.threshold(),
                        ctx -> (Double) compiled.evaluate(ctx));
            }
        };
    }
}
```

- [ ] **Step 13: Write `CallerStrategyRegistry.java`**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.judgment.CallerRef;
import io.casehub.blocks.agentic.judgment.CallerStrategy;
import io.casehub.blocks.agentic.judgment.ConsensusAgreement;
import io.casehub.blocks.agentic.yaml.spec.JudgmentSpec.AgreementSpec;
import io.casehub.blocks.agentic.yaml.spec.JudgmentSpec.CallerSpec;

import java.util.List;

public class CallerStrategyRegistry {

    public CallerStrategy resolve(CallerSpec spec) {
        return switch (spec) {
            case CallerSpec.Single s -> new CallerStrategy.Single(CallerRef.named(s.callerName()));
            case CallerSpec.FanOut fo -> new CallerStrategy.FanOut(
                    fo.callerNames().stream().map(CallerRef::named).toList(),
                    resolveAgreement(fo.agreement()));
            case CallerSpec.EscalationChain ec -> new CallerStrategy.EscalationChain(
                    ec.callerNames().stream().map(CallerRef::named).toList());
        };
    }

    private ConsensusAgreement resolveAgreement(AgreementSpec spec) {
        return switch (spec) {
            case AgreementSpec.Unanimous u -> ConsensusAgreement.unanimous();
            case AgreementSpec.Majority m -> ConsensusAgreement.majority();
            case AgreementSpec.Threshold t -> ConsensusAgreement.threshold(t.minAgreements());
        };
    }
}
```

- [ ] **Step 14: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=SelectAllRoutingTest && mvn --batch-mode test -pl agentic-yaml -Dtest=RegistryTest`
Expected: ALL PASS

- [ ] **Step 15: Commit**

```
feat(#165): strategy registries + SelectAllRouting Refs #165
```

---

### Task 5: PatternCompiler + Expression Integration

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/PatternCompiler.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/compiler/PatternCompilerTest.java`

**Interfaces:**
- Consumes: all spec records (Tasks 2-3), all registries (Task 4), `ExpressionEngine` from platform-api
- Produces: `PatternCompiler.compile(PatternSpec) → ExecutionModel<T>` — the core compilation method

- [ ] **Step 1: Write the compiler test**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.aggregation.MajorityVote;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.blocks.agentic.routing.SelectAllRouting;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PatternCompilerTest {

    private ObjectMapper mapper;
    private PatternCompiler compiler;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        compiler = new PatternCompiler(new MvelExpressionEngine());
    }

    @Test
    void compilesSupervisor() throws IOException {
        var spec = loadPattern("supervisor");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.aggregation()).isInstanceOf(CollectAll.class);
        assertThat(model.activation()).isInstanceOf(OnExplicitDispatch.class);
        assertThat(model.decomposition()).isInstanceOf(IdentityDecomposition.class);
        assertThat(model.termination()).isInstanceOf(MaxIterationsTermination.class);
        assertThat(model.candidateSupplier().get()).hasSize(2);
        assertThat(model.failurePolicy()).isEqualTo(FailurePolicy.defaults());
    }

    @Test
    void compilesDebateWithJudge() throws IOException {
        var spec = loadPattern("debate");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(model.routing()).isInstanceOf(RoundRobinRouting.class);
        assertThat(model.candidateSupplier().get()).hasSize(2);
    }

    @Test
    void compilesLoop() throws IOException {
        var spec = loadPattern("loop");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.LOOP);
        assertThat(model.termination()).isInstanceOf(MaxIterationsTermination.class);
    }

    @Test
    void compilesParallelWithDefaults() throws IOException {
        var spec = loadPattern("parallel");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.PARALLEL);
        assertThat(model.routing()).isInstanceOf(SelectAllRouting.class);
        assertThat(model.aggregation()).isInstanceOf(CollectAll.class);
        assertThat(model.candidateSupplier().get()).hasSize(3);
    }

    @Test
    void compilesVotingWithDefaults() throws IOException {
        var spec = loadPattern("voting");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.VOTING);
        assertThat(model.routing()).isInstanceOf(SelectAllRouting.class);
        assertThat(model.aggregation()).isInstanceOf(MajorityVote.class);
    }

    @Test
    void compilesSequenceWithAgentCountTermination() throws IOException {
        var spec = loadPattern("sequence");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.SEQUENCE);
        assertThat(model.routing()).isInstanceOf(SequentialRouting.class);
        assertThat(model.termination()).isInstanceOf(MaxIterationsTermination.class);
        assertThat(model.candidateSupplier().get()).hasSize(3);
    }

    @Test
    void compilesConditional() throws IOException {
        var spec = loadPattern("conditional");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.CONDITIONAL);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
    }

    @Test
    void compilesHtn() throws IOException {
        var spec = loadPattern("htn");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.HTN);
        assertThat(model.candidateSupplier().get()).hasSize(3);
    }

    @Test
    void compilesComposedAgentWithNestedModel() throws IOException {
        var yaml = """
                type: supervisor
                agents:
                  - type: composed
                    name: research-team
                    pattern:
                      type: parallel
                      agents:
                        - type: worker
                          name: web-researcher
                        - type: worker
                          name: db-researcher
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var model = compiler.compile(spec);
        var candidate = model.candidateSupplier().get().get(0);
        assertThat(candidate.ref()).isInstanceOf(AgentRef.ComposedAgent.class);
        var composed = (AgentRef.ComposedAgent) candidate.ref();
        assertThat(composed.model().patternType()).isEqualTo(PatternType.PARALLEL);
    }

    @Test
    void agentDescriptorWiredFromCapabilities() throws IOException {
        var yaml = """
                type: supervisor
                agents:
                  - type: worker
                    name: analyst
                    description: "Data analyst"
                    capabilities:
                      - data-analysis
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var model = compiler.compile(spec);
        var candidate = model.candidateSupplier().get().get(0);
        assertThat(candidate.descriptor()).isNotNull();
    }

    @Test
    void expressionEvaluatesInRoutingGuard() throws IOException {
        var yaml = """
                type: supervisor
                routing:
                  type: first-match
                  guard: "name == 'target'"
                agents:
                  - type: worker
                    name: agent-1
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var model = compiler.compile(spec);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
    }

    @Test
    void failurePolicyFromYaml() throws IOException {
        var yaml = """
                type: supervisor
                failurePolicy:
                  onRoutingFailure: RETRY_BROADER
                  onDeadlock: ESCALATE
                  agentRetry:
                    maxRetries: 5
                    backoff: PT2S
                    backoffStrategy: EXPONENTIAL
                    onExhausted: ESCALATE
                agents:
                  - type: worker
                    name: agent-1
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var model = compiler.compile(spec);
        assertThat(model.failurePolicy().onRoutingFailure())
                .isEqualTo(FailurePolicy.RoutingFailureAction.RETRY_BROADER);
        assertThat(model.failurePolicy().onDeadlock())
                .isEqualTo(FailurePolicy.AggregationFailureAction.ESCALATE);
    }

    private PatternSpec loadPattern(String name) throws IOException {
        try (var is = getClass().getResourceAsStream("/patterns/" + name + ".yaml")) {
            return mapper.readValue(is, PatternSpec.class);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PatternCompilerTest`
Expected: COMPILATION FAILURE (PatternCompiler doesn't exist)

- [ ] **Step 3: Write `PatternCompiler.java`**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.ActivationRule;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.AggregationStrategy;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.aggregation.MajorityVote;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.blocks.agentic.routing.RoutingStrategy;
import io.casehub.blocks.agentic.routing.SelectAllRouting;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.yaml.registry.ActivationRuleRegistry;
import io.casehub.blocks.agentic.yaml.registry.AggregationStrategyRegistry;
import io.casehub.blocks.agentic.yaml.registry.DecompositionStrategyRegistry;
import io.casehub.blocks.agentic.yaml.registry.CallerStrategyRegistry;
import io.casehub.blocks.agentic.yaml.registry.JudgmentTriggerRegistry;
import io.casehub.blocks.agentic.yaml.registry.RoutingStrategyRegistry;
import io.casehub.blocks.agentic.yaml.registry.TerminationConditionRegistry;
import io.casehub.blocks.agentic.yaml.spec.AgentRefSpec;
import io.casehub.blocks.agentic.yaml.spec.JudgmentSpec;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.casehub.blocks.agentic.yaml.spec.TerminationSpec;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class PatternCompiler {

    private final RoutingStrategyRegistry routingRegistry = new RoutingStrategyRegistry();
    private final TerminationConditionRegistry terminationRegistry = new TerminationConditionRegistry();
    private final AggregationStrategyRegistry aggregationRegistry = new AggregationStrategyRegistry();
    private final ActivationRuleRegistry activationRegistry = new ActivationRuleRegistry();
    private final DecompositionStrategyRegistry decompositionRegistry = new DecompositionStrategyRegistry();
    private final JudgmentTriggerRegistry judgmentTriggerRegistry = new JudgmentTriggerRegistry();
    private final CallerStrategyRegistry callerStrategyRegistry = new CallerStrategyRegistry();

    private final @Nullable ExpressionEngine expressionEngine;

    public PatternCompiler(@Nullable ExpressionEngine expressionEngine) {
        this.expressionEngine = expressionEngine;
    }

    @SuppressWarnings("unchecked")
    public <T> ExecutionModel<T> compile(PatternSpec spec) {
        var agents = buildCandidates(spec.agents());
        var patternType = resolvePatternType(spec);

        var routing = resolveRouting(spec, agents);
        var termination = resolveTermination(spec, agents);
        var aggregation = resolveAggregation(spec, patternType);
        var activation = resolveActivation(spec);
        var decomposition = resolveDecomposition(spec);
        var failurePolicy = spec.failurePolicy() != null ? spec.failurePolicy()
                : FailurePolicy.defaults();
        var task = spec.task() != null ? spec.task() : "execution";
        var judgment = spec.judgment() != null ? resolveJudgment(spec.judgment()) : null;

        var candidateList = List.copyOf(agents);
        return new ExecutionModel<>(
                (RoutingStrategy<T>) routing,
                (DecompositionStrategy<T>) decomposition,
                (ActivationRule<T>) activation,
                (AggregationStrategy<T>) aggregation,
                (TerminationCondition<T>) termination,
                () -> candidateList,
                failurePolicy,
                List.of(),
                task,
                patternType,
                null,
                (JudgmentPhase<T>) judgment
        );
    }

    private JudgmentPhase<?> resolveJudgment(JudgmentSpec spec) {
        var trigger = judgmentTriggerRegistry.resolve(spec.trigger(), expressionEngine);
        var caller = callerStrategyRegistry.resolve(spec.caller());
        return JudgmentPolicy.builder()
                .trigger(trigger)
                .caller(caller)
                .build();
    }

    private PatternType resolvePatternType(PatternSpec spec) {
        return switch (spec) {
            case PatternSpec.Supervisor s -> PatternType.SUPERVISOR;
            case PatternSpec.Debate d -> PatternType.DEBATE;
            case PatternSpec.Loop l -> PatternType.LOOP;
            case PatternSpec.Parallel p -> PatternType.PARALLEL;
            case PatternSpec.Voting v -> PatternType.VOTING;
            case PatternSpec.Conditional c -> PatternType.CONDITIONAL;
            case PatternSpec.Sequence s -> PatternType.SEQUENCE;
            case PatternSpec.Htn h -> PatternType.HTN;
        };
    }

    private RoutingStrategy<?> resolveRouting(PatternSpec spec, List<RoutingCandidate> agents) {
        if (spec.routing() != null) {
            return routingRegistry.resolve(spec.routing(), expressionEngine);
        }
        return switch (spec) {
            case PatternSpec.Parallel p -> new SelectAllRouting<>();
            case PatternSpec.Voting v -> new SelectAllRouting<>();
            case PatternSpec.Sequence s -> new SequentialRouting<>();
            case PatternSpec.Conditional c -> new FirstMatchRouting<>(candidate -> true);
            case PatternSpec.Supervisor s -> new FirstMatchRouting<>(candidate -> true);
            case PatternSpec.Debate d -> new RoundRobinRouting<>();
            case PatternSpec.Loop l -> new RoundRobinRouting<>();
            case PatternSpec.Htn h -> new SequentialRouting<>();
        };
    }

    private TerminationCondition<?> resolveTermination(PatternSpec spec,
                                                        List<RoutingCandidate> agents) {
        if (spec.termination() != null && !spec.termination().isEmpty()) {
            if (spec.termination().size() == 1) {
                return resolveSingleTermination(spec.termination().get(0), agents);
            }
            var conditions = spec.termination().stream()
                    .map(t -> resolveSingleTermination(t, agents))
                    .toList();
            var composite = conditions.get(0);
            for (int i = 1; i < conditions.size(); i++) {
                composite = composite.or(conditions.get(i));
            }
            return composite;
        }
        return switch (spec) {
            case PatternSpec.Debate d -> new MaxIterationsTermination<>(d.maxRounds());
            case PatternSpec.Loop l -> new MaxIterationsTermination<>(l.maxIterations());
            case PatternSpec.Supervisor s -> new MaxIterationsTermination<>(10);
            case PatternSpec.Parallel p -> new MaxIterationsTermination<>(1);
            case PatternSpec.Voting v -> new MaxIterationsTermination<>(1);
            case PatternSpec.Conditional c -> new MaxIterationsTermination<>(1);
            case PatternSpec.Sequence s -> new MaxIterationsTermination<>(agents.size());
            case PatternSpec.Htn h -> new MaxIterationsTermination<>(1);
        };
    }

    private TerminationCondition<?> resolveSingleTermination(TerminationSpec ts,
                                                              List<RoutingCandidate> agents) {
        if (ts instanceof TerminationSpec.AgentCount) {
            return new MaxIterationsTermination<>(agents.size());
        }
        return terminationRegistry.resolve(ts, expressionEngine);
    }

    private AggregationStrategy<?> resolveAggregation(PatternSpec spec, PatternType type) {
        if (spec.aggregation() != null) {
            return aggregationRegistry.resolve(spec.aggregation());
        }
        return switch (type) {
            case PARALLEL -> new CollectAll<>();
            case VOTING -> new MajorityVote<>();
            case DEBATE -> new CollectAll<>();
            default -> new PassThrough<>();
        };
    }

    private ActivationRule<?> resolveActivation(PatternSpec spec) {
        if (spec.activation() != null) {
            return activationRegistry.resolve(spec.activation());
        }
        return new OnExplicitDispatch<>();
    }

    private DecompositionStrategy<?> resolveDecomposition(PatternSpec spec) {
        if (spec.decomposition() != null) {
            return decompositionRegistry.resolve(spec.decomposition(), expressionEngine);
        }
        return new IdentityDecomposition<>();
    }

    private List<RoutingCandidate> buildCandidates(List<AgentRefSpec> agentSpecs) {
        return agentSpecs.stream()
                .map(this::buildCandidate)
                .toList();
    }

    private RoutingCandidate buildCandidate(AgentRefSpec agentSpec) {
        var ref = buildAgentRef(agentSpec);
        AgentDescriptor descriptor = null;
        if (agentSpec.description() != null || agentSpec.capabilities() != null) {
            descriptor = AgentDescriptor.of(agentSpec.name(),
                    agentSpec.description(), agentSpec.capabilities());
        }
        return new RoutingCandidate(ref, descriptor);
    }

    private AgentRef buildAgentRef(AgentRefSpec spec) {
        return switch (spec) {
            case AgentRefSpec.Composed c -> {
                var nestedModel = compile(c.pattern());
                yield new AgentRef.ComposedAgent(nestedModel);
            }
            default -> new AgentRef.ExternalAgent(spec.name(), null);
        };
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PatternCompilerTest`
Expected: ALL PASS

Note: The `buildAgentRef` method maps non-composed agent specs to `ExternalAgent` as a placeholder. Worker, Channel, and Human agents require runtime infrastructure (Worker registry, ChannelManager, WorkItemFactory) that the compiler can't access without CDI. The ExternalAgent mapping preserves the name for routing purposes. CDI-aware compilation (via the deployment module's recorder) will provide full resolution.

- [ ] **Step 5: Run full module tests**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```
feat(#165): PatternCompiler — YAML → ExecutionModel for all 8 topologies Refs #165
```

---

## Batch 3: Secondary Specs + Schema + Deployment

After this batch: full feature — negotiation/conversation/normative spec records, JSON schema generation from spec records, Quarkus build extension for classpath YAML discovery.

### Task 6: Negotiation, Conversation, Normative Specs

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/NegotiationSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/AcceptancePolicySpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ConversationSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/TurnPolicySpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/EpistemicRuleSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ConvergencePolicySpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ConflictResolutionSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/AcceptancePolicyRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/TurnPolicyRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/EpistemicRuleRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/ConvergencePolicyRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/ConflictResolutionRegistry.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/SecondarySpecSerializationTest.java`

**Interfaces:**
- Produces: spec records + registries for negotiation, conversation, and normative subsystems (spec records only — no compiler for these, per design spec)

- [ ] **Step 1: Write the test**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecondarySpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
    }

    @Nested
    class NegotiationSpecs {
        @Test
        void fullNegotiation() throws Exception {
            var yaml = """
                    parties:
                      - buyer
                      - seller
                    acceptance:
                      type: majority
                    termination:
                      - type: max-iterations
                        iterations: 10
                    """;
            var spec = mapper.readValue(yaml, NegotiationSpec.class);
            assertThat(spec.parties()).containsExactlyInAnyOrder("buyer", "seller");
            assertThat(spec.acceptance()).isInstanceOf(AcceptancePolicySpec.Majority.class);
        }
    }

    @Nested
    class ConversationSpecs {
        @Test
        void fullConversation() throws Exception {
            var yaml = """
                    turnPolicy:
                      type: round-robin
                    epistemicRule:
                      type: explicit-acknowledgement
                      minParticipants: 2
                    """;
            var spec = mapper.readValue(yaml, ConversationSpec.class);
            assertThat(spec.turnPolicy()).isInstanceOf(TurnPolicySpec.RoundRobin.class);
            assertThat(spec.epistemicRule())
                    .isInstanceOf(EpistemicRuleSpec.ExplicitAcknowledgement.class);
            assertThat(((EpistemicRuleSpec.ExplicitAcknowledgement) spec.epistemicRule())
                    .minParticipants()).isEqualTo(2);
        }
    }

    @Nested
    class ConflictResolutionSpecs {
        @Test
        void priority() throws Exception {
            var spec = mapper.readValue("type: priority", ConflictResolutionSpec.class);
            assertThat(spec).isInstanceOf(ConflictResolutionSpec.Priority.class);
        }

        @Test
        void mostRestrictive() throws Exception {
            var spec = mapper.readValue("type: most-restrictive", ConflictResolutionSpec.class);
            assertThat(spec).isInstanceOf(ConflictResolutionSpec.MostRestrictive.class);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=SecondarySpecSerializationTest`
Expected: COMPILATION FAILURE

- [ ] **Step 3: Write `AcceptancePolicySpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = AcceptancePolicySpec.Unanimous.class, name = "unanimous"),
        @Type(value = AcceptancePolicySpec.Majority.class, name = "majority"),
        @Type(value = AcceptancePolicySpec.Threshold.class, name = "threshold")
})
public sealed interface AcceptancePolicySpec {
    record Unanimous() implements AcceptancePolicySpec {}
    record Majority() implements AcceptancePolicySpec {}
    record Threshold(int minAcceptances) implements AcceptancePolicySpec {}
}
```

- [ ] **Step 4: Write `NegotiationSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

public record NegotiationSpec(
        Set<String> parties,
        AcceptancePolicySpec acceptance,
        @Nullable List<TerminationSpec> termination
) {}
```

- [ ] **Step 5: Write `TurnPolicySpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = TurnPolicySpec.RoundRobin.class, name = "round-robin"),
        @Type(value = TurnPolicySpec.Addressed.class, name = "addressed"),
        @Type(value = TurnPolicySpec.PointAddressed.class, name = "point-addressed"),
        @Type(value = TurnPolicySpec.Free.class, name = "free")
})
public sealed interface TurnPolicySpec {
    record RoundRobin() implements TurnPolicySpec {}
    record Addressed() implements TurnPolicySpec {}
    record PointAddressed() implements TurnPolicySpec {}
    record Free() implements TurnPolicySpec {}
}
```

- [ ] **Step 6: Write `EpistemicRuleSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = EpistemicRuleSpec.ExplicitAcknowledgement.class, name = "explicit-acknowledgement"),
        @Type(value = EpistemicRuleSpec.TacitAcceptance.class, name = "tacit-acceptance"),
        @Type(value = EpistemicRuleSpec.CommitmentResolution.class, name = "commitment-resolution")
})
public sealed interface EpistemicRuleSpec {
    record ExplicitAcknowledgement(int minParticipants) implements EpistemicRuleSpec {}
    record TacitAcceptance(int windowRounds) implements EpistemicRuleSpec {}
    record CommitmentResolution() implements EpistemicRuleSpec {}
}
```

- [ ] **Step 7: Write `ConvergencePolicySpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ConvergencePolicySpec.Structural.class, name = "structural"),
        @Type(value = ConvergencePolicySpec.CommonGroundRatio.class, name = "common-ground-ratio"),
        @Type(value = ConvergencePolicySpec.Composite.class, name = "composite")
})
public sealed interface ConvergencePolicySpec {
    record Structural(double similarityThreshold, int staleRounds) implements ConvergencePolicySpec {}
    record CommonGroundRatio(double consensusThreshold, double deadlockDisputeRatio) implements ConvergencePolicySpec {}
    record Composite(List<ConvergencePolicySpec> policies) implements ConvergencePolicySpec {}
}
```

- [ ] **Step 8: Write `ConversationSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

public record ConversationSpec(
        @Nullable TurnPolicySpec turnPolicy,
        @Nullable EpistemicRuleSpec epistemicRule,
        @Nullable ConvergencePolicySpec convergencePolicy
) {}
```

- [ ] **Step 9: Write `ConflictResolutionSpec.java`**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ConflictResolutionSpec.Priority.class, name = "priority"),
        @Type(value = ConflictResolutionSpec.Specificity.class, name = "specificity"),
        @Type(value = ConflictResolutionSpec.Recency.class, name = "recency"),
        @Type(value = ConflictResolutionSpec.MostRestrictive.class, name = "most-restrictive"),
        @Type(value = ConflictResolutionSpec.Escalation.class, name = "escalation")
})
public sealed interface ConflictResolutionSpec {
    record Priority() implements ConflictResolutionSpec {}
    record Specificity() implements ConflictResolutionSpec {}
    record Recency() implements ConflictResolutionSpec {}
    record MostRestrictive() implements ConflictResolutionSpec {}
    record Escalation(String escalationDecision) implements ConflictResolutionSpec {}
}
```

- [ ] **Step 10: Write the secondary registries**

`AcceptancePolicyRegistry.java`:
```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.AcceptancePolicySpec;
import io.casehub.blocks.negotiation.AcceptancePolicy;
import io.casehub.blocks.negotiation.MajorityAcceptance;
import io.casehub.blocks.negotiation.ThresholdAcceptance;
import io.casehub.blocks.negotiation.UnanimousAcceptance;

public class AcceptancePolicyRegistry {
    public AcceptancePolicy resolve(AcceptancePolicySpec spec) {
        return switch (spec) {
            case AcceptancePolicySpec.Unanimous u -> new UnanimousAcceptance();
            case AcceptancePolicySpec.Majority m -> new MajorityAcceptance();
            case AcceptancePolicySpec.Threshold t -> new ThresholdAcceptance(t.minAcceptances());
        };
    }
}
```

`TurnPolicyRegistry.java`:
```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.TurnPolicySpec;
import io.casehub.blocks.conversation.orchestration.AddressedTurnPolicy;
import io.casehub.blocks.conversation.orchestration.FreeTurnPolicy;
import io.casehub.blocks.conversation.orchestration.PointAddressedTurnPolicy;
import io.casehub.blocks.conversation.orchestration.RoundRobinTurnPolicy;
import io.casehub.blocks.conversation.orchestration.TurnPolicy;

public class TurnPolicyRegistry {
    public TurnPolicy resolve(TurnPolicySpec spec) {
        return switch (spec) {
            case TurnPolicySpec.RoundRobin rr -> new RoundRobinTurnPolicy();
            case TurnPolicySpec.Addressed a -> new AddressedTurnPolicy();
            case TurnPolicySpec.PointAddressed pa -> new PointAddressedTurnPolicy();
            case TurnPolicySpec.Free f -> new FreeTurnPolicy();
        };
    }
}
```

`EpistemicRuleRegistry.java`:
```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.EpistemicRuleSpec;
import io.casehub.blocks.conversation.EpistemicRule;
import io.casehub.blocks.conversation.EpistemicRules;

public class EpistemicRuleRegistry {
    public EpistemicRule resolve(EpistemicRuleSpec spec) {
        return switch (spec) {
            case EpistemicRuleSpec.ExplicitAcknowledgement ea ->
                    EpistemicRules.explicitAcknowledgement(ea.minParticipants());
            case EpistemicRuleSpec.TacitAcceptance ta ->
                    EpistemicRules.tacitAcceptance(ta.windowRounds());
            case EpistemicRuleSpec.CommitmentResolution cr ->
                    EpistemicRules.commitmentResolution();
        };
    }
}
```

`ConvergencePolicyRegistry.java`:
```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.ConvergencePolicySpec;
import io.casehub.blocks.conversation.ConvergencePolicy;
import io.casehub.blocks.conversation.ConvergencePolicies;

public class ConvergencePolicyRegistry {
    public ConvergencePolicy resolve(ConvergencePolicySpec spec) {
        return switch (spec) {
            case ConvergencePolicySpec.Structural s ->
                    ConvergencePolicies.structural(s.similarityThreshold(), s.staleRounds());
            case ConvergencePolicySpec.CommonGroundRatio cgr ->
                    ConvergencePolicies.commonGroundRatio(cgr.consensusThreshold(),
                            cgr.deadlockDisputeRatio());
            case ConvergencePolicySpec.Composite c ->
                    ConvergencePolicies.composite(
                            c.policies().stream().map(this::resolve)
                                    .toArray(ConvergencePolicy[]::new));
        };
    }
}
```

`ConflictResolutionRegistry.java`:
```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.ConflictResolutionSpec;
import io.casehub.blocks.normative.ConflictResolutionStrategy;
import io.casehub.blocks.normative.EscalationResolution;
import io.casehub.blocks.normative.MostRestrictiveResolution;
import io.casehub.blocks.normative.PriorityResolution;
import io.casehub.blocks.normative.RecencyResolution;
import io.casehub.blocks.normative.SpecificityResolution;

public class ConflictResolutionRegistry {

    @SuppressWarnings("unchecked")
    public <T> ConflictResolutionStrategy<T> resolve(ConflictResolutionSpec spec) {
        return switch (spec) {
            case ConflictResolutionSpec.Priority p -> new PriorityResolution<>();
            case ConflictResolutionSpec.Specificity s -> new SpecificityResolution<>();
            case ConflictResolutionSpec.Recency r -> new RecencyResolution<>();
            case ConflictResolutionSpec.MostRestrictive mr ->
                    (ConflictResolutionStrategy<T>) new MostRestrictiveResolution();
            case ConflictResolutionSpec.Escalation e ->
                    (ConflictResolutionStrategy<T>) new EscalationResolution<>(e.escalationDecision());
        };
    }
}
```

- [ ] **Step 11: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=SecondarySpecSerializationTest`
Expected: ALL PASS

- [ ] **Step 12: Commit**

```
feat(#165): negotiation, conversation, normative specs + registries Refs #165
```

---

### Task 7: Schema Generation

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/schema/BlocksSchemaGenerator.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/schema/SchemaGenerationTest.java`

**Interfaces:**
- Consumes: all spec records (sealed interfaces)
- Produces: `BlocksSchemaGenerator.generate(Class<?>) → JsonNode` — schema generation from spec record types

Note: add `casehub-platform-schema-generator` to `agentic-yaml/pom.xml` as a compile dependency for this task.

- [ ] **Step 1: Add schema-generator dependency to `agentic-yaml/pom.xml`**

Add to dependencies:
```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-platform-schema-generator</artifactId>
  <version>${project.version}</version>
</dependency>
```

- [ ] **Step 2: Write the schema generation test**

```java
package io.casehub.blocks.agentic.yaml.schema;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.casehub.blocks.agentic.yaml.spec.RoutingSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaGenerationTest {

    private BlocksSchemaGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new BlocksSchemaGenerator();
    }

    @Test
    void routingSpecSchemaHasDiscriminator() {
        JsonNode schema = generator.generate(RoutingSpec.class);
        assertThat(schema.has("oneOf") || schema.has("anyOf")).isTrue();
    }

    @Test
    void patternSpecSchemaHasAllTopologies() {
        JsonNode schema = generator.generate(PatternSpec.class);
        assertThat(schema.toString()).contains("supervisor");
        assertThat(schema.toString()).contains("debate");
        assertThat(schema.toString()).contains("loop");
        assertThat(schema.toString()).contains("parallel");
        assertThat(schema.toString()).contains("voting");
        assertThat(schema.toString()).contains("conditional");
        assertThat(schema.toString()).contains("sequence");
        assertThat(schema.toString()).contains("htn");
    }

    @Test
    void patternSpecSchemaHasAgentsField() {
        JsonNode schema = generator.generate(PatternSpec.class);
        assertThat(schema.toString()).contains("agents");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=SchemaGenerationTest`
Expected: COMPILATION FAILURE

- [ ] **Step 4: Write `BlocksSchemaGenerator.java`**

```java
package io.casehub.blocks.agentic.yaml.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import io.casehub.schema.generator.module.EnumInliningModule;
import io.casehub.schema.generator.module.SealedHierarchyModule;
import io.casehub.schema.generator.module.UnevaluatedPropertiesModule;

public class BlocksSchemaGenerator {

    private final SchemaGenerator generator;

    public BlocksSchemaGenerator() {
        var configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.with(new SealedHierarchyModule());
        configBuilder.with(new EnumInliningModule());
        configBuilder.with(new UnevaluatedPropertiesModule());
        this.generator = new SchemaGenerator(configBuilder.build());
    }

    public JsonNode generate(Class<?> rootType) {
        return generator.generateSchema(rootType);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=SchemaGenerationTest`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```
feat(#165): BlocksSchemaGenerator — victools schema from spec records Refs #165
```

---

### Task 8: Deployment Module

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/runtime/AgenticRecorder.java`
- Create: `agentic-yaml-deployment/src/main/java/io/casehub/blocks/agentic/yaml/deployment/AgenticYamlProcessor.java`

**Interfaces:**
- Consumes: `PatternCompiler`, `PatternSpec`, `ObjectMapper` from agentic-yaml module
- Produces: Quarkus build extension that discovers `META-INF/agentic/*.yaml`, validates them, and registers compiled `ExecutionModel` beans

- [ ] **Step 1: Write `AgenticRecorder.java`**

```java
package io.casehub.blocks.agentic.yaml.runtime;

import io.casehub.blocks.agentic.yaml.compiler.PatternCompiler;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AgenticRecorder {

    public PatternCompiler createCompiler(ExpressionEngine expressionEngine) {
        return new PatternCompiler(expressionEngine);
    }
}
```

- [ ] **Step 2: Write `AgenticYamlProcessor.java`**

```java
package io.casehub.blocks.agentic.yaml.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class AgenticYamlProcessor {

    private static final String AGENTIC_YAML_PATH = "META-INF/agentic/";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    @BuildStep
    List<HotDeploymentWatchedFileBuildItem> watchYamlFiles() {
        return List.of(new HotDeploymentWatchedFileBuildItem(AGENTIC_YAML_PATH));
    }

    @BuildStep
    AgenticPatternsBuildItem discoverAndValidatePatterns() throws IOException {
        var patterns = new ArrayList<PatternSpec>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = cl.getResources(AGENTIC_YAML_PATH);

        while (resources.hasMoreElements()) {
            URL dir = resources.nextElement();
            if ("file".equals(dir.getProtocol())) {
                var path = java.nio.file.Path.of(dir.toURI());
                try (var files = java.nio.file.Files.list(path)) {
                    files.filter(f -> f.toString().endsWith(".yaml") || f.toString().endsWith(".yml"))
                         .forEach(f -> {
                             try (InputStream in = java.nio.file.Files.newInputStream(f)) {
                                 var spec = YAML.readValue(in, PatternSpec.class);
                                 patterns.add(spec);
                             } catch (IOException e) {
                                 throw new RuntimeException(
                                         "Failed to parse agentic YAML: " + f, e);
                             }
                         });
                }
            }
        }

        return new AgenticPatternsBuildItem(patterns);
    }
}
```

Note: `AgenticPatternsBuildItem` is a simple build item wrapper:
```java
package io.casehub.blocks.agentic.yaml.deployment;

import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.quarkus.builder.item.SimpleBuildItem;

import java.util.List;

public final class AgenticPatternsBuildItem extends SimpleBuildItem {
    private final List<PatternSpec> patterns;

    public AgenticPatternsBuildItem(List<PatternSpec> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    public List<PatternSpec> patterns() { return patterns; }
}
```

- [ ] **Step 3: Run full build to verify compilation**

Run: `mvn --batch-mode install -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Run full test suite**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```
feat(#165): deployment module — AgenticRecorder + AgenticYamlProcessor Refs #165
```

---

## Future Work (out of scope for this plan)

- **D6: Annotation-driven spec record generation** — `@YamlSpec` annotation + deployment step to auto-generate spec records from SPI implementations. Reduces maintenance burden for the ~35 simple strategy specs.
- **D7: Drift detection** — compile-time test verifying hand-written specs cover all constructor parameters of their corresponding implementations.
- **Conversation compiler** — wires `ConversationSpec` → `ConversationOrchestrator` (different runtime architecture).
- **Negotiation compiler** — wires `NegotiationSpec` → `NegotiationFold` + `AcceptancePolicy`.
- **ShorthandModule integration** — `routing: round-robin` instead of `routing: { type: round-robin }` for zero-config strategies. Depends on platform promoting ShorthandModule.
- **CDI-aware agent resolution** — full Worker/Channel/Human agent resolution via CDI lookup in the compiler.

## References

- `specs/issue-165-agentic-yaml-expansion/2026-09-07-agentic-yaml-expansion-design.md` — design spec this plan implements
- `specs/issue-165-agentic-yaml-expansion/decisions.md` — D1-D8 architecture decisions
- `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/PipelineCompiler.java` — compiler precedent
- `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/SummariserRegistry.java` — registry precedent
- `summarisation-yaml/src/main/java/io/casehub/blocks/summarisation/yaml/GroupingDefinition.java` — sealed interface + Jackson pattern
- `blocks/src/main/java/io/casehub/blocks/agentic/pattern/AbstractPatternBuilder.java` — builder defaults
- `blocks/src/main/java/io/casehub/blocks/agentic/model/ExecutionModel.java` — compilation target
- `specs/main/2026-08-23-typescript-programming-model-design.md` — pattern-to-YAML mapping
- GitHub #165 — focal issue
