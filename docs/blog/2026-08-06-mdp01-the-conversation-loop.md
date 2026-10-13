---
title: "The Conversation Loop"
date: 2026-08-06
entry_type: note
subtype: diary
author: mdp
projects: [casehub-blocks]
tags: [conversation, orchestration, composition, design]
status: draft
---

# The Conversation Loop

Blocks has had all the pieces for multi-agent conversation for weeks — `ConversationProjection` folds state, `PartitionedObservationService` manages per-agent context windows, `TerminationCondition` knows when to stop, `AgentInvoker` calls LLMs. What it didn't have was the thing that wires them together into a loop that drives itself.

That's what `ConversationOrchestrator` is. Not a new framework — a composition.

## The design tension

The existing agentic execution driver (`ChoreographedDriver`) runs a five-phase loop: route, activate, dispatch, aggregate, terminate. It passes the same shared context to every agent in an iteration. Conversation needs something different — each agent gets its own rendered context from its observation partition, and responses feed back as new events that trigger more agent invocations.

I considered three relationships to the existing driver: subclass it, adapt it, or build a peer. Subclassing would mean overriding everything except `invokeAgent` — inheritance without reuse. Adapters would wrap the conversation semantics in routing/aggregation interfaces they don't fit. A peer composition root that shares the same foundation types (`AgentRef`, `AgentInvoker`, `TerminationCondition`) but has its own execution model is the cleanest cut.

## Turn-taking is not routing

The most interesting design call was whether `TurnPolicy` should be a `RoutingStrategy`. Routing answers "which candidate best fits this task?" — a decision problem with failure modes (`Unresolvable`). Turn-taking answers "per the protocol, whose turn is it?" — deterministic, never fails, silence is a valid outcome.

We could have shoehorned turn policies into routing strategies. Every parameter would have worked. But every parameter would also have been wrong — unused candidates, impossible failure modes, scoring metadata with no scores. A well-named interface that says exactly what it does is worth more than a forced unification.

## What composes

The orchestrator's constructor takes nine dependencies and produces one method: `converse(MessageView) → Uni<ConversationOutcome>`. Internally it runs a queue — the triggering message goes in, turn policy determines who responds, each response feeds back into the queue. Termination is checked after every dispatch, not at round boundaries.

The `TerminationCondition<T>` reuse worked exactly as predicted. The generic `T` parameter means conversation-specific conditions (`AllAgreedTermination`, `ContestedEscalation`) just read from `ConversationState` via `context.state()`. `MaxIterationsTermination` still works as a safety valve counting total dispatches. `ConvergenceTermination` was already doing this pattern — we just followed it.

`PartitionedObservationService` is the primary context primitive, not `SummarisationRunner`. The orchestrator controls drain timing based on the turn policy — it's turn-driven, not time-driven. Each agent gets a `PartitionedDrain` with its current partition plus remembered context from prior zones. Temporal compaction via `SummarisationRunner` is available but optional — consumers wire it externally for long conversations.

## What consumers provide

The design review pushed hard on this and it's the part I'm most satisfied with. A consumer (say, drafthouse for debate channels) provides:
- A `ConversationProjection` subclass with its vocabulary (RAISE, AGREE, COUNTER)
- A `PromptAssembler` that injects domain context (document content, selection scope)
- A `ResponseMessageBuilder` for domain-specific message formatting
- Agent configuration (system prompts per role)

They stop writing turn-taking logic, termination checks, and context assembly in application code. Those are blocks infrastructure now.

## Four turn policies, four termination conditions

`RoundRobinTurnPolicy` alternates. `AddressedTurnPolicy` responds when targeted. `PointAddressedTurnPolicy` responds to unresolved points. `FreeTurnPolicy` lets everyone speak. All stateless — pure functions of `ConversationState`.

`AllAgreedTermination` watches for consensus. `SupervisorTermination` listens for a moderator signal. `ContestedEscalation` escalates stuck disputes to humans. `CompositeTermination` chains them — first non-Continue wins.

The composition pattern is the same one used throughout the agentic framework. Nothing new to learn, just new things to compose.
