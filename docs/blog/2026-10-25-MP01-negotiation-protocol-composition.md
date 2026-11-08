---
layout: post
title: "NegotiationProtocol — Agency Patterns as Channel Protocols"
date: 2026-10-25
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [agency, qhorus, composition, negotiation, protocol]
series: issue-354-negotiation-protocol
---

# NegotiationProtocol — Agency Patterns as Channel Protocols

The composition architecture in CaseHub has always been designed around one core bet: agency patterns should implement qhorus SPIs directly. No bridge modules, no adapter layers, no wiring harness. If you put agency on the classpath, its negotiation protocol just appears in the protocol registry, ready to enforce.

Until now that was a design document. `NegotiationProtocol` makes it real.

The idea is straightforward. Qhorus already has a `ChannelProtocol` SPI — the existing built-in protocols (`REQUEST_RESPONSE`, `ROUND_ROBIN`, etc.) evaluate every message dispatch and return `DispatchAdvisory` results. The channel's `EnforcementMode` decides what happens: log the advisory, block the message, or quarantine it. Agency's negotiation types — `NegotiationProjection`, `NegotiationFold`, `AcceptancePolicy` — already exist and are well-tested. The missing piece was connecting them.

The connection required one upstream change: `ProtocolContext` didn't carry `policyOverrides`. The protocol evaluator already had the `Channel` record when constructing the context — adding the field was a one-line change. But without it, per-channel configuration (acceptance policy, max rounds, deadline) would have required a redundant channel store lookup on every dispatch.

`NegotiationRules` is the interesting part. It's a pure function — `(NegotiationState, ProtocolContext) → List<DispatchAdvisory>` — with a builder for compile-time defaults and a `withOverrides(Map<String, String>)` method for per-channel runtime tuning. Structural rules (terminal state violations, non-participant messages, orphan accepts) are always on. Policy rules (max rounds, deadline, turn order) are toggleable. The per-channel override keys map directly to the topology YAML that ops will use:

```yaml
channels:
  design-review:
    protocols: [NEGOTIATION]
    policy-overrides:
      negotiation.acceptance: majority
      negotiation.max-rounds: 5
```

The `NegotiationProtocol` itself is a thin `@ApplicationScoped` CDI bean. It injects `ChannelProjector`, projects negotiation state via the existing `NegotiationProjection`, and delegates to `NegotiationRules` for evaluation. The projection uses incremental caching — full-scan on first call, incremental from cursor on subsequent calls to the same channel. A toggle disables caching for consumers who prefer the simpler model. Cache eviction happens automatically on terminal negotiation state.

What this proves is broader than negotiation. The same pattern — agency SPI implementation, CDI-discovered, per-channel configurable — applies directly to `ConversationProtocol` and `JudgmentProtocol`. The ops topology YAML surface just needs to know the protocol name and which override keys it accepts. No per-pattern plumbing.
