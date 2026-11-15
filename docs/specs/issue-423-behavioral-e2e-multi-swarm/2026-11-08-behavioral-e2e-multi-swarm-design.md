# Behavioral E2E Tests for Multi-Swarm Coordination

**Issue:** casehubio/agency#423
**Date:** 2026-11-08

## Goal

Prove that a consumer can use the multi-swarm coordination system correctly through the public API alone — builder, register, publish, drain. No internal state inspection. These tests serve as both verification and reference documentation for consumers.

## Scope

6 scenarios testing the Java API consumer experience. YAML round-trip (issue scenario 6) is out of scope — it tests the YAML layer, which gets its own tests when that surface is reached.

## Test Class

`MultiSwarmBehavioralTest` in `agency-core/src/test/java/io/casehub/agency/orchestration/multiswarm/`.

Single class with `@Nested` groups per scenario. Shared `@BeforeEach` creates the standard consumer setup. Reuses `TestMultiSwarmRegistry`. Adds `RecordingConvergenceEvaluator` as a private static inner class.

Async assertions via Awaitility (`await().atMost(2, SECONDS).untilAsserted(...)`) — exercises real virtual-thread dispatch, asserts deterministically.

## Scenarios

### 1. Consumer Setup Flow

Build topology via builder, register swarms, verify ready state.

**Tests:**
- `builder_creates_topology_with_weighted_scoring` — build with `weightedScoring()`, verify `describeTopology()` returns `MULTI_SWARM`
- `register_swarms_enables_signal_processing` — register two swarms with overlapping capabilities, publish a finding from swarm A, verify it flows through (observable via `currentBeliefs()` becoming non-empty)
- `topology_is_autocloseable` — build, register, close, verify no exceptions

**Public API exercised:** `MultiSwarmTopology.builder()`, `.weightedScoring()`, `.consistencyChecker()`, `.registry()`, `.build()`, `registry.register()`, `topology.describeTopology()`, `topology.signalBus().publish()`, `topology.currentBeliefs()`, `topology.close()`

### 2. Signal Lifecycle

Finding from swarm A → L1 relevance scoring → L2 belief revision in swarm B's context → CrossSwarmNotification on contradiction.

**Tests:**
- `finding_from_overlapping_swarm_reaches_beliefs` — publish Finding from analysis swarm, await beliefs containing conclusion with matching domain
- `contradictory_findings_produce_conflict_notification` — publish two Findings with same entity:domain but different conclusions from different swarms, await `drainNotifications()` containing a CONFLICT notification with correct source and target swarm IDs
- `irrelevant_finding_does_not_reach_beliefs` — register two swarms with NO capability overlap, publish Finding from one, verify beliefs remain empty after a reasonable wait
- `self_signals_not_scored` — publish Finding from swarm A, verify beliefs do not contain an entry sourced from A scored against A (only other swarms score it)

**Public API exercised:** `signalBus().publish(Finding)`, `currentBeliefs()`, `drainNotifications()`

### 3. Convergence Lifecycle

Register convergence evaluator, publish progress from all swarms, verify convergence detection.

**Tests:**
- `convergence_evaluator_receives_progress_from_all_swarms` — build topology with `RecordingConvergenceEvaluator`, register 3 swarms, publish Progress from each, await evaluator receiving a `Map<UUID, SwarmProgress>` with all 3 entries
- `convergence_evaluator_not_called_with_single_swarm` — publish Progress from only 1 swarm, verify evaluator is never called (requires short wait + assertion)
- `progress_signals_trigger_escalation_checks` — register 2 swarms with high dependency (overlapping capabilities), publish Progress, verify escalation gate fires and produces DEPENDENCY notification via `drainNotifications()`

**Public API exercised:** `builder.convergenceEvaluator()`, `signalBus().publish(Progress)`, `drainNotifications()`

**Test helper:** `RecordingConvergenceEvaluator` — captures each `evaluate()` call's argument map. Private static inner class.

### 4. Dynamic Swarm Membership

Register, deregister, re-register swarms during active coordination.

