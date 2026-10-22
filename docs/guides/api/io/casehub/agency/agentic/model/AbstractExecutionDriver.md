# io.casehub.agency.orchestration.execution.AbstractExecutionDriver

**Package:** `io.casehub.agency.orchestration.execution`

**Kind:** `class`

Abstract base for execution drivers that composes the five agentic SPIs.
Extracts the common five-phase loop: route -> activate -> dispatch -> aggregate -> terminate.

<p>Tracks per-agent activation state across iterations and populates `ActivationContext`
with tracked data instead of hardcoded zeros. Calls `onExecutionStart` and
`onExecutionComplete` lifecycle hooks.

<p>Subclasses implement Object) to control iteration
vs. event-wait semantics.

## Fields

### `LOG` (`java.lang.System.Logger`)

### `activationCounts` (`java.util.Map<io.casehub.agency.core.AgentRef,java.lang.Integer>`)

### `cancelled` (`boolean`)

### `consecutiveIdleCounts` (`java.util.Map<io.casehub.agency.core.AgentRef,java.lang.Integer>`)

### `currentState` (`java.util.concurrent.atomic.AtomicReference<io.casehub.agency.orchestration.execution.ExecutionState>`)

### `executionStart` (`java.time.Instant`)

### `invoker` (`io.casehub.agency.orchestration.execution.AgentInvoker<T>`)

### `iterationCount` (`int`)

### `lastAggregationResult` (`io.casehub.agency.orchestration.aggregation.AggregationResult`)

## Constructors

### `protected AbstractExecutionDriver()`

### `protected AbstractExecutionDriver(io.casehub.agency.orchestration.execution.AgentInvoker<T> invoker)`

#### Parameters

- `invoker` (`io.casehub.agency.orchestration.execution.AgentInvoker<T>`)

## Methods

### `public Uni<java.lang.Void> cancel()`

### `protected java.util.List<io.casehub.agency.core.AgentResult> dispatchAgents(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, java.util.List<io.casehub.agency.core.AgentRef> agents, T context)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `agents` (`java.util.List<io.casehub.agency.core.AgentRef>`)
- `context` (`T`)

### `public final Uni<io.casehub.agency.orchestration.execution.ExecutionResult> execute(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, T initialContext)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `initialContext` (`T`)

### `protected io.casehub.agency.orchestration.execution.ExecutionResult executeIteration(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, T context, int iteration, java.time.Instant start, java.util.List<io.casehub.agency.core.AgentResult> allResults)`

Executes one iteration of the five-phase loop. Returns null for Continue,
otherwise returns the terminal ExecutionResult.

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `context` (`T`)
- `iteration` (`int`)
- `start` (`java.time.Instant`)
- `allResults` (`java.util.List<io.casehub.agency.core.AgentResult>`)

### `protected io.casehub.agency.core.AgentResult invokeAgent(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, io.casehub.agency.core.AgentRef agent, T context)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `agent` (`io.casehub.agency.core.AgentRef`)
- `context` (`T`)

### `protected boolean isCancelled()`

### `protected void notifyActivation(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, io.casehub.agency.core.AgentRef agent, boolean activated)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `agent` (`io.casehub.agency.core.AgentRef`)
- `activated` (`boolean`)

### `protected void notifyAgentDispatched(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, io.casehub.agency.core.AgentRef agent)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `agent` (`io.casehub.agency.core.AgentRef`)

### `protected void notifyAgentResult(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, io.casehub.agency.core.AgentResult result)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `result` (`io.casehub.agency.core.AgentResult`)

### `protected void notifyAggregation(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, io.casehub.agency.orchestration.aggregation.AggregationResult result)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `result` (`io.casehub.agency.orchestration.aggregation.AggregationResult`)

### `protected void notifyExecutionComplete(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, io.casehub.agency.orchestration.execution.ExecutionResult result)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `result` (`io.casehub.agency.orchestration.execution.ExecutionResult`)

### `protected void notifyExecutionStart(io.casehub.agency.orchestration.execution.ExecutionModel<T> model)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)

### `protected void notifyFailure(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, io.casehub.agency.core.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `agent` (`io.casehub.agency.core.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `protected void notifyRoutingDecision(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, io.casehub.agency.orchestration.routing.RoutingDecision decision, java.util.List<io.casehub.agency.core.RoutingCandidate> candidates)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `decision` (`io.casehub.agency.orchestration.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.agency.core.RoutingCandidate>`)

### `protected void notifyTermination(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, io.casehub.agency.orchestration.termination.TerminationDecision decision)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `decision` (`io.casehub.agency.orchestration.termination.TerminationDecision`)

### `protected abstract Uni<io.casehub.agency.orchestration.execution.ExecutionResult> runLoop(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, T context)`

Subclass-specific loop structure. OrchestratedDriver uses a while loop;
ChoreographedDriver transitions to WaitingForEvent between iterations.

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `context` (`T`)

### `public io.casehub.agency.orchestration.execution.ExecutionState state()`

### `protected void transition(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, io.casehub.agency.orchestration.execution.ExecutionState newState)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `newState` (`io.casehub.agency.orchestration.execution.ExecutionState`)
