# Bottom-Up Formation Orchestrator — Design Spec

**Issue:** casehubio/agency#410
**Parent:** casehubio/agency#408 (Society integration hardening)
**Date:** 2026-11-06

## Summary

Wire `CoalitionProposal` → `NegotiationProtocol` → `GroupLifecycleService` into a single orchestrator that handles the full bottom-up group formation flow. An agent proposes a coalition, the orchestrator evaluates viability, opens a negotiation channel for peer consensus, and on acceptance creates the group via eidos.

## Scope

1. `BottomUpFormationOrchestrator` — composition root wiring the three components
2. `FormationResult` — sealed outcome type
3. `MessageObserverRegistrar` — functional interface for observer registration
4. `CoalitionProposal` cleanup — add `proposer` field, remove `coveredCapabilities()`
5. New dependency: `casehub-eidos-org-api` (provided) in agency-core

Out of scope:
- Top-down formation, emergence-based formation
- `FormationRules` enforcement (caller responsibility — caller checks `FormationRules.allowedPaths` before calling `propose()`)
- selfDetermination gate (caller responsibility — caller checks `selfDetermination >= 0.5` on parent group before calling `propose()`)
- JointIntention role adoption post-formation (group returned in FORMING state; caller transitions to ACTIVE)
- Concurrent proposal guards (overlapping member detection across parallel proposals — tracked separately)

## Package

`io.casehub.agency.deliberation.coalition.formation` in `agency-core`.

Mirrors `deliberation.conversation.orchestration` but uses the domain-specific term "formation". Sub-package of `coalition` because bottom-up formation is inherently a coalition operation.

## Components

### BottomUpFormationOrchestrator

Plain class, not CDI-managed. Consumer constructs with domain-specific dependencies (same pattern as `VouchService`).

**Constructor parameters:**

| Parameter | Type | Source |
|-----------|------|--------|
| `evaluator` | `CoalitionEvaluator` | Coalition viability scoring |
| `groupLifecycle` | `GroupLifecycleService` | eidos-org-api group creation |
| `channelManager` | `ChannelManager` | qhorus channel lifecycle |
| `messageDispatcher` | `MessageDispatcher` | Send initial PROPOSE message |
| `observerRegistrar` | `MessageObserverRegistrar` | Register observer for channel messages |
| `specId` | `String` | Group spec governing formation target |
| `tenancyId` | `String` | Tenancy context |
| `acceptancePolicy` | `AcceptancePolicy` | Negotiation quorum (default: `UnanimousAcceptance`) |
| `timeout` | `Duration` | Negotiation deadline |

**API:**

```java
Uni<FormationResult> propose(CoalitionProposal proposal, CoalitionContext context)
```

### Flow

```
propose(proposal, context)
  │
  ├─ 1. evaluator.evaluate(proposal, context)
  │     └─ not viable? → FormationResult.rejected(reason)
  │
  ├─ 2. channelManager.findOrCreate(ChannelCreateRequest.builder(channelName)
  │     │     .protocols(List.of("NEGOTIATION"))
  │     │     .protocolParticipants(memberNames)
  │     │     .allowedWriters(memberNames)
  │     │     .tenancyId(tenancyId)
  │     │     .build())
  │     ├─ channelName: "formation:<taskId>:<proposer.name()>"
  │     ├─ memberNames: proposal.proposedMembers().stream().map(AgentRef::name).toList()
  │     └─ includes proposer in both protocolParticipants and allowedWriters
  │
  ├─ 3. ChannelObserver<NegotiationState> wrapping NegotiationProjection
  │     ├─ parties = proposedMembers names (Set<String>)
  │     ├─ acceptancePolicy = constructor-injected (default: UnanimousAcceptance)
  │     ├─ single subscriber only (orchestrator is sole consumer)
  │     └─ register via observerRegistrar.register(observer)
  │
  ├─ 4. messageDispatcher.dispatch(MessageDispatch.builder()
  │     │     .channelId(channelId)
  │     │     .sender(proposal.proposer().name())
  │     │     .messageType(MessageType.PROPOSE)
  │     │     .correlationId(proposal.taskId())
  │     │     .actorType(ActorType.AGENT)
  │     │     .tenancyId(tenancyId)
  │     │     .content(proposal description + required capabilities)
  │     │     .build())
  │     ├─ correlationId = taskId (used by NegotiationProjection as proposalId)
  │     └─ members respond with DONE (accept) or DECLINE (reject) using same correlationId
  │
  ├─ 5. Uni bridge via Uni.createFrom().emitter():
  │     ├─ subscribe to observer — on terminal outcome, emit result
  │     ├─ first-wins: emitter.complete() is idempotent — timeout and
  │     │   observer callback race harmlessly (whoever calls first wins)
  │     ├─ timeout: Uni.ifNoItem().after(timeout)
  │     │   └─ on timeout, check observer.currentState() — if terminal
  │     │     in the meantime, use that outcome instead of Timeout
  │     └─ cleanup via emitter.onTermination()
  │
  ├─ 6a. AGREED →
  │       ├─ groupLifecycle.proposeGroup(specId, tenancyId, proposer.name())
  │       ├─ groupLifecycle.approveProposal(instanceId, tenancyId)
  │       ├─ for each member: groupLifecycle.joinGroup(instanceId, member.name(), tenancyId)
  │       └─ FormationResult.accepted(instanceId)
  │           (group is in FORMING state with all members enrolled;
  │            caller transitions to ACTIVE via JointIntention)
  │
  ├─ 6b. DEADLOCKED/WITHDRAWN → FormationResult.negotiationFailed(reason)
  │
  ├─ 6c. Timeout → FormationResult.timeout()
  │
  └─ 7. Cleanup (all exit paths via emitter.onTermination()):
        ├─ cancel observer subscription
        ├─ cancel observer registration
        └─ channelManager.pause(channelId)
```

