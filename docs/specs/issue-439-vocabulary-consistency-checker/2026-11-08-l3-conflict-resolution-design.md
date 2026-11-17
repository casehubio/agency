# L3 LLM-Mediated Conflict Resolution — Design Spec

**Issue:** casehubio/agency#438
**Date:** 2026-11-08
**Status:** Draft

## Summary

When L2 belief revision detects a contradiction that mechanical revision cannot
resolve cleanly, an LLM mediator examines the conflicting SwarmConclusions and
produces a resolution. The mediator is budget-gated and falls back to heuristic
resolution on budget denial or LLM error.

This implements the L3 tier from the
[multi-swarm coordination design](../../docs/specs/issue-364-swarm-coordination/2026-11-07-multi-swarm-coordination-design.md)
(§L3 — LLM-Mediated Resolution).

## Architecture

### ConflictMediator SPI

A `@FunctionalInterface` in the `multiswarm` package. Two implementations:
`LlmConflictMediator` (primary) and `HeuristicConflictMediator` (fallback).
A `BudgetGatedConflictMediator` decorator composes them.

```java
@FunctionalInterface
public interface ConflictMediator {
    MediationResult mediate(ConflictContext context);
}
```

**Synchronous by design:** `StructuredAgentInvoker` methods (`invokeRaw`,
`invokeText`, `invoke`) are synchronous — they block internally on the Mutiny
stream and return results directly. The SPI follows this convention. Callers
that need async execution use `CompletableFuture.supplyAsync()` externally.

The SPI is intentionally simple — a future channel-based mediator (backed by
`NegotiationProtocol`) implements the same interface, replacing the one-shot
LLM call with multi-turn negotiation. `MultiSwarmTopology` doesn't change.

### ConflictContext

Carries everything the mediator needs to reason about the conflict.

```java
public record ConflictContext(
    CrossSwarmNotification notification,
    BeliefSet<SwarmConclusion> currentBeliefs,
    List<Belief<SwarmConclusion>> contractedBeliefs,
    Map<UUID, SwarmProgress> latestProgress,
    Map<UUID, Set<String>> resourceClaims,
    Map<UUID, SwarmDescriptor> swarmDescriptors
) {}
```

| Field | Populated by | Used for |
|-------|-------------|----------|
| `notification` | Signal handler | Conflict type, source/target swarms, trigger signal |
| `currentBeliefs` | AtomicReference snapshot | Post-revision state of BeliefSet |
| `contractedBeliefs` | Delta from revise() | What beliefs were removed during L2 revision |
| `latestProgress` | ConcurrentHashMap snapshot | Consensus scores for DEPENDENCY resolution |
| `resourceClaims` | ConcurrentHashMap snapshot | Overlapping claims for RESOURCE_CONTENTION |
| `swarmDescriptors` | Registry lookup | Capability sets, swarm metadata |

### MediationResult

Sealed hierarchy — three possible outcomes:

```java
public sealed interface MediationResult {
    record Resolved(
        String resolution,
        @Nullable Belief<SwarmConclusion> updatedBelief,
        List<CrossSwarmNotification> notifications
    ) implements MediationResult {}

    record FallbackApplied(
        String fallbackAction,
        String reason,
        List<CrossSwarmNotification> notifications
    ) implements MediationResult {}

    record Escalated(String reason) implements MediationResult {}
}
```

- **Resolved:** LLM produced a definitive resolution. `updatedBelief` replaces
  the conflicting beliefs in BeliefSet. `notifications` are forwarded to swarms.
- **FallbackApplied:** Heuristic handled it. `fallbackAction` describes what
  happened (e.g. "first_claimer_wins", "disputed_tag").
- **Escalated:** Neither LLM nor heuristic could resolve. Escalate to human
  oversight via `OversightGateService`.

### Conflict type handling

Four conflict types map to `NotificationType`:

| NotificationType | Spec conflict type | LLM prompt focus | Heuristic fallback |
|-----------------|-------------------|-------------------|-------------------|
| `CONFLICT` | Belief contradiction | Reason about which conclusion is better supported, propose resolution | Both conclusions retained with DISPUTED metadata. Escalate to oversight. |
| `RESOURCE_CONTENTION` | Resource contention | Evaluate which swarm benefits more from the contested agents | First-claimer wins. Contending swarm notified with alternative capabilities. |
| `DEPENDENCY` | Joint decision | Evaluate cross-swarm dependency and recommend coordination | Defer to swarm with higher `SwarmProgress.consensusScore()`. Lower-scoring swarm receives as external constraint. |
| `HANDOFF` | Handoff failure | Evaluate capability match and recommend best target | Retry with exponential backoff. After 3 failures, mark as blocked and notify both swarms. |

