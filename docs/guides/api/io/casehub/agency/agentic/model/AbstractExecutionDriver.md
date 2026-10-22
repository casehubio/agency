# io.casehub.agency.agentic.model.AbstractExecutionDriver

**Package:** `io.casehub.agency.agentic.model`

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

### `activationCounts` (`java.util.Map<io.casehub.agency.agentic.AgentRef,java.lang.Integer>`)

### `cancelled` (`boolean`)

### `consecutiveIdleCounts` (`java.util.Map<io.casehub.agency.agentic.AgentRef,java.lang.Integer>`)

### `currentState` (`java.util.concurrent.atomic.AtomicReference<io.casehub.agency.agentic.model.ExecutionState>`)

### `executionStart` (`java.time.Instant`)

### `invoker` (`io.casehub.agency.agentic.model.AgentInvoker<T>`)

### `iterationCount` (`int`)

### `lastAggregationResult` (`io.casehub.agency.agentic.aggregation.AggregationResult`)

## Constructors

### `protected AbstractExecutionDriver()`

### `protected AbstractExecutionDriver(io.casehub.agency.agentic.model.AgentInvoker<T> invoker)`

#### Parameters

- `invoker` (`io.casehub.agency.agentic.model.AgentInvoker<T>`)

## Methods

### `public Uni<java.lang.Void> cancel()`

### `protected java.util.List<io.casehub.agency.agentic.AgentResult> dispatchAgents(io.casehub.agency.agentic.model.ExecutionModel<T> model, java.util.List<io.casehub.agency.agentic.AgentRef> agents, T context)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `agents` (`java.util.List<io.casehub.agency.agentic.AgentRef>`)
- `context` (`T`)

### `public final Uni<io.casehub.agency.agentic.model.ExecutionResult> execute(io.casehub.agency.agentic.model.ExecutionModel<T> model, T initialContext)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `initialContext` (`T`)

### `protected io.casehub.agency.agentic.model.ExecutionResult executeIteration(io.casehub.agency.agentic.model.ExecutionModel<T> model, T context, int iteration, java.time.Instant start, java.util.List<io.casehub.agency.agentic.AgentResult> allResults)`

Executes one iteration of the five-phase loop. Returns null for Continue,
otherwise returns the terminal ExecutionResult.

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `context` (`T`)
- `iteration` (`int`)
- `start` (`java.time.Instant`)
- `allResults` (`java.util.List<io.casehub.agency.agentic.AgentResult>`)

### `protected io.casehub.agency.agentic.AgentResult invokeAgent(io.casehub.agency.agentic.model.ExecutionModel<T> model, io.casehub.agency.agentic.AgentRef agent, T context)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `context` (`T`)

### `protected boolean isCancelled()`

### `protected void notifyActivation(io.casehub.agency.agentic.model.ExecutionModel<T> model, io.casehub.agency.agentic.AgentRef agent, boolean activated)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `activated` (`boolean`)

### `protected void notifyAgentDispatched(io.casehub.agency.agentic.model.ExecutionModel<T> model, io.casehub.agency.agentic.AgentRef agent)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `agent` (`io.casehub.agency.agentic.AgentRef`)

### `protected void notifyAgentResult(io.casehub.agency.agentic.model.ExecutionModel<T> model, io.casehub.agency.agentic.AgentResult result)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `result` (`io.casehub.agency.agentic.AgentResult`)

### `protected void notifyAggregation(io.casehub.agency.agentic.model.ExecutionModel<T> model, io.casehub.agency.agentic.aggregation.AggregationResult result)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `result` (`io.casehub.agency.agentic.aggregation.AggregationResult`)

### `protected void notifyExecutionComplete(io.casehub.agency.agentic.model.ExecutionModel<T> model, io.casehub.agency.agentic.model.ExecutionResult result)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `result` (`io.casehub.agency.agentic.model.ExecutionResult`)

### `protected void notifyExecutionStart(io.casehub.agency.agentic.model.ExecutionModel<T> model)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)

### `protected void notifyFailure(io.casehub.agency.agentic.model.ExecutionModel<T> model, io.casehub.agency.agentic.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `protected void notifyRoutingDecision(io.casehub.agency.agentic.model.ExecutionModel<T> model, io.casehub.agency.agentic.routing.RoutingDecision decision, java.util.List<io.casehub.agency.agentic.RoutingCandidate> candidates)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `decision` (`io.casehub.agency.agentic.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.agency.agentic.RoutingCandidate>`)

### `protected void notifyTermination(io.casehub.agency.agentic.model.ExecutionModel<T> model, io.casehub.agency.agentic.termination.TerminationDecision decision)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `decision` (`io.casehub.agency.agentic.termination.TerminationDecision`)

### `protected abstract Uni<io.casehub.agency.agentic.model.ExecutionResult> runLoop(io.casehub.agency.agentic.model.ExecutionModel<T> model, T context)`

Subclass-specific loop structure. OrchestratedDriver uses a while loop;
ChoreographedDriver transitions to WaitingForEvent between iterations.

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `context` (`T`)

### `public io.casehub.agency.agentic.model.ExecutionState state()`

### `protected void transition(io.casehub.agency.agentic.model.ExecutionModel<T> model, io.casehub.agency.agentic.model.ExecutionState newState)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `newState` (`io.casehub.agency.agentic.model.ExecutionState`)