### FormationResult

Sealed interface in `deliberation.coalition.formation`:

```java
sealed interface FormationResult {
    record Accepted(String groupInstanceId) implements FormationResult {}
    record Rejected(String reason) implements FormationResult {}
    record NegotiationFailed(String reason) implements FormationResult {}
    record Timeout() implements FormationResult {}

    static FormationResult accepted(String id) { return new Accepted(id); }
    static FormationResult rejected(String reason) { return new Rejected(reason); }
    static FormationResult negotiationFailed(String reason) { return new NegotiationFailed(reason); }
    static FormationResult timeout() { return new Timeout(); }
}
```

`groupInstanceId` is `String` — matching `GroupInstance.instanceId` in eidos-org-api.

### MessageObserverRegistrar

Functional interface in `deliberation.coalition.formation`:

```java
@FunctionalInterface
interface MessageObserverRegistrar {
    Runnable register(MessageObserver observer);
}
```

Returns a `Runnable` that cancels the registration when called. Avoids a package dependency on `EventSource.Cancellation` from `orchestration.execution` — the concepts are different (registration cancellation vs event-source cancellation).

The orchestrator calls `registrar.register(observer)` during `propose()`. The CDI wiring layer in the consuming application provides the implementation that plugs into `MessageObserverDispatcher`.

### CoalitionProposal Changes

**Add `proposer` field:**

```java
// Before
record CoalitionProposal(String taskId, Set<String> requiredCapabilities,
                          List<AgentRef> proposedMembers) {
    Set<String> coveredCapabilities() { ... }
}

// After
record CoalitionProposal(AgentRef proposer, String taskId,
                          Set<String> requiredCapabilities,
                          List<AgentRef> proposedMembers) {
    // coveredCapabilities() removed
}
```

`proposer` is explicit and self-documenting. `coveredCapabilities()` is removed because `CapabilityCoverageEvaluator` already resolves capabilities correctly via `CoalitionContext.capabilitiesOf()`. No current consumers call `coveredCapabilities()`.

### Idempotent Channel Creation

Use `ChannelManager.findOrCreate(ChannelCreateRequest)` with a deterministic channel name: `"formation:<taskId>:<proposer.name()>"`. On retry, the same channel is reused rather than creating duplicates.

### Channel Cleanup

After terminal outcome (or timeout), the orchestrator:
1. Cancels the `ChannelObserver` subscription (stops event delivery)
2. Cancels the `MessageObserverRegistrar` registration (removes from dispatch)
3. Calls `ChannelManager.pause(channelId)` (prevents new messages, preserves history for audit)

All cleanup runs via `emitter.onTermination()`, which fires on normal completion, timeout, and external Uni cancellation.

### Timeout and Race Resolution

Timeout uses `Uni.ifNoItem().after(timeout)`. The timeout duration is constructor-injected.

