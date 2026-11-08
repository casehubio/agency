# HiveTopologyCompiler Deployment Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #418 — HiveTopologyCompiler deployment wiring via @BuildStep
**Issue group:** #416 (epic), #418 (this issue)

**Goal:** Wire `HiveTopologyCompiler` into the Quarkus deployment
pipeline so hive topologies declared in pattern YAML are automatically
compiled and registered as CDI beans.

**Architecture:** A new `@BuildStep` in `AgenticYamlProcessor` extracts
`TopologySpec.Hive` from already-parsed patterns. A `@Record(RUNTIME_INIT)`
step calls `AgenticRecorder.compileHiveTopology()` which returns a
`CompiledHive` registered as a `@Singleton` synthetic bean.

**Tech Stack:** Java 17, Quarkus deployment API, JUnit 5, AssertJ

## Global Constraints

- Deployment module package: `io.casehub.agency.core.yaml.deployment`
  (despite file path under `agentic/yaml/deployment/`)
- Runtime recorder package: `io.casehub.agency.core.yaml.runtime`
- `HiveTopologyCompiler` is in `io.casehub.agency.agentic.yaml.topology`
- `AgentProvider` is unavailable at build time — pass `null` to compiler
  (pattern execution is still stubbed)
- No Mockito in deployment module — use plain JUnit + AssertJ

---

## Batch 1: Deployment wiring

### Task 1: TopologyBuildItem + extraction build step with test

**Files:**
- Create: `agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/TopologyBuildItem.java`
- Modify: `agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AgenticYamlProcessor.java`
- Create: `agentic-yaml-deployment/src/test/java/io/casehub/agency/core/yaml/deployment/TopologyExtractionTest.java`

**Interfaces:**
- Consumes: `AgenticPatternsBuildItem.patterns()` → `List<PatternDefinition>`,
  `PatternDefinition.Swarm.topology()` → `@Nullable TopologySpec`
- Produces: `TopologyBuildItem(List<TopologySpec.Hive>)`,
  `TopologyBuildItem.hiveTopologies()` → `List<TopologySpec.Hive>`
  — used by Task 2

- [ ] **Step 1: Write the extraction test**

Create directory and test file:

```java
package io.casehub.agency.core.yaml.deployment;

import io.casehub.agency.core.yaml.spec.PatternDefinition;
import io.casehub.agency.core.yaml.spec.TopologySpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyExtractionTest {

    private final AgenticYamlProcessor processor = new AgenticYamlProcessor();

    private static PatternDefinition.Swarm swarmWithHive() {
        return new PatternDefinition.Swarm(
                "test", "desc", null, null, null, null, null, null,
                "task", List.of(), null, null,
                null, null, null, 0,
                new TopologySpec.Hive(null, List.of("coordinator", "worker"),
                        "supervisor", null),
                null, null);
    }

    private static PatternDefinition.Swarm swarmWithSwarmTopology() {
        return new PatternDefinition.Swarm(
                "test", "desc", null, null, null, null, null, null,
                "task", List.of(), null, null,
                null, null, null, 0,
                new TopologySpec.Swarm(null),
                null, null);
    }

    private static PatternDefinition.Swarm swarmWithNullTopology() {
        return new PatternDefinition.Swarm(
                "test", "desc", null, null, null, null, null, null,
                "task", List.of(), null, null,
                null, null, null, 0,
                null,
                null, null);
    }

    private static PatternDefinition.Supervisor nonSwarmPattern() {
        return new PatternDefinition.Supervisor(
                "test", "desc", null, null, null, null, null, null,
                "task", List.of(), null, null);
    }

    @Test
    void swarmWithHiveTopology_extracted() {
        var item = processor.extractHiveTopologies(
                new AgenticPatternsBuildItem(List.of(swarmWithHive())));

        assertThat(item.hiveTopologies()).hasSize(1);
        assertThat(item.hiveTopologies().get(0).orchestration()).isEqualTo("supervisor");
        assertThat(item.hiveTopologies().get(0).roles()).containsExactly("coordinator", "worker");
    }

    @Test
    void swarmWithSwarmTopology_notExtracted() {
        var item = processor.extractHiveTopologies(
                new AgenticPatternsBuildItem(List.of(swarmWithSwarmTopology())));

        assertThat(item.hiveTopologies()).isEmpty();
    }

    @Test
    void noSwarmPatterns_emptyResult() {
        var item = processor.extractHiveTopologies(
                new AgenticPatternsBuildItem(List.of(nonSwarmPattern())));

        assertThat(item.hiveTopologies()).isEmpty();
    }

    @Test
    void mixedPatterns_onlyHiveExtracted() {
        var item = processor.extractHiveTopologies(
                new AgenticPatternsBuildItem(List.of(
                        swarmWithHive(), swarmWithSwarmTopology(), nonSwarmPattern())));

        assertThat(item.hiveTopologies()).hasSize(1);
    }

    @Test
    void swarmWithNullTopology_notExtracted() {
        var item = processor.extractHiveTopologies(
                new AgenticPatternsBuildItem(List.of(swarmWithNullTopology())));

        assertThat(item.hiveTopologies()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml-deployment -Dtest=TopologyExtractionTest`
Expected: FAIL — `TopologyBuildItem` class does not exist

- [ ] **Step 3: Create TopologyBuildItem**

