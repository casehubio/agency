# io.casehub.agency.core.FailurePolicy

**Package:** `io.casehub.agency.core`

**Kind:** `record`

## Fields

### `agentRetry` (`io.casehub.agency.core.FailurePolicy.AgentRetryPolicy`)

### `onDeadlock` (`io.casehub.agency.core.FailurePolicy.AggregationFailureAction`)

### `onRoutingFailure` (`io.casehub.agency.core.FailurePolicy.RoutingFailureAction`)

## Record Components

### `agentRetry` (`io.casehub.agency.core.FailurePolicy.AgentRetryPolicy`)

### `onDeadlock` (`io.casehub.agency.core.FailurePolicy.AggregationFailureAction`)

### `onRoutingFailure` (`io.casehub.agency.core.FailurePolicy.RoutingFailureAction`)

## Constructors

### `public FailurePolicy(io.casehub.agency.core.FailurePolicy.RoutingFailureAction onRoutingFailure, io.casehub.agency.core.FailurePolicy.AggregationFailureAction onDeadlock, io.casehub.agency.core.FailurePolicy.AgentRetryPolicy agentRetry)`

#### Parameters

- `onRoutingFailure` (`io.casehub.agency.core.FailurePolicy.RoutingFailureAction`)
- `onDeadlock` (`io.casehub.agency.core.FailurePolicy.AggregationFailureAction`)
- `agentRetry` (`io.casehub.agency.core.FailurePolicy.AgentRetryPolicy`)

## Methods

### `public io.casehub.agency.core.FailurePolicy.AgentRetryPolicy agentRetry()`

### `public static io.casehub.agency.core.FailurePolicy defaults()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.agency.core.FailurePolicy.AggregationFailureAction onDeadlock()`

### `public io.casehub.agency.core.FailurePolicy.RoutingFailureAction onRoutingFailure()`

### `public final java.lang.String toString()`
