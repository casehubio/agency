---
layout: post
title: "Fourteen Open Issues Is Not a Gap Analysis"
date: 2026-08-04
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [langchain4j, agentic, gap-analysis]
---

I came into this session wanting to understand what langchain4j's agentic-ai module actually ships and how it compares to what casehub has. The engine#102 epic — "Enterprise AI Agent Orchestration Patterns" — has 16 child issues, and I hadn't looked at them through the lens of real parity gaps versus aspirational roadmap.

The distinction matters. An open issue documenting what we'd *like* to build is not a gap. A gap is something the other framework ships in code that we don't match yet.

## What the comparison actually showed

We ran the analysis two ways: reading every engine#102 child issue for its langchain4j comparison sections, and scanning the langchain4j codebase to see what it actually ships. Cross-referencing the two gave a clearer picture than either alone.

Of the 16 child issues, only four represent real functional gaps — things langchain4j has in production code that casehub doesn't yet match:

1. **LLM supervisor/planner wired to engine** — blocks has the `LlmDecomposition` SPI and the `SupervisorBuilder`, but engine doesn't consume them yet. No `LlmPlanningStrategy` wired to `PlanningStrategyLoopControl`.
2. **ReAct tool-call loop** — no built-in mechanism for the reason-act-observe cycle.
3. **A2A outbound worker provisioning** — langchain4j ships `langchain4j-agentic-a2a` for remote agent invocation via Google's A2A protocol. casehub has nothing outbound (engine#340 covers inbound only).
4. **MCP tool integration** — langchain4j wraps MCP tools as composable agents. casehub has no MCP-as-worker integration.

The other twelve issues — sagas, compliance workflows, case replay, multi-modal pipelines, contract-net bidding, regulatory decision automation — are things *neither* framework has. They're casehub's unique roadmap, not langchain4j comparison gaps.

## What was missing and what I filed

Issues #1 and #2 already had tracking (engine#110 and engine#114). For the A2A outbound direction and MCP integration, there was nothing filed. Both map cleanly onto existing casehub primitives — `WorkerProvisioner` SPI, `Capability`, `WorkerRegistry` — so they're M-scale, Med complexity, no new SPIs needed. I filed engine#830 for A2A outbound and engine#831 for MCP tools, and opened slot 67 to do both sequentially.

## The other slots

I also opened slots for two issues that had been sitting in the backlog: engine#84 (milestone/goal alignment) and work#237 (structured progress). Both reference Stage heavily in their descriptions, and Stage has been retired — so the slot context flags that the issue needs revisiting before execution.

The real question coming out of this is whether the four parity gaps are worth prioritising, or whether casehub's unique capabilities — the things no framework addresses — are where the energy should go. The gaps are straightforward wiring; the roadmap items are where the differentiation lives.
