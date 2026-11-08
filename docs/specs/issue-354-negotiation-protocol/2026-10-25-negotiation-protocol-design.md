# NegotiationProtocol — Agency Pattern as qhorus ChannelProtocol

**Issue:** casehubio/agency#354
**Epic:** casehubio/casehub-ops#186 (governed interaction patterns)
**Date:** 2026-10-25

## Overview

Implement `NegotiationProtocol` as a `ChannelProtocol` — the first agency interaction pattern that plugs directly into qhorus's enforcement pipeline via the existing protocol SPI. Proves the composition architecture: agency patterns implement qhorus SPIs directly, discovered via CDI when agency is on the classpath. No bridge module, no adapter layer.

## Architecture

### Composition Model

```
topology YAML (ops)
  └─ channel config: protocols: [NEGOTIATION], policyOverrides: {negotiation.acceptance: majority}
       └─ Channel record (qhorus)
            └─ ProtocolRegistry discovers NegotiationProtocol via CDI
                 └─ MessageService.dispatch() calls evaluate() on every message
                      └─ NegotiationProtocol projects state via ChannelProjector
                           └─ NegotiationRules evaluates state → List<DispatchAdvisory>
                                └─ EnforcementMode determines action (log / block / quarantine)
```

### Layer Responsibilities

| Layer | Owner | Responsibility |
|-------|-------|----------------|
| Protocol SPI | qhorus-api | `ChannelProtocol`, `ProtocolContext`, `DispatchAdvisory` |
| Protocol discovery | qhorus-runtime | `ProtocolRegistry` via `@Any Instance<ChannelProtocol>` |
| Protocol enforcement | qhorus-runtime | `MessageService.enforceIfRequired()` |
| Negotiation state | agency | `NegotiationProjection`, `NegotiationFold`, `NegotiationState` |
| Protocol implementation | agency | `NegotiationProtocol` implements `ChannelProtocol` |
| Rules evaluation | agency | `NegotiationRules` pure function |
| Channel config | qhorus | `Channel.policyOverrides` → `ProtocolContext.policyOverrides` |

## Upstream Change: ProtocolContext Enhancement

**Repo:** casehubio/qhorus
**Change:** Add `Map<String, String> policyOverrides` to `ProtocolContext`.

```java
public record ProtocolContext(
        UUID channelId,
        String channelName,
        MessageType incomingType,
        String sender,
        String correlationId,
        List<String> protocolParticipants,
        List<MessageView> recentMessages,
        List<Commitment> activeCommitments,
        Map<String, String> policyOverrides) {   // NEW

    public ProtocolContext {
        protocolParticipants = protocolParticipants != null ? List.copyOf(protocolParticipants) : List.of();
        recentMessages = recentMessages != null ? List.copyOf(recentMessages) : List.of();
        activeCommitments = activeCommitments != null ? List.copyOf(activeCommitments) : List.of();
        policyOverrides = policyOverrides != null ? Map.copyOf(policyOverrides) : Map.of();
    }
}
```

**Construction site** in `MessageService.dispatch()` (line ~355): add `ch.policyOverrides()` as the final argument. Backward compatible — existing protocols ignore the new field.

## Component Design

### 1. NegotiationConstants (rename)

Rename existing `NegotiationProtocol` (constants-only class) to `NegotiationConstants`. Same package (`io.casehub.agency.deliberation.negotiation`), same constants. Update any references.

### 2. NegotiationProtocol (new — implements ChannelProtocol)

**Package:** `io.casehub.agency.deliberation.negotiation.protocol`

```java
@ApplicationScoped
public class NegotiationProtocol implements ChannelProtocol {

    private final ChannelProjector projector;
    private final NegotiationRules defaultRules;
    private final ConcurrentHashMap<UUID, ProjectionResult<NegotiationState>> cache;
    private final boolean cachingEnabled;

    @Inject
    public NegotiationProtocol(ChannelProjector projector) {
        this(projector, NegotiationRules.defaults(), true);
    }

    public NegotiationProtocol(ChannelProjector projector,
                               NegotiationRules defaultRules,
                               boolean cachingEnabled) {
        this.projector = projector;
        this.defaultRules = defaultRules;
        this.cachingEnabled = cachingEnabled;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public String protocolName() {
        return "NEGOTIATION";
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        NegotiationState state = projectState(ctx.channelId(), ctx.protocolParticipants());
        NegotiationRules rules = defaultRules.withOverrides(ctx.policyOverrides());
        return rules.evaluate(state, ctx);
    }
}
```

**State projection** uses `ChannelProjector` with incremental caching:

- First call for a channel: full-scan projection via `projector.project(channelId, negotiationProjection)`
- Subsequent calls: incremental projection via `projector.project(channelId, negotiationProjection, cachedResult)`
- Cache toggle: when `cachingEnabled=false`, always does full-scan (no cache reads or writes)
- Cache eviction: when negotiation reaches a terminal state (`AGREED`, `DEADLOCKED`, `WITHDRAWN`), evict the entry

The `NegotiationProjection` is constructed per-channel with the `protocolParticipants` from `ProtocolContext` and an `AcceptancePolicy` derived from the `policyOverrides`. This is required because `NegotiationProjection` takes `Set<String> parties` and `AcceptancePolicy` at construction — both of which are per-channel.

**Cache invalidation on config change:** If a channel's `protocolParticipants` or `policyOverrides` change between dispatches, the cached `ProjectionResult` is stale because it was projected with the old `NegotiationProjection` instance. The protocol detects this by comparing the current participants and acceptance policy against the cached projection's parameters. On mismatch, it evicts the cache entry and re-projects from scratch. This is rare — config changes are administrative, not per-message.

### 3. NegotiationRules (pure function with builder)

