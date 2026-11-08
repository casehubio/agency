# Process Completion and Handoff Signals in MultiSwarmTopology

**Issue:** casehubio/agency#443
**Branch:** issue-443-process-completion-handoff-signals
**Date:** 2026-11-08

## Problem

`MultiSwarmTopology.processSignal()` has empty match arms for `Completion`
and `Handoff` signals — they are received via `CoordinationBus` but
silently ignored. No state changes, no notifications, no convergence
integration.

## Design

### Completion Processing

**New state:** `ConcurrentHashMap<UUID, Set<String>> completedDomains` —
tracks completed domain strings per swarm. Same pattern as `resourceClaims`
and `latestProgress`.

**`processCompletion(MultiSwarmSignal.Completion completion)`:**

1. Track: add `completion.domain()` to `completedDomains` for the source
   swarm. Use `computeIfAbsent` with `ConcurrentHashMap.newKeySet()`.

2. Evict beliefs (conditional): if `evictBeliefsOnCompletion` is enabled,
   contract all beliefs keyed with `sourceSwarmId + ":" + domain` from
   the belief set. A completed domain's conclusions are final — no longer
   subject to revision. Default: disabled (conservative).

3. All-complete check: after recording, check if every registered swarm
   (every key in `relevanceScorers`) has at least one entry in
   `completedDomains`. This is a convergence heuristic, not a strict
   domain coverage check — consumers needing finer granularity can query
   `completedDomains(UUID)` directly.

**Escalation gate skip:** In `processL1Progress`, when iterating
escalation gates, skip the gate pair where the *source* swarm has any
completed domain. Progress from a completed swarm is informational, not
actionable — it should not trigger escalation against other swarms.

Check: `completedDomains.containsKey(progress.sourceSwarmId())` with a
non-empty set.

**Convergence integration (D1 — dual-path):** The
`CrossSwarmConvergenceEvaluator` interface (engine-api) is unchanged.
Two independent convergence paths:

- Progress-based: existing evaluator detects consensus/deadlock from
  `SwarmProgress` (unchanged)
- Completion-based: topology tracks all-complete internally via
  `isAllComplete()`

Consumers check both: evaluator result for consensus state,
`isAllComplete()` for domain completion.

**Cleanup:** `onDeregistered(UUID)` removes the swarm from
`completedDomains`. `onInterestsUpdated` does NOT clear completions —
a domain completion is a fact, not invalidated by capability changes.

**Builder:** `evictBeliefsOnCompletion(boolean)` — default `false`.

**Accessors:**
- `Set<String> completedDomains(UUID swarmId)` — returns completed
  domains for a swarm (empty set if none)
- `boolean isAllComplete()` — true when every registered swarm has at
  least one completed domain

### Handoff Processing

**`processHandoff(MultiSwarmSignal.Handoff handoff)`:**

1. Find best target: iterate all registered swarms (via
   `relevanceScorers.keySet()`), excluding `sourceSwarmId`. For each,
   look up the descriptor via `registry.get(swarmId)` and compute
   `computeCapabilityOverlap(handoff.requiredCapabilities(),
   descriptor.capabilities())`. Track the swarm with highest overlap.

2. Produce notification: if best overlap > 0, create
   `CrossSwarmNotification(sourceSwarmId, bestTargetId, HANDOFF,
   handoff, overlapScore)` and add to `pendingNotifications`.

3. No match: if no swarm has any capability overlap with
   `requiredCapabilities`, no notification is produced. The signal
   remains in the replay buffer for consumer observation.

`NotificationType.HANDOFF` already exists in the enum — no additions
needed.

Best-match only, not fan-out. Handoff is a transfer to the most capable
swarm. Consumers needing fan-out can re-publish or query the registry
directly.

### Test Plan

Extends `MultiSwarmBehavioralTest` with new `@Nested` classes:

**`CompletionLifecycle`:**
- Completion signal tracks domain in `completedDomains`
- Multiple completions from same swarm accumulate domains
- `isAllComplete()` false with partial completions
- `isAllComplete()` true when all registered swarms complete
- Deregistration removes from `completedDomains`
- Belief eviction when enabled: beliefs for completed domain contracted
- Belief eviction when disabled (default): beliefs retained

**`CompletionEscalation`:**
- Progress from completed swarm does not trigger escalation gate
- Progress from non-completed swarm still triggers escalation normally
- Completion then progress: gate skipped for completed source

**`HandoffRouting`:**
- Handoff routed to swarm with highest capability overlap
- HANDOFF notification produced with overlap score as confidence
- No notification when no swarm matches required capabilities
- Source swarm excluded from routing candidates
- Handoff with partial capability match: best partial match selected

## References

- MultiSwarmTopology.java:145-153 — empty match arms (the bug)
- MultiSwarmTopology.java:195-219 — processL1Progress escalation logic
- MultiSwarmTopology.java:221-226 — computeCapabilityOverlap (reused for handoff)
- MultiSwarmSignal.java — Completion and Handoff record definitions
- NotificationType.java — HANDOFF already defined
- CrossSwarmConvergenceEvaluator (engine-api) — evaluate(Map<UUID, SwarmProgress>)
- MultiSwarmBehavioralTest.java — existing test patterns
- decisions.md D1 — dual-path convergence rationale
