# Orchestration Model Unification — Peer Coordination with Shared Types

**Date:** 2026-07-10
**Issue:** casehubio/engine#700
**Related:** casehubio/engine#101 (LLM supervisor mode), casehubio/agency#44 (agentic planning epic)
**Status:** Design reasoning. Captures the architectural argument for why
unification is necessary and what the shared model should look like.

## Implementation Status (updated 2026-07-12)

Phases 1 and 4 are complete. The shared types are in engine-api. Sections §3–4
below describe the pre-unification state that motivated this work — types
referenced there (`PlanItemStatus`, `AgentAssignment`, bare `workerName` String)
have been replaced by their shared equivalents.

| Phase | Status | What shipped |
|-------|--------|-------------|
| Phase 1 — shared identity | ✅ Done (engine#700) | `TaskDescriptor`, `ExecutorRef`, `TaskStatus`, `RoutingResult`, `Assignment`, `TaskSnapshot`, `OutcomeKind` in engine-api. `PlanItem` implements `TaskDescriptor` with `ExecutorRef`. |
| Phase 4 — Context Bridge | ✅ Done (engine#203) | `ContextBridge<T>` SPI with 3 built-in bridges (Map, POJO, JsonNode). `WorkerFunction<T>` parameterised. |
| Blocks adoption | Open (#50, #51, #52) | `AgentRef extends ExecutorRef` (#50), `LeafTask extends TaskDescriptor` (#51, scope widened from PlannedTask-only), `SubTaskStatus → TaskStatus` (#52) |
| Phase 2 — DAG plan | Open (engine#694, #695) | Not started |
| Phase 3 — CoordinationStrategy | Future | Depends on Phase 2 |
| Phase 5 — retire layering | Future | Depends on Phases 2–3 |

**Design evolution:** §6.1 proposed `TaskIdentity` as a shared record. Engine shipped
`TaskDescriptor` as an interface instead — better, because both `PlanItem` (mutable)
and `LeafTask` (immutable) can implement the same contract without forcing one lifecycle
model. §6.1 also proposed separate `PlanTask`/`ExecutionTask` types — engine unified
these under `TaskDescriptor` as a single interface with `status()` distinguishing
lifecycle stage.

**#51 scope note:** After first-principles review, widened from "PlannedTask implements
TaskDescriptor" to "LeafTask extends TaskDescriptor" — both leaf variants are units of
work that should be monitorable. `LeafTask` provides a default `executor()` delegating
to `agent()` to bridge the agentic and shared vocabularies.

---

## 1. The Problem: Three Models, One Question

CaseHub has three coordination models that all answer the same question:
*"Given a goal, how do I coordinate work across multiple agents?"*

| Model | Home | Planning | Execution | State |
|-------|------|----------|-----------|-------|
| **Blackboard** | engine | `PlanningStrategy` selects `Binding`s reactively | Quartz dispatch → `PlanItem` lifecycle | `CasePlanModel` (persistent, mutable) |
| **Serverless Workflow** | engine-flow | YAML pipeline declaration | Step-by-step execution via `FlowWorkerFunction` | Workflow state (CNCF spec) |
| **Agentic patterns** | blocks | `DecompositionStrategy` breaks goals into tasks | `ExecutionDriver` (orchestrated / choreographed) | `ExecutionModel<T>` (in-memory, typed) |

They evolved independently. Blackboard came first (engine's core). Serverless
Workflow was added for declarative pipelines. Agentic patterns were built in
blocks for "patterns that need LLM in the loop." The result: parallel type
hierarchies that duplicate the same concepts at different abstraction levels.

### Why This Is a Problem Now

engine#101 (LLM supervisor mode) identified three gaps. Gap 3 — goal-to-plan
decomposition — was implemented in blocks as `LlmDecomposition` (#13). Gap 1 —
LLM-backed `PlanningStrategy` — would be implemented in engine. Without
unification, these become two LLM decomposition paths with two prompt patterns,
two response parsers, and no shared infrastructure. The duplication has already
started.

---

## 2. The Layering Is Historical, Not Architectural

blocks depends on engine-api. This creates a dependency arrow:

```
blocks → engine-api → engine
```

The dependency arrow was read as an abstraction layer: blocks sits "above" engine.
But this is backwards — the dependency exists because blocks needs engine-api's
types (`AgentRoutingContext`, `RoutingOutcome`, etc.), not because blocks is a
higher-level abstraction.

**All three models are peers.** They differ in planning strategy (reactive vs
declarative vs compositional) and execution style (event-driven vs pipeline vs
driver-based), but they operate at the same conceptual level: coordinating work
across agents.

Being peers does not mean same repo or same module. It means shared types at the
foundation, with each model implementing its own planning and execution strategy
on top.

---

## 3. What They Share — The Common Concepts

Every coordination model produces and manages the same fundamental concepts:

### 3.1 Task — A Unit of Work

Something that needs to be done, assigned to someone.

| Concept | Blackboard | Agentic | Worker-API |
|---------|-----------|---------|------------|
| Type | `PlanItem` | `TaskNode.LeafTask` | `PlannedAction` |
| Description | `bindingName` (no NL description) | `description` (String) | `description` (String) |
| Executor | `workerName` (String) | `agent` (AgentRef — typed) | *(implicit — the worker)* |
| Lifecycle | `PlanItemStatus` (mutable, CAS) | *(none — immutable)* | *(none — immutable)* |
| Priority | `priority` (int) | *(none)* | *(none)* |
| Persistence | JPA entity | In-memory | In-memory |

**The `description` thread is broken.** `PlannedTask.description` (what the LLM
planner intended) → `PlanItem.???` (no description field — intent is lost) →
`PlannedAction.description` (what the worker proposes to do). The plan-time
intent disappears at the execution layer.

### 3.2 Plan — A Collection of Tasks

The structure that organises tasks — ordering, dependencies, grouping.

| Concept | Blackboard | Agentic |
|---------|-----------|---------|
| Type | `CasePlanModel` (agenda + stages + milestones) | `ExecutionModel<T>` (strategies + agents) |
| Structure | Priority-sorted agenda, stage containment | Linear list (from `DecompositionStrategy`) |
| Dependencies | Implicit via stage gating | None (sequential execution) |
| Parallelism | Concurrent PlanItems within a stage | None |

Both are too limited. The research landscape (Graph Harness, LLMCompiler, GAP)
shows that DAG-structured plans with explicit dependency edges and join semantics
are the state of the art.

### 3.3 Executor — Who Does the Work

| Concept | Blackboard | Agentic |
|---------|-----------|---------|
| Type | Worker name (String) + `BindingTarget` | `AgentRef` (sealed: Worker, Channel, Human, External, Composed) |
| Identity | `workerName` | `AgentDescriptor` (name, briefing, capabilities, slot, jurisdiction) |
| Resolution | `WorkerProvisioner` SPI | `RoutingStrategy<T>` |

The agentic model's `AgentRef` is richer — it distinguishes five kinds of
executor and carries typed metadata via `AgentDescriptor`. The blackboard's
`workerName` is a string. The shared model should use the richer representation.

### 3.4 Lifecycle — How Work Progresses

| State | Blackboard (`PlanItemStatus`) | Agentic (`ExecutionState`) |
|-------|------------------------------|---------------------------|
| Waiting | PENDING | Idle |
| Running | RUNNING | Running |
| Delegated | DELEGATED | WaitingForAgent / WaitingForEvent |
| Paused | SUSPENDED | *(none)* |
| Done | COMPLETED | Complete |
| Failed | FAULTED | Faulted |
| Refused | REJECTED | *(none)* |
| Irrelevant | OBSOLETE | *(none)* |
| Stopped | CANCELLED | Cancelled |

The blackboard lifecycle is richer (9 states, persistent). The agentic lifecycle
is simpler (7 states, transient). A shared lifecycle needs the blackboard's
expressiveness with the agentic model's sealed-variant type safety.

---

## 4. The Lifecycle Distinction That Matters

Not all three models operate at the same lifecycle stage. This is a real
architectural distinction, not a historical accident:

```
Plan-time (immutable, what should happen)
    │
    │  PlannedTask, CompoundTask — produced by DecompositionStrategy
    │  The plan is a proposal. It can be discarded, replaced, or versioned.
    │
    ▼
Execution-time (mutable, what is happening)
    │
    │  PlanItem — produced by PlanningStrategy, tracked by CasePlanModel
    │  The plan item is live. It transitions through states, persists across
    │  JVM restarts, can be suspended and resumed.
    │
    ▼
Action-time (immutable, what the worker proposes to do)
    │
    │  PlannedAction — produced by the worker, evaluated by oversight gates
    │  A specific proposed action within the scope of an executing task.
    │
    ▼
Outcome-time (immutable, what happened)
    │
    │  RoutingOutcome — SUCCESS, FAILURE, GATE_REJECTED, GATE_EXPIRED
    │  Recorded by RoutingOutcomeRecorder, fed back into CBR routing.
```

**This is not three duplicates. It is four lifecycle stages of the same work.**

The shared type model must encode this explicitly. A `Task` interface
that covers all four stages would be too broad — it would force plan-time
immutability and execution-time mutability into the same type. Instead:

```
TaskIdentity (shared — immutable, carries through all stages)
├── description: String
├── executorRef: AgentRef
└── taskId: String

PlanTask extends TaskIdentity (plan-time — immutable, replaceable)
├── rationale: @Nullable String
├── dependencies: Set<String>  (task IDs this depends on)
└── outputContract: @Nullable OutputSchema

ExecutionTask wraps TaskIdentity (execution-time — mutable, persistent)
├── status: TaskLifecycle (sealed states)
├── priority: int
├── parentStageId: @Nullable String
└── createdAt: Instant

ProposedAction (action-time — immutable, gated)
├── description: String
├── actionType: String
└── parameters: Map<String, Object>

TaskOutcome (outcome-time — immutable, recorded)
├── outcome: RoutingOutcome
├── executionDuration: Duration
└── taskId: String
```

`TaskIdentity` is the thread. It flows from plan-time through execution-time
to outcome-time. The `description` never gets lost because it's on the shared
identity, not on a stage-specific type.

---

## 5. Recursive Nesting — The Context Bridge Pattern

Coordination modes must be recursively composable:

```
Blackboard case
 └── PlanItem dispatches a worker
      └── Worker uses HTN pattern (blocks)
           └── HTN step triggers a sub-case (blackboard again)
                └── Sub-case PlanItem uses a Debate pattern
                     └── ...
```

This already happens: `AgentRef.ComposedAgent` wraps an `ExecutionModel` (an
agentic pattern is itself an agent), and `SubCaseTarget` spawns blackboard
instances from other blackboard instances.

**The Context Bridge makes nesting transparent.** When one coordination mode
nests inside another, the parent's context must propagate through the boundary:

- Case context (the shared blackboard state)
- Tenancy and security scope
- Tracing spans (OpenTelemetry)
- Oversight scope (which gates apply at this nesting level)

The bridge must be **type-safe**. Not a `Map<String, Object>` bag — the typed
generic `T` in `ExecutionModel<T>` and `DecompositionContext<T>` must be
preserved through nesting boundaries. A blackboard case with `CaseContext` as
its state type should be able to nest an agentic pattern with `CaseContext` as
its `T` — the types align, the state flows, the oversight gates compose.

```java
// Type-safe nesting: blackboard → agentic → blackboard
interface ContextBridge<PARENT, CHILD> {
    CHILD adapt(PARENT parentContext);
    void propagateResult(CHILD childResult, PARENT parentContext);
}
```

---

## 6. What the Shared Model Looks Like

### 6.1 Shared Types (engine-api or new shared module)

```java
// The identity thread — immutable, flows through all lifecycle stages
record TaskIdentity(String taskId, String description, AgentRef executorRef) {}

// Plan-time — what should happen (immutable, replaceable)
sealed interface PlanNode<T>
        permits PlanNode.LeafPlan, PlanNode.CompositePlan {

    sealed interface LeafPlan<T> extends PlanNode<T> {
        TaskIdentity identity();
    }

    record PrimitivePlan<T>(TaskIdentity identity,
                            @Nullable Predicate<T> precondition,
                            @Nullable Consumer<T> effect) implements LeafPlan<T> {}

    record PlannedPlan<T>(TaskIdentity identity,
                          @Nullable String rationale) implements LeafPlan<T> {}

    record CompositePlan<T>(String name,
                            List<DecompositionMethod<T>> methods) implements PlanNode<T> {}
}

// Execution plan — DAG of plan nodes with dependencies
record ExecutionPlan<T>(Map<String, PlanNode<T>> nodes,
                        Set<Dependency> edges,
                        String rootId) {
    enum JoinType { ALL_OF, ANY_OF }
    record Dependency(String from, String to, JoinType join) {}
}
```

### 6.2 Coordination Strategies (peers, not layers)

```java
// All coordination strategies produce ExecutionPlan from a goal
interface CoordinationStrategy<T> {
    Uni<ExecutionPlan<T>> plan(String goal, CoordinationContext<T> context);
}

// Blackboard: reactive binding selection → plan
class BlackboardStrategy<T> implements CoordinationStrategy<T> { ... }

// Workflow: YAML declaration → plan
class WorkflowStrategy<T> implements CoordinationStrategy<T> { ... }

// HTN: hierarchical decomposition → plan
class HtnStrategy<T> implements CoordinationStrategy<T> { ... }

// LLM: goal-to-plan via language model
class LlmStrategy<T> implements CoordinationStrategy<T> { ... }

// Hybrid: try static first, fall back to LLM
class HybridStrategy<T> implements CoordinationStrategy<T> { ... }
```

### 6.3 Execution Drivers (peers, not layers)

```java
// All drivers execute an ExecutionPlan
interface ExecutionDriver<T> {
    Uni<ExecutionResult<T>> execute(ExecutionPlan<T> plan, T initialState);
}

// Sequential: one task at a time
class SequentialDriver<T> implements ExecutionDriver<T> { ... }

// DAG/Parallel: topological dispatch, concurrent independent tasks
class ParallelDriver<T> implements ExecutionDriver<T> { ... }

// Choreographed: peer-to-peer event-driven
class ChoreographedDriver<T> implements ExecutionDriver<T> { ... }

// Blackboard loop: PlanningStrategy re-evaluation after each completion
class BlackboardDriver<T> implements ExecutionDriver<T> { ... }
```

### 6.4 The Composition

Any coordination strategy can be paired with any execution driver:

```
BlackboardStrategy + BlackboardDriver     = current blackboard behaviour
WorkflowStrategy   + SequentialDriver     = current Serverless Workflow
HtnStrategy        + SequentialDriver     = current HtnBuilder
LlmStrategy        + ParallelDriver       = LLM plans with DAG execution
HybridStrategy     + ParallelDriver       = ChatHTN-style with parallelism
BlackboardStrategy + ParallelDriver       = blackboard with concurrent stages
```

This is the architectural power of the peer model: strategies and drivers
compose freely. The current layered model locks each coordination mode to its
own driver.

---

## 7. Migration Path

### Phase 1 — Shared identity (non-breaking)

Add `TaskIdentity` to engine-api. Add `description` field to `PlanItem`.
The identity thread flows from `PlannedTask.description` through
`PlanItem.description` to `PlannedAction.description`. No existing code breaks.

### Phase 2 — ExecutionPlan (engine#694)

Introduce `ExecutionPlan<T>` with DAG structure. `DecompositionStrategy`
returns `ExecutionPlan<T>` instead of `List<TaskNode<T>>`. Existing strategies
get adapter methods that wrap their list output in a linear plan. No existing
consumer code breaks — adapters handle the transition.

### Phase 3 — CoordinationStrategy (engine#700)

Extract the common `CoordinationStrategy<T>` interface. Existing
`PlanningStrategy`, `DecompositionStrategy`, and workflow execution become
implementations. The blackboard loop becomes a `BlackboardDriver` rather than
a hardcoded control loop.

### Phase 4 — Context Bridge

Implement type-safe nesting. `ContextBridge<PARENT, CHILD>` adapts between
coordination mode boundaries. Tracing, tenancy, and oversight scope propagate
transparently.

### Phase 5 — Retire the layering

blocks' `ExecutionModel<T>` is replaced by `ExecutionPlan<T>` +
`CoordinationStrategy<T>` + `ExecutionDriver<T>`. The pattern builders
(`Patterns.htn()`, `Patterns.supervisor()`, etc.) remain as convenience APIs
but produce `ExecutionPlan<T>` instead of `ExecutionModel<T>`. blocks becomes
a library of coordination strategies and pattern builders — no longer a
separate orchestration framework.

---

## 8. What This Prevents

Without unification, the following duplication is imminent:

| Capability | blocks implementation | engine implementation (planned) | Shared? |
|-----------|---------------------|-------------------------------|---------|
| LLM decomposition | `LlmDecomposition` → `PlannedTask` | `LlmPlanningStrategy` → `List<Binding>` (engine#101 Gap 1) | No |
| Goal description | `PlannedTask.description` | *(missing on PlanItem)* | No |
| Agent card building | `AgentCardSupport` | *(would be duplicated for LlmPlanningStrategy)* | No |
| Outcome recording | `CbrRoutingOutcomeRecorder` | *(would need engine-side equivalent)* | Partial |
| Trust classification | `RoutingSupport.applyTrustFilter()` | `TrustCandidateClassifier` | Yes (shared via engine-ledger) |

Row 1 is the critical one. Two LLM decomposition implementations serving the
same purpose is the textbook sign that a shared abstraction is missing.

---

## 9. Design Tensions to Resolve

### 9.1 Mutable vs Immutable

`PlanItem` has CAS state transitions (`tryMarkRunning`, `markCompleted`).
`PlannedTask` is an immutable record.

**Resolution:** Plan-time types are immutable. Execution-time types are mutable.
`TaskIdentity` (shared) is immutable — it's the thread, not the state.
`ExecutionTask` (execution-time) wraps `TaskIdentity` and adds mutable lifecycle.

### 9.2 Persistent vs Transient

`PlanItem` persists to JPA and survives JVM restarts. `LeafTask` is in-memory.

**Resolution:** Persistence is an execution-time concern. `ExecutionTask` has a
persistence SPI (`TaskStore`). Plan-time types don't need persistence — the plan
can be regenerated from the goal + strategy.

### 9.3 Typed State vs Untyped State

blocks' `ExecutionModel<T>` preserves the state type through the entire pipeline.
engine's `CaseContext` is `JsonNode`-based (structurally typed but not Java-typed).

**Resolution:** The shared model uses generics (`<T>`). When `T = JsonNode`, it
composes with engine's existing CaseContext. When `T` is a domain record, it
composes with blocks' typed patterns. The Context Bridge adapts between them.

---

## 10. Sources

- [Engine#700](https://github.com/casehubio/engine/issues/700) — the tracking issue
- [Engine#101](https://github.com/casehubio/engine/issues/101) — LLM supervisor mode (overlap)
- [Blocks#44](https://github.com/casehubio/agency/issues/44) — agentic planning epic
- [Research landscape](docs/research/2026-07-09-task-decomposition-and-agent-planning-landscape.md) — framework survey
- [Graph Harness](https://arxiv.org/html/2604.11378v1) — DAG plan model, plan versioning
- [LLMCompiler](https://arxiv.org/abs/2312.04511) — parallel task execution via DAG
- [ChatHTN](https://arxiv.org/html/2505.11814) — hybrid symbolic + LLM planning
