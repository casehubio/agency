# io.casehub.agency.agentic.FailurePolicy

**Package:** `io.casehub.agency.agentic`

**Kind:** `record`

## Fields

### `agentRetry` (`io.casehub.agency.agentic.FailurePolicy.AgentRetryPolicy`)

### `onDeadlock` (`io.casehub.agency.agentic.FailurePolicy.AggregationFailureAction`)

### `onRoutingFailure` (`io.casehub.agency.agentic.FailurePolicy.RoutingFailureAction`)

## Record Components

### `agentRetry` (`io.casehub.agency.agentic.FailurePolicy.AgentRetryPolicy`)

### `onDeadlock` (`io.casehub.agency.agentic.FailurePolicy.AggregationFailureAction`)

### `onRoutingFailure` (`io.casehub.agency.agentic.FailurePolicy.RoutingFailureAction`)

## Constructors

### `public FailurePolicy(io.casehub.agency.agentic.FailurePolicy.RoutingFailureAction onRoutingFailure, io.casehub.agency.agentic.FailurePolicy.AggregationFailureAction onDeadlock, io.casehub.agency.agentic.FailurePolicy.AgentRetryPolicy agentRetry)`

#### Parameters

- `onRoutingFailure` (`io.casehub.agency.agentic.FailurePolicy.RoutingFailureAction`)
- `onDeadlock` (`io.casehub.agency.agentic.FailurePolicy.AggregationFailureAction`)
- `agentRetry` (`io.casehub.agency.agentic.FailurePolicy.AgentRetryPolicy`)

## Methods

### `public io.casehub.agency.agentic.FailurePolicy.AgentRetryPolicy agentRetry()`

### `public static io.casehub.agency.agentic.FailurePolicy defaults()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.agency.agentic.FailurePolicy.AggregationFailureAction onDeadlock()`

### `public io.casehub.agency.agentic.FailurePolicy.RoutingFailureAction onRoutingFailure()`

### `public final java.lang.String toString()`
