# Trust, Routing, Oversight YAML Surface — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #252 — Trust, routing, oversight YAML surface
**Issue group:** #248 (branch covers #246 epic)

**Goal:** Add YAML specs and registries for 5 governance config types so
YAML-only application definitions can configure trust routing, outcome
weights, disposition profiles, and risk decisions.

**Architecture:** 4 new spec records + 4 registries in agentic-yaml,
following the established sealed-interface/adapted-record/direct-reuse
patterns from #250 and #251. DispositionProfile is direct reuse. Schema
generation extended for the sealed RiskDecisionSpec.

**Tech Stack:** Jackson YAML, engine-api (RiskDecision, CandidateSetStrategy,
TrustRoutingPolicyKeys, RoutingOutcome), eidos-api (DispositionAxis),
blocks routing.agent (CbrOutcomeWeights, CoordinationOutcomeWeights,
DispositionProfile)

## Global Constraints

- All spec records in `io.casehub.blocks.agentic.yaml.spec`
- All registries in `io.casehub.blocks.agentic.yaml.registry`
- Use `Objects.requireNonNull` for non-null fields in compact constructors
- Use `Map.copyOf()` for defensive copying of map fields
- Use `@Nullable` from `org.jspecify.annotations` for nullable fields
- Sealed interfaces use `@JsonTypeInfo(use = Id.NAME, property = "type")`
  with `@JsonSubTypes`
- Verify exact import paths for engine-api types via `ide_find_class`
  before writing code — packages may differ from spec assumptions

---

## Batch 1: Weight map specs and routing policy

### Task 1: CbrOutcomeWeightsSpec + CoordinationOutcomeWeightsSpec

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/CbrOutcomeWeightsSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/CoordinationOutcomeWeightsSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/CbrOutcomeWeightsRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/CoordinationOutcomeWeightsRegistry.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/GovernanceSpecSerializationTest.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/GovernanceRegistryTest.java`

**Interfaces:**
- Consumes: `RoutingOutcome` enum from engine-api, `CbrOutcomeWeights` and `CoordinationOutcomeWeights` SPIs from blocks
- Produces: `CbrOutcomeWeightsSpec`, `CoordinationOutcomeWeightsSpec` records; `CbrOutcomeWeightsRegistry.resolve(spec)`, `CoordinationOutcomeWeightsRegistry.resolve(spec)`

- [ ] **Step 1: Verify imports — find exact packages for RoutingOutcome, CbrOutcomeWeights, CoordinationOutcomeWeights**

Run: `ide_find_class` for `RoutingOutcome`, `CbrOutcomeWeights`, `CoordinationOutcomeWeights`
Note the exact packages for use in imports.

- [ ] **Step 2: Write failing serialization test for CbrOutcomeWeightsSpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
// import RoutingOutcome with verified package
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernanceSpecSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Nested
    class CbrOutcomeWeightsSpecTests {

        @Test
        void deserialises_weight_map() throws Exception {
            var yaml = """
                    weights:
                      SUCCESS: 1.0
                      GATE_EXPIRED: 0.5
                      FAILURE: 0.0
                    """;
            var spec = mapper.readValue(yaml, CbrOutcomeWeightsSpec.class);
            assertThat(spec.weights()).hasSize(3);
            assertThat(spec.weights().get(RoutingOutcome.SUCCESS)).isEqualTo(1.0);
            assertThat(spec.weights().get(RoutingOutcome.GATE_EXPIRED)).isEqualTo(0.5);
            assertThat(spec.weights().get(RoutingOutcome.FAILURE)).isEqualTo(0.0);
        }

        @Test
        void rejects_null_weights() {
            assertThatThrownBy(() -> new CbrOutcomeWeightsSpec(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class CoordinationOutcomeWeightsSpecTests {

        @Test
        void deserialises_string_key_weight_map() throws Exception {
            var yaml = """
                    weights:
                      COMPLETED: 1.0
                      FAULTED: 0.2
                      CANCELLED: 0.0
                    """;
            var spec = mapper.readValue(yaml, CoordinationOutcomeWeightsSpec.class);
            assertThat(spec.weights()).hasSize(3);
            assertThat(spec.weights().get("COMPLETED")).isEqualTo(1.0);
            assertThat(spec.weights().get("FAULTED")).isEqualTo(0.2);
        }

        @Test
        void rejects_null_weights() {
            assertThatThrownBy(() -> new CoordinationOutcomeWeightsSpec(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceSpecSerializationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation error — `CbrOutcomeWeightsSpec` not found

- [ ] **Step 4: Implement CbrOutcomeWeightsSpec and CoordinationOutcomeWeightsSpec**

```java
// CbrOutcomeWeightsSpec.java
package io.casehub.blocks.agentic.yaml.spec;

