# Decisions — #366 Unify Prompt Optimisation with Evolution Loop

## D1: Integration architecture — implement engine SPIs as a domain participant

**Choice:** Implement the four engine evolution SPIs (`ImprovementProposalSource`, `RegressionEvaluator`, `ImprovementCategoryProvider`, `ConflictStrategy`) for a `"prompt-optimisation"` domain in a new `io.casehub.agency.optimisation.prompt.evolution` sub-package.
**Alternatives:**
- Refactor `PromptOptimisationBatch` internals into engine's pipeline stages — over-coupling, breaks agency's module boundary
- Keep `PromptOptimisationBatch` standalone and add a thin event bridge — misses category tracking, regression detection, and conflict avoidance
**Rationale:** Follows the established devtown pattern (`DevtownProposalSource`, `DevtownRegressionEvaluator`). Each SPI impl is a focused adapter delegating to existing agency types. `PromptOptimisationBatch` retains its core logic; the evolution SPIs wrap it.
**Trade-offs:** Agency gains a runtime dependency on the evolution tick cycle — prompt tuning only runs when the evolution loop is active. This is the intended design (evolution loop is the single orchestrator for all improvement types).
**Sources:** `DevtownProposalSource.java`, `DevtownRegressionEvaluator.java`, `SignalConsensusProposalSource.java`, engine#1148 generalise-evolution-conductor design spec
**Exploration:** quick
**Status:** captured

## D2: Health score bridging — map prompt quality to HealthScoreSnapshot componentScores

**Choice:** `PromptRegressionEvaluator` compares the `componentScores` map entries keyed by signature ID between baseline and current snapshots. The agency layer populates these scores via `PromptQualityMetric.score()` for each registered signature.
**Alternatives:**
- Ignore `HealthScoreSnapshot` entirely and use agency-internal quality tracking only — loses integration with engine's regression detection and circuit breaker
- Create a separate health tracking mechanism in agency — duplicates engine infrastructure
**Rationale:** `HealthScoreSnapshot.componentScores()` is a `Map<String, Double>` — naturally maps to per-signature quality scores. The engine's `RegressionDetector` already does delta comparison; the evaluator just provides domain-specific confidence.
**Trade-offs:** Requires a component that keeps prompt quality scores up to date in the health tracking system. This is the PromptQualityMetric's role — it already scores variant outcomes.
**Sources:** `HealthScoreSnapshot.java`, `HealthScoreDeltaRegressionEvaluator.java`, `DevtownRegressionEvaluator.java`
**Exploration:** quick
**Status:** captured

## D3: Category and stage design — single category, four stages

**Choice:** One improvement category `"prompt-tuning"` with four stages: `evaluate` (check quality/outcome data), `optimise` (run optimisers), `promote` (promotion decision), `outcome-recording` (record results). No approval gates — prompt tuning is fully automated with safety rails.
**Alternatives:**
- Multiple categories per optimiser type (few-shot, instruction) — unnecessary granularity, both run together in `PromptOptimisationBatch`
- Add approval gates before promotion — contradicts the existing automated promotion protocol (consecutive wins)
**Rationale:** The existing `PromptOptimisationBatch` treats all optimisers as a single pipeline. The four stages map to its internal phases. `ImprovementCategoryTracker`'s suppression (3 consecutive failures) replaces `SafetyConfig.maxExperimentCycles`.
**Trade-offs:** Single category means all prompt tuning is suppressed/paused together, not per-optimiser. This matches current behaviour.
**Sources:** `CodeEvolutionCategoryProvider.java`, `ImprovementCategoryTracker.java`, `PromptOptimisationBatch.java`
**Exploration:** quick
**Status:** captured

## D4: Conflict strategy — signature-level mutual exclusion

**Choice:** `PromptConflictStrategy` checks if any active improvement targets the same `PromptSignature.id()`. Conflict = same signature being tuned concurrently.
**Alternatives:**
- No conflict strategy — rely only on `PromptOptimisationBatch.runningSignatures` guard — misses cross-pipeline conflicts
- Path-based conflict (like `FilePathConflictStrategy`) — prompt tuning doesn't have file paths
**Rationale:** The existing `runningSignatures` ConcurrentHashMap in `PromptOptimisationBatch` implements exactly this — per-signature mutual exclusion. The `ConflictStrategy` SPI replaces it at the evolution pipeline level. The metadata field `signatureId` in `ImprovementRequest.metadata()` carries the signature ID for conflict matching.
**Trade-offs:** `runningSignatures` guard in `PromptOptimisationBatch` becomes redundant for evolution-driven runs (but stays for any direct `run()` calls).
**Sources:** `FilePathConflictStrategy.java`, `ConflictStrategyRegistry.java`, `PromptOptimisationBatch.java`
**Exploration:** quick
**Status:** captured

## D5: VariantSelector circuit breaker coexistence

**Choice:** Keep `VariantSelector`'s per-capability circuit breaker as the fast-path safety rail. The evolution pipeline's circuit breaker handles the slower feedback loop (health score regression). Both operate independently.
**Alternatives:**
- Remove `VariantSelector` circuit breaker entirely — loses the fast-path protection against streaming bad variants to production
- Unify both into one circuit breaker — different granularity (per-capability vs per-domain) and different triggers (consecutive failures vs health score delta)
**Rationale:** Per the issue spec: "VariantSelector circuit breaker remains as a fast-path safety rail; evolution pipeline circuit breaker handles the slower feedback loop." Different time horizons, different purposes. Fast-path catches immediate failures; evolution catches gradual regression.
**Trade-offs:** Two circuit breakers that could theoretically conflict — but they protect different things at different speeds. No conflict in practice.
**Sources:** `VariantSelector.java`, `ImprovementCircuitBreaker` (engine), issue #366 spec
**Exploration:** quick
**Status:** captured
