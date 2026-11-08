## D1: Naming conflict — existing NegotiationProtocol constants class

**Choice:** Rename existing `NegotiationProtocol` (constants-only utility) to `NegotiationConstants`, freeing the name for the `ChannelProtocol` implementation.
**Alternatives:**
- Name the new class `NegotiationChannelProtocol` — avoids migration but longer name
- Fold constants into `NegotiationOutcome` enum — tighter but changes the public API of the enum
**Rationale:** `NegotiationProtocol` is the natural name for a `ChannelProtocol` implementation. The constants class is a low-traffic utility with no domain weight.
**Trade-offs:** Requires a rename in any consumer referencing `NegotiationProtocol.OUTCOME_*` constants.
**Sources:** `agency-core/src/main/java/io/casehub/agency/deliberation/negotiation/NegotiationProtocol.java`
**Exploration:** quick
**Status:** captured

## D2: State source — how NegotiationProtocol gets negotiation state

**Choice:** Incremental projection with cached `ProjectionResult` per channel via `ChannelProjector`, with a toggle to fall back to full-scan projection.
**Alternatives:**
- Full-scan projection every time — correct but wasteful for high-traffic channels
- Fold from `ProtocolContext.recentMessages()` only — simpler but could miss early proposals outside lookback window
**Rationale:** Incremental projection is O(new messages) after the initial scan. Cache per channel in `ConcurrentHashMap<UUID, ProjectionResult<NegotiationState>>`. Full-scan fallback available by disabling caching. `ChannelProjector` already supports both modes.
**Trade-offs:** Caching adds memory footprint proportional to active negotiation channels. Stale cache risk if messages are deleted (unlikely in qhorus). Cache eviction on terminal state mitigates unbounded growth.
**Sources:** `io.casehub.qhorus.api.channel.ChannelProjector` (full-scan and incremental overloads), `NegotiationProjection` (existing fold implementation)
**Exploration:** quick
**Status:** captured

## D3: Per-channel configuration — how the protocol reads policyOverrides

**Choice:** Add `Map<String, String> policyOverrides` to `ProtocolContext` as an upstream qhorus change. `MessageService` already has the `Channel` record when constructing the context — one-line addition.
**Alternatives:**
- Inject `ChannelManager` to read `Channel` by ID — extra DB read per dispatch when the caller already had the data
- Default config only, no per-channel overrides — doesn't match the issue spec
**Rationale:** The `Channel` record is already in scope at `ProtocolContext` construction time. Passing it through avoids redundant lookups and benefits all future protocol implementations.
**Trade-offs:** Requires a qhorus PR (casehubio/qhorus API change). Minor — additive change, backward compatible (nullable field with empty default).
**Sources:** `io.casehub.qhorus.api.spi.ProtocolContext`, `MessageService.java:346-362` (construction site), `Channel.policyOverrides`
**Exploration:** quick
**Status:** captured

## D4: Module placement

**Choice:** New package `io.casehub.agency.deliberation.negotiation.protocol` in `agency-core`.
**Alternatives:**
- Separate Maven module (`agency-negotiation-protocol`) — cleaner dependency isolation but more modules to manage
- In the `agency` (test-sibling) module — wrong place for production code
**Rationale:** `agency-core` already has `@ApplicationScoped` CDI beans and depends on qhorus-api. The protocol types are tightly coupled to the existing negotiation package. No new dependencies introduced.
**Trade-offs:** None significant — `agency-core` already carries the qhorus-api dependency.
**Sources:** Existing CDI beans in agency-core: `ThreadSummaryObserver`, `ChannelSummariser`
**Exploration:** quick
**Status:** captured

## D5: Rules design — configurable via builder with defaults

**Choice:** All rules exist in `NegotiationRules`, configurable via builder with sensible defaults. Core structural rules (non-participant, orphan accept/reject, terminal state violation) are always on. Policy rules (max rounds, deadline, turn-order) are toggleable with defaults. Per-channel `policyOverrides` can override builder defaults at evaluation time.
**Alternatives:**
- Fixed core-only rule set — simpler but no extensibility
- Fixed all-rules-on — no customisation per deployment or channel
**Rationale:** Consistent with the incremental caching design (D2) — full capability with opt-in simplification. Builder pattern lets consumers tune enforcement without subclassing. policyOverrides provides per-channel runtime tuning on top.
**Trade-offs:** Builder adds a small amount of API surface. Defaults must be well-chosen since most consumers won't override.
**Sources:** Issue spec policyOverrides table, existing `AcceptancePolicy` implementations, negotiation termination conditions
**Exploration:** quick
**Status:** captured
