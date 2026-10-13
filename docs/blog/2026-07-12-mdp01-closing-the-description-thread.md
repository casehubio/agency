---
layout: post
title: "Closing the Description Thread"
date: 2026-07-12
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [orchestration, unification, shared-types]
series: issue-52-shared-orchestration-types
---

*Part of a series on [#44 — agentic planning architecture](https://github.com/casehubio/blocks/issues/44). Previous: [The Type System as Architecture Document](2026-07-10-mdp01-type-system-as-architecture.md).*

## What Landed

Engine shipped the shared orchestration types — `TaskDescriptor`, `ExecutorRef`, `TaskStatus`, `RoutingResult`. `PlanItem` now implements `TaskDescriptor` with an `ExecutorRef` instead of a bare `workerName` string. The description thread we identified two sessions ago — where `PlannedTask.description` disappeared at the `PlanItem` layer — is closed on the engine side.

On blocks, I completed the first adoption step: `SubTaskStatus` (the conversation package's local 3-value enum) replaced by `TaskStatus` from engine-api. Mechanical, but it surfaced an issue — the `ConversationRenderer` had a switch over the old enum's three values. `TaskStatus` has nine. A default case was needed, which is the kind of thing that looks trivial until you forget it and get a compile error three modules downstream.

Also closed the `issue-694-dag-plan-structure` branch that had been sitting open — `ExecutionPlan` with DAG structure, dependency edges, join semantics. `DecompositionStrategy` now returns `ExecutionPlan<T>` instead of `List<TaskNode<T>>`. That was blocking the next phase of work.

## The Five-Item Review

The more interesting part of the session was the systematic review of what engine#700 shipped against what we designed. Five items surfaced:

The one that matters most: `LeafTask` should extend `TaskDescriptor`, not just `PlannedTask`. Both leaf variants are units of work — if only one implements the shared type system, monitoring and audit have a blind spot for HTN tasks. `LeafTask` provides a default `executor()` that delegates to `agent()`, bridging the agentic vocabulary ("agent") and the shared vocabulary ("executor") with one default method.

The research doc got a status header so readers know which sections describe the pre-unification state. The migration phase comment on engine#700 was updated. And the `RoutingPromptSection` promotion turned out to already be clean — blocks was already consuming it from engine-api.

## What's Next

Two issues remain on the open branch: `AgentRef extends ExecutorRef` (#50) and `LeafTask extends TaskDescriptor` (#51). Both are design-settled — the decisions were made this session, the issue bodies capture the exact code. The next session picks up where this one stopped.