**Package:** `io.casehub.agency.deliberation.negotiation.protocol`

Pure function: `(NegotiationState, ProtocolContext) → List<DispatchAdvisory>`.

```java
public final class NegotiationRules {

    // Structural rules (always on)
    // - Non-participant message → WARNING
    // - Accept/reject without active proposal → ADVISORY
    // - Message on terminal negotiation → CRITICAL

    // Policy rules (configurable via builder, overridable via policyOverrides)
    private final int maxRounds;           // default: 10
    private final boolean enforceDeadline; // default: false
    private final Duration deadline;       // default: null
    private final boolean enforceTurnOrder;// default: false
    private final AcceptancePolicy acceptancePolicy; // default: unanimous

    public static NegotiationRules defaults() { ... }

    public static Builder builder() { ... }

    public NegotiationRules withOverrides(Map<String, String> policyOverrides) {
        // Reads negotiation.* keys from policyOverrides
        // Returns new NegotiationRules with overridden values
        // Unknown keys ignored
    }

    public List<DispatchAdvisory> evaluate(NegotiationState state, ProtocolContext ctx) {
        List<DispatchAdvisory> advisories = new ArrayList<>();
        evaluateStructuralRules(state, ctx, advisories);
        evaluatePolicyRules(state, ctx, advisories);
        return List.copyOf(advisories);
    }
}
```

**policyOverrides key mapping:**

| Key | Values | Default | Rule effect |
|-----|--------|---------|-------------|
| `negotiation.acceptance` | `unanimous`, `majority`, `threshold` | `unanimous` | Which `AcceptancePolicy` to use |
| `negotiation.max-rounds` | integer | `10` | Max proposals before WARNING |
| `negotiation.deadline` | ISO duration | none | CRITICAL when deadline exceeded |
| `negotiation.threshold` | 0.0-1.0 | `0.5` | Threshold when acceptance=threshold |
| `negotiation.enforce-turn-order` | `true`/`false` | `false` | Turn-order enforcement |

**Advisory severity mapping:**

| Rule | Severity | SuggestedAction |
|------|----------|-----------------|
| Message on terminal negotiation | CRITICAL | ESCALATE |
| Deadline exceeded | CRITICAL | ESCALATE |
| Max rounds exceeded | WARNING | INVESTIGATE |
| Non-participant message | WARNING | LOG |
| Proposer accepting own proposal | WARNING | LOG |
| Consecutive proposals without response | ADVISORY | LOG |
| Accept/reject without active proposal | ADVISORY | LOG |

### 4. AcceptancePolicy Resolution

`NegotiationRules.withOverrides()` maps the `negotiation.acceptance` key to the existing `AcceptancePolicy` implementations:

| Value | Implementation |
|-------|---------------|
| `unanimous` | `UnanimousAcceptance` |
| `majority` | `MajorityAcceptance` |
| `threshold` | `ThresholdAcceptance(n)` where n derived from `negotiation.threshold` and party count |

This reuses the existing acceptance policy types — no new implementations needed.

## Testing Strategy

### Unit Tests (CDI-free)

**`NegotiationRulesTest`** — the primary test surface:
- Each structural rule: non-participant, orphan accept/reject, terminal state
- Each policy rule: max rounds, deadline, turn-order
- policyOverrides parsing: valid keys, unknown keys ignored, malformed values
- Builder + withOverrides composition
- Severity correctness per rule

**`NegotiationProtocolTest`** — integration with projection:
- Mocked `ChannelProjector` returns pre-built `ProjectionResult<NegotiationState>`
- Verify `evaluate()` returns correct advisories for given state
- Verify incremental caching: second call uses `project(channelId, projection, previous)`
- Verify cache eviction on terminal state
- Verify full-scan fallback when caching disabled

**`NegotiationConstantsTest`** — trivial rename verification.

### No @QuarkusTest needed

The issue spec mentions `@QuarkusTest` for CDI discovery, but this is unnecessary for the agency side — agency tests are plain JUnit 5 with Mockito. CDI discovery is a qhorus concern (tested by qhorus's own protocol registry tests). Agency only needs to verify the `ChannelProtocol` contract is correctly implemented.

## Deliverables

| File | What |
|------|------|
| `NegotiationConstants.java` | Renamed from `NegotiationProtocol` (constants) |
| `NegotiationProtocol.java` | `ChannelProtocol` implementation |
| `NegotiationRules.java` | Pure function with builder |
| `NegotiationRulesTest.java` | Rules unit tests |
| `NegotiationProtocolTest.java` | Protocol + projection tests |
| qhorus `ProtocolContext.java` | Add `policyOverrides` field |
| qhorus `MessageService.java` | Pass `ch.policyOverrides()` to context |

## References

- `io.casehub.qhorus.api.spi.ChannelProtocol` — the SPI we implement
- `io.casehub.qhorus.api.channel.ChannelProjector` — projection facade (qhorus#496)
- `io.casehub.qhorus.runtime.message.MessageService:346-362` — protocol evaluation pipeline
- `io.casehub.qhorus.runtime.message.protocol.ProtocolRegistry` — CDI discovery and lookup
- `io.casehub.qhorus.runtime.message.protocol.RequestResponseProtocol` — reference implementation
- `io.casehub.agency.deliberation.negotiation.NegotiationProjection` — existing fold
- `io.casehub.agency.deliberation.negotiation.NegotiationFold` — pure state transitions
- `io.casehub.agency.deliberation.negotiation.AcceptancePolicy` — existing acceptance SPI
- casehubio/casehub-ops#186 — governed interaction patterns epic
- casehubio/qhorus#496 — ChannelProjector API facade (closed, unblocked)
- `docs/architecture/platform-composition.md` in casehub-ops — architecture doc
