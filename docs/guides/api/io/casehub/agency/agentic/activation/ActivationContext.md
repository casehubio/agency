# io.casehub.agency.orchestration.activation.ActivationContext

**Package:** `io.casehub.agency.orchestration.activation`

**Kind:** `record`

## Fields

### `activationCount` (`int`)

### `agent` (`io.casehub.agency.core.AgentRef`)

### `consecutiveIdleActivations` (`int`)

### `event` (`java.lang.Object`)

### `lastAggregationResult` (`java.util.Optional<io.casehub.agency.orchestration.aggregation.AggregationResult>`)

### `state` (`T`)

## Record Components

### `activationCount` (`int`)

### `agent` (`io.casehub.agency.core.AgentRef`)

### `consecutiveIdleActivations` (`int`)

### `event` (`java.lang.Object`)

### `lastAggregationResult` (`java.util.Optional<io.casehub.agency.orchestration.aggregation.AggregationResult>`)

### `state` (`T`)

## Constructors

### `public ActivationContext(java.lang.Object event, T state, io.casehub.agency.core.AgentRef agent, int activationCount, java.util.Optional<io.casehub.agency.orchestration.aggregation.AggregationResult> lastAggregationResult, int consecutiveIdleActivations)`

#### Parameters

- `event` (`java.lang.Object`)
- `state` (`T`)
- `agent` (`io.casehub.agency.core.AgentRef`)
- `activationCount` (`int`)
- `lastAggregationResult` (`java.util.Optional<io.casehub.agency.orchestration.aggregation.AggregationResult>`)
- `consecutiveIdleActivations` (`int`)

## Methods

### `public int activationCount()`

### `public io.casehub.agency.core.AgentRef agent()`

### `public int consecutiveIdleActivations()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.Object event()`

### `public final int hashCode()`

### `public java.util.Optional<io.casehub.agency.orchestration.aggregation.AggregationResult> lastAggregationResult()`

### `public T state()`

### `public final java.lang.String toString()`
