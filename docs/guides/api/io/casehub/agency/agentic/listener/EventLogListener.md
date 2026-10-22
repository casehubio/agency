# io.casehub.agency.orchestration.listener.EventLogListener

**Package:** `io.casehub.agency.orchestration.listener`

**Kind:** `class`

Persists orchestration events to the engine's EventLog via a consumer-provided sink.
Records all events for operational observability.

<p>Sink implementations must be non-blocking — use in-memory buffers or async
queues. The sink is called synchronously from the driver loop; blocking I/O
in the sink stalls the execution.

## Fields

### `sink` (`io.casehub.agency.orchestration.listener.EventLogListener.EventSink`)

## Constructors

### `public EventLogListener(io.casehub.agency.orchestration.listener.EventLogListener.EventSink sink)`

#### Parameters

- `sink` (`io.casehub.agency.orchestration.listener.EventLogListener.EventSink`)

## Methods

### `public void onActivation(io.casehub.agency.core.AgentRef agent, boolean activated)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `activated` (`boolean`)

### `public void onAgentDispatched(io.casehub.agency.core.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)

### `public void onAgentResult(io.casehub.agency.core.AgentResult result)`

#### Parameters

- `result` (`io.casehub.agency.core.AgentResult`)

### `public void onAggregation(io.casehub.agency.orchestration.aggregation.AggregationResult result)`

#### Parameters

- `result` (`io.casehub.agency.orchestration.aggregation.AggregationResult`)

### `public void onExecutionComplete(io.casehub.agency.orchestration.execution.ExecutionResult result, java.time.Duration executionDuration, int iterationCount)`

#### Parameters

- `result` (`io.casehub.agency.orchestration.execution.ExecutionResult`)
- `executionDuration` (`java.time.Duration`)
- `iterationCount` (`int`)

### `public void onExecutionStart(io.casehub.agency.orchestration.execution.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<?>`)

### `public void onFailure(io.casehub.agency.core.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `public void onRoutingDecision(io.casehub.agency.orchestration.routing.RoutingDecision decision, java.util.List<io.casehub.agency.core.RoutingCandidate> candidates)`

#### Parameters

- `decision` (`io.casehub.agency.orchestration.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.agency.core.RoutingCandidate>`)

### `public void onTermination(io.casehub.agency.orchestration.termination.TerminationDecision decision)`

#### Parameters

- `decision` (`io.casehub.agency.orchestration.termination.TerminationDecision`)

### `private static java.lang.String truncate(java.lang.String s, int max)`

#### Parameters

- `s` (`java.lang.String`)
- `max` (`int`)
