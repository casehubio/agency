# Weighted Routing Outcomes + LlmDecomposition Design

**Date:** 2026-07-10
**Issues:** #43 (weighted routing outcomes), #13 (LlmDecomposition + PlannedTask)
**Epic:** #44 (agentic planning architecture)
**Branch:** `issue-43-weighted-outcomes-and-llm-decomposition`
**Research:** `docs/research/2026-07-09-task-decomposition-and-agent-planning-landscape.md` — landscape
analysis that identified the gaps this spec addresses (no description on primitive nodes, absence of
LLM decomposition strategy)

---

## 1. Weighted Routing Outcomes (#43)

### Problem

`CbrAgentRoutingStrategy.analyseByType()` treats all non-SUCCESS outcomes identically.
A worker that consistently proposes bold actions (gate-rejected) is penalised the same
as a worker that crashes (FAILURE). The `RoutingOutcome` enum already distinguishes
SUCCESS, FAILURE, GATE_REJECTED, GATE_EXPIRED — and `CbrRoutingOutcomeRecorder` stores
`outcome.name()` as `stepOutcome` in `PlanTrace` — but the numeric CBR scoring path
is blind to the distinction.

### Design

Replace binary `successes / total` scoring with weighted scoring:

| Outcome | Weight | Rationale |
|---------|--------|-----------|
| `SUCCESS` | 1.0 | Fully successful |
| `GATE_EXPIRED` | 0.5 | Unreviewed — no evidence of quality either way. Oversight process failed, not the worker. Neutral weight. |
| `GATE_REJECTED` | 0.25 | Worker was capable enough to act, but a human explicitly judged the action inappropriate. Quarter credit. |
| `FAILURE` | 0.0 | Worker incompetence |

**Formula:** `weightedScore = sum(outcomeWeights) / totalObservations`

**Implementation:** Replace `int[] {successes, total}` accumulator in `analyseByType()`
with `double[] {weightedScore, total}`. Map `stepOutcome` strings to weights via a
private `Map<String, Double>` constant. Unknown outcomes default to 0.0 (defensive).

Selection logic (highest rate, ties broken by count) is unchanged.

### Scope

One method body: `CbrAgentRoutingStrategy.analyseByType()` (lines 235–279). Private method,
unchanged return type (`@Nullable String`). No API changes. No config changes.

### Breaking Changes

None. Method is private, return type unchanged, API unchanged.

---

## 2. TaskNode Hierarchy Evolution (#13)

### Problem

1. `PrimitiveTask` has no description — its identity is implicit from its `AgentRef`.
   Every other framework in the research landscape gives tasks human-readable identity.
2. `PrimitiveTask` entangles execution identity (agent) with HTN planning semantics
   (precondition, effect). LLM-decomposed tasks have identity but no HTN semantics.
3. No shared contract for "executable leaf task" — code that needs the executing agent
   must know the specific variant type.

### Design: LeafTask Sealed Interface

```
TaskNode<T>
├── LeafTask<T>            ← sealed, shared agent() + description() accessors
│   ├── PrimitiveTask<T>   ← HTN leaf (description + agent + precondition + effect)
│   └── PlannedTask<T>     ← LLM leaf (description + agent + rationale)
└── CompoundTask<T>        ← decomposable goal (name + methods)
```

#### TaskNode.java

```java
public sealed interface TaskNode<T>
        permits TaskNode.LeafTask, TaskNode.CompoundTask {

    sealed interface LeafTask<T> extends TaskNode<T>
            permits TaskNode.PrimitiveTask, TaskNode.PlannedTask {
        AgentRef agent();
        @Nullable String description();
    }

    record PrimitiveTask<T>(@Nullable String description, AgentRef agent,
                            @Nullable Predicate<T> precondition,
                            @Nullable Consumer<T> effect) implements LeafTask<T> {}

    record CompoundTask<T>(String name, List<DecompositionMethod<T>> methods)
            implements TaskNode<T> {
        public CompoundTask { methods = List.copyOf(methods); }
    }

    record PlannedTask<T>(String description, AgentRef agent,
                          @Nullable String rationale) implements LeafTask<T> {
        public PlannedTask {
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(agent, "agent");
        }
    }
}
```