**Terminology note:** `NotificationType.DEPENDENCY` serves as the "joint
decision" case from the spec. The name reflects that these notifications
originate from dependency/escalation detection, but the L3 mediator treats
them as joint coordination decisions.

## Implementations

### LlmConflictMediator

Uses `StructuredAgentInvoker.invokeRaw()` with type-specific system prompts.

```java
public class LlmConflictMediator implements ConflictMediator {
    private final AgentProvider agentProvider;

    @Override
    public MediationResult mediate(ConflictContext context) {
        var config = buildPrompt(context);
        var result = StructuredAgentInvoker.invokeRaw(agentProvider, config);
        return switch (result) {
            case InvocationResult.Success<JsonNode> s -> parseResolution(s.value(), context);
            case InvocationResult.ParseError<JsonNode> p -> throw new MediationException(p.parseError());
            case InvocationResult.AgentError<JsonNode> e -> throw new MediationException(e.reason());
        };
    }
}
```

**Prompt structure per type:**

- **CONFLICT:** "Two swarms reached contradicting conclusions about the same
  domain. Swarm A concluded [X] with confidence [C1]. Swarm B concluded [Y]
  with confidence [C2]. Beliefs removed by AGM revision: [list]. Which
  conclusion should prevail, and why? Respond with JSON: {resolution, updatedConclusion, confidence, justification}."

- **RESOURCE_CONTENTION:** "Two swarms are competing for the same agents: [list].
  Swarm A capabilities: [caps]. Swarm B capabilities: [caps]. Swarm A progress:
  [consensus, stability]. Swarm B progress: [consensus, stability]. Which swarm
  should retain the agents? {winner, reassignments, justification}."

- **DEPENDENCY:** "Cross-swarm dependency detected. Source: [swarm, progress].
  Target: [swarm, progress]. Capabilities overlap: [overlap]. Should these
  swarms coordinate, and how? {coordinationAction, constraints, justification}."

- **HANDOFF:** "A swarm is handing off work requiring capabilities: [caps].
  Best candidate overlap score: [score]. Target capabilities: [caps].
  Should the handoff proceed? {proceed, modifications, justification}."

**Error handling:** Any LLM invocation error (parse failure, timeout, refusal)
throws `MediationException` (unchecked) — the `BudgetGatedConflictMediator`
catches this and falls back to heuristic.

### HeuristicConflictMediator

Implements the spec's fallback table. No LLM calls. Deterministic.

```java
public class HeuristicConflictMediator implements ConflictMediator {
    private final ConcurrentHashMap<String, AtomicInteger> handoffAttempts = new ConcurrentHashMap<>();

    @Override
    public MediationResult mediate(ConflictContext context) {
        return switch (context.notification().type()) {
            case CONFLICT            -> resolveConflictHeuristic(context);
            case RESOURCE_CONTENTION -> resolveResourceHeuristic(context);
            case DEPENDENCY          -> resolveDependencyHeuristic(context);
            case HANDOFF             -> resolveHandoffHeuristic(context);
        };
    }
}
```

| Method | Logic |
|--------|-------|
| `resolveConflictHeuristic` | Both conclusions retained. Add metadata `disputed=true` to both beliefs. Create notification to both swarms with DISPUTED status. Return `FallbackApplied("disputed_tag")`. |
| `resolveResourceHeuristic` | Compare `resourceClaims` timestamps or arrival order — first claimer wins. Notify losing swarm with remaining capabilities. Return `FallbackApplied("first_claimer_wins")`. |
| `resolveDependencyHeuristic` | Compare `SwarmProgress.consensusScore()` — defer to higher score. Lower-scoring swarm receives decision as external constraint via notification. Return `FallbackApplied("consensus_delegation")`. |
| `resolveHandoffHeuristic` | Track attempt count per handoff (keyed by source+domain). Retry with exponential backoff on low-confidence matches. After 3 failures, return `Escalated("handoff_blocked")` with notification to both swarms. Uses `ConcurrentHashMap<String, AtomicInteger>` for stateful tracking. |

### BudgetGatedConflictMediator

Decorator composing LLM + heuristic.

