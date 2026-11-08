# Design Decisions — #284 LLM Observation & Local Rules

## D1: Execution model — event-driven, not synchronous SPI

**Choice:** The LLM layer sits alongside the engine's tick loop, not inside it. It does NOT implement `EnvironmentObserver`.
**Alternatives:**
- Implement `EnvironmentObserver` SPI with cache — impedance mismatch; LLM has unbounded latency, SPI assumes bounded (100ms timeout). Returning stale cached results defeats the purpose of LLM interpretation.
- Add `AsyncEnvironmentObserver` SPI to the engine — even configurable per-observer timeouts are wrong for unbounded-latency operations. A 60-minute LLM call blocking a virtual thread is structurally fine for the carrier pool, but the tick loop's `evaluateAndDispatch()` calls `observations()` then `localRules()` then `convergenceDetection()` sequentially — blocking for 60 minutes holds up the entire evaluation pipeline for that case. The event-driven model avoids this entirely. Additionally, the engine repo should not take a design dependency on LLM-scale latency — the synchronous observation model is correct for what the engine owns.
**Rationale:** LLM calls have unbounded latency (30s to 60min). The engine's tick loop is designed for bounded-latency deterministic observers. Forcing an unbounded operation into a bounded interface creates a category error. The event-driven model lets the LLM take as long as it needs, writing results back into the environment to trigger subsequent ticks.
**Trade-offs:** Latency between environment change and LLM response — events must accumulate before being processed. Deterministic observers react instantly; LLM interpretation lags by design.
**Sources:** engine `CaseContextChangedEventHandler.observations()` (100ms hardcoded timeout), engine `CaseContextChangedEventHandler.evaluateAndDispatch()` (sequential pipeline: observations → localRules → convergenceDetection), engine `EnvironmentObserver` SPI
**Exploration:** deep-analysis
**Status:** captured

## D2: Composition from summarisation primitives via builder templates

**Choice:** The LLM interpretation layer is composed from existing summarisation primitives (`EventAccumulator`, `WindowPolicy`, `Compactor`, `ContentSummariser`, `SummarisationRunner`, `EventStreamBus`) via named template builders. No new runner type.
**Alternatives:**
- New `LlmEventDigest` type — duplicates accumulation/windowing/ticking infrastructure; creates parallel concept alongside summarisation
- Custom infrastructure — ignores existing well-tested primitives
**Rationale:** The summarisation framework's primitives (accumulation, windowing, compaction, tick-driven processing) are exactly what LLM interpretation needs. `ContentSummariser<T, R>` has the right shape: batch + previous result → single output with continuity. `asSummariser()` bridges into `StatefulSummariser` for the runner. Named templates (e.g., `LlmSummarisers.observation(...)`) make specific compositions discoverable while keeping the primitives open for novel use cases.
**Trade-offs:** Depends on the summarisation-api module. But this is a natural dependency — the pattern IS event batching + processing.
**Sources:** `ContentSummariser<T,R>`, `SummarisationRunner`, `EventStreamBus`, wacky-manor `NarratorAgent` (examples/wacky-manor)
**Exploration:** deep-analysis
**Status:** captured

## D3: Named templates as pre-configured ContentSummariser compositions

**Choice:** `LlmSummarisers` factory class provides named templates that produce ready-to-use `ContentSummariser` implementations. Each template handles prompt assembly, LLM invocation (via `StructuredAgentInvoker`), response parsing, and invocation policy monitoring internally. Prompt assembly integrates with existing observation pipeline infrastructure (`ObservationRenderer`, `AffordanceRenderer`, `CognitiveObservationSections`) rather than building a parallel rendering path.
**Alternatives:**
- Single generic class with config flags — less discoverable, harder to find the right configuration
- New SPI hierarchy (`Interpreter<E,R>`) — creates semantic tension with existing `ContentSummariser` which has the same shape
**Rationale:** The `ContentSummariser<T, R>` contract (`summarise(List<T> items, @Nullable R previous) → CompletionStage<R>`) is identical to what LLM interpretation needs. Named templates (`observation`, `action`, `combined`, `narration`) give concrete entry points for common use cases while the builder allows custom compositions for use cases we haven't anticipated.
**Trade-offs:** Templates become a catalog to maintain. But each template is thin — it configures prompt assembly and response parsing, delegating the heavy lifting to `StructuredAgentInvoker`.
**Sources:** `ContentSummariser<T,R>.asSummariser()`, `StructuredAgentInvoker`, `ObservationRenderer<E>`, `AffordanceRenderer`, `CognitiveObservationSections`, wacky-manor `NarratorSummariser` (examples/wacky-manor)
**Exploration:** deep-analysis
**Depends on:** D2 (composition from summarisation primitives)
**Status:** captured

## D4: Caller-supplied latency policy