**Tests:**
- `deregistration_cleans_beliefs_for_that_swarm` — register 2 swarms, publish Finding from A to populate beliefs, deregister A, verify beliefs no longer contain entries from A
- `deregistered_swarm_signals_ignored` — deregister swarm A, publish Finding with A's UUID as source, verify beliefs remain unchanged
- `re_registration_restores_scoring` — register A, deregister A, re-register A (new descriptor), publish Finding from B, verify A's scorer processes it (observable via beliefs or notifications)
- `deregistration_during_active_processing` — publish Finding from A, immediately deregister B (the target), verify no exceptions thrown and topology remains functional for subsequent signals

**Public API exercised:** `registry.register()`, `registry.deregister()`, `currentBeliefs()`, `signalBus().publish()`

### 5. Strategy Switching

Verify consumer can choose between weighted and BJT scoring via builder.

**Tests:**
- `weighted_scoring_produces_beliefs` — build with `weightedScoring()`, register swarms, publish Finding, verify beliefs populated
- `bjt_scoring_produces_beliefs` — build with `bjtScoring()`, register swarms, publish Finding, verify beliefs populated
- `both_strategies_agree_on_clear_signals` — build two separate topologies (one weighted, one BJT) with same registry setup, publish identical high-confidence Finding with full capability overlap, verify both produce non-empty beliefs (clear signals should be relevant under any strategy)
- `default_scoring_works_without_explicit_strategy` — build with neither `weightedScoring()` nor `bjtScoring()`, verify topology still processes signals (defaults to weighted 0.5/0.6)

**Public API exercised:** `builder.weightedScoring()`, `builder.bjtScoring()`, `currentBeliefs()`

### 6. Error Paths

Unknown swarm signals, empty swarm sets, deregistration edge cases.

**Tests:**
- `unknown_swarm_event_silently_ignored` — call `onSwarmEvent()` with an event for an unregistered swarm UUID, verify no exception and signal bus replay buffer is empty
- `finding_from_unknown_source_does_not_crash` — publish Finding with a UUID not registered in the registry, verify no exception
- `builder_rejects_null_registry` — call `build()` without `registry()`, verify NullPointerException
- `builder_rejects_null_consistency_checker` — call `build()` without `consistencyChecker()`, verify NullPointerException
- `drain_notifications_is_idempotent` — drain once (gets results), drain again immediately (gets empty list)
- `close_then_publish_does_not_throw` — close topology, publish signal, verify no unhandled exception (bus should handle gracefully)

**Public API exercised:** `onSwarmEvent()`, `signalBus().publish()`, `builder.build()`, `drainNotifications()`, `close()`

## Test Infrastructure

| Component | Source |
|-----------|--------|
| `TestMultiSwarmRegistry` | Existing — `agency-core/src/test/.../multiswarm/TestMultiSwarmRegistry.java` |
| `DomainConsistencyChecker` | Existing — `agency-core/src/main/.../multiswarm/DomainConsistencyChecker.java` |
| `RecordingConvergenceEvaluator` | New — private static inner class in `MultiSwarmBehavioralTest`. Captures `evaluate()` calls. |

## Observable API Surface

Tests assert only through these public methods:

| Method | What it reveals |
|--------|----------------|
| `describeTopology(UUID)` | Topology is correctly configured |
| `currentBeliefs()` | Findings have been scored, reached L2, and revised into the belief set |
| `drainNotifications()` | Contradictions detected (CONFLICT) or escalation triggered (DEPENDENCY) |
| `signalBus().replayBuffer()` | Signals were published to the bus |
| `RecordingConvergenceEvaluator.calls()` | Convergence evaluator was invoked with correct data |

No access to `relevanceScorers`, `escalationGates`, `latestProgress`, or any other internal field.

## References

- `agency-core/src/main/java/.../multiswarm/MultiSwarmTopology.java` — production class under test
- `agency-core/src/main/java/.../multiswarm/MultiSwarmTopologyBuilder.java` — builder API
- `agency-core/src/main/java/.../multiswarm/CoordinationBus.java` — async signal dispatch
- `agency-core/src/test/java/.../multiswarm/TieredCoordinationIntegrationTest.java` — existing integration tests (component-level, not consumer-behavioral)
- `agency-core/src/test/java/.../multiswarm/TestMultiSwarmRegistry.java` — reused test helper
- casehubio/agency#423 — issue specification
