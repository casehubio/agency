# RAG-Enriched Routing — Design Spec

**Issue:** casehubio/agency#16
**Branch:** `issue-016-rag-enriched-routing`
**Date:** 2026-07-06

## Problem

The platform has two engine-layer routing strategies in blocks — `LlmAgentRoutingStrategy`
(id: `"llm"`) and `CbrAgentRoutingStrategy` (id: `"cbr"`) — that use disjoint signal sources.
The LLM strategy reasons about agent descriptors and case context but has no historical evidence.
The CBR strategy tallies PlanTrace success rates but applies no LLM reasoning. Neither records
routing outcomes, so there is no feedback loop.

These are two independent concerns conflated into the strategy identity:

| Axis | Examples |
|------|----------|
| Decision mechanism | LLM reasoning, statistical tallying |
| Signal sources | CBR history, trust scores, semantic similarity |

Creating a new strategy per signal combination (e.g. `"rag"`, `"rag-semantic"`) leads to
M×N proliferation. The right architecture separates signal enrichment from decision-making.

## Design

### 1. RoutingPromptSection — composable LLM enrichment

**Package:** `io.casehub.agency.governance.routing.agent`

```java
public interface RoutingPromptSection {
    @Nullable String render(AgentRoutingContext context,
                            List<AgentCandidate> eligible);
}
```

- CDI-discovered via `Instance<RoutingPromptSection>` — ALL available sections render
- Receives eligible (post trust-filtered) candidates, not all candidates
- Returns null to skip (store unavailable, cold-start, no data)
- Not a `NamedStrategy`, not resolved by `StrategyResolver` — CDI-discovered enrichment
- Rendering failures are caught and logged by `RoutingPromptAssembler` (see below),
  never propagate to the routing decision

**RoutingPromptAssembler** — `@ApplicationScoped` bean that owns iteration over all
discovered `RoutingPromptSection` implementations:

```java
@ApplicationScoped
public class RoutingPromptAssembler {
    private final List<RoutingPromptSection> sections;

    @Inject
    public RoutingPromptAssembler(Instance<RoutingPromptSection> sections) {
        this.sections = sections.stream()
            .sorted(RoutingPromptAssembler::compareByPriority)
            .toList();
    }

    public @Nullable String assemble(AgentRoutingContext context,
                                      List<AgentCandidate> eligible) {
        var sb = new StringBuilder();
        for (var section : sections) {
            try {
                String rendered = section.render(context, eligible);
                if (rendered != null && !rendered.isBlank()) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(rendered);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "RoutingPromptSection threw — skipping: "
                    + section.getClass().getName(), e);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
```

- Sorts sections by `@Priority` annotation (lower value = rendered first; unprioritised
  sections render after prioritised ones in CDI discovery order)
- Wraps each `render()` call in try-catch — failures are logged and skipped, remaining
  sections still contribute
- Returns concatenated enrichment text, or null if no section produced output
- Used only by `LlmAgentRoutingStrategy` — non-LLM strategies do not invoke the assembler

### 2. CbrRoutingPromptSection — first enrichment provider

**Package:** `io.casehub.agency.governance.routing.agent`

Queries `CbrCaseMemoryStore` and formats historical context for the LLM prompt.

**Dependencies (all optional via `Instance<T>`):**
- `CbrCaseMemoryStore` — if absent, `render()` returns null
- `RoutingFeatureExtractor` — uses default if no override present

**Config:**
- `casehub.agency.cbr.top-k` (default 10)
- `casehub.agency.cbr.min-similarity` (default 0.5)

**Query construction:**
1. Uses `RoutingFeatureExtractor.extractProblem()` for text similarity
2. Uses `RoutingFeatureExtractor.extractFeatures()` for structured features
3. Domain set to `MemoryDomain(capabilityName)`, caseType `"agent-routing"`

**Output format — summary-first for LLM reasoning:**

