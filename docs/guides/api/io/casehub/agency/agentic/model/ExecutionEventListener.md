# io.casehub.agency.orchestration.execution.ExecutionEventListener

**Package:** `io.casehub.agency.orchestration.execution`

**Kind:** `interface`

## Methods

### `public static java.lang.String agentName(io.casehub.agency.core.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)

### `public default void onActivation(io.casehub.agency.core.AgentRef agent, boolean activated)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `activated` (`boolean`)

### `public default void onAgentDispatched(io.casehub.agency.core.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)

### `public default void onAgentResult(io.casehub.agency.core.AgentResult result)`

#### Parameters

- `result` (`io.casehub.agency.core.AgentResult`)

### `public default void onAggregation(io.casehub.agency.orchestration.aggregation.AggregationResult result)`

#### Parameters

- `result` (`io.casehub.agency.orchestration.aggregation.AggregationResult`)

### `public default void onExecutionComplete(io.casehub.agency.orchestration.execution.ExecutionResult result, java.time.Duration executionDuration, int iterationCount)`

#### Parameters

- `result` (`io.casehub.agency.orchestration.execution.ExecutionResult`)
- `executionDuration` (`java.time.Duration`)
- `iterationCount` (`int`)

### `public default void onExecutionStart(io.casehub.agency.orchestration.execution.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<?>`)

### `public default void onFailure(io.casehub.agency.core.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `public default void onRoutingDecision(io.casehub.agency.orchestration.routing.RoutingDecision decision, java.util.List<io.casehub.agency.core.RoutingCandidate> candidates)`

#### Parameters

- `decision` (`io.casehub.agency.orchestration.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.agency.core.RoutingCandidate>`)

### `public default void onStateTransition(io.casehub.agency.orchestration.execution.ExecutionState from, io.casehub.agency.orchestration.execution.ExecutionState to)`

#### Parameters

- `from` (`io.casehub.agency.orchestration.execution.ExecutionState`)
- `to` (`io.casehub.agency.orchestration.execution.ExecutionState`)

### `public default void onTermination(io.casehub.agency.orchestration.termination.TerminationDecision decision)`

#### Parameters

- `decision` (`io.casehub.agency.orchestration.termination.TerminationDecision`)
