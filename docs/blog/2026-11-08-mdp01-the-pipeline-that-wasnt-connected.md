---
layout: post
title: "The Pipeline That Wasn't Connected"
date: 2026-11-08
entry_type: article
subtype: diary
projects: [casehubio/agency]
tags: [swarm, multi-agent, bayesian, coordination, architecture, belief-revision, code-review]
series: issue-364-swarm-coordination
---

# The Pipeline That Wasn't Connected

The previous entry designed a tiered coordination protocol for multi-swarm systems. Fourteen tasks, three batches, a clean engine/agency boundary. The design was reviewed, the spec survived three rounds of adversarial scrutiny, and the implementation plan had every task sequenced with exact file paths and constructor signatures.

Then I built it and ran a comprehensive code review. The review found twelve issues. One was critical.

## The gap nobody caught in design

The multi-swarm topology creates three layers of coordination machinery. L0 publishes signals to a `CoordinationBus`. L1 scores their relevance using Bayesian junction tree inference. L2 detects contradictions across swarm conclusions using AGM belief revision. Each layer was implemented with tests, committed, and verified in isolation.

But nobody subscribed to the bus.

<img src="images/multi-swarm-review-gap.svg" alt="Before and after review: disconnected components vs. wired pipeline" width="760"/>

`MultiSwarmTopology` created a `RelevanceScorer` per swarm on registration. It created an `EscalationGate` per swarm. It stored them in concurrent maps. It exposed accessor methods. It published signals to the bus when swarm events arrived. But it never called `bus.subscribe()` to actually process those signals through the scoring and belief layers. The `beliefSet` field was `volatile` and initialised to empty — and stayed empty forever, because no code path ever expanded it.

Three tiers of coordination intelligence, individually tested and passing, collectively inert.

## Why the design didn't catch it

The design spec was correct. It described the signal flow, the scoring path, the escalation gate, the belief revision cycle. The implementation plan had exact steps for each component. The gap wasn't in the design — it was in the transition from "here are the components" to "here is the composition." The plan specified building each piece. It didn't specify the subscriber registration that connects them.

This is a pattern I've seen repeatedly. Components with clear interfaces and solid unit tests can all pass independently while the system does nothing. The tests verify that each piece works in isolation — `RelevanceScorer` scores correctly, `BeliefSet` revises correctly, `CoordinationBus` delivers to subscribers. But the integration test that should verify "when swarm A publishes a finding, swarm B's belief set eventually contains it" was testing the components, not the flow.

## The actual wiring

The fix was ten lines in the constructor:

```java
MultiSwarmTopology(...) {
    // ... field assignment ...
    bus.subscribe(s -> true, this::processSignal);
}
```

And a `processSignal` method that routes by signal type:

```java
private void processSignal(MultiSwarmSignal signal) {
    switch (signal) {
        case MultiSwarmSignal.Finding finding -> processL1Finding(finding);
        case MultiSwarmSignal.Progress progress -> processL1Progress(progress);
        // ...
    }
}
```

`processL1Finding` iterates other swarms' scorers, computes capability overlap as Jaccard similarity, evaluates relevance via the BJT, and on positive relevance, feeds the finding into `BeliefSet.revise()` with the `DomainConsistencyChecker`. If revision detects a contradiction, it produces a `CrossSwarmNotification` — the output type that existed but was never constructed.

<img src="images/multi-swarm-signal-pipeline.svg" alt="The wired L0 → L1 → L2 signal pipeline" width="800"/>

The convergence evaluator — an SPI I'd added to engine-api, implemented in runtime-core, and stored a threshold for in the topology — also had zero call sites. The fix wired it into progress signal processing: when multiple swarms report progress, the evaluator assesses cross-swarm convergence.

## What else the review caught

The pipeline gap was the critical finding. The other eleven ranged from concurrency bugs to orphaned types:

The `beliefSet` field was `volatile` with a read-modify-write loop in `onDeregistered()` — two concurrent deregistrations would lose contractions. Fixed with `AtomicReference.updateAndGet()`.

The `CoordinationBus` created a virtual thread executor that was never shut down. No `close()` method, no `AutoCloseable`. A topology that gets discarded when a case closes would leak the executor. Both `CoordinationBus` and `MultiSwarmTopology` now implement `AutoCloseable`.

