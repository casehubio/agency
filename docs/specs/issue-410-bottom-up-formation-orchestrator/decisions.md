# Decisions — #410 Bottom-Up Formation Orchestrator

## D1: Orchestrator sends initial PROPOSE message

**Choice:** Orchestrator creates the negotiation channel AND sends the initial PROPOSE message via MessageDispatcher on behalf of the proposing agent. The PROPOSE message's `sender` field uses `proposer.name()` (from D2's `proposer(AgentRef)`) to match the NegotiationProjection's party set.
**Alternatives:**
- Caller sends separately — more flexible but leaks negotiation mechanics to consumers
**Rationale:** `propose()` should be a single call that starts the full formation flow. Consumers shouldn't need to understand negotiation channel internals. The sender identity must match the NegotiationProjection's party set — `AgentRef.name()` is the identity contract per `ExecutorRef`.
**Trade-offs:** Less flexibility if a consumer wants custom proposal message content. Acceptable — the coalition proposal carries the task description. Constrains proposers to AgentRef variants with stable, meaningful `name()` values (WorkerAgent, ChannelAgent). Edge-case variants like unlabelled ExternalAgent return "external" — such variants should not be used as proposers.
**Sources:** Engine spec §3.3, agency hardening spec §7, NegotiationProjection.doApply(), ExecutorRef.java
**Exploration:** quick
**Status:** revised

## D2: Explicit proposer field on CoalitionProposal

**Choice:** Add `proposer(AgentRef)` field to `CoalitionProposal` record.
**Alternatives:**
- Convention: proposedMembers[0] is the proposer — implicit contract, easy to violate
**Rationale:** Self-documenting, no ordering dependency, clear who initiated the formation.
**Trade-offs:** Minor breaking change to the record constructor. No current consumers outside tests.
**Sources:** CoalitionProposal.java, CoalitionFormationTest.java
**Exploration:** quick
**Status:** captured

## D3: Orchestrator pauses channel after terminal outcome

**Choice:** After terminal outcome (AGREED/DEADLOCKED/WITHDRAWN/timeout), orchestrator cancels the ChannelObserver subscription first, then calls `ChannelManager.pause()` to prevent new messages while preserving the channel and message history for audit.
**Alternatives:**
- `ChannelManager.delete(channelId, force)` — destructive, removes channel and message history needed for audit trails and post-negotiation analysis
- No cleanup, rely on TTL — leaves orphaned channels that continue accepting messages
- Add new `close()` semantic to ChannelManager — unnecessary; `pause()` already provides the right semantics (prevent new messages, preserve state)
**Rationale:** Deterministic cleanup. The orchestrator owns the channel lifecycle. `pause()` preserves message history for audit while preventing post-negotiation messages. Subscription cancellation before `pause()` avoids races with in-transit messages triggering callbacks on a paused channel.
**Trade-offs:** Channel remains in a paused state indefinitely. May need a separate TTL-based deletion for long-term hygiene. Acceptable — audit retention outweighs storage concerns.
**Sources:** ChannelManager API (qhorus-api: `pause(UUID)`, `delete(UUID, boolean)`, no `close()`), ChannelConfig.java
**Exploration:** quick
**Status:** revised

## D4: Plain class architecture with async observation bridge

**Choice:** Plain class (not CDI-managed), constructor-injected dependencies, `Uni<FormationResult>` return type. Uses ChannelObserver subscription (D5) to bridge the async negotiation wait to a Uni emitter. Cleanup on all exit paths (terminal outcome, timeout, and Uni cancellation) via `emitter.onTermination()`.
**Alternatives:**
- ChoreographedDriver — event-driven via EventSource. Provides multi-iteration execution loop with routing, activation, aggregation, and termination SPIs. Overkill: the formation flow has one async wait point, not a multi-iteration agent execution loop.
- `CompletionStage<FormationResult>` — works but diverges from ConversationOrchestrator's Uni convention.
**Rationale:** The formation flow is mostly sequential (evaluate → create channel → send PROPOSE → **wait** → handle outcome → create group) with one genuinely async step: waiting for negotiation responses from autonomous agents. This does not require the full execution driver machinery. A thin async bridge (`ChannelObserver.subscribe() → Uni.createFrom().emitter()`) handles the wait cleanly. `Uni` provides consistency with ConversationOrchestrator and Mutiny-native timeout support (D7). The `emitter.onTermination()` callback ensures D3's cleanup (cancel observer subscription, pause channel) runs on all exit paths — including Uni cancellation from a higher-level caller timeout or application shutdown — not only on negotiation terminal outcomes.
**Trade-offs:** Not CDI-discoverable — consumers construct explicitly. Does not leverage ChoreographedDriver's multi-iteration loop or termination SPI — these are not needed for a single-proposal flow.
**Sources:** ConversationOrchestrator.java (`Uni<ConversationOutcome>`), ChannelObserver.java, ChoreographedDriver.java
**Exploration:** quick
**Status:** revised