**Choice:** The caller who creates the prompt provides expected duration, warning threshold, and error threshold via `LlmInvocationPolicy`. The framework does not guess or hardcode timeouts.
**Alternatives:**
- Framework-hardcoded timeout — wrong for every use case; LLM latency depends on model, prompt complexity, provider load
- No timeout at all — no way to detect stuck calls
**Rationale:** Only the caller knows the expected complexity. A simple classification prompt takes ~30s; a deep multi-key analysis might take 5 minutes. The policy is embedded in the summariser template — it monitors actual invocation duration, logs warnings, and cancels/errors at caller-specified thresholds.
**Trade-offs:** Requires callers to estimate complexity. But callers who build the prompt already have this intuition.
**Sources:** engine `CaseContextChangedEventHandler.observations()` (hardcoded 100ms — the anti-pattern). Engine's `RuleConfig.ruleEvaluationTimeoutMs()` is the solution pattern — caller-configurable timeouts — which `LlmInvocationPolicy` extends with finer granularity (expected duration, warning threshold, error threshold).
**Exploration:** deep-analysis
**Status:** captured

## D5: Separated observation and action by default — mirrors engine phasing

**Choice:** Default to separated observation → action as two `ContentSummariser` compositions run in sequence. The observation summariser interprets the environment and produces `Observation` records. The action summariser reads those observations plus environment state and produces `RuleAction` recommendations. A `combined()` template is available for callers who explicitly opt into coupling.
**Alternatives:**
- Combined by default (single LLM call for both observation and action) — confirmation bias: when the LLM interprets and decides in one prompt, the desire to recommend a particular action colors the observation, selectively attending to features that support the preferred action. This is anchoring bias, and is the precise reason the engine separates `observations()` → `localRules()` into distinct phases.
- Action-only — misses the observation value (other agents can't react to LLM observations)
**Rationale:** The engine's two-phase pipeline (observe then decide) is architecturally deliberate — observations are shared data that other agents' rules can consume. Separated phases produce unbiased observations that feed cleanly into the engine's pipeline. Separation also enables using different models per phase: a cheaper/faster model for observation, a more capable model for action reasoning. The `combined()` template remains available for cases where coupling is explicitly acceptable — but it is an opt-in, not the default.
**Trade-offs:** Two LLM calls instead of one when both phases are LLM-backed. But focused prompts can use cheaper models, and the total cost of two cheap+focused calls may be less than one expensive+combined call. Separation also improves testability and debuggability.
**Sources:** engine `CaseContextChangedEventHandler.evaluateAndDispatch()` (observations() then localRules() — two-phase pipeline), engine `ObservationRegistry.storeObservations()` / `getObservations()` (observations are shared data between agents)
**Exploration:** deep-analysis (revised from quick after decision review R1-01)
**Depends on:** D1 (event-driven model), D2 (composition from summarisation primitives)
**Status:** revised

## D6: Output routing via EventStreamBus subscriber — caller chooses destination, async write-back

**Choice:** LLM interpretation results publish to `EventStreamBus` like any summarisation output. A subscriber handles environment-specific write-back (signal deposits, context writes, observation storage). The `EnvironmentWriteBack` helper dispatches write-back operations asynchronously via `CompletableFuture.runAsync()` to avoid blocking the LLM thread.
**Alternatives:**
- Custom output mechanism — would require new runner type or output abstraction
- Hardcoded write-back — not composable; different consumers need different routing
- Synchronous write-back — blocks the LLM thread during signal deposits, context writes, and observation storage, which may involve I/O or trigger further engine evaluation cycles
**Rationale:** `EventStreamBus` is the standard output mechanism for all summarisation runners. Using it for interpretation means no runner changes. The subscriber pattern lets different consumers route output differently. Async dispatch in the write-back subscriber follows the bus's own guidance: "blocking callbacks block the entire publish loop — wrap in `CompletableFuture.runAsync()` if the callback may block."
**Trade-offs:** Async write-back means results are not immediately visible in the engine environment after the bus publish returns. But the event-driven model already accepts eventual consistency — results appear on the next tick regardless.
**Sources:** `EventStreamBus` (publish contract and blocking callback warning), wacky-manor `NarratorAgent` output wiring
**Exploration:** deep-analysis
**Depends on:** D2 (composition from summarisation primitives)
**Status:** revised

## D7: Backpressure via concurrency guard

**Choice:** Skip tick if the previous LLM call is still in-flight. The `EventAccumulator` continues collecting events, but the summariser will not be invoked until the previous call completes. Prevents unbounded concurrent LLM calls.
**Alternatives:**
- No guard (unbounded concurrent calls) — events accumulate indefinitely while multiple LLM calls run in parallel. `EventAccumulator` has no size bound. Multiple in-flight calls waste tokens and may produce contradictory results.
- Max buffer size with oldest-first eviction — loses events, which may contain important changes
- Pause event collection entirely — risks dropping events from the engine's event source
**Rationale:** LLM calls take 30s–60min. Without a concurrency guard, rapid environment changes could trigger multiple overlapping LLM evaluations, each processing a partially overlapping batch. A simple in-flight guard ensures at most one LLM call is active at a time. Events arriving during the call accumulate in the buffer and are processed in the next batch — which is actually more efficient (larger batch = better context for the LLM).
**Trade-offs:** During long LLM calls, the buffer grows without bound. For extremely high-frequency environments, a configurable max buffer size with compaction could be added as a future refinement.
**Sources:** `SummarisationRunner.tick()` (does not check whether previous summarisation is in-flight), `EventAccumulator` (no size bound)
**Exploration:** deep-analysis
**Status:** captured

## D8: Change-gated invocation via EmissionPolicy

**Choice:** Only invoke the LLM when watched keys have actually changed since the last evaluation. Implemented via a custom `EmissionPolicy<E, S>` that checks the intersection of `changedKeys` with the interpreter's watched key set. If no watched keys changed, the tick is a no-op regardless of window policy.
**Alternatives:**
- Always invoke on window trigger — wastes tokens when the environment hasn't changed in ways the LLM cares about
- Cache the full LlM response and return it on no-change ticks — adds complexity; the `ContentSummariser` contract always invokes the LLM
**Rationale:** Issue #284 explicitly requires "Observation caching — avoid redundant LLM calls for unchanged environment sections." The `EmissionPolicy` SPI already supports state-aware emission gating with `anyOf()`/`allOf()` composition. A change-detection emission policy composes naturally with `WindowPolicy`-based time/count gating: the window policy accumulates events, the emission policy gates whether the accumulated batch is worth sending to the LLM.
**Trade-offs:** Requires the interpreter to declare watched keys upfront (similar to `EnvironmentObserver.watchedKeys()`). A broad key set means frequent invocation; a narrow key set means the LLM may miss relevant cross-key patterns. The builder should support both explicit key sets and a "watch all" mode.
**Sources:** `EmissionPolicy<IN, S>` (composable emission gating), `EnvironmentObserver.watchedKeys()` (engine's key-change filtering pattern), issue #284 body ("Observation caching — avoid redundant LLM calls")
**Exploration:** deep-analysis
**Status:** captured

## D9: Structured JSON output with per-item attribution

**Choice:** LLM produces structured JSON with per-observation triggering keys and per-action justifying pattern IDs. LLM-facing response records (`LlmObservationResponse`, `LlmActionResponse`) are separate from engine-facing records (`Observation`, `RuleAction`). Templates convert between them. Actions are wrapped in `RecommendedAction` which pairs a `RuleAction` with the `patternId`s that justify it.
**Alternatives:**
- Free-text output parsed heuristically — fragile, loses structural correlation
- Engine types as JSON directly — engine's sealed `RuleAction` can't be directly deserialized from JSON; LLM can't produce Java-specific type discriminators
**Rationale:** Structured JSON via `StructuredAgentInvoker.invoke()` gives reliable deserialization. Separate LLM-facing records decouple the prompt schema from engine internals. Per-item attribution (`triggeringKeys`, `justifyingPatternIds`) enables full causal tracing: context keys → observations → actions.
**Trade-offs:** LLM must produce valid JSON conforming to the schema. But this is a well-established pattern (`StructuredAgentInvoker` already handles fence-stripping and JSON extraction).
**Sources:** `StructuredAgentInvoker.invoke()`, `StructuredAgentInvoker.extractJson()`
**Exploration:** quick
**Depends on:** D5 (separated phases make attribution cleaner — each phase has clear input→output)
**Status:** captured

## D10: Pattern-matched observation handlers replace second LLM call for actions

**Choice:** The caller registers handlers keyed to the observation vocabulary defined in the system prompt. The LLM observes and groups events; caller-supplied handlers decide what to do with each observation type. The prompt and handlers are two sides of the same configuration — the caller controls both the vocabulary and the reactions. `EnvironmentWriteBack` becomes one possible handler implementation, not the primary output mechanism.
**Alternatives:**
- Second LLM call for actions (D5 phase 2) — the LLM decides what actions to take. More autonomous but less controllable, 2x latency, and the caller loses explicit control over responses to specific patterns
- Simple EventStreamBus subscribers (D6) — generic pub/sub without pattern-based dispatch. Caller must filter by patternId manually in each subscriber
- Engine's LocalRule system only — observations stored in ObservationRegistry, deterministic rules react. Works but requires pre-registering static rules for each LLM observation type
**Rationale:** The LLM's strength is semantic interpretation and event grouping — detecting that "these 5 changes together mean X." The caller's strength is knowing what to DO about X (deposit a signal, block a transaction, escalate to a human). Splitting these roles matches the engine's observe/decide separation and gives the caller explicit control over actions. The observation vocabulary in the prompt and the handler registration form a closed contract — the framework can validate that every vocabulary entry has a handler and vice versa.
**Trade-offs:** Requires the caller to anticipate observation types upfront (in both the prompt and handlers). Novel/unexpected observations go to `onUnhandled()`. A second LLM call (D5 phase 2) remains available as a handler implementation for cases where the action decision is also too complex for static rules.
**Sources:** `ChannelAgentHandler.handles()` + `ChannelAgentDispatcher` (first-match handler routing pattern in agency communication.dispatch)
**Exploration:** deep-analysis
**Depends on:** D5 (separated observation/action), D9 (structured output with patternId)
**Status:** captured
