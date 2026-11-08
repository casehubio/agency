---
layout: post
title: "When Theory Meets Code: The Topological Sort You Don't Expect"
date: 2026-10-25
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [stigmergy, llm, implementation, composition, concurrency]
series: issue-284-llm-observation-local-rules
---

# When Theory Meets Code: The Topological Sort You Don't Expect

The [design session](2026-10-25-mdp01-the-llm-doesnt-belong-in-the-tick-loop.md) produced ten decisions and a composition model. This is what happened when we built it.

## The composition held

The design's central bet was that the LLM layer needed zero new framework — just configuration of existing summarisation primitives. After implementing all four batches, the bet paid off. `ContentSummariser<E, LlmObservationResult>` is the composition seam. The builder produces one, `asSummariser()` bridges it to the runner, the runner publishes to the bus. Every piece existed before this work started.

What I didn't anticipate: how naturally `EmissionPolicy` composition would extend. The design called for change-gated invocation — only call the LLM when watched keys actually changed. I expected this to be the fiddly part. It wasn't. `ChangeGatedEmissionPolicy` implements `EmissionPolicy<E, Object>`, delegates to a wrapped policy for the "enough events accumulated" check, and adds a prefix-matching scan of changed keys across the buffer. The two concerns compose via `allOf()` — both the change gate and the window must agree before emission happens.

The prefix matching was a small design choice worth recording. Watched keys can be exact (`risk.score`) or prefixed (`risk.*`). The `*` suffix strips to a `startsWith` check. It's deliberately limited — no regex, no glob patterns beyond trailing wildcard. The engine's own `EnvironmentObserver.watchedKeys()` uses the same convention. Consistency with the engine's idiom matters more than generality.

## The concurrency guard

The `InFlightGate` pattern turned out cleaner than I expected. A `volatile CompletionStage<?>` field, set on invocation, cleared on completion. The gating policy checks `isDone()` — if the previous call hasn't finished, emission is suppressed and events stay in the buffer.

The subtlety from the design — gating at `EmissionPolicy` rather than wrapping the summariser — worked exactly as predicted. The `SummarisationRunner.tickWithPolicy()` calls `shouldEmit()` before draining. If the gate says no, the drain never happens. Events accumulate until the in-flight call completes, then the next tick processes a larger batch. The LLM gets richer context for the cost of one extra tick delay.

There's a race condition in the `whenComplete` callback — `if (inflight == result) inflight = null` isn't atomic. But `SummarisationRunner.tick()` is `synchronized`, so the summariser never runs concurrently. And even outside that guarantee, the worst case is a stale reference that suppresses one extra tick, self-correcting on the next `isDone()` check. I left it as-is rather than adding `AtomicReference` overhead for a non-problem.

## The topological sort

The `ObservationDispatcher` needed Kahn's algorithm, which I hadn't expected when writing the design. The LLM produces observations with `derivedFrom` edges — "compound-risk derives from risk-spike." Handlers for derived observations may depend on side effects from their parent handlers. Dispatching in batch order would process `compound-risk` before `risk-spike` if the LLM happened to return them in that sequence.

The implementation builds an adjacency map from `derivedFrom` edges, computes in-degrees, and walks from zero-in-degree nodes outward. Cycles get logged at SEVERE and fall back to batch order — the LLM shouldn't produce cycles, but if it does, dropping observations silently would be worse than processing them in an arbitrary order.

The handler precedence was simpler: `on(patternId)` beats `onDerived()` beats `onUnhandled()`. First-match, no ambiguity. Handler exceptions are caught per-observation and logged — one broken handler doesn't abort the batch.

## What composition gives you for free

The part that still surprises me is what we didn't have to build. The `SummarisationRunner` already manages state per partition via `StatefulSummariser` detection. The `EventStreamBus` already handles subscriber dispatch. The `ContentSummariser.asSummariser()` bridge already adapts the simpler two-arg interface to the three-arg stateful one. The `InvocationMetadata` capture was already in `StructuredAgentInvoker`.

The new code is records, an emission policy, a concurrency guard, a converter, a dispatcher, and three template factories. Everything beneath those is existing infrastructure running a path it was already designed for but hadn't been asked to take yet.
