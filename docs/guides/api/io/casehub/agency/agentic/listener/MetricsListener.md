# io.casehub.agency.orchestration.listener.MetricsListener

**Package:** `io.casehub.agency.orchestration.listener`

**Kind:** `class`

## Fields

### `ACTIVATED` (`AttributeKey<java.lang.String>`)

### `AGENT` (`AttributeKey<java.lang.String>`)

### `DECISION_TYPE` (`AttributeKey<java.lang.String>`)

### `OUTCOME` (`AttributeKey<java.lang.String>`)

### `STATUS` (`AttributeKey<java.lang.String>`)

### `activationEvaluations` (`LongCounter`)

### `agentDuration` (`DoubleHistogram`)

### `agentFailures` (`LongCounter`)

### `executionDuration` (`DoubleHistogram`)

### `executionIterations` (`DoubleHistogram`)

### `routingDecisions` (`LongCounter`)

## Constructors

### `public MetricsListener(Meter meter)`

#### Parameters

- `meter` (`Meter`)

## Methods

### `public void onActivation(io.casehub.agency.core.AgentRef agent, boolean activated)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `activated` (`boolean`)

### `public void onAgentResult(io.casehub.agency.core.AgentResult result)`

#### Parameters

- `result` (`io.casehub.agency.core.AgentResult`)

### `public void onExecutionComplete(io.casehub.agency.orchestration.execution.ExecutionResult result, java.time.Duration executionDuration, int iterationCount)`

#### Parameters

- `result` (`io.casehub.agency.orchestration.execution.ExecutionResult`)
- `executionDuration` (`java.time.Duration`)
- `iterationCount` (`int`)

### `public void onFailure(io.casehub.agency.core.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `public void onRoutingDecision(io.casehub.agency.orchestration.routing.RoutingDecision decision, java.util.List<io.casehub.agency.core.RoutingCandidate> candidates)`

#### Parameters

- `decision` (`io.casehub.agency.orchestration.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.agency.core.RoutingCandidate>`)
