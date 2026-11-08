---
layout: post
title: "When Curiosity Drives the Loop"
date: 2026-10-26
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [evolution, cognition, drives, mood, stigmergy]
series: evolution-loop-integration
---

# When Curiosity Drives the Loop

The evolution loop has a timing problem. Engine's `EvolutionTicker` runs on two triggers: event-driven (convergence detection) and a timer backstop. Both are mechanical. The ticker doesn't know whether the agent is actively exploring or passively waiting. It fires at the same rate regardless of what the agent is doing cognitively.

The cognitive tick cycle already computes drive profiles. `DriveOrchestrator.currentDrives()` returns a `DriveProfile` with intensity values for each axis — CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY. An agent with high CURIOSITY is actively exploring its environment, noticing novel patterns, seeking improvement. An agent with low CURIOSITY is in maintenance mode — handling routine work, not looking for anything new.

The evolution loop should listen to this. High curiosity means "I'm actively looking for ways to improve" — tick every time. Low curiosity means "I'm not in an exploratory state right now" — skip the tick entirely. The middle range is probabilistic: the higher the curiosity, the more likely the tick fires.

The integration point is `CognitionAvatarAdapter.tick()`. After `core.tick()` returns, drives have been computed in the DERIVED phase — the drive profile is current. The evolution tick fires with the drive data it needs. The timing is correct because the cognitive pipeline guarantees the ordering: FOUNDATION → SOURCE → DERIVED → TERMINAL. Drives live in DERIVED. The evolution tick runs after.

## The mood feedback loop

Circuit breakers are the evolution loop's safety rail. When a proposal source generates too many failed improvements in a row, the circuit breaker trips — blocking further proposals until conditions change. This is a binary gate: PASSED or BLOCKED.

But circuit breaker state is also a signal about the agent's relationship with its environment. A tripped breaker means "the improvement strategies I've been trying aren't working." That's frustrating. When the breaker recovers — proposals start succeeding again — that's a relief.

`CircuitBreakerMoodMapper` translates these gate transitions into PAD mood signals. BLOCKED produces a small negative pleasure shift and a spike in arousal — mild frustration, heightened alertness. Recovery produces the inverse — a small positive shift, decreased arousal, slight increase in dominance. The mapper is stateful: it tracks whether the previous tick was blocked so it can detect the recovery transition.

The mood signals feed into `MoodOrchestrator.record()`, which means they participate in the next mood tick. A frustrated agent — one whose evolution proposals keep failing — develops a subtly different emotional texture than one whose improvements are landing cleanly. This doesn't change behaviour directly (mood modulates prompt assembly, not action selection), but it gives the agent a more honest internal state.

## The boundary that matters

`EvolutionTickBridge` is a `@FunctionalInterface` with a single method: `TickTrace tick()`. That's it. The consumer constructs it by binding `EvolutionTicker.tick(caseId, tenancyId, config, EVENT_DRIVEN)` — currying the case-specific parameters at construction time. Agency never sees `EvolutionTicker`. It can't — engine runtime-core is not on the compile classpath.

This is the same boundary pattern used everywhere in agency: depend on engine-api types (`TickTrace`, `GateResult`), let the consumer wire the runtime dependency. The bridge carries no context about what the evolution loop does internally. It returns a `TickTrace` because the cognitive layer needs the gate results for mood feedback. If it didn't need those, the bridge would return void.

The adapter sits between the bridge and the cognitive system. `CognitiveEvolutionAdapter` queries drives, decides whether to tick, calls the bridge, and feeds mood signals. Four responsibilities, four fields, sixty lines. It owns a `CircuitBreakerMoodMapper` instance because the mapper is stateful per agent — no sharing, no CDI, no lifecycle management beyond the adapter's own lifetime.

`CognitionAvatarAdapter` gains one optional parameter and three lines. The null-check keeps backward compatibility — every existing constructor still works. The evolution tick fires after `core.tick()` and before `consumeGoalRevisions()`. If the adapter isn't wired, nothing changes.