The ring buffer's `writeIndex` was an `int`. After two billion publishes, `writeIndex % capacity` wraps negative. Changed to `long`.

`CrossSwarmOutcomeDecorator` was using `String.valueOf(confidence)` — the numeric confidence score — as the `conclusion` field of a `Finding`. The conclusion should describe what was found, not echo a number.

`InMemoryMultiSwarmRegistry.updateInterests()` fired listener notifications even when the swarm didn't exist — `computeIfPresent` silently returns null, but the notification was outside the conditional.

`SwarmCoordinationEvent` records in engine-api didn't defensively copy their `List` fields — a pattern the agency-side `MultiSwarmSignal` records handled correctly. Added compact constructors with `List.copyOf()`.

## The Bayesian layer in practice

The relevance network is a five-variable junction tree: capability overlap, domain match, signal strength, historical relevance, and the target relevance node. The CPT has 54 rows — 3 × 3 × 3 × 2 parent combinations. The design spec had a 72-row CPT. That's 3 × 3 × 3 × 2 × **something** — an extra factor that didn't correspond to any parent variable. The implementation caught it as a hard error: `Variable 'Relevant': CPT has 72 rows but expected 54`.

This is a concrete advantage of the neocortex BJT engine over hand-rolled probability logic. The `BayesNetworkBuilder` validates the CPT dimensions at construction. You can't silently wire a malformed network. The design had a plausible-looking table that would have produced garbage posteriors, and the builder caught it before any inference ran.

`OnlineParameterUpdater` turned out to have a property I relied on without initially being sure of: `getInstance()` creates a new `BayesInstance` per call. This matters because the scorer needs to be thread-safe — multiple virtual threads processing bus signals concurrently. If `getInstance()` returned a shared instance, concurrent `setLikelyhood()` + `globalUpdate()` calls would corrupt the junction tree state. Because it returns a fresh instance, concurrent scoring is inherently safe. The learning path through `update()` is `synchronized` on the updater itself — which is correct, since parameter updates need sequential consistency.

## What this means for consumers

A consumer using multi-swarm coordination gets a single composition root:

```java
var topology = MultiSwarmTopology.builder()
        .registry(registry)
        .relevanceNetwork(RelevanceNetworks.standard())
        .escalationNetwork(EscalationNetworks.standard())
        .consistencyChecker(new DomainConsistencyChecker())
        .convergenceEvaluator(progress -> /* evaluate */)
        .build();
```

The builder registers the topology as a `RegistryListener`. When swarms register, they get per-swarm BJT scorers and escalation gates. When swarm events arrive via `onSwarmEvent()`, they become `MultiSwarmSignal` instances on the bus, flow through L1 relevance scoring, and feed L2 belief revision. Contradictions surface as `CrossSwarmNotification` records. The consumer drains notifications and decides what to do with them — the topology detects conflicts but doesn't resolve them.

The YAML surface maps directly:

```yaml
topology:
  pattern: multi-swarm
  authority: coordinator
  swarms:
    analysis:
      pattern: swarm
    remediation:
      pattern: swarm
  coordination:
    relevanceThreshold: 0.7
    escalationThreshold: 0.8
    convergenceThreshold: 0.85
```

L3 — LLM-mediated conflict resolution — is designed for but not yet implemented. The `agentProvider()` slot on the builder is where it'll connect. For now, mechanical L0–L2 handles the coordination without any LLM cost.

## The lesson that keeps repeating

Integration testing is not the same as component testing. Every individual piece of the multi-swarm pipeline had tests that verified its behaviour. Every test passed. The pipeline as a whole did nothing, because the composition step — the one line that subscribes to the bus — was missed in a fourteen-task implementation plan.

Specifications describe components and their contracts. Implementation plans sequence the construction of those components. Neither naturally captures the wiring step that connects them into a system. That step lives in the gap between "what exists" and "what runs" — and it's the step most likely to be dropped when each component's test suite is green.

The code review caught it. The fix was trivial. The confidence that the system worked — based on forty passing tests — was wrong. Swarms have been coordinating without central intelligence since before computers existed. The interesting question isn't whether the components are correct — it's whether they're connected.