## D5: Negotiation observation via ChannelObserver\<NegotiationState\>

**Choice:** Use `ChannelObserver<NegotiationState>` wrapping `NegotiationProjection` to observe negotiation completion. The observer projects state atomically via `AtomicReference`, implements `MessageObserver` for channel-scoped message filtering, and implements `EventSource` for push-based notification. The orchestrator subscribes via `observer.subscribe()` and checks `observer.currentState().outcome().isTerminal()` on each event. Registration with the qhorus message dispatch infrastructure is via a constructor-injected `MessageObserverRegistrar` functional interface:
```java
@FunctionalInterface
interface MessageObserverRegistrar {
    EventSource.Cancellation register(MessageObserver observer);
}
```
The orchestrator calls `registrar.register(observer)` during `propose()`, receiving a `Cancellation` for lifecycle management. The CDI wiring layer (in the consuming application) provides the implementation that plugs the observer into `MessageObserverDispatcher`'s discovery mechanism.
**Alternatives:**
- Direct ChannelProjection polling — pull-based, no existing polling support in the platform (channels are push-oriented)
- NegotiationProtocol (ChannelProtocol) — evaluates on message dispatch, returns `List<DispatchAdvisory>` to the dispatcher, not callbacks to external consumers
- Custom MessageObserver — reinvents ChannelObserver without the projection management, EventSource integration, or AtomicReference safety
- Self-registration via CDI `Instance<MessageObserver>` — impossible; the observer is dynamically created at runtime by a non-CDI class, not CDI-discoverable
**Rationale:** ChannelObserver is purpose-built for exactly this pattern. It wraps a projection, maintains state atomically, implements MessageObserver for channel filtering, and pushes events to subscribers. The NegotiationProjection's party set is constructed from the coalition proposal members' `AgentRef.name()` values, ensuring identity consistency with D1's sender contract. The `MessageObserverRegistrar` follows the same constructor-injection pattern as the rest of D4's design — the orchestrator doesn't know HOW observers are registered, only that registration returns a `Cancellation`. The registration `Cancellation` is a separate concern from the `ChannelObserver.subscribe()` cancellation — both must be cancelled during cleanup (D3).
**Trade-offs:** ChannelObserver supports only one subscriber (throws `IllegalStateException` on duplicate subscription). The formation orchestrator is the sole consumer — correct for this use case. The `MessageObserverRegistrar` adds one more constructor parameter. The registrar interface lives in `agency-core` (alongside ChannelObserver) but its implementation lives in the CDI wiring layer.
**Sources:** ChannelObserver.java, NegotiationProjection.java, ChannelObserverIntegrationTest.java, EventSource.java, MessageObserverDispatcher (qhorus — CDI `Instance<MessageObserver>` discovery)
**Exploration:** quick
**Status:** revised

## D6: Type bridge — CoalitionProposal to GroupLifecycleService

**Choice:** The orchestrator bridges agency-layer types to eidos-layer operations:
- `CoalitionProposal.proposer().name()` → `GroupLifecycleService.proposeGroup(specId, tenancyId, proposerAgentId)`
- `specId` is a constructor-injected parameter (the group spec defining the formation target), not derived from `CoalitionProposal.taskId()`
- `tenancyId` is a constructor-injected parameter (the tenancy context), not carried on CoalitionProposal
- `proposedMembers` are mapped via `AgentRef.name()` for the NegotiationProjection party set
- `FormationOrigin.PROPOSAL` is the correct origin for bottom-up formation
**Alternatives:**
- Derive specId from taskId — conflates task identity (what work to do) with group spec identity (what organisational structure to form)
- Carry tenancyId on CoalitionProposal — leaks tenancy concern into the coalition domain, which should be tenancy-agnostic
- Use `FormationOrigin.EMERGENCE` — semantically wrong; emergence implies spontaneous formation without an explicit proposal
**Rationale:** The orchestrator sits at the boundary between agency (coalition) and eidos (organisational structure). Cross-layer parameters (specId, tenancyId) belong in the orchestrator's constructor, not in the coalition domain type. The coalition domain should not be aware of eidos concepts.
**Trade-offs:** The orchestrator's constructor has more parameters (specId, tenancyId in addition to its service dependencies). This makes the cross-layer boundary explicit rather than hidden.
**Sources:** CoalitionProposal.java, GroupLifecycleService.java, FormationProposal.java, FormationOrigin.java (TOP_DOWN, PROPOSAL, EMERGENCE)
**Exploration:** quick
**Status:** captured

## D7: Timeout via Uni-native mechanism

