---
layout: post
title: "The Hybrid That Isn't About Catching Up"
date: 2026-07-14
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [decomposition, htn, hybrid, langchain4j]
---

CaseHub's decomposition infrastructure already has both halves — `StaticDecomposition` for guard-based method selection, `LlmDecomposition` for when the LLM needs to reason about which agents to deploy. What it didn't have was the composition: try static first, fall back to LLM when no guard matches.

The ChatHTN paper calls this interleaving. langchain4j's open PR (#5584, Mario Fusco) calls it the HTN pattern. Both implement it imperatively — the planner walks the task tree one step at a time, returning the next agent to call. CaseHub does something different: `DecompositionStrategy` returns a complete `ExecutionPlan<T>` — an immutable DAG with dependency edges and join semantics. The plan is data you can inspect, audit, version, and parallelise. The planner never touches execution.

That separation matters more than the hybrid itself. ChatHTN bolts SHOP-style effects onto its symbolic planner so downstream guards can reason about projected future state — because a symbolic planner can't reason. It needs `Consumer<AgenticScope>` applied during traversal to simulate what upstream tasks will produce. CaseHub doesn't need that mechanism. The LLM already reasons about cause and effect naturally. When `LlmDecomposition` receives the current state and available agents, it projects forward without being told to.

The implementation turned out to be small. `HybridDecomposition<T>` wraps two `DecompositionStrategy<T>` instances — primary and fallback. The primary runs; if it throws `NoMethodMatchedException` (a new typed exception replacing the raw `IllegalStateException`), the fallback fires. Everything else propagates unchanged.

The adversarial design review caught something I'd missed: `HtnBuilder.flatten()` reimplemented guard matching inline and never delegated to `this.decomposition`. Any strategy set via `.decompose()` was dead code. The fix was straightforward — delegate to the configured strategy — but without the review it would have shipped broken and nobody would have noticed until someone tried to use `HybridDecomposition` through the builder API.

The review also pushed the full-control constructor from concrete types (`StaticDecomposition`, `LlmDecomposition`) to interface types (`DecompositionStrategy<T>`, `DecompositionStrategy<T>`). The fields are now `primaryStrategy` and `fallbackStrategy` — role-named, not type-named. Any strategy can compose with any other.

Three follow-on issues came out of the langchain4j comparison: recursive LLM decomposition (#54), SHOP-style forward reasoning (#56), and a task tree construction DSL (#57). None blocks the hybrid. The recursive decomposition is the most interesting — letting the LLM return `CompoundTask` nodes for further decomposition, creating multi-level plans autonomously. That's a natural extension of the type system; the sealed interface already permits it.
