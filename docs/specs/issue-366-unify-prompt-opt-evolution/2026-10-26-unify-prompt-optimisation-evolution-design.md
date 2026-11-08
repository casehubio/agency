# Design: Unify Prompt Optimisation Batch with Engine Evolution Loop

**Issue:** casehubio/agency#366
**Date:** 2026-10-26
**Status:** Draft

## Context

Agency's `PromptOptimisationBatch` runs an isolated offline improvement cycle for LLM prompts — score variants, promote winners, demote losers, create candidates via `FewShotOptimiser` and `InstructionOptimiser`. Engine#1115 landed a generalised continuous evolution pipeline (`EvolutionTicker`) that orchestrates the same observe→identify→plan→implement→review→deploy loop across all improvement types, with circuit breakers, regression detection, conflict avoidance, and budget enforcement.

The prompt optimisation framework should register as a domain participant in the evolution loop rather than running its own isolated cycle.

### Reference Implementations

The devtown app provides the closest reference pattern:
- `DevtownProposalSource` (`@ApplicationScoped`, injects domain-specific capability areas, proposes when health drops below threshold)
- `DevtownRegressionEvaluator` (`@ApplicationScoped`, computes simple health score delta)

Engine's own `runtime-core` provides the `"code-evolution"` domain implementations:
- `SignalConsensusProposalSource`, `HealthScoreDeltaRegressionEvaluator`, `CodeEvolutionCategoryProvider`, `FilePathConflictStrategy`

All follow the same pattern: `domainId()` scoping, main behavioural method, and a corresponding registry keyed by domain ID.

## Architecture

### New Sub-package

`io.casehub.agency.optimisation.prompt.evolution` — four SPI implementations and one support type, all in `agency-core`.

### Domain Identity

Domain ID: `"prompt-optimisation"`. All four SPI implementations return this from `domainId()`.

### Component Design

#### 1. PromptOptimisationProposalSource

Implements `ImprovementProposalSource`.

```
sourceId() → "prompt-optimisation"
domainId() → "prompt-optimisation"
```

**`propose(caseId, tenancyId, config)`:**
1. Iterates registered `PromptSignature` instances (via constructor-injected `List<PromptSignature>`)
2. For each signature, queries the `PromptVariantStore` for the active control variant
3. Collects recent `VariantOutcome` data via a `PromptOutcomeProvider` SPI (new functional interface — consumer supplies the data source)
4. If outcome count ≥ `OptimiserConfig.minOutcomeCount()` AND current quality score is below a tuning threshold (quality floor + margin), proposes an `ImprovementRequest`:
   - `improvementType` = `"prompt-tuning"`
   - `category` = `"prompt-tuning"`
   - `target` = signature ID
   - `estimatedSize` = 1 (prompt tuning is a lightweight operation)
   - `metadata` = `{"signatureId": "<id>", "currentQuality": "<score>"}`
   - `domainId` = `"prompt-optimisation"`

**Constructor dependencies:**
- `List<PromptSignature>` — registered signatures to scan
- `PromptVariantStore` — query active variants
- `PromptQualityMetric` — score current quality
- `PromptOutcomeProvider` — retrieve recent outcomes per signature
- `OptimiserConfig` — minimum outcome thresholds

#### 2. PromptOutcomeProvider

New `@FunctionalInterface` SPI:

```java
@FunctionalInterface
public interface PromptOutcomeProvider {
    List<VariantOutcome> outcomes(String signatureId);
}
```

Consumers supply the data source — the prompt optimisation framework doesn't own outcome storage. This keeps the same boundary as `OptimisationDataset` but provides per-signature retrieval.

#### 3. PromptRegressionEvaluator

Implements `RegressionEvaluator`.

```
evaluatorId() → "prompt-quality"
domainId()    → "prompt-optimisation"
```

**`evaluate(caseId, baseline, current, category)`:**
1. Extracts per-signature quality scores from `HealthScoreSnapshot.componentScores()` — keys are signature IDs prefixed with `"prompt:"` (e.g. `"prompt:swarm-team-formation"`)
2. For each signature present in both baseline and current, computes the delta
3. If any signature's quality dropped by more than the regression threshold (0.1 — matching devtown's pattern), returns `RegressionVerdict.Detected` with confidence = `Math.abs(maxDelta)`
4. Otherwise returns `RegressionVerdict.NoRegression`

**Constructor dependencies:**
- Regression threshold (double, default 0.1)

#### 4. PromptOptimisationCategoryProvider

Implements `ImprovementCategoryProvider`.

```
domainId() → "prompt-optimisation"
```

**`categories()`:** Single `CategoryDescriptor`:
- Name: `"prompt-tuning"`
- Description: "LLM prompt variant optimisation via few-shot curation and instruction refinement"

**`stages()`:** Four `StageDescriptor` entries in order:
1. `evaluate` (ordinal 0) — assess quality, decide whether to optimise
2. `optimise` (ordinal 1) — run `FewShotOptimiser` and `InstructionOptimiser`
3. `promote` (ordinal 2) — promotion decision (consecutive wins check)
4. `outcome-recording` (ordinal 3) — record results for next cycle

No approval gates — prompt tuning is fully automated with safety rails from `VariantSelector` circuit breaker and `SafetyConfig` quality floor.

#### 5. PromptConflictStrategy

Implements `ConflictStrategy`.

```
domainId() → "prompt-optimisation"
```

**`check(request, activeImprovements, trivialThreshold)`:**
1. Extracts `signatureId` from `request.metadata().get("signatureId")`
2. Scans `activeImprovements` for any entry whose metadata contains the same `signatureId`
3. If found → `ConflictResult.Conflicting(blockingId, "Prompt tuning already active for signature: " + signatureId)`
4. If not found → `ConflictResult.Clear()`