```
Historical context (5 similar past cases for capability "analysis"):

Outcomes by agent:
  "reviewer": 3 cases — 2 SUCCESS, 1 FAILURE (67% success)
  "implementor": 2 cases — 0 SUCCESS, 2 FAILURE (0% success)

Case details:
  1. [score: 0.92] problem: "Validate regulatory filing" → agent: "reviewer" → SUCCESS
  2. [score: 0.88] problem: "Check compliance rules" → agent: "reviewer" → SUCCESS
  3. [score: 0.85] problem: "Implement data transform" → agent: "implementor" → FAILURE
  4. [score: 0.82] problem: "Review edge case handling" → agent: "reviewer" → FAILURE
  5. [score: 0.78] problem: "Build validation pipeline" → agent: "implementor" → FAILURE
```

The summary gives the LLM aggregate patterns; the case details enable nuanced reasoning
about when those patterns hold.

**Eligible-agent filtering:** CBR retrieval queries by domain/capability, not by agent.
Retrieved cases may reference agents that are currently trust-excluded or unavailable.
The prompt section filters all results to the `eligible` candidate list:
- "Outcomes by agent" summary includes only agents present in `eligible`
- Case details include only `PlanCbrCase` entries where the worker appears in `eligible`
- `TextualCbrCase` entries are always included (no agent attribution to filter)

This prevents the LLM from seeing evidence for agents it cannot select.

**CbrCase type handling:**
- `PlanCbrCase` — extracts worker names and outcomes from `PlanTrace` entries matching
  the current `capabilityName`. Full agent attribution. Only eligible agents appear in
  the summary and case details.
- `TextualCbrCase` — appears in the "Case details" section without agent attribution:
  `[score: 0.85] problem: "Validate filing" → FAILURE`. Excluded from the "Outcomes by
  agent" summary (no worker identity). Provides contextual background about what kinds
  of problems succeed or fail for this capability.
- Unknown subtypes — falls back to `CbrCase` interface (problem/solution/outcome),
  rendered like `TextualCbrCase`.

Returns null when no similar cases are found (cold-start graceful degradation), or
when all retrieved cases reference only non-eligible agents.

### 3. LlmAgentRoutingStrategy refactor

**Changes:**
- Constructor: inject `RoutingPromptAssembler promptAssembler`
- `doSelect()`: after `RoutingSupport.buildUserPrompt()`, delegate to assembler

```java
String enrichment = promptAssembler.assemble(context, eligible);
if (enrichment != null) {
    prompt = prompt + "\n\n" + enrichment;
}
```

**Unchanged:**
- Strategy id: `"llm"`
- System prompt: updated to acknowledge historical evidence (one-line change)
- Trust filtering: `RoutingSupport.applyTrustFilter()`
- Response parsing: `RoutingSupport.parseSelection()`
- Fallback chain: `classifier.decide()` on failure

**System prompt update:** see §9 for full updated text (preserves task-matching guidance)

### 4. RoutingFeatureExtractor — domain-pluggable feature extraction

**Package:** `io.casehub.agency.governance.routing.agent`

```java
public interface RoutingFeatureExtractor {
    Map<String, Object> extractFeatures(AgentRoutingContext context);
    @Nullable String extractProblem(AgentRoutingContext context);
}
```

**Default implementation (`@DefaultBean`):**

```java
@ApplicationScoped
@DefaultBean
public class TextOnlyFeatureExtractor implements RoutingFeatureExtractor {
    @Override
    public Map<String, Object> extractFeatures(AgentRoutingContext context) {
        return Map.of();
    }

    @Override
    public @Nullable String extractProblem(AgentRoutingContext context) {
        if (context.caseContext() == null || context.caseContext().isNull()) return null;
        String text = context.caseContext().toString();
        return text.isBlank() ? null : text;
    }
}
```

- Domain repos override with `@Alternative @Priority` to provide structured features
- Used by both `CbrRoutingPromptSection` (query construction) and
  `CbrRoutingOutcomeRecorder` (recording) — same feature vocabulary for queries and records

### 5. RoutingOutcomeRecorder — engine-api SPI

**Package:** `io.casehub.api.spi.routing` (engine-api)

