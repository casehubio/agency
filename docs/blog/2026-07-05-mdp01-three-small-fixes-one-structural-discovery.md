---
layout: post
title: "Three small fixes, one structural discovery"
date: 2026-07-05
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [otel, metrics, routing, trust-classification, type-safety]
series: issue-7-activation-context-metrics-hardening
---

## Three small fixes, one structural discovery

I'd planned to knock out the three small open issues — type-tighten `ActivationContext`, add the metrics listener, harden the routing strategies — as a single-branch batch. All S-scale. All well-scoped. The kind of work that should be mechanical.

The type fix was mechanical. `ActivationContext.lastAggregationResult` was typed `Optional<Object>` when it only ever holds an `AggregationResult`. Two lines. The tests were more interesting — the plan had wrong expectations for `consecutiveIdleActivations`. The context is built *before* the activation decision, so idle counts reflect the state entering the decision, not leaving it. Sequence is `[0, 0, 1]`, not `[0, 1, 0]`. A subtlety that matters if anyone builds an activation rule that reads idle counts.

The metrics listener prompted a genuine architectural question: sink pattern or OTel API? The other two listeners — `EventLogListener` and `LedgerExecutionListener` — both use custom sinks. Consistency would say do the same. But the reason those use sinks is that their destination formats are application-specific — the EventLog schema and the compliance ledger schema are consumer concerns. Metrics don't have that problem. Metrics *are* standardised. A custom `MetricsSink` would just force every consumer to write the same OTel bridge. We went with `Meter` directly, declared as `provided` scope — same pattern as mutiny. The listener is stateless: execution duration and iteration count come through an enriched `onExecutionComplete` callback from the driver, not from listener-side tracking.

That callback change — `onExecutionComplete(ExecutionResult result, Duration executionDuration, int iterationCount)` — surfaced an older problem. `AgentResult.duration` was always `Duration.ZERO`. Every factory method hardcoded it. The driver's `invokeAgent()` never timed anything. So the EventLogListener was already logging `durationMs: 0` for every invocation. We fixed this in the driver: wrap the invocation with `Instant.now()` before and `Duration.between()` after, reconstruct the result with actual timing. Both success and failure paths now carry real durations.

The routing hardening is where the structural discovery happened. The issue asked for three things: MP config for CBR's `topK` and `minSimilarity`, a Jackson parser to replace the fragile `indexOf`-based JSON extraction, and tests for mixed-pool trust edge cases. All straightforward.

But reading both routing strategies side by side to add the tests, the trust classification code was identical. Not similar — identical. Thirty-five lines of classify, bootstrap guard, filter eligible, empty-pool delegation, copy-pasted between `LlmAgentRoutingStrategy` and `CbrAgentRoutingStrategy`. The duplication was created during the #30 implementation — the code was working, so it shipped. The problem isn't aesthetic. In a regulated trust context, two copies of the filtering logic that must evolve in lockstep is a compliance risk. If a new trust phase is added and only one copy gets updated, the other strategy silently misclassifies candidates.

We extracted the shared logic to a `TrustFilterOutcome` sealed interface — `Proceed` carries the eligible candidates and classification data, `Decided` carries a terminal assignment (escalation or classifier decision). Both strategies now call `RoutingSupport.applyTrustFilter()` and switch on the result. The extraction also exposed a gap: the LLM strategy wasn't falling back to `classifier.decide()` when the LLM failed but trust data was present. The CBR strategy did this correctly — it was a copy-paste divergence that had already happened.

The `NullNode` bug was a nice catch. `AgentRoutingContext.caseContext()` can be `NullNode.instance` — Jackson's representation of JSON null. `NullNode.toString()` returns the string `"null"`, which passes `!isBlank()` and silently leaks into LLM prompts as literal case context text "null". The fix is `JsonNode.isNull()` — one method call, but the failure mode is completely silent.

Six OTel instruments. A sealed interface for trust filtering. Jackson replacing manual JSON parsing. And `AgentResult.duration` finally means something. The three issues are closed, and the trust classification logic lives in one place instead of two.