// import RoutingOutcome with verified package
import java.util.Map;
import java.util.Objects;

public record CbrOutcomeWeightsSpec(Map<RoutingOutcome, Double> weights) {
    public CbrOutcomeWeightsSpec {
        Objects.requireNonNull(weights, "weights");
        weights = Map.copyOf(weights);
    }
}
```

```java
// CoordinationOutcomeWeightsSpec.java
package io.casehub.blocks.agentic.yaml.spec;

import java.util.Map;
import java.util.Objects;

public record CoordinationOutcomeWeightsSpec(Map<String, Double> weights) {
    public CoordinationOutcomeWeightsSpec {
        Objects.requireNonNull(weights, "weights");
        weights = Map.copyOf(weights);
    }
}
```

- [ ] **Step 5: Run serialization tests — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceSpecSerializationTest`
Expected: PASS

- [ ] **Step 6: Write failing registry tests**

```java
// GovernanceRegistryTest.java
package io.casehub.blocks.agentic.yaml.registry;

// import RoutingOutcome with verified package
import io.casehub.blocks.agentic.yaml.spec.CbrOutcomeWeightsSpec;
import io.casehub.blocks.agentic.yaml.spec.CoordinationOutcomeWeightsSpec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceRegistryTest {

    @Nested
    class CbrOutcomeWeightsRegistryTests {

        @Test
        void resolve_returns_spi_with_weights() {
            var spec = new CbrOutcomeWeightsSpec(Map.of(
                    RoutingOutcome.SUCCESS, 1.0,
                    RoutingOutcome.FAILURE, 0.0));
            var registry = new CbrOutcomeWeightsRegistry();
            var result = registry.resolve(spec);
            assertThat(result.weights()).containsEntry(RoutingOutcome.SUCCESS, 1.0);
            assertThat(result.weights()).containsEntry(RoutingOutcome.FAILURE, 0.0);
        }
    }

    @Nested
    class CoordinationOutcomeWeightsRegistryTests {

        @Test
        void resolve_returns_spi_with_weights() {
            var spec = new CoordinationOutcomeWeightsSpec(Map.of(
                    "COMPLETED", 1.0,
                    "CANCELLED", 0.0));
            var registry = new CoordinationOutcomeWeightsRegistry();
            var result = registry.resolve(spec);
            assertThat(result.weights()).containsEntry("COMPLETED", 1.0);
            assertThat(result.weights()).containsEntry("CANCELLED", 0.0);
        }
    }
}
```

- [ ] **Step 7: Run registry tests to verify fail**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceRegistryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation error — `CbrOutcomeWeightsRegistry` not found

- [ ] **Step 8: Implement registries**

```java
// CbrOutcomeWeightsRegistry.java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.routing.agent.CbrOutcomeWeights;
import io.casehub.blocks.agentic.yaml.spec.CbrOutcomeWeightsSpec;

import java.util.Map;

public class CbrOutcomeWeightsRegistry {
    public CbrOutcomeWeights resolve(CbrOutcomeWeightsSpec spec) {
        var weights = Map.copyOf(spec.weights());
        return () -> weights;
    }
}
```

