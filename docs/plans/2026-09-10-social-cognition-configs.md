# Social Cognition Configs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #247 — Social cognition configs — YAML surface for agent personality and cognition
**Issue group:** #247

**Goal:** Make all 13 social cognition config records YAML-expressible via spec records with `@Nullable` fields, a `CognitionCompiler` that merges partial YAML with `defaults()`, and a deployment processor that discovers `cognition.yaml` on the classpath.

**Architecture:** Spec records with `@Nullable` fields mirror the runtime configs. `CognitionDefinition` is the root Jackson type for `cognition.yaml`. `CognitionCompiler` merges non-null spec values with `defaults()` to produce runtime configs. The deployment processor discovers YAML, deserializes, compiles, and registers configs as CDI beans.

**Tech Stack:** Java 17, Jackson YAML (jackson-dataformat-yaml + JavaTimeModule), Quarkus deployment module (BuildStep, Recorder, SyntheticBeanBuildItem), JUnit 5 + AssertJ.

## Global Constraints

- Spec records live in `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/`
- Compiler lives in `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/`
- All spec record fields are `@Nullable` — primitives boxed (`double` → `Double`)
- `Duration` fields use ISO-8601 in YAML (`PT4H`, `PT2S`) — Jackson JavaTimeModule handles this
- Omitted sections in YAML use the runtime config's `defaults()` (or `DEFAULT` for RetentionConfig)
- `agentic-yaml` pom.xml needs `casehub-neocortex-memory-api` as a provided dependency (for `MoodBaseline`, `MemoryDomain`)

---

## Batch 1: Foundation — root type, compiler, 3 representative configs

### Task 1: CognitionDefinition root + DriveConfigSpec + compiler skeleton

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/CognitionDefinition.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/DriveConfigSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/CognitionCompiler.java`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/compiler/CognitionCompilerTest.java`
- Create: `agentic-yaml/src/test/resources/cognition/drive-only.yaml`

**Interfaces:**
- Produces: `CognitionDefinition` (root record), `DriveConfigSpec` (first spec record), `CognitionCompiler.compile(CognitionDefinition) → CompiledCognition`, `CompiledCognition` (record holding all 13 compiled configs)

