# Engine Adapter — YAML Surface for Engine-Hosted Patterns

**Issue:** casehubio/agency#255
**Date:** 2026-09-11
**Scope:** 5 engine adapter types in coverage matrix §21, plus supporting CallerConfigSpec/QuorumConfigSpec

## Problem

The engine-adapter module has 5 runtime types with no YAML surface:
PatternJudgmentConfig, EngineHostedBackend, CheckpointingListener,
LlmEvaluationVerifier, SchemaValidationVerifier. These are the last
remaining gaps in the coverage matrix for the blocks repo.

## Solution

Add spec records and registries following the established pattern.
Three categories of work:

### 1. Extend existing sealed interfaces

**`ExecutionBackendSpec.EngineHosted`** — new variant of the existing sealed interface.

```java
record EngineHosted() implements ExecutionBackendSpec {}
```

Jackson subtype: `@Type(value = EngineHosted.class, name = "engine-hosted")`.
Registry maps to `new EngineHostedBackend<>()`.

**`ExecutionListenerSpec.Checkpointing`** — new variant.

```java
record Checkpointing() implements ExecutionListenerSpec {}
```

Jackson subtype: `@Type(value = Checkpointing.class, name = "checkpointing")`.
Registry resolution requires engine runtime context (caseId, patternId,
tenancyId, persistFn) — the registry cannot construct this directly.
Instead, the registry returns a marker that the engine's
PatternWorkerFunction resolves at runtime.

**Design decision:** `ExecutionListenerRegistry.resolve()` currently
returns `ExecutionEventListener` directly. Adding Checkpointing breaks
this because it needs engine context. Two options:

- Option A: Registry returns a `ListenerFactory` for Checkpointing
  (a function from engine context to listener). Adds complexity.
- Option B: Registry throws `UnsupportedOperationException` for
  Checkpointing — the engine-adapter resolves it separately.

**Choice: Option B** — matches `EngineHostedBackend` which also throws
UnsupportedOperationException when used outside the engine. The
Checkpointing spec exists so YAML declarations can reference
`type: checkpointing` — the engine's PatternWorkerFunction handles
actual construction.

### 2. New VerifierStrategySpec

```java
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

`VerifierStrategyRegistry` resolves to a verifier strategy ID string
(not a `JudgmentVerifier` instance — those are CDI-managed). The
registry maps spec → string ID that the engine looks up from CDI.

### 3. PatternJudgmentConfigSpec

```java
public record PatternJudgmentConfigSpec(
    String prompt,
    CallerConfigSpec callerConfig,
    @Nullable VerifierStrategySpec verifier,
    List<EvidenceRequirement> evidenceRequirements,
    @Nullable PatternJudgmentConfig.JudgmentMode mode,
    boolean afterStep) {}
```

**Direct reuse:** `EvidenceRequirement`, `EvidenceType`,
`PatternJudgmentConfig.JudgmentMode` — all YAML-safe.

### 4. CallerConfigSpec (full mirror)

```java
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

Adaptation rules:
- `ExpressionEvaluator titleExpression` → `@Nullable String titleExpression` (compiled at runtime via ExpressionEngine)
- `ExpressionEvaluator scopeExpression` → `@Nullable String scopeExpression` (same)
- `Class<?> payloadType` → `@Nullable String payloadType` (resolved via Class.forName at runtime)
- `CandidateSetSpec` → `CandidateSetStrategySpec` (existing spec type; Named variant handled by strategy ID reference)
- `QuorumConfig` → `QuorumConfigSpec` (no validation in spec)

### 5. QuorumConfigSpec

```java
public record QuorumConfigSpec(
    int instances,
    int required,
    @Nullable OnThresholdReached onThresholdReached,
    boolean allowSameAssignee) {}
```

Direct reuse of `OnThresholdReached` enum. No validation in spec —
registry validates when constructing `QuorumConfig`.

### Registries

| Registry | Input | Output |
|----------|-------|--------|
| `ExecutionBackendRegistry` | extend existing switch | `EngineHostedBackend<>()` |
| `ExecutionListenerRegistry` | extend existing switch | `UnsupportedOperationException` (engine resolves) |
| `VerifierStrategyRegistry` (new) | `VerifierStrategySpec` → `String` | verifier ID string |
| `CallerConfigRegistry` (new) | `CallerConfigSpec` → `CallerConfig` | runtime CallerConfig |
| `PatternJudgmentConfigRegistry` (new) | `PatternJudgmentConfigSpec` → `PatternJudgmentConfig` | runtime config |

`CallerConfigRegistry` needs `ExpressionEngine` for expression compilation
and handles `Class.forName` for payloadType. `PatternJudgmentConfigRegistry`
composes `CallerConfigRegistry` and `VerifierStrategyRegistry`.

## Test Plan

| Test | What it verifies |
|------|-----------------|
| `ExecutionBackendSpec` YAML round-trip | `engine-hosted` deserializes |
| `ExecutionListenerSpec` YAML round-trip | `checkpointing` deserializes |
| `VerifierStrategySpec` YAML round-trip | `llm-evaluation` and `schema-validation` deserialize |
| `CallerConfigSpec` YAML round-trip | All 4 variants deserialize with all fields |
| `PatternJudgmentConfigSpec` YAML round-trip | Full config with nested types deserializes |
| `CallerConfigRegistry` resolution | Spec → CallerConfig for all 4 variants |
| `VerifierStrategyRegistry` resolution | Spec → verifier ID string |
| `PatternJudgmentConfigRegistry` resolution | Full config resolves with nested registries |
| Schema generation | All new spec types appear in generated schema |

## Coverage Matrix Update

§21: all 5 gaps → Done.

## References

- `engine-adapter/src/main/java/io/casehub/engine/agentic/judgment/PatternJudgmentConfig.java` — runtime type
- `engine-adapter/src/main/java/io/casehub/engine/agentic/EngineHostedBackend.java` — runtime type
- `engine-adapter/src/main/java/io/casehub/engine/agentic/CheckpointingListener.java` — runtime type
- `engine-adapter/src/main/java/io/casehub/engine/agentic/judgment/LlmEvaluationVerifier.java` — runtime type
- `engine-adapter/src/main/java/io/casehub/engine/agentic/judgment/SchemaValidationVerifier.java` — runtime type
- `engine-api: CallerConfig.java` — sealed interface with Human/Llm/A2A/Any
- `engine-api: EvidenceRequirement.java`, `EvidenceType.java` — direct reuse
- `engine-api: QuorumConfig.java` — adapted (validation removed in spec)
- `agentic-yaml: CandidateSetStrategySpec.java` — reused for candidate groups
- [GitHub #255](https://github.com/casehubio/agency/issues/255)
