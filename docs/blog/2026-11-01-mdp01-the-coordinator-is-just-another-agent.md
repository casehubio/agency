---
ylayout: post
title: "The Coordinator Is Just Another Agent"
date: 2026-11-01
entry_type: article
subtype: diary
projects: [casehubio/agency, casehubio/engine]
tags: [swarm, multi-agent, distributed-coordination, architecture, design]
---

# The Coordinator Is Just Another Agent

Most multi-agent frameworks draw a sharp line between the coordinator and the agents it coordinates. The coordinator is special — it has a different interface, different lifecycle, different code path. The agents are workers. They execute; the coordinator thinks.

We had this exact architecture in CaseHub. `LlmSwarmCoordinator` was a single LLM making three kinds of decisions: team formation (who works on what), collective reasoning (synthesise everyone's findings), and provisioning (do we need more agents). Swarm members were opaque task executors with no local reasoning at all.

It worked. But it has a ceiling.

## The insight that changes everything

When Claude and I dug into the coordinator's three LLM invocations, we found they're structurally identical to the per-agent stigmergy pipeline that already exists in the codebase. Both do the same thing:

1. **Observe** — gather context (candidates, agent results, swarm state)
2. **Reason** — invoke an LLM with a composed prompt
3. **Act** — dispatch the result (routing decision, aggregation result, provisioning advice)

The coordinator's team formation call gathers candidate agents, builds a prompt with experience replay and swarm state, calls the LLM, and parses a routing decision. A per-agent decision loop would gather local task state, build a prompt with its own observations, call the LLM, and decide how to adapt its approach.

Same primitive. Different scope.

![The observe-reason-act cycle is the same abstraction whether it runs as a centralised coordinator or as a per-agent loop](images/unified-primitive.svg)

This means the coordinator isn't a separate system — it's one configuration of a general-purpose decision loop. A centralised coordinator is a single loop with global observation scope. Distributed coordination is N loops with local scope. A hybrid topology is both. The topology determines how many loops exist and what each one can see and do.

## The engine/agency split — and why it matters

The more interesting design question was where the boundary between engine and agency should be. I pushed hard on making engine the heavy side. Agency should be thin — it composes engine primitives with LLM reasoning.

The result is a split where engine owns mechanical enforcement and agency owns intelligence:

![Engine provides the mechanical enforcement layer; agency composes it with LLM reasoning](images/coordination-layers.svg)

The critical piece is `AgentCapabilityGuard` — engine mechanically enforces what each agent is permitted to do. An agent with "reactive" scope physically cannot initiate a message to another agent. Not because the LLM prompt says "don't initiate messages" and we hope it complies. Because the engine blocks the action at the channel dispatch level.

This matters for anyone building trustworthy multi-agent systems. Prompt-based constraints are suggestions. Mechanical enforcement is a guarantee. When you're running R&D experiments with agents that have varying degrees of autonomy, you need the guarantee.

## A toolkit, not a system

The honest answer to "what's the best coordination topology?" is that nobody knows yet. Centralised coordination works for simple swarms. Fully distributed might work for stigmergic environments. Hierarchical might scale better. Graduated autonomy — where agents earn more decision authority over time — is genuinely unexplored territory.

So instead of betting on one topology, we designed five independent strategy dimensions:

| Dimension | What it controls |
|-----------|-----------------|
| **Topology** | How decision loops interact — centralised, distributed, hybrid, hierarchical, graduated |
| **Scope** | What each loop can observe and act on — reactive, task-adaptation, full autonomy |
| **Communication** | How agents exchange information — stigmergy, direct messaging, broadcast |
| **Budget** | LLM cost control — flat per-agent limits |
| **Observation** | What triggers reasoning — events, ticks, on-demand |

Each dimension is an independent SPI. The topology doesn't know about the budget model. The budget model doesn't know about the communication mode. Researchers turn knobs independently to explore the design space.

Pre-composed coordination profiles — `centralised-v1`, `distributed-stigmergy`, `graduated-autonomy` — give sane defaults for common configurations. But the point is the composable surface, not the shipped presets. The presets are starting points. The experiments people haven't imagined yet are what the toolkit is for.

## Degradation that preserves intelligence

One design choice I'm particularly pleased with: when an agent hits its LLM budget limit, it doesn't fall back to heuristics. It narrows its scope.

Full autonomy → task adaptation → reactive. The agent still reasons with an LLM — it just reasons about less. This preserves the value proposition of LLM coordination (intelligent decisions in novel situations) under resource pressure, rather than abandoning it entirely for predetermined heuristics that might not fit the situation.

The cascade model has hysteresis to prevent oscillation (triggers at N degraded agents, reverses at N-2) and a half-open circuit breaker for recovery (one agent at a time, exponential backoff). Over-engineered for R&D? Maybe. But degradation cascades are where distributed systems fail in production, and getting the primitives right early is cheaper than retrofitting them later.

## What this opens up

The immediate practical value is that CaseHub's swarm can now support per-agent LLM reasoning without replacing the coordinator that already works. The existing centralised model becomes `CentralisedTopology` — identical behaviour, just formalised as one strategy.

The longer-term value is a research platform. Can graduated autonomy outperform fixed topologies in dynamic environments? Does stigmergic communication scale better than direct messaging for large swarms? When an agent has enough local context to make good decisions without asking the coordinator, what's the convergence profile?

These are empirical questions. The answers require a toolkit that lets you configure the experiment, not a system that bakes in assumptions. That's what we built.