```java
public interface RoutingOutcomeRecorder {
    Uni<Void> record(AgentRoutingContext context, String workerId, String bindingName,
                     String executionOutcome, @Nullable Duration executionDuration);
}
```

- `workerId` — the assigned worker (not a full `AgentAssignment`; the original routing
  rationale is not available at outcome time and a synthetic reconstruction would be a
  misleading API contract)
- `bindingName` — the case definition binding that dispatched the worker (needed for
  correct `PlanTrace` construction)
- `executionDuration` — nullable; available only when the engine tracks dispatch
  timestamps (future enhancement)
- Optional — engine uses `Instance<RoutingOutcomeRecorder>`, skips if unsatisfied
- Reactive (`Uni<Void>`) for consistency with engine's Mutiny chains
- Engine subscribes fire-and-forget — recording failure never blocks execution
- Not a `NamedStrategy` — optional infrastructure, not per-binding selectable

### 6. Engine runtime integration

**Call site:** `WorkflowExecutionCompletedHandler`, which processes all worker completions
regardless of outcome. Both success and failure paths record through the same mechanism.

**Why `WorkflowExecutionCompleted`, not `WorkerOutcomeResolvedEvent`:**
`WorkerOutcomeResolvedEvent` fires only for non-success outcomes (its Javadoc: "Published
by `WorkflowExecutionCompletedHandler` when a worker returns a non-success `WorkerOutcome`").
Using it as the integration point would record only failures — the CBR store would
accumulate exclusively negative evidence. `WorkflowExecutionCompleted` fires for ALL
outcomes and carries `WorkerOutcome outcome` (sealed: `Success`, `Declined`, `Failed`,
`Expired`).

**Outcome mapping:**

| `WorkerOutcome` subtype | CBR outcome string |
|-------------------------|--------------------|
| `Success` | `"SUCCESS"` |
| `Declined` | `"FAILURE"` |
| `Failed` | `"FAILURE"` |
| `Expired` | `"FAILURE"` |

This matches the vocabulary `CbrAgentRoutingStrategy.analyseByType()` expects:
`"SUCCESS".equals(trace.stepOutcome())`.

**Context snapshot:** the engine captures a snapshot of the working-layer context
**before** output application (success path) or failure-outcome processing. This ensures
the recorded problem description matches the case state at routing time, not the
post-execution state that includes the worker's own output.

**Recording flow:**

```java
// In WorkflowExecutionCompletedHandler — inject Instance<RoutingOutcomeRecorder>

// 1. Capture context snapshot before any mutations (deep copy via snapshot())
JsonNode contextSnapshot = caseInstance.getCaseContext().snapshot()
    .layer(ContextLayer.WORKING).asJsonNode();

// 2. Skip recording for PlannedAction events — deferred to gate resolution
boolean isGateDeferred = event.outcome() instanceof WorkerOutcome.Success s
    && s.plannedAction() != null;

if (!isGateDeferred) {
    // 3. Determine outcome
    String outcomeString = event.outcome() instanceof WorkerOutcome.Success
        ? "SUCCESS" : "FAILURE";
    String capabilityName = extractCapabilityTag(caseInstance, worker, event.bindingName());

    // 4. Fire recorder asynchronously
    if (!outcomeRecorder.isUnsatisfied() && capabilityName != null) {
        var ctx = new AgentRoutingContext(
            caseInstance.getUuid(), capabilityName,
            contextSnapshot, caseInstance.tenancyId);
        outcomeRecorder.get()
            .record(ctx, worker.name(), event.bindingName(), outcomeString, null)
            .subscribe().with(ignored -> {}, err ->
                LOG.warn("Outcome recording failed", err));
    }
}

// 5. Continue with existing success/failure handling paths
```

**Gate-checked actions:** when a worker returns a `PlannedAction` that requires gate
approval, step 2 skips recording — the outcome is not yet final. The control flow is:
- Gate approves → `WorkflowExecutionCompleted.approved()` re-dispatches with
  `Success(null)` → `plannedAction()` is null → step 2 proceeds → records `"SUCCESS"`
- Gate rejects → no re-dispatch occurs → no `"SUCCESS"` recorded

**Known limitation — gate rejection bias (#36):** when a gate rejects a worker's
`PlannedAction`, no outcome is recorded. Workers that consistently produce gate-rejected
actions accumulate no negative feedback — their CBR history shows only past successes
(or nothing). This creates a silent positive bias. Recording gate rejections requires a
call site in the gate rejection handler (outside `WorkflowExecutionCompletedHandler`),
with outcome string `"GATE_REJECTED"`. Tracked as a separate enhancement.

**Engine-api changes:**
- Add `RoutingOutcomeRecorder` interface

**Engine runtime changes:**
- Inject `Instance<RoutingOutcomeRecorder>` in `WorkflowExecutionCompletedHandler`
- Capture context snapshot, then check `PlannedAction` before firing recorder
- PlannedAction events skip recording (deferred to gate resolution); `approved()`
  re-dispatch triggers recording on second pass with `Success(null)`

No new dependencies added to engine-api or engine runtime `pom.xml` — the SPI uses
only types already present (`AgentRoutingContext`, `Uni`, `Duration`).

### 7. CbrRoutingOutcomeRecorder — blocks implementation

**Package:** `io.casehub.agency.governance.routing.agent`

Records a `PlanCbrCase` with a single `PlanTrace` entry per routing decision.

```java
@ApplicationScoped
public class CbrRoutingOutcomeRecorder implements RoutingOutcomeRecorder {
    private final @Nullable CbrCaseMemoryStore cbrStore;
    private final RoutingFeatureExtractor featureExtractor;

    @Override
    public Uni<Void> record(AgentRoutingContext context, String workerId, String bindingName,
                            String executionOutcome, @Nullable Duration executionDuration) {
        if (cbrStore == null) return Uni.createFrom().voidItem();

        return Uni.createFrom().item(() -> {
            var trace = new PlanTrace(
                bindingName, context.capabilityName(),
                workerId, executionOutcome, 0, Map.of());
            var problem = featureExtractor.extractProblem(context);
            var cbrCase = new PlanCbrCase(
                problem != null ? problem : context.capabilityName(),
                "Routed to " + workerId,
                executionOutcome, null,
                featureExtractor.extractFeatures(context),
                List.of(trace));
            cbrStore.store(cbrCase, context.caseId().toString(),
                "agent-routing", new MemoryDomain(context.capabilityName()),
                context.tenancyId(), context.caseId().toString());
            return null;
        }).emitOn(Infrastructure.getDefaultWorkerPool())
          .replaceWithVoid();
    }
}
```

This feeds both:
- `CbrAgentRoutingStrategy` — tallies PlanTrace success rates from retrieved cases
- `CbrRoutingPromptSection` — shows historical cases with outcomes to the LLM

### 8. CbrAgentRoutingStrategy — feature extractor adoption and relationship clarification

Adopt `RoutingFeatureExtractor` to replace hardcoded `Map.of()` in CBR queries.
One-line change: inject extractor, use `extractProblem()` and `extractFeatures()` in
`tryCbrStore()`. Aligns query construction with the prompt section and recorder.

**Architectural relationship to LLM + prompt sections:**

`CbrAgentRoutingStrategy` (id: `"cbr"`) and `LlmAgentRoutingStrategy` (id: `"llm"`) are
alternative routing strategies — a binding configures exactly one. They are not composable:

- **`"llm"` strategy:** `LlmAgentRoutingStrategy` invokes `RoutingPromptAssembler`, which
  discovers `CbrRoutingPromptSection` via CDI. The LLM sees historical evidence and
  reasons qualitatively about agent selection.
- **`"cbr"` strategy:** `CbrAgentRoutingStrategy` queries the CBR store directly and
  selects statistically (highest success rate). No LLM reasoning, no prompt sections.

No double-dip scenario exists: prompt sections are internal to `LlmAgentRoutingStrategy`
and are never invoked by `CbrAgentRoutingStrategy`. A binding cannot use both strategies
simultaneously.

`CbrAgentRoutingStrategy` is not deprecated — it serves as a lightweight no-LLM alternative
for deployments without an LLM provider, or for capabilities where statistical selection
is preferred over reasoning-based selection.

Both strategies consume the same CBR outcome data written by `CbrRoutingOutcomeRecorder`.
Both now use `RoutingFeatureExtractor` for query construction — same feature vocabulary,
consistent similarity matching.

### 9. RoutingSupport changes

**`SYSTEM_PROMPT`:** update to acknowledge historical evidence while preserving
task-matching guidance:

```
Select based on the agent's capabilities, briefing, domain expertise,
and any historical evidence provided. Choose the agent whose skills
most closely match the task requirements, using historical evidence
to inform confidence when multiple agents are comparably qualified.
```

**`buildUserPrompt()`:** unchanged — still builds the core prompt structure. Prompt
sections are appended by the strategy after this method returns.

## Dependency map

```
engine-api
  └── RoutingOutcomeRecorder (new interface)

engine runtime
  └── Instance<RoutingOutcomeRecorder> (inject + call at outcome)

blocks
  ├── RoutingPromptSection (new interface)
  ├── RoutingPromptAssembler (new — iterates sections with error handling + ordering)
  ├── RoutingFeatureExtractor (new interface)
  ├── TextOnlyFeatureExtractor (default impl)
  ├── CbrRoutingPromptSection (implements RoutingPromptSection)
  ├── CbrRoutingOutcomeRecorder (implements RoutingOutcomeRecorder)
  ├── LlmAgentRoutingStrategy (refactored — uses RoutingPromptAssembler)
  └── CbrAgentRoutingStrategy (minor — adopts feature extractor)
```

No new Maven dependencies in either repo.

## Testing

**RoutingPromptAssembler:**
- Multiple sections, one throwing — verify surviving sections still contribute
- No sections available — returns null
- `@Priority` ordering — verify lower-priority sections render first

**CbrRoutingPromptSection:**
- Mixed `PlanCbrCase`/`TextualCbrCase` results — verify output format
- Returns null on empty/cold-start CBR (no similar cases)
- Returns null when all retrieved cases reference non-eligible agents
- Eligible-agent filtering — verify excluded agents absent from summary and details
- `TextualCbrCase` rendering — appears in details without agent, absent from summary

**CbrRoutingOutcomeRecorder:**
- `record()` with null `CbrCaseMemoryStore` — returns completed `Uni<Void>`
- `record()` stores correct `PlanTrace` with `bindingName` (not `workerId`)
- Outcome strings match `CbrAgentRoutingStrategy` vocabulary (`"SUCCESS"`, `"FAILURE"`)

**LlmAgentRoutingStrategy:**
- `doSelect()` with assembler returning null — prompt unchanged, LLM invoked normally
- `doSelect()` with assembler returning enrichment — appended to prompt

**RoutingFeatureExtractor:**
- `TextOnlyFeatureExtractor` default: null/NullNode/blank context returns null
- Domain override via `@Alternative @Priority` — verify override takes precedence

**End-to-end:**
- Record outcome → retrieve similar → verify prompt enrichment contains recorded case
- Cold start (no history) → enrichment section returns null → routing works without history

## What is NOT in scope

Note: this spec uses `PlanCbrCase` as a data structure to store individual routing
outcomes with a single `PlanTrace` entry. This is distinct from the deferred "plan
composition matching" item, which is about analysing multi-step plan structures.

- Plan-Based CBR plan composition matching (#32) — analysing multi-step
  plan structures for routing decisions; future neural-text work
- Structured feature extraction for specific domains (#33) — domain repos
  implement `RoutingFeatureExtractor` when needed
- General signal enrichment for non-LLM strategies (#34) — different SPI,
  different issue
- Semantic embedding enrichment (#35) — future `RoutingPromptSection`
  implementation, separate issue