Both the observer callback (terminal negotiation outcome) and the timeout handler funnel through a single `UniEmitter`. Mutiny's emitter is thread-safe — the first call to `emitter.complete(result)` wins, subsequent calls are no-ops. This eliminates the TOCTOU race between timeout and observer threads.

The timeout recovery path checks `observer.currentState()` before emitting `FormationResult.timeout()` — if the negotiation reached a terminal outcome between the timeout firing and the check, the actual outcome is used instead.

## Type Bridge

The orchestrator bridges agency-layer types to eidos-layer operations:

| Agency concept | Eidos operation |
|----------------|-----------------|
| `proposal.proposer().name()` | `proposeGroup(specId, tenancyId, proposerAgentId)` |
| `specId` (constructor param) | Group spec defining formation target |
| `tenancyId` (constructor param) | Tenancy context |
| `FormationOrigin.PROPOSAL` | Bottom-up formation origin |
| Negotiation AGREED | `approveProposal(instanceId, tenancyId)` |
| `proposal.proposedMembers()` | `joinGroup(instanceId, member.name(), tenancyId)` per member |

Cross-layer parameters (`specId`, `tenancyId`) are constructor-injected, not carried on `CoalitionProposal`. The coalition domain is tenancy-agnostic.

**Return type note:** The parent hardening spec uses `CompletionStage<FormationResult>`. This spec uses `Uni<FormationResult>` for consistency with `ConversationOrchestrator` (the other orchestrator in agency-core) and for Mutiny-native timeout support. `io.smallrye.mutiny` is already a provided-scope dependency of agency-core.

## Dependencies

**New in agency-core/pom.xml:**

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-eidos-org-api</artifactId>
    <scope>provided</scope>
</dependency>
```

Consistent with existing provided-scope dependencies (`casehub-platform-api`, `casehub-engine-ledger`, `casehub-neocortex-*`).

## Test Strategy

Plain JUnit 5 with Mockito (no CDI container). Mock `GroupLifecycleService`, `ChannelManager`, `MessageDispatcher`, `MessageObserverRegistrar`.

| Test case | Scenario |
|-----------|----------|
| Happy path | Viable coalition → negotiation AGREED → group created |
| Non-viable coalition | Low score → `FormationResult.rejected()` |
| Negotiation rejected | DEADLOCKED → `FormationResult.negotiationFailed()` |
| Negotiation withdrawn | WITHDRAWN → `FormationResult.negotiationFailed()` |
| Timeout | No terminal outcome within deadline → `FormationResult.timeout()` |
| Cleanup on success | Observer unsubscribed, registration cancelled, channel paused |
| Cleanup on failure | Same cleanup on all failure paths |
| Cleanup on cancellation | External Uni cancellation triggers cleanup |
| Idempotent retry | `findOrCreate` returns existing channel on retry |
| Proposer identity | PROPOSE message sender matches `proposal.proposer().name()` |
| CoalitionProposal proposer | Explicit proposer field used correctly |
| CoalitionProposal no coveredCapabilities | Method removed, evaluator uses CoalitionContext |
| Member enrollment | All proposed members enrolled via `joinGroup()` after approval |
| Timeout race | Observer reaches terminal state during timeout → actual outcome used |
| PROPOSE message fields | correlationId, actorType, tenancyId set correctly |
| Viability check | `CoalitionScore.isViable()` determines rejection (all-or-nothing) |

## References

- `agency-core/.../deliberation/coalition/CoalitionProposal.java` — current record, `coveredCapabilities()` bug
- `agency-core/.../deliberation/coalition/CapabilityCoverageEvaluator.java` — correct capability resolution via CoalitionContext
- `agency-core/.../deliberation/negotiation/NegotiationProjection.java` — channel projection for negotiation state
- `agency-core/.../deliberation/negotiation/protocol/NegotiationProtocol.java` — ChannelProtocol impl
- `agency-core/.../communication/channel/ChannelObserver.java` — observation + EventSource bridge
- `agency-core/.../governance/trust/VouchService.java` — composition root pattern (non-CDI, constructor-injected)
- `agency-core/.../deliberation/conversation/orchestration/ConversationOrchestrator.java` — Uni return type convention
- Engine spec §3.3 — authoritative bottom-up formation design
- Agency hardening spec §7 — implementation-level detail
- casehubio/agency#408 — parent epic
- casehubio/desiredstate#230 — cross-repo reference