```java
package io.casehub.agency.core.yaml.deployment;

import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.quarkus.builder.item.SimpleBuildItem;

import java.util.List;

public final class TopologyBuildItem extends SimpleBuildItem {
    private final List<TopologySpec.Hive> hiveTopologies;

    public TopologyBuildItem(List<TopologySpec.Hive> hiveTopologies) {
        this.hiveTopologies = List.copyOf(hiveTopologies);
    }

    public List<TopologySpec.Hive> hiveTopologies() {
        return hiveTopologies;
    }
}
```

- [ ] **Step 4: Add extractHiveTopologies to AgenticYamlProcessor**

Add this method to `AgenticYamlProcessor`, after the
`discoverCognitionConfig()` method:

```java
@BuildStep
TopologyBuildItem extractHiveTopologies(AgenticPatternsBuildItem patterns) {
    var hiveSpecs = patterns.patterns().stream()
            .filter(p -> p instanceof PatternDefinition.Swarm s
                    && s.topology() instanceof TopologySpec.Hive)
            .map(p -> (TopologySpec.Hive)
                    ((PatternDefinition.Swarm) p).topology())
            .toList();
    return new TopologyBuildItem(hiveSpecs);
}
```

Add these imports to `AgenticYamlProcessor`:

```java
import io.casehub.agency.core.yaml.spec.PatternDefinition;
import io.casehub.agency.core.yaml.spec.TopologySpec;
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml-deployment -Dtest=TopologyExtractionTest`
Expected: All 5 tests PASS

- [ ] **Step 6: Commit**

```bash
git add agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/TopologyBuildItem.java
git add agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AgenticYamlProcessor.java
git add agentic-yaml-deployment/src/test/java/io/casehub/agency/core/yaml/deployment/TopologyExtractionTest.java
git commit -m "feat(deployment): TopologyBuildItem + extraction build step for hive topologies

Refs casehubio/agency#418"
```

### Task 2: Recorder method + registration build step

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgenticRecorder.java`
- Modify: `agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AgenticYamlProcessor.java`

**Interfaces:**
- Consumes: `TopologyBuildItem.hiveTopologies()` from Task 1,
  `HiveTopologyCompiler.compile(TopologySpec.Hive, AgentProvider)` →
  `CompiledHive`, `HiveTopologyCompiler.CompiledHive` record
- Produces: `AgenticRecorder.compileHiveTopology(TopologySpec.Hive)` →
  `RuntimeValue<CompiledHive>`, `@Singleton` CDI bean of type `CompiledHive`

- [ ] **Step 1: Add compileHiveTopology to AgenticRecorder**

Add this method to `AgenticRecorder`, after `createPromptOptimisationCompiler`:

```java
public RuntimeValue<HiveTopologyCompiler.CompiledHive> compileHiveTopology(
        TopologySpec.Hive spec) {
    return new RuntimeValue<>(new HiveTopologyCompiler().compile(spec, null));
}
```

Add these imports to `AgenticRecorder`:

```java
import io.casehub.agency.agentic.yaml.topology.HiveTopologyCompiler;
import io.casehub.agency.core.yaml.spec.TopologySpec;
```

- [ ] **Step 2: Add registerHiveTopologies build step to AgenticYamlProcessor**

Add this method to `AgenticYamlProcessor`, after `extractHiveTopologies`:

```java
@BuildStep
@Record(RUNTIME_INIT)
List<SyntheticBeanBuildItem> registerHiveTopologies(
        AgenticRecorder recorder,
        TopologyBuildItem topologies) {
    return topologies.hiveTopologies().stream()
            .map(spec -> {
                RuntimeValue<HiveTopologyCompiler.CompiledHive> rv =
                        recorder.compileHiveTopology(spec);
                return SyntheticBeanBuildItem
                        .configure(HiveTopologyCompiler.CompiledHive.class)
                        .scope(jakarta.inject.Singleton.class)
                        .runtimeValue(rv)
                        .done();
            })
            .toList();
}
```

Add these imports to `AgenticYamlProcessor`:

```java
import io.casehub.agency.agentic.yaml.topology.HiveTopologyCompiler;
import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
```

- [ ] **Step 3: Verify compilation**

Run: `mvn --batch-mode compile -pl agentic-yaml,agentic-yaml-deployment`
Expected: BUILD SUCCESS

- [ ] **Step 4: Run all deployment tests**

Run: `mvn --batch-mode test -pl agentic-yaml-deployment`
Expected: BUILD SUCCESS — all tests pass

- [ ] **Step 5: Run full build**

Run: `mvn --batch-mode install -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgenticRecorder.java
git add agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AgenticYamlProcessor.java
git commit -m "feat(deployment): recorder + registration for HiveTopology CDI bean

AgenticRecorder.compileHiveTopology() compiles TopologySpec.Hive at
RUNTIME_INIT. Registered as @Singleton CompiledHive synthetic bean.

Refs casehubio/agency#418"
```

## References

- [2026-11-07-hive-topology-deployment-design.md] — design spec
- `AgenticYamlProcessor.java` — existing build steps
- `AgenticPatternsBuildItem.java` — pattern build item
- `AgenticRecorder.java` — existing recorder
- `HiveTopologyCompiler.java` — compiler being wired
- `PatternDefinition.java` — Swarm variant with topology field
- `TopologySpec.java` — Hive variant
- GitHub #418 — focal issue
- GitHub #416 — parent epic
