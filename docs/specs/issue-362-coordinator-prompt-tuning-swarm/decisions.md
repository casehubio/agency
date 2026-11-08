# Decisions — #362 Coordinator Prompt Tuning for Swarm Coordination

## D1: Scope — all three prompt sites

**Choice:** Wire all three swarm prompt sites (team formation, collective reasoning, provisioning) into the optimisation framework in this issue.
**Alternatives:**
- Team formation only — highest impact but requires a second pass for the other two
- Team formation + reasoning — skip provisioning (fewer invocations, advisory-only)
**Rationale:** The scaffolding types (`SwarmPromptSignatures`, `SwarmOutcomeMetric`, `SwarmOutcomeMapper`) already exist for all three. The wiring is repetitive, not complex — doing all three avoids context-switching overhead of a second pass.
**Trade-offs:** Slightly larger PR. If any one site needs rework, all three are in the same change.
**Sources:** `SwarmPromptSignatures.java` (three constants), issue #362 body (deferred from #285)
**Exploration:** quick
**Status:** captured

## D2: Overall approach — direct wiring with decorator-based outcome recording

**Choice:** Direct wiring following the proven routing prompt optimisation pattern. SystemPromptCustomiser on all three sites, decorator-based outcome recording on termination condition, evolution loop types.
**Alternatives:**
- Event-driven pipeline (EventStreamBus + SummarisationRunner) — adds unnecessary windowed aggregation complexity for a simple write
- Deferred batch-only (scan historical events) — requires persistence layer that doesn't exist for swarm events
**Rationale:** The routing prompt optimisation already proves this exact pattern works. Approach matches existing code structure and keeps the implementation straightforward. Decorator on `TerminationCondition` captures outcomes at the exact termination point without modifying the event hierarchy.
**Trade-offs:** Decorator pattern requires `LlmSwarmCoordinator` to accept `TerminationCondition<SwarmContext>` interface instead of the concrete `SwarmConvergenceTermination`. This is the right design — the coordinator should not be coupled to a specific termination implementation.
**Sources:** `OptimisedFewShotSection.java`, `VariantAwareSystemPromptCustomiser.java`, `PromptOptimisationBatch.java`
**Exploration:** quick
**Status:** revised — R1-04: fixed misleading "CDI observer" description to match actual decorator design

## D3: Outcome recorder location — swarm/prompt/ package

**Choice:** New `SwarmOutcomeRecorder` as a `TerminationCondition<SwarmContext>` decorator in `orchestration.swarm.prompt`. Decorates `SwarmConvergenceTermination` — intercepts terminal decisions and records outcomes. Not CDI-managed (per-coordinator instance, created by builder). Registered with CDI-managed `SwarmPromptOutcomeProvider` for aggregation.
**Alternatives:**
- CDI observer on SwarmCoordinationEvent — swarm termination has no CDI event path; this approach doesn't work
- Extend SwarmCoordinatorBuilder — tighter coupling but guaranteed lifecycle
- In the evolution sub-package — groups batch-cycle plumbing together but separates from swarm prompt types
**Rationale:** Decorator pattern captures outcomes at the exact termination point without modifying the event hierarchy. Co-located with `SwarmOutcomeMetric`, `SwarmOutcomeMapper`, and `SwarmOptimisedFewShotSection`.
**Trade-offs:** One recorder per coordinator instance — outcomes must be aggregated via `SwarmPromptOutcomeProvider`.
**Sources:** `SwarmOutcomeMetric.java`, `SwarmOutcomeMapper.java`
**Exploration:** quick
**Status:** captured

## D4: Few-shot section — generalise existing class

**Choice:** Generalise `SwarmOptimisedFewShotSection` to be parameterised by `PromptSignature`, creating one class that serves all three prompt sites.
**Alternatives:**
- Separate classes per site (SwarmTeamFormationFewShotSection, etc.) — more explicit but mostly copy-paste
**Rationale:** `SwarmOptimisedFewShotSection` already takes a `PromptSignature` at construction. The rendering logic is identical across sites — only the signature and example format differ. Parameterisation eliminates duplication.
**Trade-offs:** If a prompt site later needs radically different few-shot rendering, we'd need to subclass or split. Unlikely given the current uniform structure.
**Sources:** `SwarmOptimisedFewShotSection.java`
**Exploration:** quick
**Status:** captured

## D5: Variant slot selection — at coordinator build time

**Choice:** `SwarmCoordinatorBuilder` calls `VariantSelector.selectSlot()` once per coordinator build and passes the slot to all three prompt sites.
**Alternatives:**
- Per-execution in LlmSwarmCoordinator — more flexible but inconsistent with how other consumers use VariantSelector
- Per-site independent selection — allows independent A/B testing per site but makes outcome correlation across sites impossible
**Rationale:** Consistent by construction — all three prompt sites in a single swarm execution use the same variant slot. Matches how `variantSlot` is already set on `CollectiveReasoningAggregation`. Outcomes can be attributed to a single variant configuration.
**Trade-offs:** Can't independently A/B test team formation vs reasoning vs provisioning prompts. Acceptable for now — cross-site correlation is more valuable than per-site granularity when tuning a coordinated system.
**Depends on:** D1 (all three sites in scope)
**Sources:** `SwarmCoordinatorBuilder.java`, `VariantSelector.java`
**Exploration:** quick
**Status:** captured

## D6: Outcome metric — single SwarmOutcomeMetric for all signatures

**Choice:** Use `SwarmOutcomeMetric` (convergence-based scoring) for all three prompt signatures. No per-signature metrics.
**Alternatives:**
- Per-signature metrics (team utilisation for formation, synthesis quality for reasoning, accuracy for provisioning) — more precise causal attribution per site
- Composite metric weighting per-site sub-metrics — comprehensive but requires defining sub-metric weights with no data to calibrate them
**Rationale:** The variant slot is shared across all three sites (D5). The optimisation loop tunes the entire configuration, not individual sites. End-to-end convergence outcome is the right signal: "did this prompt variant configuration lead to better swarm outcomes?" Per-site metrics would require isolating each site's contribution, which the shared-slot design deliberately avoids.
**Trade-offs:** Cannot distinguish whether poor convergence was caused by bad team formation, bad reasoning, or bad provisioning. Acceptable for initial tuning — if the signal proves insufficient, per-site metrics can be layered on without changing the recording infrastructure.
**Depends on:** D5 (shared variant slot)
**Sources:** `SwarmOutcomeMetric.java`
**Exploration:** quick
**Status:** captured