```java
// CoordinationOutcomeWeightsRegistry.java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.routing.agent.CoordinationOutcomeWeights;
import io.casehub.blocks.agentic.yaml.spec.CoordinationOutcomeWeightsSpec;

import java.util.Map;

public class CoordinationOutcomeWeightsRegistry {
    public CoordinationOutcomeWeights resolve(CoordinationOutcomeWeightsSpec spec) {
        var weights = Map.copyOf(spec.weights());
        return () -> weights;
    }
}
```

- [ ] **Step 9: Run all tests — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceSpecSerializationTest,GovernanceRegistryTest`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/CbrOutcomeWeightsSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/CoordinationOutcomeWeightsSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/CbrOutcomeWeightsRegistry.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/CoordinationOutcomeWeightsRegistry.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/GovernanceSpecSerializationTest.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/GovernanceRegistryTest.java
git commit -m "feat(#252): CbrOutcomeWeightsSpec, CoordinationOutcomeWeightsSpec + registries"
```

### Task 2: TrustRoutingPolicyKeysSpec

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/TrustRoutingPolicyKeysSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/TrustRoutingPolicyKeysRegistry.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/GovernanceSpecSerializationTest.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/GovernanceRegistryTest.java`

**Interfaces:**
- Consumes: `TrustRoutingPolicyKeys` from engine-api (verify package via `ide_find_class`)
- Produces: `TrustRoutingPolicyKeysSpec` record; `TrustRoutingPolicyKeysRegistry.resolve(spec) → TrustRoutingPolicyKeys`

- [ ] **Step 1: Verify imports — find exact package for TrustRoutingPolicyKeys**

Run: `ide_find_class` for `TrustRoutingPolicyKeys`

- [ ] **Step 2: Add failing serialization test to GovernanceSpecSerializationTest**

```java
@Nested
class TrustRoutingPolicyKeysSpecTests {

    @Test
    void deserialises_with_floors() throws Exception {
        var yaml = """
                scopePrefix: aml
                floors:
                  investigation-accuracy: investigation-accuracy-floor
                  sar-quality: sar-quality-floor
                """;
        var spec = mapper.readValue(yaml, TrustRoutingPolicyKeysSpec.class);
        assertThat(spec.scopePrefix()).isEqualTo("aml");
        assertThat(spec.floors()).hasSize(2);
        assertThat(spec.floors()).containsEntry("investigation-accuracy",
                "investigation-accuracy-floor");
    }

    @Test
    void deserialises_without_floors() throws Exception {
        var yaml = """
                scopePrefix: clinical
                """;
        var spec = mapper.readValue(yaml, TrustRoutingPolicyKeysSpec.class);
        assertThat(spec.scopePrefix()).isEqualTo("clinical");
        assertThat(spec.floors()).isNull();
    }

