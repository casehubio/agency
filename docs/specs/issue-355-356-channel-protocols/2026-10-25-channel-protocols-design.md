# ConversationProtocol and JudgmentProtocol — ChannelProtocol Implementations

**Issues:** casehubio/agency#355, casehubio/agency#356
**Branch:** issue-355-356-channel-protocols
**Date:** 2026-10-25

## Overview

Two new `ChannelProtocol` implementations following the pattern established by `NegotiationProtocol` (#354). Both use the same two-class architecture: an `@ApplicationScoped` protocol class with projection caching, and a pure immutable rules class with `Builder` and `withOverrides(Map)`.

## Prerequisite: Rename ConversationProtocol → ConversationConstants

The existing `ConversationProtocol` class in `io.casehub.agency.deliberation.conversation` is a constants/sentinel utility (meta key names, infrastructure entry types). Rename it to `ConversationConstants` to free the name for the new `ChannelProtocol` implementation. Update all references in agency and downstream consumers (DraftHouse).

## ConversationProtocol (#355)

### Package

`io.casehub.agency.deliberation.conversation.protocol`

### Classes

**DefaultConversationProjection** — concrete `ConversationProjection` subclass with defaults:
- `sentinel()` returns `ConversationConstants.SENTINEL` (renamed class)
- `isPointInitiator()` returns true for standard entry types (QUESTION, PROPOSAL, CONCERN, OBSERVATION)
- `statusAfter()` maps RESPONSE → ACTIVE, RESOLUTION → RESOLVED, ACKNOWLEDGE → ACKNOWLEDGED

**ConversationProtocol** — `@ApplicationScoped`, implements `ChannelProtocol`:
- `protocolName()` → `"CONVERSATION"`
- `evaluate(ProtocolContext)`:
  1. Merge `policyOverrides` into `ConversationRules` via `withOverrides()`
  2. Construct `DefaultConversationProjection`
  3. Project state via `ChannelProjector` (cached per channelId, invalidated on participant change)
  4. Delegate to `rules.evaluate(state, ctx)`
- Cache: `ConcurrentHashMap<UUID, CachedProjection>` with `CachedProjection(ProjectionResult<ConversationState>, Set<String> participants)`. Terminal state evicts. Participant change invalidates.

**ConversationRules** — pure, immutable:
- Fields: `turnPolicy` (String, default `"free-form"`), `maxConsecutive` (int, default 2), `convergenceThreshold` (Double, default null)
- `defaults()` factory, `Builder`, `withOverrides(Map<String,String>)`
- `evaluate(ConversationState, ProtocolContext) → List<DispatchAdvisory>`

### Rules

**Structural (always on):**
- Non-participant sender → WARNING ("sender not in protocol participants")
- All points resolved and no open threads → ADVISORY ("conversation appears complete")

**Policy (configurable):**

| Config | Rule |
|--------|------|
| `conversation.turn-policy=round-robin` | WARNING if sender violates strict alternation order from participant list |
| `conversation.turn-policy=moderated` | WARNING if non-first-participant sends without being addressed (first participant is moderator) |
| `conversation.turn-policy=free-form` | No turn enforcement |
| `conversation.max-consecutive=N` | WARNING when same sender sends > N consecutive messages (checked from recent messages in ProtocolContext) |
| `conversation.convergence-threshold=T` | ADVISORY when convergence score (via `ConvergencePolicies.structural()` + `ConvergenceAnalyser`) exceeds threshold — signals conversation should wrap up |

### Convergence Integration

When `convergenceThreshold` is configured, `ConversationRules` computes convergence:
1. Build `CommonGroundState` via `CommonGroundAnalyser.analyse(state, EpistemicRules.tacitAcceptance(2))`
2. Build `ConvergenceSignal` via `ConvergenceAnalyser.analyse(state, commonGround, ConvergencePolicies.structural(0.8, 3), recentWindow=5)`
3. If `signal.confidence() >= convergenceThreshold` → ADVISORY with convergence state in evidence

## JudgmentProtocol (#356)

### Package

`io.casehub.agency.deliberation.judgment.protocol`

### Classes

**JudgmentOutcome** — enum: `PENDING`, `APPROVED`, `REJECTED`, `ESCALATED`. `isTerminal()` for non-PENDING.

**JudgmentVerdict** — record: `reviewer` (String), `decision` (Decision enum: APPROVE/REJECT), `reason` (@Nullable String), `respondedAt` (Instant).

**JudgmentState** — record: `reviewers` (Set<String>), `verdicts` (Map<String, JudgmentVerdict>), `outcome` (JudgmentOutcome), `subject` (@Nullable String). Methods: `pendingReviewers()` (reviewers minus verdict keys), `hasAllReviewed()`, `approvalCount()`, `rejectionCount()`.

**JudgmentFold** — pure static state transitions:
- `submitVerdict(state, verdict) → JudgmentState` — adds verdict, last-write-wins on duplicate reviewer
- `approve(state) → JudgmentState` — outcome → APPROVED
- `reject(state) → JudgmentState` — outcome → REJECTED
- `escalate(state) → JudgmentState` — outcome → ESCALATED

**JudgmentProjection** — concrete `ChannelProjection<JudgmentState>`:
- Constructor: `(Set<String> reviewers)` from protocolParticipants
- `identity()` → empty state with reviewer set, PENDING outcome
- `apply()` dispatches on MessageType:
  - `DONE` → `JudgmentFold.submitVerdict()` with APPROVE decision
  - `DECLINE` → `JudgmentFold.submitVerdict()` with REJECT decision
  - Unknown senders (not in reviewer set) → ignored
  - Other message types → passed through (no state change)
- Does NOT evaluate agreement — that's the rules layer's job

**JudgmentProtocol** — `@ApplicationScoped`, implements `ChannelProtocol`:
- `protocolName()` → `"JUDGMENT"`
- `evaluate(ProtocolContext)`:
  1. Merge `policyOverrides` into `JudgmentRules` via `withOverrides()`
  2. Construct `JudgmentProjection` with reviewer set from `protocolParticipants`
  3. Project state via `ChannelProjector` (cached, invalidated on reviewer set change)
  4. Delegate to `rules.evaluate(state, ctx)`
- Cache: same `CachedProjection` pattern, keyed by channelId, staleness on reviewer set change

**JudgmentRules** — pure, immutable:
- Fields: `agreementType` (String, default `"consensus"`), `minReviewers` (int, default 2), `escalation` (String, default `"none"`)
- `defaults()` factory, `Builder`, `withOverrides(Map<String,String>)`
- `evaluate(JudgmentState, ProtocolContext) → List<DispatchAdvisory>`

### Rules

**Structural (always on):**
- Terminal state (APPROVED/REJECTED/ESCALATED) → CRITICAL ("judgment concluded")
- Non-reviewer sender submitting verdict → WARNING ("sender not in reviewer set")

**Policy (configurable):**

| Config | Rule |
|--------|------|
| `judgment.min-reviewers=N` | WARNING at evaluate-time if reviewer set size < N |
| `judgment.agreement=unanimous` | After all reviewed: approved only if all approve; otherwise rejected (or escalated if configured) |
| `judgment.agreement=majority` | After sufficient verdicts: approved if >50% approve |
| `judgment.agreement=consensus` | After sufficient verdicts: approved if ≥2/3 approve |
| `judgment.escalation=chain` | When all reviewed and no agreement → ESCALATED instead of REJECTED |
| `judgment.escalation=none` | No agreement → REJECTED |

### Agreement Evaluation in Rules

`JudgmentRules.evaluate()` checks agreement after each verdict submission:
1. Count approvals and rejections from state
2. Check if early determination is possible (majority: enough approvals already, or enough rejections to make approval impossible)
3. If all reviewed or early determination: apply agreement policy, transition state via fold
4. Return appropriate advisory: ADVISORY for approval, WARNING for rejection, CRITICAL for escalation

Agreement is evaluated in the rules layer, not the projection layer — the projection only folds messages into state. This separation matches NegotiationProtocol where `NegotiationRules` checks `AcceptancePolicy`, not `NegotiationProjection`.

## Testing

Both protocols follow the NegotiationProtocol test structure:

**`ConversationProtocolTest`** — mocked `ChannelProjector`:
- Protocol name
- Non-participant sender → WARNING
- Turn policy violations (round-robin, moderated)
- Max consecutive enforcement
- Convergence threshold advisory
- Incremental projection caching
- Cache invalidation on participant change
- policyOverrides per-call

**`ConversationRulesTest`** — pure unit tests:
- Structural rules (non-participant, conversation complete)
- Policy rules (turn policy modes, max consecutive, convergence)
- withOverrides parsing (valid, malformed, unknown keys)

**`JudgmentProtocolTest`** — mocked `ChannelProjector`:
- Protocol name
- Terminal state → CRITICAL
- Non-reviewer verdict → WARNING
- Agreement evaluation (all three modes)
- Escalation behavior
- Min-reviewers warning
- Incremental projection caching
- Cache invalidation on reviewer change
- policyOverrides per-call

**`JudgmentRulesTest`** — pure unit tests:
- Structural rules (terminal, non-reviewer)
- Agreement logic (unanimous, majority, consensus)
- Escalation vs rejection
- Min-reviewers check
- withOverrides parsing
- Early determination (majority with enough votes)

**`JudgmentProjectionTest`** — pure fold tests:
- DONE → approve verdict
- DECLINE → reject verdict
- Unknown sender ignored
- Duplicate reviewer supersedes
- Other message types pass through

**`JudgmentFoldTest`** — pure state transition tests:
- submitVerdict, approve, reject, escalate

## Cross-Repo Impact

**DraftHouse:** Update imports from `ConversationProtocol` → `ConversationConstants`. No behavioral change.

## File Summary

| File | Package |
|------|---------|
| `ConversationConstants.java` (rename) | `deliberation.conversation` |
| `DefaultConversationProjection.java` | `deliberation.conversation.protocol` |
| `ConversationProtocol.java` | `deliberation.conversation.protocol` |
| `ConversationRules.java` | `deliberation.conversation.protocol` |
| `JudgmentOutcome.java` | `deliberation.judgment.protocol` |
| `JudgmentVerdict.java` | `deliberation.judgment.protocol` |
| `JudgmentState.java` | `deliberation.judgment.protocol` |
| `JudgmentFold.java` | `deliberation.judgment.protocol` |
| `JudgmentProjection.java` | `deliberation.judgment.protocol` |
| `JudgmentProtocol.java` | `deliberation.judgment.protocol` |
| `JudgmentRules.java` | `deliberation.judgment.protocol` |

## References

- `NegotiationProtocol.java`, `NegotiationRules.java` — reference pattern (agency#354)
- `ConversationProjection.java` — abstract base for conversation fold
- `ConversationProtocol.java` — existing constants class (to be renamed)
- `ConversationState.java`, `ConversationFold.java` — existing conversation state
- `ConvergenceAnalyser.java`, `CommonGroundAnalyser.java` — convergence detection
- `AgreementPolicy.java`, `ConsensusAgreement.java` — existing agreement logic (referenced but not reused in protocol)
- `JudgmentPhase.java`, `JudgmentPolicy.java` — existing execution-driver judgment (separate concern)
- qhorus `ChannelProtocol`, `ChannelProjector`, `ChannelProjection` SPIs (qhorus#496)
