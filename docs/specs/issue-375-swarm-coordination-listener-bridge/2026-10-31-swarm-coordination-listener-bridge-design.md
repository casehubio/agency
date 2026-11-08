# SwarmCoordinationListener Bridge — Design

**Issue:** casehubio/agency#375
**Parent epic:** casehubio/engine#1274
**Date:** 2026-10-31

## Problem

Engine's `SwarmProvisioner` fires swarm lifecycle events via the
`SwarmCoordinationListener` SPI. Agency's `SwarmEventSource` is ready
to consume them and update `SwarmContext` — the state that informs
prompt construction, routing decisions, and termination logic. But no
production adapter connects them. Without this bridge, `SwarmContext`
is always stale.

## Design

### New class: `SwarmCoordinationListenerBridge`

**Package:** `io.casehub.agency.orchestration.swarm`
**Location:** `agency-core/src/main/java/...`

```java
@ApplicationScoped
public class SwarmCoordinationListenerBridge implements SwarmCoordinationListener {

    private volatile SwarmEventSource eventSource;

    public void bind(SwarmEventSource eventSource) { ... }

    public boolean isBound() { ... }

    // 6 callback methods from SwarmCoordinationListener
}
```

**CDI lifecycle:** The bridge is `@ApplicationScoped` (not `@DefaultBean`
— the engine SPI javadoc explicitly says multi-instance, no
`@DefaultBean`). The engine's `RuntimeBeans` discovers it via
`Instance<SwarmCoordinationListener>` and injects it into
`SwarmProvisioner`.

**Binding:** The bridge starts unbound (`eventSource == null`). The
consumer calls `bind(SwarmEventSource)` after building the
`LlmSwarmCoordinator` via `SwarmCoordinatorBuilder`:

```java
var coordinator = new SwarmCoordinatorBuilder()
    .agentProvider(provider)
    .build();
bridge.bind(coordinator.eventSource());
```

Before binding, all callbacks are no-ops. This is correct — no
coordinator exists to receive events yet.

### Callback mapping

Each `SwarmCoordinationListener` method maps to one
`SwarmCoordinationEvent` sealed variant:

| Engine callback | Agency event | Notes |
|----------------|-------------|-------|
| `onSwarmInitialised(caseId, progress)` | `SwarmInitialised(List.of(), progress)` | Experiences passed as empty — loaded separately by coordinator |
| `onRoleChanged(caseId, roles, progress)` | `RoleChanged(roles, progress)` | Direct mapping |
| `onTeamChanged(caseId, teams, progress)` | `TeamChanged(teams, progress)` | Direct mapping |
| `onProgressUpdated(caseId, progress)` | `ProgressUpdated(progress)` | Direct mapping |
| `onMemberCompleted(caseId, agentId)` | `MemberCompleted(agentId, null)` | Result passed as null — engine SPI doesn't provide it |
| `onProvisioningChanged(caseId, current, max)` | `ProvisioningChanged(current, max)` | Direct mapping |

The `caseId` parameter from the engine SPI is not carried in agency's
event records. This is consistent — `SwarmContext` is per-coordinator,
and the coordinator is per-deployment (D1), so `caseId` is implicit.

### Error handling

Each callback wraps the delegation in try-catch. A failing bridge must
never propagate exceptions back to the engine's `SwarmProvisioner` —
that would break the provisioning cycle for all listeners. Exceptions
are logged and swallowed.

### Thread safety

- `eventSource` field is `volatile` — safe for the bind-once,
  read-many pattern
- `SwarmEventSource.onSwarmEvent()` is already thread-safe
  (`CopyOnWriteArrayList` subscribers)
- No additional synchronisation needed

## Testing

Plain JUnit 5 + Mockito (no CDI container — per project conventions).

| Test | What it verifies |
|------|-----------------|
| Each callback maps correctly | Call bridge method → verify `SwarmEventSource.onSwarmEvent()` receives the right event variant with correct field values |
| Pre-bind no-op safety | Call each callback before `bind()` → no exception, no interaction |
| Bind lifecycle | `isBound()` returns false before bind, true after |
| Exception isolation | `SwarmEventSource` throws → bridge catches, doesn't propagate |

## Files changed

| File | Change |
|------|--------|
| `agency-core/src/main/java/.../swarm/SwarmCoordinationListenerBridge.java` | New — the bridge |
| `agency/src/test/java/.../swarm/SwarmCoordinationListenerBridgeTest.java` | New — tests |
| `CLAUDE.md` | Update swarm package description to include bridge |

## Scope exclusions

- No multi-coordinator registry — single-coordinator-per-deployment (D1)
- No engine SPI changes — experiences and result mismatches handled
  at the bridge level (D2)
- No changes to `SwarmCoordinatorBuilder` — consumer calls `bind()`
  explicitly (D3)

## References

- `engine/api/.../SwarmCoordinationListener.java:30` — engine SPI
- `agency-core/.../SwarmCoordinationEvent.java:10` — agency event hierarchy
- `agency-core/.../SwarmEventSource.java:9` — event source and context update
- `agency-core/.../SwarmContext.java:11` — mutable state updated by events
- `agency-core/.../SwarmCoordinatorBuilder.java:82-113` — coordinator construction
- `engine/runtime-core/.../SwarmProvisioner.java:62-88` — listener injection and invocation
- `engine/runtime/.../RuntimeBeans.java:1360` — CDI discovery of listeners
