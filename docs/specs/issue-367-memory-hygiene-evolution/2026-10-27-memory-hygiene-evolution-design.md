# Memory Hygiene Evolution Loop Participant

**Issue:** casehubio/agency#367
**Branch:** issue-367-memory-hygiene-evolution
**Date:** 2026-10-27

## Summary

Wire `MemoryHygieneOrchestrator` as the second evolution loop participant (after prompt optimisation, #366). Implements five engine-api SPIs to propose, detect regression, categorise, and conflict-check memory hygiene tuning improvements. Validates that the evolution loop generalises beyond prompt optimisation.

## Architecture

Six classes in `io.casehub.agency.social.memory.evolution`, following the established pattern from `io.casehub.agency.optimisation.prompt.evolution`.

All share domain ID `"memory-hygiene"`.

### Class Summary

| Class | Engine SPI | Constructor params |
|-------|-----------|-------------------|
| `MemoryHygieneOutcomeProvider` | — (`@FunctionalInterface`) | — |
| `MemoryHygieneProposalSource` | `ImprovementProposalSource` | `MemoryHygieneOutcomeProvider`, quality threshold, min sample size |
| `MemoryHygieneRegressionEvaluator` | `RegressionEvaluator` | regression threshold |
| `MemoryHygieneCategoryProvider` | `ImprovementCategoryProvider` | (stateless) |
| `MemoryHygieneConflictStrategy` | `ConflictStrategy` | (stateless) |
| `MemoryHygieneCapabilityArea` | `CapabilityArea` | `MemoryHygieneOutcomeProvider` |

### Registration

Classes register with the engine's evolution loop independently — not through `CognitiveEvolutionAdapter`. Consumers construct them with domain-specific bindings, same as prompt optimisation.

## Component Detail

### MemoryHygieneOutcomeProvider

`@FunctionalInterface` SPI. Consumer-supplied — decouples proposal logic from how tick data is collected and stored.

```java
@FunctionalInterface
public interface MemoryHygieneOutcomeProvider {
    MemoryHygieneOutcome outcomes(String agentId);
}
```

### MemoryHygieneOutcome

Record aggregating quality signals from both `HygieneTick` and `MaintenanceTick`:

```java
public record MemoryHygieneOutcome(
    double retentionQuality,     // mean composite retention score across memories
    double evictionRate,         // evicted / totalScored ratio
    double consolidationRate,    // consolidated / totalScored ratio
    int lowRetentionCount,       // memories below LOW_RETENTION_THRESHOLD
    int integrityViolations,     // from MaintenanceTick
    int reflectionsGenerated,    // from MaintenanceTick
    int sampleSize               // total ticks aggregated
) {}
```

### MemoryHygieneProposalSource

`ImprovementProposalSource` (sourceId: `"memory-hygiene"`, domainId: `"memory-hygiene"`).

Constructor takes `MemoryHygieneOutcomeProvider`, `double qualityThreshold` (default 0.7), `int minSampleSize` (default 20).

`propose()` logic:
1. Extract agentId from `ImprovementConfig` metadata or derive from `tenancyId` parameter. Call outcome provider with agentId.
2. Gate: if `sampleSize < minSampleSize`, return empty — insufficient data
3. Compute composite quality score:
   - retentionQuality weight 0.4
   - (1 - evictionRate) weight 0.3
   - consolidationRate weight 0.2
   - (integrityViolations == 0 ? 1.0 : 0.0) weight 0.1
4. If quality < qualityThreshold + TUNING_MARGIN (0.1), propose improvement
5. Metadata includes `agentId` for conflict detection
6. Improvement type determined by weakest dimension:
   - Low retentionQuality → `retention-threshold`
   - High evictionRate → `confidence-weights`
   - Low consolidationRate → `consolidation-timing`

### MemoryHygieneRegressionEvaluator

`RegressionEvaluator` (evaluatorId: `"memory-hygiene"`, domainId: `"memory-hygiene"`).

Constructor takes `double regressionThreshold` (default 0.1).

`evaluate()` logic:
1. Scan `current.componentScores()` for keys prefixed `"memory-hygiene:"`
2. For each key, compute drop from `baseline.componentScores()`
3. Max drop exceeds threshold → `RegressionVerdict.Detected(maxDrop, reason)`
4. Otherwise → `RegressionVerdict.NoRegression()`

Component score keys published by the ProposalSource:
- `memory-hygiene:retention-quality`
- `memory-hygiene:eviction-rate`
- `memory-hygiene:consolidation-rate`
- `memory-hygiene:integrity`

### MemoryHygieneCategoryProvider

`ImprovementCategoryProvider` (domainId: `"memory-hygiene"`).

Stateless. Returns:
- One category: `CategoryDescriptor("memory-hygiene", "Memory Hygiene Tuning", "...", "memory-hygiene")`
- Three stages (no gate checkpoints):
  1. `evaluate` — assess current memory quality
  2. `tune` — adjust retention/consolidation/confidence parameters
  3. `verify` — confirm quality improved after tuning

### MemoryHygieneConflictStrategy

`ConflictStrategy` (domainId: `"memory-hygiene"`).

Stateless. Extracts `agentId` from `request.metadata()` and each active improvement's metadata. Same `agentId` → `ConflictResult.Conflicting(blockingId, reason)`. Different or missing `agentId` → `ConflictResult.Clear()`.

### MemoryHygieneCapabilityArea

`CapabilityArea` (id: `"memory-hygiene"`, name: `"Memory Hygiene"`, description: `"Agent memory retention, consolidation, and integrity"`).

Constructor takes `MemoryHygieneOutcomeProvider`.

`assess()` logic:
1. Call outcome provider to get current quality metrics
2. Compute `healthScore` from composite quality (same formula as ProposalSource)
3. Map to `LandscapePosition`:
   - healthScore >= 0.8 → `AHEAD`
   - healthScore >= 0.6 → `AT_PARITY`
   - healthScore >= 0.3 → `BEHIND`
   - healthScore < 0.3 → `ABSENT`
4. Impact/cost/ROI are heuristic: impact = 1.0 - healthScore, cost = 0.2 (low — parameter tuning only), ROI = impact / cost

## Package Structure

```
agency-core/src/main/java/io/casehub/agency/social/memory/evolution/
├── MemoryHygieneOutcomeProvider.java    (@FunctionalInterface)
├── MemoryHygieneOutcome.java            (record)
├── MemoryHygieneProposalSource.java     (ImprovementProposalSource)
├── MemoryHygieneRegressionEvaluator.java (RegressionEvaluator)
├── MemoryHygieneCategoryProvider.java   (ImprovementCategoryProvider)
├── MemoryHygieneConflictStrategy.java   (ConflictStrategy)
└── MemoryHygieneCapabilityArea.java     (CapabilityArea)

agency/src/test/java/io/casehub/agency/social/memory/evolution/
├── MemoryHygieneProposalSourceTest.java
├── MemoryHygieneRegressionEvaluatorTest.java
├── MemoryHygieneCategoryProviderTest.java
├── MemoryHygieneConflictStrategyTest.java
└── MemoryHygieneCapabilityAreaTest.java
```

## Testing

Plain JUnit 5 + Mockito. No CDI container.

| Test class | Verifies |
|-----------|----------|
| `MemoryHygieneProposalSourceTest` | Proposals when quality below threshold; no proposals when healthy; min sample size gate; metadata contains agentId; weakest-dimension routing |
| `MemoryHygieneRegressionEvaluatorTest` | Regression detection above threshold; no regression below; ignores non-prefixed keys |
| `MemoryHygieneCategoryProviderTest` | Category ID; stages in correct order; no gate checkpoints |
| `MemoryHygieneConflictStrategyTest` | Same agentId = Conflicting; different agentId = Clear; missing metadata = Clear |
| `MemoryHygieneCapabilityAreaTest` | Health score calculation; landscape position mapping; ROI formula |

## Dependencies

No new compile or test dependencies. Uses existing:
- `casehub-engine-api` — improvement SPIs (already in pom.xml)
- `HygieneTick`, `MaintenanceTick`, `RetentionScore` — same module
- JUnit 5, Mockito, AssertJ — existing test dependencies

## Scope Boundaries

**In scope:**
- Five engine SPI implementations + outcome provider + outcome record
- Unit tests for all classes
- CLAUDE.md key directory update

**Out of scope:**
- Wiring into a specific consumer (that's consumer-side work)
- Actual tuning execution (the evolution loop proposes; execution is engine-side)
- Extended SPIs (DenyPatternProvider, EscalationProvider, etc.)

## References

- `agency-core/.../optimisation/prompt/evolution/` — pattern followed
- `agency-core/.../social/memory/MemoryHygieneOrchestrator.java` — tick data source
- `agency-core/.../social/memory/MemoryHygieneScheduler.java` — maintenance tick data
- `agency-core/.../social/cognition/evolution/CognitiveEvolutionAdapter.java` — evolution entry point
- `engine/api/.../spi/improvement/ImprovementProposalSource.java` — engine SPI
- `engine/api/.../spi/improvement/CapabilityArea.java` — engine SPI (first implementor)
- casehubio/agency#365 — cognitive evolution ticker
- casehubio/agency#366 — prompt optimisation evolution (pattern)
- casehubio/engine#1115 — evolution loop infrastructure
