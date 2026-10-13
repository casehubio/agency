---
layout: post
title: "Prompts That Learn From Experience"
date: 2026-08-02
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [prompt-optimisation, dspy, cbr, routing, decomposition]
series: issue-48-dspy-prompt-optimisation
---

CaseHub's LLM-invoking components — the routing strategy that selects which agent handles a task, and the decomposition strategy that breaks compound tasks into steps — have always used static, hardcoded prompts. The CBR infrastructure captures rich outcome data: which agent was selected, whether it succeeded, how similar the current case was to past ones. But that data only feeds numeric scoring. The prompts themselves never change.

I wanted to close that loop. DSPy (Declarative Self-improving Python) — Stanford's framework for programming rather than prompting LLMs — showed the way: typed signatures, automated prompt optimisation, modular teleprompters that compile prompts from data instead of hand-crafting them. But DSPy operates in a tight compile-time loop that doesn't map to CaseHub's reality. Our outcomes arrive hours or days after the routing decision. The system is distributed and CDI-driven. Optimisation has to coexist with live traffic without risking it.

## The Framework

The design adapts DSPy's concepts rather than porting DSPy directly. At its core: an offline batch cycle that analyses accumulated CBR outcomes and produces versioned prompt artifacts.

A `PromptSignature` declares an optimisation target — any LLM-invoking component registers one. `LlmAgentRoutingStrategy` registers `"llm-routing"`, `LlmDecomposition` registers `"llm-decomposition"`. The signature carries the base system prompt, the I/O types, and a human-readable description. It's the contract between the component and the optimisation framework: "I produce prompts with this shape — improve them for me."

Two `PromptOptimiser` implementations run in each batch cycle. `FewShotOptimiser` is pure data-driven: it filters past cases by outcome quality, ranks them by `qualityScore × similarityScore`, and selects the top N as few-shot examples. No LLM cost. `InstructionOptimiser` analyses outcome patterns — "capability X has 80% gate rejection rate" — and asks an LLM to generate refined instructions. The two are independent and compose: examples from one, instruction delta from the other, merged into a single `PromptVariant`.

A `PromptVariant` is a versioned bundle: curated examples, an optional instruction delta, a quality score, a timestamp, and a lineage pointer to the variant it evolved from. The `PromptVariantStore` holds active variants in two slots — `control` and `experiment`. At runtime, `VariantSelector` deterministically hashes `(caseId, capabilityName)` to route 10% of traffic to the experiment. Same case always gets the same slot. The few-shot examples flow into the routing prompt via `OptimisedFewShotSection` — a `RoutingPromptSection` discovered alongside `CbrRoutingPromptSection` through the existing CDI assembler. Instruction refinements flow through `SystemPromptCustomiser`, which appends the delta to the base system prompt. The strategies opt in with a one-line change.

## What This Actually Gets You

**Prompts that improve from experience.** Today, no matter how many successful or failed routing decisions accumulate, the prompts never change. A new deployment uses the same static instructions as day one. The framework makes prompts adaptive — each batch cycle incorporates what worked and what didn't.

**Causal measurement, not correlation.** The deterministic A/B split means you can compare the experiment variant's success rate against control with real statistical confidence. Deploy-and-compare conflates time effects with prompt effects — maybe outcomes improved because of something else entirely. The random split isolates the prompt's contribution.

**Zero-risk deployment.** The experiment starts at 10% traffic. A circuit breaker on `VariantSelector` trips after consecutive failures — per-capability, not global. The batch enforces a quality floor: if the experiment drops below 0.3, it's automatically killed. Promotion requires consecutive winning cycles, not a single lucky batch. And there's a master switch that makes the entire system invisible — strategies fall back to hardcoded prompts instantly.

**Composable optimisation.** The `PromptOptimiser` SPI is open. `FewShotOptimiser` and `InstructionOptimiser` are two implementations. Domain repos can add their own — a domain-specific optimiser that weights certain capabilities differently, or one that injects domain terminology into the instructions. The batch merges results from all registered optimisers, deduplicating examples and concatenating instruction deltas.

**Pluggable quality metrics.** `PromptQualityMetric` scores variant performance. The default (`WeightedOutcomeMetric`) applies the same graduated weights as `CbrOutcomeWeights` — SUCCESS at 1.0, GATE_EXPIRED at 0.5, GATE_REJECTED at 0.25, FAILURE at 0.0. Domain repos override with their own: factor in execution duration, gate rejection patterns, or domain-specific signals.

## When You Can Use It

The framework is in blocks and ready to deploy. No cross-repo changes are needed for the core functionality — the prompt optimisation types, the batch orchestrator, the store, the selector, the optimisers, the runtime sections all live in `io.casehub.blocks.prompt`.

For **manual enrichment** — curating your own examples and storing them in `InMemoryPromptVariantStore` — you can start today. The `OptimisedFewShotSection` and `VariantAwareSystemPromptCustomiser` will serve them at routing time without any A/B machinery.

For the **full automated cycle** — batch analyses outcomes, produces variants, A/B tests them, promotes winners — two cross-repo changes need to land first. `PlanTrace` in neocortex needs a `variantId` field so outcomes can be correlated with the prompt variant that produced them. `Assignment` in engine-api needs the same field so the routing strategy can tag its decisions. Both are additive nullable fields — no breaking changes, no coordinated release. The issues are filed.

Once those land, a consumer wires a scheduled job that calls `PromptOptimisationBatch.run()` with a `PromptSignature` and an `OptimisationDataset` extracted from the CBR store. The batch handles everything else: gating on minimum data volume, scoring variants, running optimisers, comparing experiment against control, promoting winners, killing losers.

The pattern applies to any LLM-invoking component, not just routing and decomposition. Any component that registers a `PromptSignature` and feeds outcomes back through the CBR retain path gets prompt optimisation for free.