**Choice:** Use Uni's `ifNoItem().after(timeout).recoverWithItem(FormationResult.timeout())` for negotiation timeout. The timeout duration is a constructor-injected parameter.
**Alternatives:**
- `CompletableFuture.orTimeout()` — JDK native, but conflates timeout with a generic `TimeoutException`. Inconsistent with D4's Uni return type.
- `ScheduledExecutorService` — introduces a new executor dependency for a single timer
- `NegotiationCompositeTermination` — operates within the execution driver framework (requires `TerminationContext`), which D4 does not use
**Rationale:** Mutiny's timeout is composable with the Uni pipeline and produces a specific result type (`FormationResult.timeout()`) rather than a generic exception. No additional infrastructure required. The timeout is at the orchestrator level, independent of the negotiation protocol's own termination conditions.
**Trade-offs:** A negotiation could reach terminal state simultaneously with the timeout firing — the orchestrator must check observer state before emitting a timeout result. This is a standard last-check-wins race that Uni's thread-safety handles.
**Sources:** ConversationOrchestrator.java (Uni pattern), NegotiationCompositeTermination.java, NegotiationOutcome.java
**Exploration:** quick
**Status:** captured

## D8: Package and module location — deliberation.coalition.formation in agency-core

**Choice:** Package `io.casehub.agency.deliberation.coalition.formation` in the `agency-core` module. Add `casehub-eidos-org-api` as a `provided`-scope dependency to `agency-core/pom.xml`.
**Alternatives:**
- `deliberation.coalition.orchestration` — mirrors `conversation.orchestration` but "formation" is more descriptive of the domain operation
- `deliberation.formation` — too broad, loses the coalition context (formation could also be top-down, which doesn't go through coalition evaluation)
- `deliberation.coalition` (flat) — pollutes the coalition package with formation-specific types (FormationResult, FormationOrchestrator)
- Place in `agency` module (CDI layer) — wrong: the orchestrator is non-CDI (D4). Placing a plain POJO class in the CDI module because of a dependency concern inverts the layering.
- New `agency-formation` module — premature: one orchestrator class doesn't justify a new module
**Rationale:** Formation is a specific operation within the coalition domain. The sub-package mirrors `deliberation.conversation.orchestration` but uses the domain-specific term. `agency-core` currently depends on `casehub-eidos-api` (agent descriptors, capabilities) but NOT on `casehub-eidos-org-api` (organisational structure: formations, groups). Adding `eidos-org-api` at `provided` scope is a new dependency — it introduces organisational model types to agency-core. This is architecturally appropriate because: (a) the coalition domain inherently touches organisational structure (forming groups IS organising agents), (b) `eidos-org-api` is pure POJOs with no framework dependency, consistent with agency-core's "zero CDI, zero Spring" principle, (c) `provided` scope ensures downstream consumers don't transitively inherit the dependency — the same pattern used for `casehub-platform-api`, `casehub-engine-ledger`, and `casehub-neocortex-*` in agency-core's pom.xml.
**Trade-offs:** Deeper package nesting. The `eidos-org-api` dependency is new and widens agency-core's domain surface. Acceptable: the `provided` scope limits the blast radius, and the coalition-to-organisation bridge is an inherent concern of bottom-up formation.
**Sources:** agency-core/pom.xml (current dependencies), deliberation.conversation.orchestration (ConversationOrchestrator), deliberation.coalition (CoalitionProposal, CoalitionEvaluator), agentic-yaml/pom.xml (existing `eidos-org-api` consumer in the agency project)
**Exploration:** quick
**Status:** revised

## D9: Idempotent proposal via findOrCreate

**Choice:** Use `ChannelManager.findOrCreate(ChannelCreateRequest)` instead of `create()` for the negotiation channel. The `ChannelCreateRequest` includes a deterministic channel name derived from proposal identity (e.g., `"formation:<taskId>:<proposer.name()>"`) to enable deduplication across retries.
**Alternatives:**
- Non-idempotent `create()` — duplicate channels on retry, wasting resources and causing confusion
- Caller-side deduplication — leaks channel lifecycle concern to consumers, contradicts D1's encapsulation
**Rationale:** Bottom-up formation proposals may be retried after transient failures. Creating duplicate negotiation channels for the same proposal wastes resources and causes confusion. `ChannelManager.findOrCreate()` already exists in the API and handles the "create or find existing" semantics naturally via `FindOrCreateResult`.
**Trade-offs:** Requires a deterministic channel naming convention. The naming scheme must be collision-free across independent proposals — using `taskId` + proposer name provides sufficient uniqueness within a tenancy.
**Sources:** ChannelManager.findOrCreate(ChannelCreateRequest), FindOrCreateResult
**Exploration:** quick
**Status:** captured