- [ ] **Step 1: Write the failing test — DriveConfig partial YAML merge**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionCompilerTest {

    private ObjectMapper mapper;
    private CognitionCompiler compiler;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        compiler = new CognitionCompiler();
    }

    @Test
    void drivePartialOverrideMergesWithDefaults() throws IOException {
        var yaml = """
                drive:
                  changeThreshold: 0.1
                  axisWeights:
                    CURIOSITY: 1.5
                """;
        var definition = mapper.readValue(yaml, CognitionDefinition.class);
        var compiled = compiler.compile(definition);

        assertThat(compiled.drive().changeThreshold()).isEqualTo(0.1);
        assertThat(compiled.drive().axisWeights().get(DriveAxis.CURIOSITY)).isEqualTo(1.5);
        // Non-overridden fields keep defaults
        assertThat(compiled.drive().maxIntensity()).isEqualTo(DriveConfig.defaults().maxIntensity());
        assertThat(compiled.drive().moodPleasureModulation()).isEqualTo(DriveConfig.defaults().moodPleasureModulation());
    }

    @Test
    void nullSectionUsesDefaults() throws IOException {
        var yaml = "{}";
        var definition = mapper.readValue(yaml, CognitionDefinition.class);
        var compiled = compiler.compile(definition);

        assertThat(compiled.drive()).isEqualTo(DriveConfig.defaults());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=CognitionCompilerTest`
Expected: FAIL — classes don't exist yet

- [ ] **Step 3: Create DriveConfigSpec**

```java
package io.casehub.blocks.agentic.yaml.spec.cognition;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

public record DriveConfigSpec(
        @Nullable Map<DriveAxis, Double> axisWeights,
        @Nullable Double changeThreshold,
        @Nullable Double moodPleasureModulation,
        @Nullable Double moodArousalModulation,
        @Nullable Double personalityModulationStrength,
        @Nullable Double maxIntensity,
        @Nullable Double minIntensity,
        @Nullable Double affiliationDecayThreshold,
        @Nullable Duration affiliationStaleDuration,
        @Nullable Double autonomyConfidenceFloor,
        @Nullable Double narrativeModulationStrength) {}
```

- [ ] **Step 4: Create CognitionDefinition root**

```java
package io.casehub.blocks.agentic.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

public record CognitionDefinition(
        @Nullable DriveConfigSpec drive) {}
```

Start with only `drive` — other fields added as spec records are created in later tasks.

- [ ] **Step 5: Create CompiledCognition and CognitionCompiler**

`CompiledCognition` — start with only `drive`:

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.social.drive.DriveConfig;

public record CompiledCognition(DriveConfig drive) {}
```

`CognitionCompiler`:

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import io.casehub.blocks.agentic.yaml.spec.cognition.DriveConfigSpec;
import org.jspecify.annotations.Nullable;

public class CognitionCompiler {

    public CompiledCognition compile(CognitionDefinition definition) {
        return new CompiledCognition(
                compileDrive(definition.drive()));
    }

    private DriveConfig compileDrive(@Nullable DriveConfigSpec spec) {
        if (spec == null) return DriveConfig.defaults();
        var d = DriveConfig.defaults();
        return new DriveConfig(
                spec.axisWeights() != null ? spec.axisWeights() : d.axisWeights(),
                spec.changeThreshold() != null ? spec.changeThreshold() : d.changeThreshold(),
                spec.moodPleasureModulation() != null ? spec.moodPleasureModulation() : d.moodPleasureModulation(),
                spec.moodArousalModulation() != null ? spec.moodArousalModulation() : d.moodArousalModulation(),
                spec.personalityModulationStrength() != null ? spec.personalityModulationStrength() : d.personalityModulationStrength(),
                spec.maxIntensity() != null ? spec.maxIntensity() : d.maxIntensity(),
                spec.minIntensity() != null ? spec.minIntensity() : d.minIntensity(),
                spec.affiliationDecayThreshold() != null ? spec.affiliationDecayThreshold() : d.affiliationDecayThreshold(),
                spec.affiliationStaleDuration() != null ? spec.affiliationStaleDuration() : d.affiliationStaleDuration(),
                spec.autonomyConfidenceFloor() != null ? spec.autonomyConfidenceFloor() : d.autonomyConfidenceFloor(),
                spec.narrativeModulationStrength() != null ? spec.narrativeModulationStrength() : d.narrativeModulationStrength());
    }
}
```

- [ ] **Step 6: Add neocortex-memory-api dependency to agentic-yaml pom.xml**

Add to `agentic-yaml/pom.xml` dependencies:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-neocortex-memory-api</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=CognitionCompilerTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add agentic-yaml/pom.xml agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/ agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/CognitionCompiler.java agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/CompiledCognition.java agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/compiler/CognitionCompilerTest.java
git commit -m "feat(#247): CognitionDefinition + DriveConfigSpec + CognitionCompiler skeleton

Refs casehubio/blocks#247"
```

### Task 2: MoodConfigSpec + PersonalityEvolutionConfigSpec (external dep + simple)

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/MoodConfigSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/PersonalityEvolutionConfigSpec.java`
- Modify: `CognitionDefinition.java` — add `mood` and `personality` fields
- Modify: `CompiledCognition.java` — add `mood` and `personality` fields
- Modify: `CognitionCompiler.java` — add `compileMood()` and `compilePersonality()` methods
- Modify: `CognitionCompilerTest.java` — add mood and personality tests

**Interfaces:**
- Consumes: `CognitionDefinition`, `CognitionCompiler`, `CompiledCognition` from Task 1
- Produces: `MoodConfigSpec`, `PersonalityEvolutionConfigSpec`, compiler methods for each

- [ ] **Step 1: Write failing tests — mood partial merge, personality full override**

Add to `CognitionCompilerTest.java`:

```java
@Test
void moodPartialOverrideWithExternalNestedRecord() throws IOException {
    var yaml = """
            mood:
              baseline:
                pleasure: 0.5
                arousal: 0.3
                dominance: 0.2
              maxDisplacement: 0.8
            """;
    var definition = mapper.readValue(yaml, CognitionDefinition.class);
    var compiled = compiler.compile(definition);

    assertThat(compiled.mood().baseline().pleasure()).isEqualTo(0.5);
    assertThat(compiled.mood().maxDisplacement()).isEqualTo(0.8);
    assertThat(compiled.mood().decayTimeConstant()).isEqualTo(MoodConfig.defaults().decayTimeConstant());
}

@Test
void personalitySimplestConfig() throws IOException {
    var yaml = """
            personality:
              driftRate: 0.05
            """;
    var definition = mapper.readValue(yaml, CognitionDefinition.class);
    var compiled = compiler.compile(definition);

    assertThat(compiled.personality().driftRate()).isEqualTo(0.05);
}
```

- [ ] **Step 2: Run to verify failures, then implement spec records and compiler methods**

Create `MoodConfigSpec` (5 fields: `@Nullable MoodBaseline baseline`, `@Nullable Duration decayTimeConstant`, `@Nullable Double maxDisplacement`, `@Nullable Double moodInfluence`, `@Nullable Duration staleStateTimeout`).

Create `PersonalityEvolutionConfigSpec` (3 fields — read PersonalityEvolutionConfig to get exact field names and types).

Add `compileMood()` and `compilePersonality()` to `CognitionCompiler` — same null-coalesce pattern as `compileDrive()`.

Update `CognitionDefinition` and `CompiledCognition` to include `mood` and `personality` fields.

- [ ] **Step 3: Run tests to verify all pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=CognitionCompilerTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/ agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/
git commit -m "feat(#247): MoodConfigSpec + PersonalityEvolutionConfigSpec

Refs casehubio/blocks#247"
```

## Batch 2: Remaining 10 config spec records

### Task 3: All remaining spec records + compiler methods

**Files:**
- Create 10 spec records in `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/`:
  - `UserModelConfigSpec.java` (11 fields + nested `RelationshipStageConfigSpec`)
  - `RelationshipStageConfigSpec.java` (nested type with `@Nullable List<StageTier>`)
  - `StrategyLearningConfigSpec.java` (10 fields)
  - `MentalModelConfigSpec.java` (13 fields — largest)
  - `NarrativeConfigSpec.java` (7 fields + nested `NarrativeSynthesisGateSpec`)
  - `NarrativeSynthesisGateSpec.java` (3 fields)
  - `GoalProposalConfigSpec.java` (6 fields)
  - `GoalEscalationConfigSpec.java` (7 fields)
  - `NormDetectionConfigSpec.java` (6 fields)
  - `CollectiveGoalConfigSpec.java` (3 fields)
  - `RetentionConfigSpec.java` (5 fields)
- Modify: `CognitionDefinition.java` — add all 10 remaining fields
- Modify: `CompiledCognition.java` — add all 10 remaining fields
- Modify: `CognitionCompiler.java` — add 10 compile methods
- Modify: `CognitionCompilerTest.java` — add tests for nested types, RetentionConfig DEFAULT

**Interfaces:**
- Consumes: Pattern established in Tasks 1-2
- Produces: Complete set of 13 spec records, full `CognitionCompiler`

For each config, the spec record follows the same pattern as `DriveConfigSpec`:
1. Read the runtime config record to get field names and types
2. Create spec record with all fields `@Nullable`, primitives boxed
3. Add `compileX()` method to `CognitionCompiler` — null-coalesce each field with `defaults()`
4. Add field to `CognitionDefinition` and `CompiledCognition`

**Special cases:**
- `NarrativeConfigSpec` has nested `NarrativeSynthesisGateSpec` — the compile method must handle nested partial merge
- `UserModelConfigSpec` has nested `RelationshipStageConfigSpec` with `List<StageTier>` — if the nested spec is non-null, use its value; otherwise use defaults
- `RetentionConfigSpec` — runtime type uses `RetentionConfig.DEFAULT` not `defaults()`. The compile method uses `RetentionConfig.DEFAULT` as the fallback

- [ ] **Step 1: Write tests for special cases first**

```java
@Test
void narrativeWithNestedSynthesisGatePartialMerge() throws IOException {
    var yaml = """
            narrative:
              maxEpisodes: 100
              synthesisGate:
                minNewReflections: 3
            """;
    var definition = mapper.readValue(yaml, CognitionDefinition.class);
    var compiled = compiler.compile(definition);

    assertThat(compiled.narrative().maxEpisodes()).isEqualTo(100);
    assertThat(compiled.narrative().synthesisGate().minNewReflections()).isEqualTo(3);
    assertThat(compiled.narrative().synthesisGate().noveltyThreshold())
            .isEqualTo(NarrativeSynthesisGate.defaults().noveltyThreshold());
}

@Test
void retentionUsesDefaultStaticField() throws IOException {
    var yaml = """
            retention:
              recencyWeight: 0.5
            """;
    var definition = mapper.readValue(yaml, CognitionDefinition.class);
    var compiled = compiler.compile(definition);

    assertThat(compiled.retention().recencyWeight()).isEqualTo(0.5);
}

@Test
void fullYamlAllSections() throws IOException {
    try (var is = getClass().getResourceAsStream("/cognition/full.yaml")) {
        var definition = mapper.readValue(is, CognitionDefinition.class);
        var compiled = compiler.compile(definition);
        // All 13 configs non-null
        assertThat(compiled.drive()).isNotNull();
        assertThat(compiled.mood()).isNotNull();
        assertThat(compiled.personality()).isNotNull();
        assertThat(compiled.userModel()).isNotNull();
        assertThat(compiled.strategyLearning()).isNotNull();
        assertThat(compiled.mentalModel()).isNotNull();
        assertThat(compiled.narrative()).isNotNull();
        assertThat(compiled.goalProposal()).isNotNull();
        assertThat(compiled.goalEscalation()).isNotNull();
        assertThat(compiled.normDetection()).isNotNull();
        assertThat(compiled.collectiveGoal()).isNotNull();
        assertThat(compiled.retention()).isNotNull();
    }
}
```

- [ ] **Step 2: Create all 10 spec records following the pattern**

Each spec record: read the runtime config, create @Nullable mirror. For nested types (NarrativeSynthesisGate, RelationshipStageConfig), create a separate spec record.

- [ ] **Step 3: Update CognitionDefinition, CompiledCognition, CognitionCompiler**

Add all remaining fields and compile methods.

- [ ] **Step 4: Create test fixture `agentic-yaml/src/test/resources/cognition/full.yaml`**

A YAML file with at least one field overridden per section.

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/ agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/ agentic-yaml/src/test/
git commit -m "feat(#247): complete 13 cognition config spec records + compiler

Refs casehubio/blocks#247"
```

## Batch 3: Deployment + integration + coverage

### Task 4: Deployment processor + recorder

**Files:**
- Modify: `agentic-yaml-deployment/src/main/java/io/casehub/blocks/agentic/yaml/deployment/AgenticYamlProcessor.java` — add `discoverCognitionConfig()` build step
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/runtime/AgenticRecorder.java` — add `createCognitionCompiler()` method
- Modify: `agentic-yaml-deployment/pom.xml` — add neocortex-memory-api if not already present

**Interfaces:**
- Consumes: `CognitionDefinition`, `CognitionCompiler`, `CompiledCognition` from Tasks 1-3
- Produces: CDI beans for each of the 13 config types

- [ ] **Step 1: Add build step to AgenticYamlProcessor**

Add a new `@BuildStep` method that discovers `META-INF/cognition.yaml` on the classpath, deserializes to `CognitionDefinition`, and produces a build item:

```java
@BuildStep
CognitionConfigBuildItem discoverCognitionConfig() throws IOException {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    URL resource = cl.getResource("META-INF/cognition.yaml");
    if (resource == null) return new CognitionConfigBuildItem(null);
    try (InputStream in = resource.openStream()) {
        var definition = YAML.readValue(in, CognitionDefinition.class);
        return new CognitionConfigBuildItem(definition);
    }
}
```

Create `CognitionConfigBuildItem` as a simple `SimpleBuildItem` wrapper.

- [ ] **Step 2: Add recorder method**

```java
public CognitionCompiler createCognitionCompiler() {
    return new CognitionCompiler();
}
```

- [ ] **Step 3: Add CDI bean registration build step**

A build step that takes `CognitionConfigBuildItem`, compiles via recorder, and registers each config as a `SyntheticBeanBuildItem` with `@DefaultBean` scope.

- [ ] **Step 4: Run full build**

Run: `mvn --batch-mode install -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml-deployment/ agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/runtime/AgenticRecorder.java
git commit -m "feat(#247): deployment processor for cognition.yaml discovery

Refs casehubio/blocks#247"
```

### Task 5: Coverage doc update

**Files:**
- Modify: `docs/yaml-coverage.md` — update §14 Social Cognition Configs

- [ ] **Step 1: Update §14 — mark all 13 configs as Done**

- [ ] **Step 2: Update summary table count**

- [ ] **Step 3: Commit**

```bash
git add docs/yaml-coverage.md
git commit -m "docs(#247): update social cognition config coverage — 13/13 done

Refs casehubio/blocks#247"
```

## References

- [2026-09-10-social-cognition-configs-design.md] — design spec this plan implements
- [DriveConfig.java] — representative config with enum map, Duration, validation
- [MoodConfig.java] — config with external nested record (MoodBaseline)
- [NarrativeConfig.java] — config with nested NarrativeSynthesisGate
- [UserModelConfig.java] — deepest nesting (RelationshipStageConfig → StageTier)
- [RetentionConfig.java] — uses DEFAULT not defaults()
- [AgenticYamlProcessor.java] — deployment module pattern
- [AgenticRecorder.java] — recorder pattern
- [PatternSpec.java] — spec record precedent
- [GitHub #247] — focal issue