```java
public class BudgetGatedConflictMediator implements ConflictMediator {
    private final ConflictMediator primary;      // LlmConflictMediator
    private final ConflictMediator fallback;     // HeuristicConflictMediator
    private volatile @Nullable AgentBudgetView budgetView;

    void bindPool(AutonomyBudgetPool pool) {
        this.budgetView = pool.createView("l3-mediator", BudgetPriority.RESERVED);
    }

    @Override
    public MediationResult mediate(ConflictContext context) {
        var view = this.budgetView;
        if (view == null) return fallback.mediate(context);

        var grant = view.tryAcquire();
        return switch (grant) {
            case BudgetGrant.Granted g -> {
                try {
                    yield primary.mediate(context);
                } catch (Exception e) {
                    yield fallback.mediate(context);
                }
            }
            case BudgetGrant.Denied d  -> fallback.mediate(context);
        };
    }
}
```

Uses `AgentBudgetView` (created via `pool.createView()`) for per-agent
call/token tracking. The view is bound via `bindPool()` when `configure()`
creates the `AutonomyBudgetPool`. Before binding, all mediation falls back
to heuristic (safe default).

**Budget priority:** `RESERVED` — consistent with the meta-coordinator's
priority level (spec §MultiSwarmTopology.configure).

## Integration with MultiSwarmTopology

### New fields

```java
private final @Nullable ConflictMediator conflictMediator;
private final ConcurrentLinkedQueue<ConflictContext> pendingConflicts = new ConcurrentLinkedQueue<>();
```

### Signal handler changes

**processL2Finding** — after detecting a contradiction, build a `ConflictContext`
capturing the pre-revision and post-revision belief delta, and enqueue it:

```java
private void processL2Finding(MultiSwarmSignal.Finding finding, UUID targetSwarmId) {
    // ... existing belief revision code ...

    if (contradicted) {
        var notification = new CrossSwarmNotification(...);
        pendingNotifications.add(notification);

        if (conflictMediator != null) {
            var context = new ConflictContext(
                notification, beliefSet.get(), contractedBeliefs,
                Map.copyOf(latestProgress), Map.copyOf(resourceClaims),
                lookupDescriptors(finding.sourceSwarmId(), targetSwarmId));
            pendingConflicts.add(context);
        }
    }
}
```

**processResourceClaim** — detect overlapping claims and create
RESOURCE_CONTENTION conflicts:

```java
private void processResourceClaim(MultiSwarmSignal.ResourceClaim claim) {
    var previous = resourceClaims.put(claim.sourceSwarmId(), Set.copyOf(claim.agentIds()));

    if (conflictMediator != null) {
        for (var entry : resourceClaims.entrySet()) {
            if (entry.getKey().equals(claim.sourceSwarmId())) continue;
            if (!Collections.disjoint(entry.getValue(), claim.agentIds())) {
                var notification = new CrossSwarmNotification(
                    claim.sourceSwarmId(), entry.getKey(),
                    NotificationType.RESOURCE_CONTENTION, claim, 0.8);
                pendingNotifications.add(notification);
                pendingConflicts.add(buildContext(notification));
            }
        }
    }
}
```

### Conflict resolution method

```java
public List<MediationResult> resolveConflicts() {
    if (conflictMediator == null) return List.of();

    var results = new ArrayList<MediationResult>();
    ConflictContext ctx;
    while ((ctx = pendingConflicts.poll()) != null) {
        var result = conflictMediator.mediate(ctx);
        applyResult(result);  // update BeliefSet, forward notifications
        results.add(result);
    }
    return List.copyOf(results);
}
```

Sequential processing matters — an earlier conflict resolution may change the
BeliefSet state that affects later conflict assessment.

### Builder changes

```java
public MultiSwarmTopologyBuilder conflictMediator(ConflictMediator mediator) {
    this.conflictMediator = mediator;
    return this;
}
```

In `build()`: when `agentProvider` is set but `conflictMediator` is not,
auto-create the budget-gated stack:

```java
ConflictMediator mediator = this.conflictMediator;
if (mediator == null && agentProvider != null) {
    // AutonomyBudgetPool is created in configure() — mediator gets it via setter
    mediator = new BudgetGatedConflictMediator(
        new LlmConflictMediator(agentProvider),
        new HeuristicConflictMediator(),
        "l3-mediator");
}
```

