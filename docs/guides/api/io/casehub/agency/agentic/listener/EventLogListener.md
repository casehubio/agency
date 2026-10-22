# io.casehub.agency.agentic.listener.EventLogListener

**Package:** `io.casehub.agency.agentic.listener`

**Kind:** `class`

Persists orchestration events to the engine's EventLog via a consumer-provided sink.
Records all events for operational observability.

<p>Sink implementations must be non-blocking — use in-memory buffers or async
queues. The sink is called synchronously from the driver loop; blocking I/O
in the sink stalls the execution.

## Fields

### `sink` (`io.casehub.agency.agentic.listener.EventLogListener.EventSink`)

## Constructors

### `public EventLogListener(io.casehub.agency.agentic.listener.EventLogListener.EventSink sink)`

#### Parameters

- `sink` (`io.casehub.agency.agentic.listener.EventLogListener.EventSink`)

## Methods

### `public void onActivation(io.casehub.agency.agentic.AgentRef agent, boolean activated)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `activated` (`boolean`)

### `public void onAgentDispatched(io.casehub.agency.agentic.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)

### `public void onAgentResult(io.casehub.agency.agentic.AgentResult result)`

#### Parameters

- `result` (`io.casehub.agency.agentic.AgentResult`)

### `public void onAggregation(io.casehub.agency.agentic.aggregation.AggregationResult result)`

#### Parameters

- `result` (`io.casehub.agency.agentic.aggregation.AggregationResult`)

### `public void onExecutionComplete(io.casehub.agency.agentic.model.ExecutionResult result, java.time.Duration executionDuration, int iterationCount)`

#### Parameters

- `result` (`io.casehub.agency.agentic.model.ExecutionResult`)
- `executionDuration` (`java.time.Duration`)
- `iterationCount` (`int`)

### `public void onExecutionStart(io.casehub.agency.agentic.model.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<?>`)

### `public void onFailure(io.casehub.agency.agentic.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `public void onRoutingDecision(io.casehub.agency.agentic.routing.RoutingDecision decision, java.util.List<io.casehub.agency.agentic.RoutingCandidate> candidates)`

#### Parameters

- `decision` (`io.casehub.agency.agentic.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.agency.agentic.RoutingCandidate>`)

### `public void onTermination(io.casehub.agency.agentic.termination.TerminationDecision decision)`

#### Parameters

- `decision` (`io.casehub.agency.agentic.termination.TerminationDecision`)

### `private static java.lang.String truncate(java.lang.String s, int max)`

#### Parameters

- `s` (`java.lang.String`)
- `max` (`int`)
