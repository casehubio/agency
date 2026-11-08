---
layout: post
title: "The LLM Doesn't Belong in the Tick Loop"
date: 2026-10-25
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [stigmergy, llm, architecture, summarisation, composition]
series: issue-284-llm-observation-local-rules
---

# The LLM Doesn't Belong in the Tick Loop

The engine's stigmergy model has a clean separation. On every context change, a tick fires: deterministic observers check patterns, local rules fire actions, convergence is evaluated. Each observer gets 100ms on a virtual thread. Threshold checks, signal correlations, temporal sequences — all microsecond-scale operations. The 100ms timeout is generous for what it protects against.

I wanted to add LLM-backed observation to this. The issue description said "implement `EnvironmentObserver`" — same interface, different implementation. The LLM interprets the full environment state semantically, detecting patterns too complex for static rules.

The first instinct was to make the timeout configurable. Per-observer, the LLM declares "I need 30 seconds." But that's wrong. LLM calls don't have a predictable upper bound. They range from 30 seconds to 60 minutes depending on model, prompt complexity, and provider load. No fixed timeout value is correct — set it to 30 seconds and you kill legitimate calls, set it to 60 minutes and you can't distinguish "still thinking" from "stuck." The timeout model itself is the wrong abstraction for unbounded operations.

The second instinct was a cache. Return stale observations within 100ms, trigger a background LLM call, fresh results on the next tick. But what does "stale observations" actually mean? The whole point of calling the LLM is to interpret the *current* state. Returning yesterday's interpretation defeats the purpose.

What I eventually landed on: the LLM doesn't belong in the tick loop at all. It's not a fast sensor running slightly slower — it's a fundamentally different kind of computation. A deterministic observer answers "does this pattern exist right now?" (instant, yes/no). An LLM answers "what does this situation mean?" (slow, nuanced, unbounded). Forcing the second into the first's interface is a category error.

## The event-driven model

The LLM sits alongside the tick loop, not inside it. Context changes accumulate in a buffer. When enough has changed — gated by a `WindowPolicy` or `EmissionPolicy` — the batch is sent to the LLM. The LLM takes as long as it takes. When it finishes, results write back to the environment (signals, context entries), triggering a new tick. Deterministic rules then react to the LLM's output.

This mirrors how a human analyst would work. You don't stand at their desk waiting for an answer. You send the report, continue working, and when they respond, you incorporate their analysis.

## Composition, not creation

The interesting design question was: does this need a new framework? Agency already has the summarisation pipeline — `EventAccumulator`, `WindowPolicy`, `Compactor`, `SummarisationRunner`, `EventStreamBus`. Wacky-manor's narrator uses exactly this pattern: game events accumulate, a window policy triggers, the batch goes to an LLM-backed summariser.

I initially proposed a new `LlmEventDigest` type as a reusable pattern. Claude challenged this: isn't that just `SummarisationRunner` with a different name? The `ContentSummariser<T, R>` interface has the right shape — batch plus previous result, returns a single output. The `asSummariser()` bridge connects it to the runner. The output goes to `EventStreamBus` subscribers.

I pushed back — `SummarisationRunner` hard-couples to `EventStreamBus` for output, and the LLM interpretation needs environment write-back, not pipeline flow. But the bus *is* the output routing mechanism. A subscriber that writes to the engine environment is just a different subscriber. The bus isn't dummy infrastructure — it's the standard output path that also enables audit logging and metrics subscribers.

The answer was composition via builder templates. `LlmSummarisers.observation()` produces a pre-configured `ContentSummariser` that handles prompt assembly, LLM invocation, response parsing, and batch correlation. It plugs into the existing runner. No new runner type. No parallel concept.

## The handler pattern

The second half of the problem: what happens when the LLM produces observations? The LLM groups events semantically — "these five changes together mean anomaly X" — and each pattern type needs a different response.

The caller defines both sides: the prompt tells the LLM what patterns to look for (the vocabulary), and handler registrations define what to do when each pattern is observed. Prompt and handlers are two sides of the same contract:

```java
var dispatcher = ObservationDispatcher.builder()
    .on("risk-spike", (obs, raw, ctx) -> {
        ctx.depositSignal("alert:risk-spike", obs.confidence());
    })
    .on("compound-risk", (obs, raw, ctx) -> {
        ctx.depositSignal("escalate:aml", obs.confidence());
        escalateToHumanReviewer(ctx.caseId(), obs);
    })
    .build();
```

The LLM observes and groups. The handlers decide and act. The engine's original observe-then-decide separation, expressed through a different mechanism.

## The drain-before-skip trap

One subtlety worth recording. I needed a concurrency guard — don't start a new LLM call while the previous is still in-flight. The natural approach: wrap the `ContentSummariser` to skip invocation when busy.

This silently destroys data. `SummarisationRunner.tickWithPolicy()` drains the accumulator *before* invoking the summariser. If the wrapper skips, the drained events are gone — removed from the buffer, never processed, no error logged.

The fix is to gate at the `EmissionPolicy` level instead. By suppressing emission, the drain never happens. Events stay in the accumulator until the in-flight call completes, then get included in the next batch. Same result, no data loss — but you'd never reach for it unless you understood the runner's internal execution order.

## What this opens up

The composition model means the LLM interpretation layer is not a special-purpose feature — it's a configuration of existing primitives. The same builder that creates an LLM observer could create an LLM narrator, an LLM classifier, or an LLM-based convergence detector. The templates are named entry points; the builder is open for compositions I haven't thought of yet.

The handler dispatch pattern has a similar quality. The observation vocabulary is caller-defined, the handlers are caller-registered, and the `derivedFrom` hierarchy lets the LLM express multi-level analysis — leaf observations from raw events, derived observations from combinations. The dispatcher topologically sorts and routes. It's the engine's local rule system reimagined for unbounded-latency semantic interpretation.
