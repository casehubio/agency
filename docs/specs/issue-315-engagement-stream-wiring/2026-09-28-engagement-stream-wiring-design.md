# Design: Engagement stream wiring (#315)

## Problem

`CognitionCore.recordInteraction()` creates `EngagementEvent` records and passes them to `StrategyLearningOrchestrator`, which buffers in-memory and stores via `StrategyStore`. But neocortex's `EngagementRecorderCore` — the persistent memory store — is never called. Engagement data doesn't reach the neocortex memory layer, so downstream subsystems (consolidation, temporal focus, social comparison) can't query it.

## Scope

**In scope:**
- Wire `EngagementRecorderCore` into `CognitionCore` so each interaction persists an `EngagementEvent` to neocortex memory
- Thread the dependency through `SocialAvatarCognition`
- Unit tests with Mockito (no CDI container)

**Out of scope:**
- `RelationshipObserver` (documented but not implemented — separate work)
- Consuming `EngagementRecorded` CDI events (hook point exists; actual consumers come in later issues)
- Modifying `StrategyLearningOrchestrator` (existing path stays unchanged)

## Design

### Change 1: CognitionCore — add EngagementRecorderCore field

Add `@Nullable EngagementRecorderCore engagementRecorder` as a field on `CognitionCore`.

Thread it through the constructor chain:
- Short constructors (lines 87, 99, 113): pass `null`
- Full constructor (line 128): accept and store the new parameter

The field is `@Nullable` to maintain backward compatibility — existing callers that don't have access to the recorder continue to work.

### Change 2: CognitionCore.recordInteraction() — persist to neocortex memory

In `recordInteraction()`, after the existing engagement signal block (lines 250-273), add a call to persist the same `EngagementEvent` via `EngagementRecorderCore.record()`.

The `EngagementEvent` is already constructed at lines 260-269. Extract it into a local variable so both the `StrategyLearningOrchestrator` path and the new `EngagementRecorderCore` path use the same event instance.

```java
// Extract the event construction (existing code, just lifted to a local var)
var engagementEvent = new EngagementEvent(agentId, subjectId,
        tenantId, caseId,
        UUID.randomUUID().toString(),
        Instant.now(),
        userMessage.isBlank() ? "[interaction]" : userMessage,
        null, Map.of(), true, null,
        (int) response.length(),
        null, null, null);

// Existing path: strategy learning
if (config.strategyEnabled() && strategy != null) {
    safeRun(() -> strategy.record(
            new EngagementSignal.TurnOutcome(engagementEvent, Map.of(), response),
            agentId, subjectId, tenantId));
}

// New path: neocortex memory persistence
if (engagementRecorder != null) {
    safeRun(() -> engagementRecorder.record(engagementEvent));
}
```

The `engagementRecorder.record()` call is wrapped in `safeRun()` (existing pattern) so persistence failures don't break the interaction flow. The recorder is guarded by a null check — if no recorder is available, the path is silently skipped.

The neocortex persistence path is **independent of `config.strategyEnabled()`** — engagement memory should be recorded regardless of whether strategy learning is active. This is intentional: strategy learning is a consumer of engagement data, not a gate on it.

The persistence call remains inside the existing `if (subjectId != null)` guard (line 238) because `EngagementEvent` requires `otherAgentId` (mapped from `subjectId`) to be non-null. Interactions without a subject cannot produce valid engagement events.

When `impact.strategySignal()` is provided (pre-constructed by caller), the engagement event comes from the signal. In that case, persist `impact.strategySignal().event()` rather than constructing a new one. This ensures the persisted event matches what strategy learning sees.

### Change 3: SocialAvatarCognition — thread the dependency

Add `Optional<EngagementRecorderCore> engagementRecorder` to `SocialAvatarCognition`'s constructor. Pass `.orElse(null)` to `CognitionCore`'s full constructor.

`EngagementRecorderCore` is already an `@ApplicationScoped` CDI bean produced by neocortex's `MemoryBeans`. Using `Optional` for the constructor parameter allows CDI to inject it when available (blocks has `casehub-neocortex-memory` on the classpath) and skip gracefully when not.

### Testing

Plain JUnit 5 + Mockito (per blocks convention — no CDI container):

1. **CognitionCore records to EngagementRecorderCore** — mock the recorder, call `recordInteraction()`, verify `record(EngagementEvent)` was called with matching fields
2. **CognitionCore null recorder — no error** — construct with `null` recorder, call `recordInteraction()`, verify no NPE
3. **Pre-constructed signal uses signal's event** — provide `CognitiveImpact` with a `strategySignal`, verify the recorder receives the signal's event, not a new one
4. **Recorder failure doesn't propagate** — mock recorder to throw, verify `recordInteraction()` completes normally (safeRun absorbs the exception)

### Data flow (after)

```
CognitionCore.recordInteraction()
  ├── mood.record(signal)                     [existing]
  ├── userModel.record(signal)                [existing]
  ├── mentalModel: extractAndRecord()         [existing]
  ├── strategy.record(EngagementSignal)       [existing — in-memory buffering]
  └── engagementRecorder.record(event)        [NEW — neocortex memory persistence]
        └── fires EngagementRecorded CDI event  [hook point for future consumers]
```

## Trade-offs

- `CognitionCore` constructor gains one more parameter (15th). The telescoping chain is already long. Accepted: this follows the established pattern, and a builder or config object would be a separate refactoring concern.
- Engagement events are persisted independently of strategy learning. This means the two stores may drift if one fails. Accepted: they serve different purposes (memory recall vs. strategy optimization) and eventual consistency is fine.

## References

- `CognitionCore.java:226` — `recordInteraction()` method
- `CognitionCore.java:128` — full constructor (14 parameters)
- `SocialAvatarCognition.java:41` — constructor that builds CognitionCore
- `EngagementRecorderCore` (neocortex `memory-core`) — `record()` and `recordAll()` methods
- `EngagementEvent` (neocortex `memory-api`) — 15-field record
- `EngagementRecorded` (neocortex `memory-api`) — CDI event fired after store
- `MemoryBeans.java:89` (neocortex `memory`) — CDI producer for EngagementRecorderCore
- casehubio/blocks#311 — parent epic
