# io.casehub.agency.agentic.model.ExecutionEventListener

**Package:** `io.casehub.agency.agentic.model`

**Kind:** `interface`

## Methods

### `public static java.lang.String agentName(io.casehub.agency.agentic.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)

### `public default void onActivation(io.casehub.agency.agentic.AgentRef agent, boolean activated)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `activated` (`boolean`)

### `public default void onAgentDispatched(io.casehub.agency.agentic.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)

### `public default void onAgentResult(io.casehub.agency.agentic.AgentResult result)`

#### Parameters

- `result` (`io.casehub.agency.agentic.AgentResult`)

### `public default void onAggregation(io.casehub.agency.agentic.aggregation.AggregationResult result)`

#### Parameters

- `result` (`io.casehub.agency.agentic.aggregation.AggregationResult`)

### `public default void onExecutionComplete(io.casehub.agency.agentic.model.ExecutionResult result, java.time.Duration executionDuration, int iterationCount)`

#### Parameters

- `result` (`io.casehub.agency.agentic.model.ExecutionResult`)
- `executionDuration` (`java.time.Duration`)
- `iterationCount` (`int`)

### `public default void onExecutionStart(io.casehub.agency.agentic.model.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<?>`)

### `public default void onFailure(io.casehub.agency.agentic.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `public default void onRoutingDecision(io.casehub.agency.agentic.routing.RoutingDecision decision, java.util.List<io.casehub.agency.agentic.RoutingCandidate> candidates)`

#### Parameters

- `decision` (`io.casehub.agency.agentic.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.agency.agentic.RoutingCandidate>`)

### `public default void onStateTransition(io.casehub.agency.agentic.model.ExecutionState from, io.casehub.agency.agentic.model.ExecutionState to)`

#### Parameters

- `from` (`io.casehub.agency.agentic.model.ExecutionState`)
- `to` (`io.casehub.agency.agentic.model.ExecutionState`)

### `public default void onTermination(io.casehub.agency.agentic.termination.TerminationDecision decision)`

#### Parameters

- `decision` (`io.casehub.agency.agentic.termination.TerminationDecision`)