#### Design Decisions

**`description` first in record components.** Identity before mechanism. Consistent
with `CompoundTask(String name, ...)`. All three variants lead with their identity field.

**`description` nullable on `PrimitiveTask`, non-null on `PlannedTask`.** Existing HTN
primitives don't have descriptions — forcing one would be busywork. LLM decomposition
always produces a description — enforced by the constructor.

**`LeafTask` as sealed interface with shared accessors.** Enables switches at two
granularities: `LeafTask` for "anything executable" or specific variant when the
distinction matters. Future additions (output contracts, DAG node metadata) attach
to `LeafTask` — the shared surface for all executable tasks.

**`rationale` on `PlannedTask`.** Captures why the LLM chose this agent. Useful for
observability and oversight display. Nullable — enrichment, not structure.

### Breaking Changes

| Location | Break | Fix |
|----------|-------|-----|
| `TaskNode` permits clause | Compile — sealed hierarchy changes | Update permits to `LeafTask`, `CompoundTask` |
| `HtnBuilder.flatten()` switch | Compile — non-exhaustive | Match `LeafTask` (not individual variants — forward-compatible with blocks#46) |
| `HtnBuilder.flatten()` return type | Compile — `List<PrimitiveTask>` → `List<LeafTask>` | Widen type, update agent extraction, update `@SuppressWarnings` cast to `List<LeafTask<T>>` |
| `Decomposition.primitive()` | Compile — `PrimitiveTask` constructor changes | Add `null` description as first arg |
| `TaskNodeTest` | Test — permitted subclass count | Update assertions |
| `*Test` PrimitiveTask constructors | Compile — new `description` parameter | Add `null` as first arg (~11 sites) |

All breaks are compile-time, all within blocks. Zero downstream consumer impact —
no repo references `TaskNode` or its variants directly.

---

## 3. LlmDecomposition Strategy (#13)

### Class: `LlmDecomposition<T>`

**Package:** `io.casehub.agency.agentic.decomposition`
**Implements:** `DecompositionStrategy<T>`
**Dependencies:** `AgentProvider`, `Function<T, String> stateRenderer`

### Constructor

```java
public LlmDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer)
public LlmDecomposition(AgentProvider agentProvider)  // defaults to Object::toString
```

Same pattern as `LlmSelectedRouting<T>`.

### Prompt Design

**System prompt:**
```
You are a task planner. Given a goal, current state, and available agents,
decompose the goal into a sequence of agent tasks.

Respond with JSON only: [{"agent": "<name>", "task": "<description>", "rationale": "<why>"}]

Each step should be a concrete action assigned to the agent best suited for it.
Order matters — steps execute sequentially.
```

**User prompt built from:**
1. Goal — `CompoundTask.name()`
2. Current state — `stateRenderer.apply(context.state())` (omitted if null)
3. Available agents — card per `RoutingCandidate` using `AgentCardSupport.buildCard()`
   and `AgentCardSupport.candidateName()`. Card includes: agent name (from descriptor,
   worker, or positional fallback), worker description, briefing, capabilities
   (with epistemic domains), and slot. Does not include health or running jobs —
   those are runtime routing concerns, not planning concerns.

#### AgentCardSupport extraction

`LlmSelectedRouting.buildAgentCard()` (private) and `candidateName()` (package-private)
are extracted into `AgentCardSupport` in `io.casehub.agency.agentic` — accessible to
both `routing` and `decomposition` sub-packages. `LlmSelectedRouting` is refactored
to delegate to `AgentCardSupport`. This avoids duplicating card-building and name-resolution
logic, and prepares for `HybridDecomposition` (#45) which will need the same utilities.

### Non-CompoundTask Input

If `decompose()` receives a non-`CompoundTask` (i.e. any `LeafTask`), return
`List.of(compound)` — consistent with `StaticDecomposition` and
`IdentityDecomposition`. The LLM is only invoked for `CompoundTask` goals.

### Response Parsing

Strip markdown code fences before parsing (LLMs frequently wrap JSON in
` ```json ... ``` ` despite instructions), then parse the JSON array using
Jackson `ObjectMapper.readTree()`. Both steps are consistent with
`RoutingSupport.extractAgentName()`. For each element:
1. Match `"agent"` against `DecompositionContext.agents()`:
   - First: `AgentDescriptor.name()`
   - Then: `WorkerAgent.worker().name()`
   - Then: positional fallback name
2. Unknown agent → skip step, log warning
3. No steps matched → return empty list
4. Map to `PlannedTask<T>(task, matchedAgentRef, rationale)`

### Error Handling

| Failure | Behaviour |
|---------|-----------|
| `AgentProvider` unavailable | Log warning, return empty list |
| LLM returns unparseable response | Log warning, return empty list |
| LLM returns empty plan | Return empty list |
| Some agent names unresolvable | Skip those steps, return partial plan |

`LlmDecomposition` does not escalate or retry. Empty decomposition results in
immediate termination of `HtnBuilder` with zero iterations — the current `flatten()`
termination condition (`iterationCount() >= agents.size()`) fires at 0 >= 0.
This is silent success with no work done. Improving empty-decomposition handling
(warning log, error escalation) is a pre-existing `HtnBuilder` concern, not
introduced by this spec.

### Integration

Plugs into the existing HTN method system:

```java
var model = Patterns.<MyState>htn()
    .rootTask(Decomposition.compound("investigate-fraud", List.of(
        Decomposition.method(s -> true, new LlmDecomposition<>(agentProvider))
    )))
    .agents(candidates)
    .execute(initialState);
```

A compound task can have a static method first and LLM as fallback — the foundation
for `HybridDecomposition` (#45).

### Decomposition Factory Additions

```java
// In Decomposition.java

public static <T> TaskNode.PrimitiveTask<T> primitive(String description, AgentRef agent) {
    return new TaskNode.PrimitiveTask<>(description, agent, null, null);
}

public static <T> TaskNode.PlannedTask<T> planned(String description, AgentRef agent) {
    return new TaskNode.PlannedTask<>(description, agent, null);
}

public static <T> TaskNode.PlannedTask<T> planned(String description, AgentRef agent,
                                                   String rationale) {
    return new TaskNode.PlannedTask<>(description, agent, rationale);
}
```

Existing `primitive(AgentRef)` factory signature preserved — callers don't break.

### Known Limitation: Eager Decomposition

`HtnBuilder.execute()` calls `flatten(rootTask, initialContext)` once before any
tasks execute. `LlmDecomposition` plans against initial state only — after the
first task executes and mutates state, subsequent tasks may no longer be
appropriate. The plan was optimised for a state that no longer exists.
Re-planning (engine#696) will address this.

### Out of Scope

- CDI / `@ApplicationScoped` — plain class, instantiated by caller
- Re-planning on failure — engine#696
- DAG output — returns `List<TaskNode<T>>` (linear). DAG is engine#694
- Output contracts per step — blocks#46

---

## 4. Testing Strategy

### 4.1 Weighted Outcomes (#43)

| Test | Verifies |
|------|----------|
| `successOnlyWorkerScoresOne` | All SUCCESS → 1.0 |
| `failureOnlyWorkerScoresZero` | All FAILURE → 0.0 |
| `gateExpiredCountsAsHalfCredit` | All GATE_EXPIRED → 0.5 |
| `gateRejectedCountsAsQuarterCredit` | All GATE_REJECTED → 0.25 |
| `mixedOutcomesWeightedCorrectly` | 2 SUCCESS + 1 GATE_REJECTED + 1 FAILURE → 0.5625 |
| `boldWorkerBeatsFailingWorker` | 50% SUCCESS + 50% GATE_REJECTED (0.625) beats 50% SUCCESS + 50% FAILURE (0.5) |
| `consistentSuccessBeatsGateRejected` | 100% SUCCESS (1.0) beats 100% GATE_REJECTED (0.25) |
| `crossCaseAccumulationWeightsCorrectly` | Same worker across 2 CBR cases: case1 SUCCESS + case2 GATE_REJECTED → (1.0 + 0.25) / 2 = 0.625 |
| `tiesBrokenByTotalCount` | Equal weighted score → higher observation count wins |
| `unknownOutcomeTreatedAsFailure` | Unrecognised `stepOutcome` → weight 0.0 |

### 4.2 TaskNode Hierarchy

| Test | Verifies |
|------|----------|
| `sealedInterfacePermitsLeafAndCompound` | `TaskNode` permits exactly 2 |
| `leafTaskPermitsPrimitiveAndPlanned` | `LeafTask` permits exactly 2 |
| `primitiveTaskCarriesDescriptionAndAgent` | Fields accessible, precondition/effect work |
| `primitiveTaskNullDescriptionAllowed` | Backward-compatible HTN usage |
| `plannedTaskRequiresDescriptionAndAgent` | Non-null enforced |
| `plannedTaskRejectsNullDescription` | NPE on null description |
| `plannedTaskRejectsNullAgent` | NPE on null agent |
| `leafTaskAgentAccessorWorks` | Polymorphic access for both variants |
| `leafTaskDescriptionAccessorWorks` | Polymorphic access for both variants |

### 4.3 LlmDecomposition

| Test | Verifies |
|------|----------|
| `decomposesGoalIntoPlannedTasks` | Happy path — valid JSON, agents matched, PlannedTasks created |
| `mapsAgentNamesByDescriptorName` | Primary matching via `AgentDescriptor.name()` |
| `mapsAgentNamesByWorkerName` | Fallback to `Worker.name()` |
| `skipsUnknownAgentNames` | Unmatched agent → step skipped, rest preserved |
| `returnsEmptyOnUnparseableResponse` | Garbage → empty list |
| `returnsEmptyWhenAgentProviderFails` | Exception → empty list |
| `returnsEmptyOnEmptyLlmPlan` | `[]` → empty list |
| `includesStateInPromptWhenPresent` | State renderer output in user prompt |
| `omitsStateFromPromptWhenNull` | Null state → no state section |
| `includesAgentCardsInPrompt` | Candidate metadata in prompt |
| `passesCompoundTaskNameAsGoal` | CompoundTask.name() as goal |
| `preservesTaskOrdering` | LLM response order → result order |
| `returnsInputUnchangedForNonCompoundTask` | LeafTask input → `List.of(input)`, no LLM call |
| `parsesCodeFenceWrappedJson` | Valid JSON wrapped in ` ```json ... ``` ` parses successfully |

### 4.4 Decomposition Factory

| Test | Verifies |
|------|----------|
| `plannedFactoryCreatesPlannedTask` | Null rationale default |
| `plannedFactoryWithRationale` | All fields carried |
| `primitiveFactoryBackwardCompatible` | `primitive(agent)` still works |
| `primitiveFactoryWithDescription` | Description carried |

---

## 5. File Change Summary

| File | Change Type | Issue |
|------|------------|-------|
| `CbrAgentRoutingStrategy.java` | Modify `analyseByType()` | #43 |
| `CbrAgentRoutingStrategyTest.java` | Add 10 weighted scoring tests | #43 |
| `TaskNode.java` | Restructure sealed hierarchy | #13 |
| `Decomposition.java` | Update `primitive()`, add `planned()` factories | #13 |
| `AgentCardSupport.java` | **New file** — extract `buildCard()` + `candidateName()` from `LlmSelectedRouting` | #13 |
| `LlmSelectedRouting.java` | Refactor to delegate to `AgentCardSupport` | #13 |
| `LlmDecomposition.java` | **New file** | #13 |
| `LlmDecompositionTest.java` | **New file** | #13 |
| `HtnBuilder.java` | Widen `flatten()` to `List<LeafTask>`, match `LeafTask` in switch | #13 |
| `TaskNodeTest.java` | Update hierarchy assertions, add PlannedTask tests | #13 |
| `StaticDecompositionTest.java` | Update PrimitiveTask constructors | #13 |
| `IdentityDecompositionTest.java` | Update PrimitiveTask constructor | #13 |
| `HtnBuilderTest.java` | Update PrimitiveTask constructors | #13 |
