# Design: Coordinator Prompt Tuning for Swarm Coordination (#362)

Wire the three swarm coordinator prompt sites into the existing prompt
optimisation framework for systematic A/B testing and improvement.

## Background

The v1 swarm prompt infrastructure (agency#285) built the scaffolding
types: `SwarmPromptSignatures` (three `PromptSignature` constants),
`SwarmOutcomeMetric` (convergence outcome scoring), `SwarmOutcomeMapper`
(raw outcomes → `VariantOutcome`), and `SwarmOptimisedFewShotSection`
(few-shot injection for team formation). These types define the
integration surface but the plumbing is not connected — no outcome
recording pipeline, incomplete `SystemPromptCustomiser` wiring, and
no evolution loop integration.

## Scope

Three prompt sites, one optimisation pipeline:

| Prompt site | Current state | Change |
|-------------|--------------|--------|
| Team formation (`SwarmRoutingStrategy`) | No `SystemPromptCustomiser` hook | Add customiser + variant slot |
| Collective reasoning (`CollectiveReasoningAggregation`) | Has customiser, builder wires it | Wire variant slot from builder |
| Provisioning (`SwarmProvisioningAdvisorImpl`) | Has customiser, NOT wired by builder | Wire customiser + variant slot from builder |

Plus: outcome recording pipeline, outcome provider for batch cycle,
generalised few-shot injection, evolution loop integration.

## Changes

### 1. Add SystemPromptCustomiser to SwarmRoutingStrategy

Current constructor:
```java
SwarmRoutingStrategy(AgentProvider, SwarmExperienceSection,
    int preFilterThreshold, @Nullable SwarmInvocationTracker)
```

New constructor:
```java
SwarmRoutingStrategy(AgentProvider, SwarmExperienceSection,
    int preFilterThreshold, @Nullable SwarmInvocationTracker,
    @Nullable SystemPromptCustomiser promptCustomiser)
```

Add `setVariantSlot(@Nullable String variantSlot)` matching the pattern
on `CollectiveReasoningAggregation` and `SwarmProvisioningAdvisorImpl`.

In `route()`, after `buildPrompt()` returns and before
`AgentSessionConfig.of()`, apply the customiser:
```java
if (promptCustomiser != null) {
    prompt = promptCustomiser.customise(prompt,
        TEAM_FORMATION.id(), variantSlot);
}
```

### 2. Wire SwarmCoordinatorBuilder

Builder currently passes `promptCustomiser` only to
`CollectiveReasoningAggregation`. Changes:

- Change `LlmSwarmCoordinator.convergenceTermination` field from
  `SwarmConvergenceTermination` to `TerminationCondition<SwarmContext>`.
  Constructor parameter and accessor change accordingly (`convergenceTermination()`
  becomes `terminationCondition()`). This enables the `SwarmOutcomeRecorder`
  decorator (§3) to be transparently substituted.
- Pass `promptCustomiser` to `SwarmRoutingStrategy` constructor
- Accept optional `VariantSelector` via `variantSelector(VariantSelector)`
- In `build()`: if `VariantSelector` is present, call
  `selectSlot(UUID.randomUUID(), "swarm-coordination")` once and
  propagate the slot to all three sites via their `setVariantSlot()`
  methods, AND to all `SwarmOptimisedFewShotSection` instances via
  their `setVariantSlot()` methods. The random UUID gives independent
  variant assignment per coordinator build; the shared capability name
  ensures one slot for all three signatures (D5). Coordinators are
  single-use per build — each `build()` creates fresh `SwarmContext`
  state and a fresh variant assignment.
- Accept optional `SwarmProvisioningAdvisorImpl` via
  `provisioningAdvisor(SwarmProvisioningAdvisorImpl)` so the builder can
  wire its customiser and variant slot. If not provided, provisioning
  optimisation is skipped (backward compatible).
- Pass `additionalSections` to `SwarmRoutingStrategy` so
  `SwarmOptimisedFewShotSection` can contribute to team formation
  prompts. This requires adding `List<SwarmPromptSection>` to
  `SwarmRoutingStrategy`'s constructor and composing their output into
  `buildPrompt()`.

### 3. SwarmOutcomeRecorder (new class)

Package: `io.casehub.agency.orchestration.swarm.prompt`

```java
public class SwarmOutcomeRecorder implements TerminationCondition<SwarmContext> {
    SwarmOutcomeRecorder(TerminationCondition<SwarmContext> delegate,
                         PromptVariantStore store,
                         String variantSlot) { ... }

    TerminationDecision evaluate(SwarmContext context) { ... }

    List<VariantOutcome> outcomes(String signatureId) { ... }
}
```

**Design:** Decorates `SwarmConvergenceTermination` rather than observing
CDI events (swarm termination has no CDI event path — the execution loop
lives in the driver layer and `SwarmConvergenceTermination.evaluate()` is
a pure function over `SwarmContext`).

When `delegate.evaluate()` returns a terminal decision (Complete or
Escalate/Failed), the recorder:
1. Extracts convergence outcome from the decision reason
2. Reads `SwarmProgress` (consensusScore, stabilityScore) from
   `SwarmContext.swarmProgress()`
3. Maps via `SwarmOutcomeMapper` (one mapper per signature, all three
   recorded per execution since the variant slot is shared)
4. Records `VariantOutcome` to `PromptVariantStore`

The `variantSlot` is set at construction by the builder (D5 — consistent
slot per execution). No execution ID correlation needed — the recorder
is scoped to one coordinator instance and records once on terminal
decision.

`outcomes(signatureId)` provides read access for the batch cycle.
Thread-safe via `CopyOnWriteArrayList`.

**Composition precondition:** The recorder wraps the raw
`SwarmConvergenceTermination`. If additional termination conditions
(timeout, cycle limit) are needed, compose them with the recorder
(e.g. `recorder.or(timeout)`), not underneath it — the recorder
must be in the composition tree to intercept terminal decisions.

### 4. SwarmPromptOutcomeProvider (new class)

Package: `io.casehub.agency.orchestration.swarm.prompt`

```java
@ApplicationScoped
public class SwarmPromptOutcomeProvider implements PromptOutcomeProvider {
    void register(SwarmOutcomeRecorder recorder) { ... }

    List<VariantOutcome> outcomes(String signatureId) { ... }
}
```

CDI-managed aggregation point. Each `SwarmOutcomeRecorder` (one per
coordinator instance) registers itself on construction. `outcomes()`
aggregates across all registered recorders for the given signature.

The batch cycle calls this to score control vs experiment variants.
`ConcurrentHashMap<String, List<VariantOutcome>>` keyed by signatureId.

### 5. Generalise SwarmOptimisedFewShotSection

`SwarmOptimisedFewShotSection` already takes a `signatureId` in its
3-arg constructor. The 2-arg constructor defaults to `TEAM_FORMATION`.

Changes:
- Add `setVariantSlot(@Nullable String variantSlot)`. When set,
  `contribute()` uses the provided slot directly for
  `store.getActive()` instead of calling `selector.selectSlot()`.
  This eliminates the split-brain between independent slot selection
  and the builder-set variant slot (D5). The `selector`-based path
  remains as fallback for standalone usage without builder integration.
- Fix `SwarmPromptSection.contribute()` return type to `@Nullable
  String` to match existing implementation behavior. The coordinator's
  `assembleCoordinatorPrompt()` already null-checks the return.
- Builder creates three instances (one per signature) and wires them
  to the appropriate prompt sites.
- Team formation: instance added to `additionalSections` passed to
  `SwarmRoutingStrategy`.
- Collective reasoning and provisioning: instances contribute via
  `SwarmPromptSection.contribute()` called before the customiser in
  each site's prompt construction.

### 6. Evolution Loop Integration (new classes)

Package: `io.casehub.agency.orchestration.swarm.prompt`

All implementations use domain ID `"swarm-prompt-optimisation"`.

| Class | Role | What it does |
|-------|------|-------------|
| `SwarmPromptProposalSource` | `ImprovementProposalSource` | Scans three `SwarmPromptSignatures`, evaluates quality via `SwarmOutcomeMetric`, proposes tuning when quality drops |
| `SwarmPromptRegressionEvaluator` | `RegressionEvaluator` | Monitors `swarm-prompt:*` component scores for regression |
| `SwarmPromptCategoryProvider` | `ImprovementCategoryProvider` | Single category `"swarm-prompt-tuning"`, four stages: evaluate → optimise → promote → outcome-recording |
| `SwarmPromptConflictStrategy` | `ConflictStrategy` | Signature-level mutual exclusion via metadata key `signatureId` |

These follow the exact pattern of the existing `PromptOptimisation*`
evolution types, scoped to swarm signatures.

## Testing

| What | How |
|------|-----|
| SwarmRoutingStrategy customiser hook | Unit test: verify prompt includes instruction delta when customiser provided |
| SwarmCoordinatorBuilder wiring | Unit test: build coordinator, verify all three sites receive customiser and variant slot |
| SwarmOutcomeRecorder | Unit test: register execution, fire terminal event, verify VariantOutcome recorded |
| SwarmPromptOutcomeProvider | Unit test: record outcomes, call outcomes(signatureId), verify correct list returned |
| SwarmOptimisedFewShotSection per-site | Unit test: verify contribute() returns formatted examples for each signature |
| Evolution loop types | Unit test per type following existing PromptOptimisation* test patterns |
| Integration: builder → execution → recording | Integration test: build coordinator, run mock execution to convergence, verify outcome recorded with correct variant |

## Non-Goals

- Actual prompt content changes (this wires the framework; content
  tuning happens via the batch cycle at runtime)
- Persistent outcome storage (in-memory via `PromptVariantStore`
  `@DefaultBean` is sufficient; consumers override with persistence)
- Cross-site independent A/B testing (D5: consistent slot per execution)

## References

- `SwarmPromptSignatures.java` — three PromptSignature constants
- `SwarmOptimisedFewShotSection.java` — existing few-shot section
- `SwarmOutcomeMetric.java` — existing quality metric
- `SwarmOutcomeMapper.java` — existing outcome mapper
- `SwarmCoordinatorBuilder.java` — builder to modify
- `SwarmRoutingStrategy.java` — team formation prompt site (no customiser hook)
- `CollectiveReasoningAggregation.java` — reasoning prompt site (has customiser)
- `SwarmProvisioningAdvisorImpl.java` — provisioning prompt site (has customiser, not wired by builder)
- `PromptOptimisationBatch.java` — batch cycle orchestrator
- `PromptOptimisationProposalSource.java` — evolution loop pattern to follow
- `OptimisedFewShotSection.java` — routing few-shot pattern to follow
- agency#285 — v1 swarm prompt scaffolding
- agency#362 — this issue