    @Test
    void rejects_null_scope_prefix() {
        assertThatThrownBy(() -> new TrustRoutingPolicyKeysSpec(null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 3: Run test to verify fail**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceSpecSerializationTest#TrustRoutingPolicyKeysSpecTests`
Expected: compilation error — `TrustRoutingPolicyKeysSpec` not found

- [ ] **Step 4: Implement TrustRoutingPolicyKeysSpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record TrustRoutingPolicyKeysSpec(
        String scopePrefix,
        @Nullable Map<String, String> floors) {

    public TrustRoutingPolicyKeysSpec {
        Objects.requireNonNull(scopePrefix, "scopePrefix");
        if (floors != null) floors = Map.copyOf(floors);
    }
}
```

- [ ] **Step 5: Run serialization test — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceSpecSerializationTest#TrustRoutingPolicyKeysSpecTests`
Expected: PASS

- [ ] **Step 6: Add failing registry test to GovernanceRegistryTest**

```java
@Nested
class TrustRoutingPolicyKeysRegistryTests {

    @Test
    void resolve_creates_keys_with_scope() {
        var spec = new TrustRoutingPolicyKeysSpec("aml", null);
        var registry = new TrustRoutingPolicyKeysRegistry();
        var result = registry.resolve(spec);
        assertThat(result.scopePrefix()).isEqualTo("aml");
    }

    @Test
    void resolve_creates_keys_with_floors() {
        var spec = new TrustRoutingPolicyKeysSpec("aml",
                Map.of("accuracy", "accuracy-floor"));
        var registry = new TrustRoutingPolicyKeysRegistry();
        var result = registry.resolve(spec);
        assertThat(result.scopePrefix()).isEqualTo("aml");
        assertThat(result.floorKeys()).containsKey("accuracy");
    }
}
```

- [ ] **Step 7: Run registry test to verify fail**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceRegistryTest#TrustRoutingPolicyKeysRegistryTests`
Expected: compilation error — `TrustRoutingPolicyKeysRegistry` not found

- [ ] **Step 8: Implement TrustRoutingPolicyKeysRegistry**

```java
package io.casehub.blocks.agentic.yaml.registry;

// import TrustRoutingPolicyKeys with verified package
import io.casehub.blocks.agentic.yaml.spec.TrustRoutingPolicyKeysSpec;

public class TrustRoutingPolicyKeysRegistry {

    public TrustRoutingPolicyKeys resolve(TrustRoutingPolicyKeysSpec spec) {
        var keys = TrustRoutingPolicyKeys.create(spec.scopePrefix());
        if (spec.floors() != null) {
            for (var entry : spec.floors().entrySet()) {
                keys = keys.withFloor(entry.getKey(), entry.getValue());
            }
        }
        return keys;
    }
}
```

- [ ] **Step 9: Run all tests — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceSpecSerializationTest,GovernanceRegistryTest`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/TrustRoutingPolicyKeysSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/TrustRoutingPolicyKeysRegistry.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/GovernanceSpecSerializationTest.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/GovernanceRegistryTest.java
git commit -m "feat(#252): TrustRoutingPolicyKeysSpec + registry"
```

## Batch 2: Oversight spec, schema, and docs

### Task 3: RiskDecisionSpec

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/RiskDecisionSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/RiskDecisionRegistry.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/GovernanceSpecSerializationTest.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/GovernanceRegistryTest.java`

**Interfaces:**
- Consumes: `RiskDecision`, `CandidateSetStrategy`, `QuorumConfig` from engine-api (verify packages via `ide_find_class`)
- Produces: `RiskDecisionSpec` sealed interface; `RiskDecisionRegistry.resolve(spec) → RiskDecision`

- [ ] **Step 1: Verify imports — find exact packages**

Run: `ide_find_class` for `RiskDecision`, `CandidateSetStrategy`, `QuorumConfig`, `OnThresholdReached`

- [ ] **Step 2: Add failing serialization tests to GovernanceSpecSerializationTest**

Add `JavaTimeModule` registration to the mapper (Duration deserialization):

```java
private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        .registerModule(new JavaTimeModule());
```

Add nested test class:

```java
@Nested
class RiskDecisionSpecTests {

    @Test
    void deserialises_autonomous() throws Exception {
        var yaml = """
                type: autonomous
                """;
        var spec = mapper.readValue(yaml, RiskDecisionSpec.class);
        assertThat(spec).isInstanceOf(RiskDecisionSpec.Autonomous.class);
    }

    @Test
    void deserialises_gate_required() throws Exception {
        var yaml = """
                type: gate-required
                reason: SAR filing requires human review
                reversible: false
                candidateGroups:
                  type: static
                  groups:
                    - mlro
                expiresIn: PT24H
                """;
        var spec = mapper.readValue(yaml, RiskDecisionSpec.class);
        assertThat(spec).isInstanceOf(RiskDecisionSpec.GateRequired.class);
        var g = (RiskDecisionSpec.GateRequired) spec;
        assertThat(g.reason()).isEqualTo("SAR filing requires human review");
        assertThat(g.reversible()).isFalse();
        assertThat(g.expiresIn()).isEqualTo(Duration.ofHours(24));
        assertThat(g.scope()).isNull();
        assertThat(g.resolutionType()).isNull();
        assertThat(g.quorum()).isNull();
    }

    @Test
    void deserialises_gate_required_with_quorum() throws Exception {
        var yaml = """
                type: gate-required
                reason: Dual approval required
                reversible: true
                candidateGroups:
                  type: static
                  groups:
                    - compliance
                    - legal
                expiresIn: PT48H
                scope: critical-action
                quorum:
                  instances: 2
                  required: 2
                  allowSameAssignee: false
                """;
        var spec = mapper.readValue(yaml, RiskDecisionSpec.class);
        var g = (RiskDecisionSpec.GateRequired) spec;
        assertThat(g.quorum()).isNotNull();
        assertThat(g.quorum().instances()).isEqualTo(2);
        assertThat(g.quorum().required()).isEqualTo(2);
        assertThat(g.scope()).isEqualTo("critical-action");
    }

    @Test
    void rejects_null_reason() {
        assertThatThrownBy(() -> new RiskDecisionSpec.GateRequired(
                null, false, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 3: Run test to verify fail**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceSpecSerializationTest#RiskDecisionSpecTests`
Expected: compilation error — `RiskDecisionSpec` not found

- [ ] **Step 4: Implement RiskDecisionSpec**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
// import CandidateSetStrategy, QuorumConfig with verified packages
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = RiskDecisionSpec.Autonomous.class, name = "autonomous"),
        @Type(value = RiskDecisionSpec.GateRequired.class, name = "gate-required")
})
public sealed interface RiskDecisionSpec {

    record Autonomous() implements RiskDecisionSpec {}

    record GateRequired(
            String reason,
            boolean reversible,
            CandidateSetStrategy candidateGroups,
            Duration expiresIn,
            @Nullable String scope,
            @Nullable String resolutionType,
            @Nullable QuorumConfig quorum) implements RiskDecisionSpec {

        public GateRequired {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(candidateGroups, "candidateGroups");
            Objects.requireNonNull(expiresIn, "expiresIn");
        }
    }
}
```

- [ ] **Step 5: Run serialization tests — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceSpecSerializationTest#RiskDecisionSpecTests`
Expected: PASS. If CandidateSetStrategy deserialization fails (Jackson annotation
mismatch), check the `@JsonSubTypes` on CandidateSetStrategy in engine-api and
adjust the YAML accordingly.

- [ ] **Step 6: Add failing registry tests to GovernanceRegistryTest**

```java
@Nested
class RiskDecisionRegistryTests {

    @Test
    void resolve_autonomous() {
        var spec = new RiskDecisionSpec.Autonomous();
        var registry = new RiskDecisionRegistry();
        var result = registry.resolve(spec);
        assertThat(result).isInstanceOf(RiskDecision.Autonomous.class);
    }

    @Test
    void resolve_gate_required() {
        // Use ide_find_class to find StaticSetStrategy or construct CandidateSetStrategy
        var spec = new RiskDecisionSpec.GateRequired(
                "needs review", true,
                /* construct a CandidateSetStrategy instance */,
                Duration.ofHours(1), "test-scope",
                null, null);
        var registry = new RiskDecisionRegistry();
        var result = registry.resolve(spec);
        assertThat(result).isInstanceOf(RiskDecision.GateRequired.class);
        var g = (RiskDecision.GateRequired) result;
        assertThat(g.reason()).isEqualTo("needs review");
        assertThat(g.reversible()).isTrue();
        assertThat(g.scope()).isEqualTo("test-scope");
    }

    @Test
    void resolve_gate_required_with_resolution_type() {
        var spec = new RiskDecisionSpec.GateRequired(
                "typed gate", false,
                /* construct a CandidateSetStrategy instance */,
                Duration.ofMinutes(30), null,
                "java.lang.String", null);
        var registry = new RiskDecisionRegistry();
        var result = registry.resolve(spec);
        var g = (RiskDecision.GateRequired) result;
        assertThat(g.resolutionType()).isEqualTo(String.class);
    }

    @Test
    void resolve_gate_required_rejects_unknown_class() {
        var spec = new RiskDecisionSpec.GateRequired(
                "bad type", false,
                /* construct a CandidateSetStrategy instance */,
                Duration.ofMinutes(30), null,
                "com.nonexistent.FakeClass", null);
        var registry = new RiskDecisionRegistry();
        assertThatThrownBy(() -> registry.resolve(spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown resolutionType");
    }
}
```

Note: Replace `/* construct a CandidateSetStrategy instance */` with the actual
construction after verifying the available subtypes via `ide_find_implementations`
on `CandidateSetStrategy`. Expected: `new StaticSetStrategy(Set.of("group"))`.

- [ ] **Step 7: Run registry tests to verify fail**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceRegistryTest#RiskDecisionRegistryTests`
Expected: compilation error — `RiskDecisionRegistry` not found

- [ ] **Step 8: Implement RiskDecisionRegistry**

```java
package io.casehub.blocks.agentic.yaml.registry;

// import RiskDecision with verified package
import io.casehub.blocks.agentic.yaml.spec.RiskDecisionSpec;

public class RiskDecisionRegistry {

    public RiskDecision resolve(RiskDecisionSpec spec) {
        return switch (spec) {
            case RiskDecisionSpec.Autonomous ignored ->
                    new RiskDecision.Autonomous();
            case RiskDecisionSpec.GateRequired g -> {
                Class<?> resolutionType = null;
                if (g.resolutionType() != null) {
                    try {
                        resolutionType = Class.forName(g.resolutionType());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException(
                                "Unknown resolutionType: " + g.resolutionType(), e);
                    }
                }
                yield new RiskDecision.GateRequired(
                        g.reason(), g.reversible(), g.candidateGroups(),
                        g.expiresIn(), g.scope(), resolutionType, g.quorum());
            }
        };
    }
}
```

- [ ] **Step 9: Run all tests — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceSpecSerializationTest,GovernanceRegistryTest`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/RiskDecisionSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/RiskDecisionRegistry.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/GovernanceSpecSerializationTest.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/GovernanceRegistryTest.java
git commit -m "feat(#252): RiskDecisionSpec sealed interface + registry"
```

### Task 4: Schema generation, DispositionProfile test, coverage matrix

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/schema/BlocksSchemaGenerator.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/schema/SchemaGenerationTest.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/GovernanceSpecSerializationTest.java`
- Modify: `docs/yaml-coverage.md`

**Interfaces:**
- Consumes: All spec types from Tasks 1-3, `DispositionProfile` from blocks, `DispositionAxis` from eidos-api
- Produces: JSON Schema entries for all governance spec types; updated coverage matrix

- [ ] **Step 1: Verify DispositionAxis serialization format**

Run: `ide_find_class` for `DispositionAxis`. Read the source to check for `@JsonValue`
on `jsonKey()`. This determines whether YAML keys are `SOCIAL_ORIENTATION` (enum
constant name) or `socialOrient` (jsonKey). Write the test accordingly.

- [ ] **Step 2: Add DispositionProfile serialization test to GovernanceSpecSerializationTest**

```java
@Nested
class DispositionProfileTests {

    @Test
    void deserialises_with_desired_and_weights() throws Exception {
        // Use enum constant names OR jsonKey() based on Step 1 finding
        var yaml = """
                desired:
                  SOCIAL_ORIENTATION: collaborative
                  RISK_APPETITE: conservative
                weights:
                  SOCIAL_ORIENTATION: 0.8
                  RISK_APPETITE: 0.6
                """;
        var profile = mapper.readValue(yaml, DispositionProfile.class);
        assertThat(profile.desired()).hasSize(2);
        assertThat(profile.desired().get(DispositionAxis.SOCIAL_ORIENTATION))
                .isEqualTo("collaborative");
        assertThat(profile.weights().get(DispositionAxis.RISK_APPETITE))
                .isEqualTo(0.6);
    }

    @Test
    void deserialises_without_weights() throws Exception {
        var yaml = """
                desired:
                  RULE_FOLLOWING: strict
                """;
        var profile = mapper.readValue(yaml, DispositionProfile.class);
        assertThat(profile.desired()).hasSize(1);
        assertThat(profile.weights()).isEmpty();
    }
}
```

- [ ] **Step 3: Run DispositionProfile test — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=GovernanceSpecSerializationTest#DispositionProfileTests`
Expected: PASS. If it fails because of `@JsonValue` on `jsonKey()`, update the
YAML keys to camelCase (socialOrient, riskAppetite, etc.) and re-run.

- [ ] **Step 4: Add RiskDecisionSpec to BlocksSchemaGenerator discriminator overrides**

Read `BlocksSchemaGenerator.java` to find the `buildDiscriminatorOverrides()` method.
Add `RiskDecisionSpec.class` to the `List.of(...)`:

```java
// In the existing List.of(...) in buildDiscriminatorOverrides():
RiskDecisionSpec.class,
```

- [ ] **Step 5: Add schema generation tests for new types**

Add to `SchemaGenerationTest.java`:

```java
@Test
void generates_schema_for_cbr_outcome_weights_spec() {
    var schema = generator.generate(CbrOutcomeWeightsSpec.class);
    assertThat(schema).isNotNull();
}

@Test
void generates_schema_for_coordination_outcome_weights_spec() {
    var schema = generator.generate(CoordinationOutcomeWeightsSpec.class);
    assertThat(schema).isNotNull();
}

@Test
void generates_schema_for_trust_routing_policy_keys_spec() {
    var schema = generator.generate(TrustRoutingPolicyKeysSpec.class);
    assertThat(schema).isNotNull();
}

@Test
void generates_schema_for_risk_decision_spec() {
    var schema = generator.generate(RiskDecisionSpec.class);
    assertThat(schema).isNotNull();
    // Discriminator test — verify "type" property with autonomous/gate-required
    assertThat(schema.toString()).contains("autonomous");
    assertThat(schema.toString()).contains("gate-required");
}

@Test
void generates_schema_for_disposition_profile() {
    var schema = generator.generate(DispositionProfile.class);
    assertThat(schema).isNotNull();
}
```

- [ ] **Step 6: Run schema generation tests — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=SchemaGenerationTest`
Expected: PASS

- [ ] **Step 7: Update coverage matrix**

Edit `docs/yaml-coverage.md` §19 and §20:

§19 — change the 6 entries:
- TrustRoutingPolicyKeys → `Done | #252 — adapted record`
- CbrOutcomeWeights → `Done | #252 — weight map spec`
- CoordinationOutcomeWeights → `Done | #252 — weight map spec`
- DispositionProfile → `Done | #252 — direct reuse`
- AttestationContext → `N/A | Runtime data — not config`
- AttestationIntent → `N/A | Runtime data — not config`

§20 — change the 3 entries:
- RiskDecision → `Done | #252 — sealed interface spec`
- ClassificationContext → `N/A | Runtime data — not config`
- GateOutcome → `N/A | Response type — not config`

Update the summary count at the bottom of the file.

- [ ] **Step 8: Run full agentic-yaml test suite**

Run: `mvn test -pl agentic-yaml`
Expected: all tests PASS

- [ ] **Step 9: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/schema/BlocksSchemaGenerator.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/schema/SchemaGenerationTest.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/GovernanceSpecSerializationTest.java \
       docs/yaml-coverage.md
git commit -m "feat(#252): schema generation, DispositionProfile test, coverage matrix §19 §20 Done"
```

## References

- [2026-09-11-trust-routing-oversight-yaml-design.md] — design spec
- engine-api: TrustRoutingPolicyKeys, RiskDecision, CandidateSetStrategy, QuorumConfig, RoutingOutcome
- eidos-api: DispositionAxis
- blocks: CbrOutcomeWeights, CoordinationOutcomeWeights, DispositionProfile
- GE-20260811-e941cc — DispositionProfile vs AgentDisposition type split
- GE-20260530-9cdfb5 — MapPreferences.get() null default
- [GitHub #252](https://github.com/casehubio/blocks/issues/252)
