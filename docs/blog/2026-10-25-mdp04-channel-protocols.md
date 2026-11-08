---
layout: post
title: "Governed channels — conversation and judgment as ChannelProtocol"
date: 2026-10-25
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [channel-protocol, conversation, judgment, qhorus, governed-interaction]
series: issue-355-356-channel-protocols
---

The NegotiationProtocol we landed yesterday proved that qhorus's `ChannelProtocol` SPI works as an advisory enforcement layer — you fold channel messages into domain state, evaluate rules against that state, and return advisories rather than blocking. Today we replicated the pattern twice more: `ConversationProtocol` for turn-taking and convergence detection, and `JudgmentProtocol` for quorum-based approval gates.

## The pattern

All three protocols share the same two-class architecture. A `*Protocol` class (`@ApplicationScoped`, implements `ChannelProtocol`) owns projection caching and delegates to a `*Rules` class. The rules class is pure and immutable — `defaults()` factory, `Builder`, `withOverrides(Map)` for per-channel policy configuration. Rules split into structural (always-on safety checks) and policy (configurable behaviour driven by `policyOverrides`).

The caching layer deserves a note. Each protocol maintains a `ConcurrentHashMap<UUID, CachedProjection>` keyed by channel ID, storing the last `ProjectionResult` plus a domain-specific staleness key (participant set for conversation and negotiation, reviewer set for judgment). On the next evaluate call, if the staleness key matches, the projector does an incremental scan from the cursor. If participants change, the cache is invalidated and a full scan runs. Terminal states evict entirely — no point caching a concluded interaction.

## ConversationProtocol

The conversation infrastructure already had most of what we needed. `ConversationProjection` is an abstract `ChannelProjection<ConversationState>` with three hooks: `sentinel()`, `isPointInitiator()`, and `statusAfter()`. DraftHouse's `DebateChannelProjection` overrides these with debate-specific entry types. For the ChannelProtocol, we added a `DefaultConversationProjection` that treats any non-infrastructure entry type as a point initiator — the general-purpose default.

The rules enforce three configurable policies:

- **Turn policy** — `round-robin` checks strict alternation against the participant list, `moderated` requires the first participant (moderator) to address others before they speak, `free-form` (default) imposes no constraint
- **Max consecutive** — flags when the same sender exceeds a consecutive message limit. The count comes from `recentMessages` in the `ProtocolContext`, so it's based on what the channel has already seen
- **Convergence threshold** — when configured, evaluates convergence using the existing `CommonGroundAnalyser` and `ConvergenceAnalyser`. If confidence exceeds the threshold, an ADVISORY signals that the conversation should wrap up

The naming required a small detour. The existing `ConversationProtocol` class was a constants utility (meta key names, infrastructure entry types), not a `ChannelProtocol`. We renamed it to `ConversationConstants` to free the name.

## JudgmentProtocol

Judgment needed more new code. No channel projection existed for verdict-based interactions, so we built the full fold stack: `JudgmentOutcome` (PENDING/APPROVED/REJECTED/ESCALATED), `JudgmentVerdict` (reviewer + decision + optional reason), `JudgmentState` (reviewer set, verdicts map, outcome), and `JudgmentFold` (pure state transitions).

`JudgmentProjection` maps message types to verdicts — `DONE` records an approval, `DECLINE` records a rejection. Unknown senders are silently ignored. The first non-verdict message captures the subject under review. Duplicate verdicts from the same reviewer supersede (last-write-wins).

The agreement evaluation lives in `JudgmentRules`, not in the projection — matching the NegotiationProtocol pattern where `NegotiationRules` checks `AcceptancePolicy`, not `NegotiationProjection`. Three agreement modes:

- **Unanimous** — any rejection is an early determination. Approval requires all reviewers to approve.
- **Majority** — early determination when approvals or rejections exceed half the reviewer count.
- **Consensus** (default) — requires ≥2/3 approval. Early determination when enough approvals are in.

When agreement fails and `escalation=chain` is configured, the outcome is ESCALATED rather than REJECTED — the advisory severity jumps to CRITICAL with a suggested action of ESCALATE.

## What this opens up

Three ChannelProtocol implementations now cover the main governed-interaction patterns: proposals (negotiation), deliberation (conversation), and approval gates (judgment). Channels can compose multiple protocols — a review channel might run both `ConversationProtocol` (for the discussion phase) and `JudgmentProtocol` (for the final verdict). The advisories accumulate, each protocol evaluating its own concern independently.

The `policyOverrides` mechanism makes these protocols configurable per-channel without code changes. A stringent review board gets `judgment.agreement=unanimous` and `judgment.min-reviewers=3`. A casual feedback channel gets `conversation.turn-policy=free-form` and no convergence threshold.
