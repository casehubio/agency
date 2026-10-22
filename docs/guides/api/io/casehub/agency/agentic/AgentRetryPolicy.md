# io.casehub.agency.agentic.FailurePolicy.AgentRetryPolicy

**Package:** `io.casehub.agency.agentic`

**Kind:** `record`

## Fields

### `backoff` (`java.time.Duration`)

### `backoffStrategy` (`io.casehub.agency.agentic.FailurePolicy.BackoffStrategy`)

### `maxRetries` (`int`)

### `onExhausted` (`io.casehub.agency.agentic.FailurePolicy.AgentFailureAction`)

## Record Components

### `backoff` (`java.time.Duration`)

### `backoffStrategy` (`io.casehub.agency.agentic.FailurePolicy.BackoffStrategy`)

### `maxRetries` (`int`)

### `onExhausted` (`io.casehub.agency.agentic.FailurePolicy.AgentFailureAction`)

## Constructors

### `public AgentRetryPolicy(int maxRetries, java.time.Duration backoff, io.casehub.agency.agentic.FailurePolicy.BackoffStrategy backoffStrategy, io.casehub.agency.agentic.FailurePolicy.AgentFailureAction onExhausted)`

#### Parameters

- `maxRetries` (`int`)
- `backoff` (`java.time.Duration`)
- `backoffStrategy` (`io.casehub.agency.agentic.FailurePolicy.BackoffStrategy`)
- `onExhausted` (`io.casehub.agency.agentic.FailurePolicy.AgentFailureAction`)

## Methods

### `public java.time.Duration backoff()`

### `public io.casehub.agency.agentic.FailurePolicy.BackoffStrategy backoffStrategy()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int maxRetries()`

### `public io.casehub.agency.agentic.FailurePolicy.AgentFailureAction onExhausted()`

### `public final java.lang.String toString()`
