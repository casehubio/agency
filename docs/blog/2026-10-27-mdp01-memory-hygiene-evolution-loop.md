---
layout: post
title: "Memory Hygiene Joins the Evolution Loop"
date: 2026-10-27
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [evolution, memory-hygiene, improvement-loop, capability-area, langchain4j, platform-evolution]
---

# Memory Hygiene Joins the Evolution Loop

The evolution loop landed in engine#1115 as a general-purpose continuous improvement pipeline — any domain can register as a participant by implementing four SPIs. Prompt optimisation was the first participant (#366), wiring variant quality scoring and regression detection into the loop. Memory hygiene is the second, and the interesting part isn't the code — it's what the second participant reveals about the pattern.

## The Four-Piece Structure Is Real

When I designed the evolution loop SPIs, the implicit claim was that any domain could plug in by implementing `ImprovementProposalSource`, `RegressionEvaluator`, `ImprovementCategoryProvider`, and `ConflictStrategy`. Prompt optimisation validated the mechanics, but one participant doesn't prove generalisation. Memory hygiene does.

The mapping was mechanical. `PromptOutcomeProvider` becomes `MemoryHygieneOutcomeProvider`. Per-signature conflict mutex becomes per-agent conflict mutex. Quality scoring shifts from variant outcomes to retention/eviction/consolidation metrics. The structure held — no forcing required, no awkward adapters, no "almost fits but needs a shim."

The one design question that wasn't obvious: what granularity should the conflict mutex use? Prompt optimisation uses per-signature (each prompt template is an independent tuning target). Memory hygiene could be per-agent, per-dimension (retention vs consolidation vs confidence), or per-agent-per-dimension. I chose per-agent — `RetentionConfig` is a single record, so tuning retention independently of consolidation while both are in flight would produce inconsistent results. The dimensions interact; the mutex should respect that.

## CapabilityArea — The First Implementor

The engine-api had a `CapabilityArea` SPI sitting unused — it lets the evolution loop assess the health of a domain before deciding where to invest improvement effort. Nobody had implemented it yet. Memory hygiene is a natural fit: the quality score maps directly to a `CapabilityAreaAssessment` with landscape positioning (AHEAD when retention quality is high, BEHIND when eviction rates are excessive, ABSENT when integrity violations are accumulating). The ROI calculation is simple — parameter tuning is cheap (cost = 0.2), so even modest health improvements yield high ROI.

This matters because it gives the evolution loop a way to prioritise across domains. With prompt optimisation and memory hygiene both registered, the loop can compare their `CapabilityAreaAssessment` scores and allocate improvement effort where it has the most impact.

## The Quality Formula

The composite quality score weights four dimensions:

- Retention quality (0.4) — mean composite retention score across memories
- Eviction rate inverted (0.3) — lower eviction means healthier retention thresholds
- Consolidation rate (0.2) — how effectively similar memories are being merged
- Integrity (0.1) — binary: any violations drop this to zero

When quality falls below the threshold plus a tuning margin, the proposal source identifies the weakest dimension and proposes the corresponding improvement type: `retention-threshold`, `confidence-weights`, or `consolidation-timing`. The weakest-dimension routing means proposals are targeted — the loop doesn't just say "memory hygiene is degraded," it says which parameter to adjust.

## What This Opens Up

Two evolution participants validates the pattern. The third will be easier than the second — the template is now concrete across two domains with genuinely different quality signals. Candidates: trust scoring (ledger-driven, different quality surface), coalition formation (team assembly outcomes), and cognitive evolution itself (the adapter is already wired, just needs improvement proposal generation).

The `CapabilityArea` SPI now has a reference implementation. Other domains can look at `MemoryHygieneCapabilityArea` for the pattern: outcome provider → composite health score → landscape position → ROI. The assessment contract is small enough that adding it to an existing domain participant is a single class.

## When the Platform Moves Under You

Reviewing the open issue backlog turned up #150 — a LangChain4j integration module from August that proposed bridging LC4j `@SupervisorAgent` CDI beans into CaseHub workers via `ChatModelAgentProvider`. The design made sense at the time: platform had `agent-langchain4j` modules with `ChatModelAgentProvider` and `AgentProviderChatModel` bridging the two worlds.

Except platform#638 removed all of it. The LC4j dependency is gone. Platform now uses native SDK implementations directly — `AnthropicNativeChatModel`, `GeminiNativeChatModel`, `OpenAiNativeChatModel`. The bridge classes that #150 would have composed with no longer exist.

This is the kind of issue that looks valid until you check the ground truth. The title reads well, the scope is clear, the ADR reference checks out — but the foundation shifted. The governance composition parts (oversight gates, trust routing on LC4j-backed workers) were always orthogonal — those SPIs are provider-agnostic. The LC4j-specific bridge was the only part that mattered, and it's gone.

Closed it as "not planned." If LC4j interop is needed in the future, it would start from a different architecture entirely.
