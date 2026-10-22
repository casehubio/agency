# Social Cognition Configs — YAML Surface

**Issue:** casehubio/blocks#247
**Date:** 2026-09-10
**Scope:** 13 config records → YAML-expressible via spec records + compiler + deployment processor

## Problem

The social cognition orchestrators (Drive, Mood, Personality, UserModel, Strategy, MentalModel, Narrative, GoalProposal, GoalEscalation, NormDetection, CollectiveGoal) and the memory hygiene system (Retention) each take a config record via CDI injection. All configs have `defaults()` factories and are pure data. But there's no YAML surface — consumers must construct them in Java.

## Solution

Spec records with `@Nullable` fields in `agentic-yaml`, a `CognitionDefinition` root type, a `CognitionCompiler`, and a deployment processor that discovers `cognition.yaml` on the classpath. Partial YAML — omitted fields use `defaults()`.

## YAML Surface

```yaml
# cognition.yaml — discovered on classpath
drive:
  changeThreshold: 0.1
  axisWeights:
    CURIOSITY: 1.5
    COMPETENCE: 1.0
mood:
  decayTimeConstant: PT2H
  maxDisplacement: 0.8
narrative:
  maxEpisodes: 100
  synthesisGate:
    minNewReflections: 3
# omitted sections use defaults()
```

## Architecture

### Root Definition

```java
// agentic-yaml spec package
public record CognitionDefinition(
    @Nullable DriveConfigSpec drive,
    @Nullable MoodConfigSpec mood,
    @Nullable PersonalityEvolutionConfigSpec personality,
    @Nullable UserModelConfigSpec userModel,
    @Nullable StrategyLearningConfigSpec strategyLearning,
    @Nullable MentalModelConfigSpec mentalModel,
    @Nullable NarrativeConfigSpec narrative,
    @Nullable GoalProposalConfigSpec goalProposal,
    @Nullable GoalEscalationConfigSpec goalEscalation,
    @Nullable NormDetectionConfigSpec normDetection,
    @Nullable CollectiveGoalConfigSpec collectiveGoal,
    @Nullable RetentionConfigSpec retention) {}
```

Jackson deserializes `cognition.yaml` directly into `CognitionDefinition`. Each section is optional.

### Spec Records — 13 types

Each spec record mirrors the runtime config with all fields `@Nullable`. Representative example:

```java
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

Primitives become boxed (`double` → `Double`) so they can be `@Nullable`. Duration stays as-is (already an object type).

### Nested Types

| Config | Nested type | Approach |
|--------|------------|----------|
| MoodConfig | `MoodBaseline` (neocortex-memory-api) | Deserialize directly — it's a record with 3 doubles |
| NarrativeConfig | `NarrativeSynthesisGate` (blocks) | Use `NarrativeSynthesisGateSpec` with @Nullable fields |
| UserModelConfig | `RelationshipStageConfig` → `StageTier` | Use `RelationshipStageConfigSpec` with @Nullable fields; `StageTier` is small enough to deserialize directly |
| StrategyLearningConfig | `MemoryDomain` (neocortex-memory-api) | Deserialize directly — it's a record |

External-dep nested records (`MoodBaseline`, `MemoryDomain`) are deserialized directly by Jackson since they're records with simple fields. No spec wrappers needed for them.

### Compiler

```java
public class CognitionCompiler {
    public CompiledCognition compile(CognitionDefinition definition) {
        return new CompiledCognition(
            compileDrive(definition.drive()),
            compileMood(definition.mood()),
            // ... each section
        );
    }

    private DriveConfig compileDrive(@Nullable DriveConfigSpec spec) {
        if (spec == null) return DriveConfig.defaults();
        var d = DriveConfig.defaults();
        return new DriveConfig(
            spec.axisWeights() != null ? spec.axisWeights() : d.axisWeights(),
            spec.changeThreshold() != null ? spec.changeThreshold() : d.changeThreshold(),
            // ... each field
        );
    }
}
```

`CompiledCognition` is a record holding all 13 compiled configs. The deployment module registers each config as a separate CDI bean.

### Deployment Processor

Follows `SummarisationYamlProcessor` pattern:

1. Scan classpath for `cognition.yaml` files (Quarkus `HotDeploymentWatchedFileBuildItem`)
2. Deserialize to `CognitionDefinition` via Jackson YAML
3. Compile via `CognitionCompiler`
4. Register each config as a `@DefaultBean` CDI bean via `SyntheticBeanBuildItem`
5. Consumer's `@ApplicationScoped` beans override if they provide their own config

### Recorder

```java
public class CognitionRecorder {
    public CognitionCompiler createCompiler() {
        return new CognitionCompiler();
    }
}
```

## Config Inventory — Full Field Map

| # | Config | Fields | Nested | Notes |
|---|--------|--------|--------|-------|
| 1 | DriveConfig | 11 | — | `Map<DriveAxis, Double>` for axis weights |
| 2 | MoodConfig | 5 | MoodBaseline (external) | PAD emotional baseline |
| 3 | PersonalityEvolutionConfig | 3 | — | Simplest config (3 doubles) |
| 4 | UserModelConfig | 11 | RelationshipStageConfig → StageTier | Deepest nesting |
| 5 | StrategyLearningConfig | 10 | MemoryDomain (external) | External enum/record |
| 6 | MentalModelConfig | 13 | — | Largest config |
| 7 | NarrativeConfig | 7 | NarrativeSynthesisGate | Nested blocks config |
| 8 | NarrativeSynthesisGate | 3 | — | Also standalone in issue scope |
| 9 | GoalProposalConfig | 6 | — | Simple |
| 10 | GoalEscalationConfig | 7 | — | No Duration fields |
| 11 | NormDetectionConfig | 6 | — | Simple |
| 12 | CollectiveGoalConfig | 3 | — | Simplest with Duration |
| 13 | RetentionConfig | 5 | — | Uses `DEFAULT` not `defaults()` |

**Total: ~90 fields across 13 configs.**

## Dependencies

`agentic-yaml` already depends on `casehub-agency` (which contains all config records). New provided deps needed:
- `casehub-neocortex-memory-api` — for `MoodBaseline`, `MemoryDomain` (deserialized directly)

## Testing

- `CognitionCompilerTest`: for each config, verify that null spec → defaults, partial spec → merged, full spec → all overridden
- `CognitionDefinitionTest`: YAML deserialization round-trip — partial YAML produces correct `CognitionDefinition`
- End-to-end: `cognition.yaml` fixture → compiled configs match expected values

## References

- `DriveConfig.java` — representative config with enum map, Duration, validation
- `MoodConfig.java` — config with external nested record (MoodBaseline)
- `UserModelConfig.java` — deepest nesting (RelationshipStageConfig → StageTier)
- `RetentionConfig.java` — uses `DEFAULT` static field instead of `defaults()`
- `SummarisationYamlProcessor.java` — deployment module precedent
- `SummarisationRecorder.java` — recorder precedent
- `PatternSpec.java` — spec record pattern precedent
- `PatternCompiler.java` — compiler pattern precedent