This replaces the `runningSignatures` ConcurrentHashMap guard in `PromptOptimisationBatch` at the evolution pipeline level.

### Changes to Existing Code

#### PromptOptimisationBatch

**No structural changes.** The batch retains its full logic and remains callable directly via `run()`. The evolution pipeline calls `run()` — the proposal source constructs the `OptimisationDataset` from `PromptOutcomeProvider` data and delegates to the batch.

The `runningSignatures` guard in `run()` stays — it protects against concurrent direct calls. For evolution-driven runs, the `ConflictStrategy` provides the guard at the pipeline level. Both can coexist without conflict (the inner guard is a no-op when the outer guard already prevents concurrent runs for the same signature).

#### VariantSelector

**No changes.** The fast-path circuit breaker stays as-is. It operates at a different timescale (per-request) than the evolution pipeline's circuit breaker (per-tick cycle).

#### SafetyConfig

**No changes.** `maxExperimentCycles` remains as a local safety cap. `ImprovementCategoryTracker`'s suppression (3 consecutive failures) provides the evolution-level cap. Both can coexist — the tighter limit wins.

### What Doesn't Change

Per the issue spec:
- `PromptSignature`, `PromptVariant`, `FewShotExample`, `VariantOutcome` — core model
- `FewShotOptimiser`, `InstructionOptimiser` — optimiser implementations
- `SwarmPromptSignatures`, `SwarmOptimisedFewShotSection`, `SwarmOutcomeMetric` — swarm-specific types
- `VariantAwareSystemPromptCustomiser` — runtime injection

### Dependency Impact

Agency already has `casehub-engine-api` as a compile dependency. The improvement SPIs (`ImprovementProposalSource`, `RegressionEvaluator`, `ImprovementCategoryProvider`, `ConflictStrategy`) are all in `io.casehub.api.spi.improvement` — no new dependencies needed.

Types used from engine-api:
- `ImprovementRequest` (`io.casehub.api.model.improvement`)
- `HealthScoreSnapshot` (`io.casehub.api.model.stigmergy`)
- `ImprovementConfig` (`io.casehub.api.model.improvement`)
- `RegressionVerdict` (`io.casehub.api.spi.improvement`)
- `CategoryDescriptor`, `StageDescriptor` (`io.casehub.api.spi.improvement`)

### Registration

Agency does not include a Jandex index. Consumers that want the evolution integration must:

1. Add agency to their Jandex index dependency configuration
2. Or explicitly register the SPI implementations with the engine's registries:
   - `ImprovementProposalSourceRegistry.register(new PromptOptimisationProposalSource(...))`
   - `RegressionEvaluatorRegistry.register(new PromptRegressionEvaluator(...))`
   - `ConflictStrategyRegistry.register(new PromptConflictStrategy())`
   - `ImprovementCategoryRegistry.register(new PromptOptimisationCategoryProvider())`

## Testing

All tests are plain JUnit 5 + Mockito (no Quarkus runtime, matching agency's test pattern).

| Test class | What it verifies |
|-----------|-----------------|
| `PromptOptimisationProposalSourceTest` | Proposes when quality below threshold, skips when data insufficient, skips when quality healthy, includes correct metadata |
| `PromptRegressionEvaluatorTest` | Detects regression when component score drops, returns NoRegression for stable/improving scores, handles missing signatures in snapshots |
| `PromptOptimisationCategoryProviderTest` | Returns correct domain ID, single category, four ordered stages, no approval gates |
| `PromptConflictStrategyTest` | Detects conflict on matching signature ID, returns Clear for different signatures, handles missing metadata |
| `PromptOutcomeProviderTest` | (functional interface — tested implicitly through proposal source tests) |

## File Inventory

New files (all in `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/`):

| File | Type |
|------|------|
| `PromptOptimisationProposalSource.java` | `ImprovementProposalSource` impl |
| `PromptRegressionEvaluator.java` | `RegressionEvaluator` impl |
| `PromptOptimisationCategoryProvider.java` | `ImprovementCategoryProvider` impl |
| `PromptConflictStrategy.java` | `ConflictStrategy` impl |
| `PromptOutcomeProvider.java` | `@FunctionalInterface` SPI |

New test files (all in `agency/src/test/java/io/casehub/agency/optimisation/prompt/evolution/`):

| File | Tests |
|------|-------|
| `PromptOptimisationProposalSourceTest.java` | ~6 tests |
| `PromptRegressionEvaluatorTest.java` | ~4 tests |
| `PromptOptimisationCategoryProviderTest.java` | ~3 tests |
| `PromptConflictStrategyTest.java` | ~4 tests |

## References

- `DevtownProposalSource.java` — domain-specific `ImprovementProposalSource` reference pattern
- `DevtownRegressionEvaluator.java` — domain-specific `RegressionEvaluator` reference pattern
- `SignalConsensusProposalSource.java` — engine-internal proposal source
- `HealthScoreDeltaRegressionEvaluator.java` — engine-internal regression evaluator
- `CodeEvolutionCategoryProvider.java` — engine-internal category provider (5 categories, 11 stages)
- `FilePathConflictStrategy.java` — engine-internal conflict strategy
- `PromptOptimisationBatch.java` — existing agency prompt optimisation orchestrator
- `VariantSelector.java` — existing agency A/B traffic routing with circuit breaker
- engine#1148 generalise-evolution-conductor design spec
- casehubio/agency#362 — swarm prompt signatures and outcome metrics (dependency, landed)
