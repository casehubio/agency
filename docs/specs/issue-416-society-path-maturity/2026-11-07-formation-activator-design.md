# FormationActivator — Design Spec

**Issue:** casehubio/agency#417
**Parent:** casehubio/agency#416 (Society path maturity)
**Date:** 2026-11-07

## Summary

Bridge the gap between group formation (FORMING state) and activation
(ACTIVE state) by creating a `FormationActivator` that validates member
commitment via `JointIntention` and `IntentionMonitor`, then calls
`GroupLifecycleService.activateGroup()`.

## Scope

1. `FormationActivator` — composition root for intention-based activation
2. `ActivationResult` — sealed outcome type for activation operations

Out of scope:
- Changes to `BottomUpFormationOrchestrator` (it correctly returns FORMING)
- Role adoption after activation (separate concern)
- Async/channel-based commitment gathering (IntentionMonitor is synchronous)

## Package

`io.casehub.agency.deliberation.coalition.formation` in `agency-core`.

Same package as `BottomUpFormationOrchestrator` — both are formation
lifecycle components.

## Components

### FormationActivator

Plain class, not CDI-managed. Consumer constructs with domain-specific
dependencies (same pattern as `VouchService`, `BottomUpFormationOrchestrator`).

**Constructor parameters:**

| Parameter | Type | Purpose |
|-----------|------|---------|
| `groupLifecycle` | `GroupLifecycleService` | Calls `activateGroup()` |
| `monitor` | `@Nullable IntentionMonitor` | Optional pre-activation validation |

**API:**

```java
public class FormationActivator {

    ActivationResult activate(String groupInstanceId,
                              Set<String> memberNames,
                              String planDescription,
                              String tenancyId);
}
```

Synchronous return — all underlying calls (`IntentionMonitor.evaluate()`,
`GroupLifecycleService.activateGroup()`) are synchronous. No Uni wrapper
needed.

### Flow

```
activate(groupInstanceId, memberNames, planDescription, tenancyId)
  │
  ├─ 1. JointIntention.form(groupInstanceId, planDescription,
  │     memberNames, Instant.now())
  │
  ├─ 2. If monitor != null:
  │     ├─ monitor.evaluate(intention)
  │     ├─ signal != null → ActivationResult.monitorRejected(signal)
  │     └─ null → proceed
  │
  ├─ 3. intention.activate()
  │
  ├─ 4. groupLifecycle.activateGroup(groupInstanceId, tenancyId)
  │     └─ exception → ActivationResult.activationFailed(message)
  │
  └─ 5. ActivationResult.activated(groupInstanceId, activatedIntention)
```

**intentionId mapping:** Uses `groupInstanceId` as the `intentionId` for the
`JointIntention`. The group instance ID is already unique and correlates
the intention to the group it governs.

**Monitor rejection semantics:** Any non-null `ReconsiderationSignal` from
the monitor blocks activation — both `shouldDrop=true` and `shouldDrop=false`
signals are treated as rejection. Reconsideration is a signal that conditions
are not stable enough for activation. The caller can inspect the signal and
retry if appropriate.

### ActivationResult

Sealed interface in `deliberation.coalition.formation`:

```java
public sealed interface ActivationResult {

    record Activated(String groupInstanceId,
                     JointIntention intention) implements ActivationResult {}

    record MonitorRejected(
            ReconsiderationSignal signal) implements ActivationResult {}

    record ActivationFailed(String reason) implements ActivationResult {}

    static ActivationResult activated(String groupInstanceId,
                                      JointIntention intention) {
        return new Activated(groupInstanceId, intention);
    }

    static ActivationResult monitorRejected(ReconsiderationSignal signal) {
        return new MonitorRejected(signal);
    }

    static ActivationResult activationFailed(String reason) {
        return new ActivationFailed(reason);
    }
}
```

`Activated` carries the `JointIntention` in ACTIVE status — callers can
use it for downstream lifecycle tracking (e.g., `IntentionMonitor`
for reconsideration, `fulfill()` on task completion).

### Caller Composition

Typical caller flow after `BottomUpFormationOrchestrator.propose()`:

```java
var formationResult = orchestrator.propose(proposal, context)
        .await().atMost(timeout);

if (formationResult instanceof FormationResult.Accepted accepted) {
    var activationResult = activator.activate(
            accepted.groupInstanceId(),
            memberNames,
            "Execute task: " + proposal.taskId(),
            tenancyId);

    return switch (activationResult) {
        case ActivationResult.Activated a -> // proceed with active group
        case ActivationResult.MonitorRejected r -> // handle rejection
        case ActivationResult.ActivationFailed f -> // handle failure
    };
}
```

## Dependencies

No new dependencies. `FormationActivator` uses types already available
in agency-core:
- `GroupLifecycleService` (eidos-org-api, already provided-scope)
- `JointIntention`, `IntentionMonitor`, `ReconsiderationSignal` (agency deliberation.intention)

## Test Strategy

Plain JUnit 5 with Mockito. Mock `GroupLifecycleService`.

| Test case | Scenario |
|-----------|----------|
| Happy path (no monitor) | Null monitor → unconditional activation |
| Happy path (with monitor) | Monitor returns null → activation proceeds |
| Monitor rejects (shouldDrop) | Monitor returns drop signal → `MonitorRejected` |
| Monitor rejects (reconsider) | Monitor returns reconsider signal → `MonitorRejected` |
| activateGroup() failure | Service throws → `ActivationFailed` with message |
| Intention fields | Verify intentionId = groupInstanceId, parties = memberNames, status = ACTIVE |
| Null parameter validation | Null groupInstanceId/memberNames/tenancyId → NPE |

## References

- `BottomUpFormationOrchestrator.java` — predecessor, returns FORMING state
- `FormationResult.java` — formation outcome type (not extended)
- `JointIntention.java` — intention lifecycle (form → activate)
- `IntentionMonitor.java` — optional validation SPI
- `ReconsiderationSignal.java` — monitor output type
- `GroupLifecycleService.java` — eidos activateGroup() API
- `GroupLifecycleState.java` — FORMING → ACTIVE transition
- `VouchService.java` — composition root pattern precedent
- Predecessor spec #410 §Out of scope — explicitly deferred activation
- decisions.md — D1, D2, D3