**Budget pool binding:** The `AutonomyBudgetPool` is created in `configure()`
at runtime — not available at `build()` time. The `BudgetGatedConflictMediator`
uses a deferred binding: it stores the agent ID at construction, and the pool
reference is set via `bindPool(AutonomyBudgetPool)` when `configure()` runs.
When unbound, it falls back to the heuristic mediator (no budget check = no LLM).

### Capturing the belief revision delta

The current `processL2Finding` calls `beliefSet.updateAndGet(current -> current.revise(...))`.
To capture what was contracted, compute the delta:

```java
var before = beliefSet.get();
var after = before.revise(newBelief, consistencyChecker);
var contractedBeliefs = before.all().stream()
    .filter(b -> !after.contains(b.key()))
    .toList();
beliefSet.set(after);
```

This replaces the `updateAndGet` with a compareAndSet loop — necessary because
`CoordinationBus` publishes on the caller's thread and concurrent signals from
different threads could race on the beliefSet reference:

```java
BeliefSet<SwarmConclusion> before, after;
List<Belief<SwarmConclusion>> contracted;
do {
    before = beliefSet.get();
    after = before.revise(newBelief, consistencyChecker);
    contracted = before.all().stream()
        .filter(b -> !after.contains(b.key()))
        .toList();
} while (!beliefSet.compareAndSet(before, after));
```

## Degradation

| Failure | Behaviour |
|---------|-----------|
| LLM parse error | `LlmConflictMediator` throws `MediationException` → `BudgetGatedConflictMediator` catches → heuristic |
| LLM timeout | Same as parse error |
| Budget denied | `BudgetGrant.Denied` → heuristic directly |
| Heuristic error | Log + `MediationResult.Escalated` |
| No AgentProvider | `conflictMediator` is null → no L3, conflicts stay as notifications |

## Test Strategy

1. **HeuristicConflictMediatorTest** — each conflict type returns correct
   FallbackApplied result. Edge cases: empty beliefs, zero-confidence.

2. **LlmConflictMediatorTest** — mock AgentProvider, verify prompt structure
   per conflict type. Parse error → failed stage. Valid JSON → Resolved.

3. **BudgetGatedConflictMediatorTest** — budget granted → LLM path. Budget
   denied → heuristic path. LLM error → heuristic fallback.

4. **MultiSwarmBehavioralTest (L3 nested class)** — end-to-end:
   - Conflict detected → mediator called → BeliefSet updated
   - Budget exhausted → heuristic fallback
   - No AgentProvider → conflicts stay as notifications (existing behaviour)
   - Resource contention detection → RESOURCE_CONTENTION notification
   - Sequential conflict resolution maintains BeliefSet consistency

## New Types Summary

| Type | Package | What it is |
|------|---------|------------|
| `ConflictMediator` | `multiswarm` | `@FunctionalInterface` SPI |
| `ConflictContext` | `multiswarm` | Record: notification + state snapshots |
| `MediationResult` | `multiswarm` | Sealed: Resolved, FallbackApplied, Escalated |
| `LlmConflictMediator` | `multiswarm` | LLM-backed via StructuredAgentInvoker |
| `HeuristicConflictMediator` | `multiswarm` | Deterministic fallback table |
| `BudgetGatedConflictMediator` | `multiswarm` | Decorator: LLM → heuristic |
| `MediationException` | `multiswarm` | Unchecked exception for LLM invocation failures |

## Changes to Existing Types

| Type | Change |
|------|--------|
| `MultiSwarmTopology` | New `conflictMediator` field, `pendingConflicts` queue, `resolveConflicts()` method, `bindBudgetPool()`. Enhanced `processL2Finding()` and `processResourceClaim()`. |
| `MultiSwarmTopologyBuilder` | New `conflictMediator()` method. Auto-compose from `agentProvider` in `build()`. |

## References

- [2026-11-07-multi-swarm-coordination-design.md §L3](../../docs/specs/issue-364-swarm-coordination/2026-11-07-multi-swarm-coordination-design.md) — original L3 design
- `MultiSwarmTopology.java:57` — existing `agentProvider` slot
- `MultiSwarmTopology.java:228-244` — existing `processL2Finding()` (L2 conflict detection)
- `BeliefSet.java:56-78` — AGM `revise()` with entrenchment ordering
- `DomainConsistencyChecker.java` — structural negation detection
- `VocabularyConsistencyChecker.java` — vocabulary-aware contradiction detection
- `StructuredAgentInvoker` — shared LLM invocation utility
- `AutonomyBudgetPool` / `BudgetGrant` — budget governance
- `NegotiationProtocol` — future channel-based mediator would use this
