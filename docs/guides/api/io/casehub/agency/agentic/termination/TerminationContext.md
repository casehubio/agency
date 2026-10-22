# io.casehub.agency.agentic.termination.TerminationContext

**Package:** `io.casehub.agency.agentic.termination`

**Kind:** `record`

## Fields

### `elapsed` (`java.time.Duration`)

### `iterationCount` (`int`)

### `results` (`java.util.List<io.casehub.agency.agentic.AgentResult>`)

### `state` (`T`)

## Record Components

### `elapsed` (`java.time.Duration`)

### `iterationCount` (`int`)

### `results` (`java.util.List<io.casehub.agency.agentic.AgentResult>`)

### `state` (`T`)

## Constructors

### `public TerminationContext(T state, int iterationCount, java.time.Duration elapsed, java.util.List<io.casehub.agency.agentic.AgentResult> results)`

#### Parameters

- `state` (`T`)
- `iterationCount` (`int`)
- `elapsed` (`java.time.Duration`)
- `results` (`java.util.List<io.casehub.agency.agentic.AgentResult>`)

## Methods

### `public java.time.Duration elapsed()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int iterationCount()`

### `public java.util.List<io.casehub.agency.agentic.AgentResult> results()`

### `public T state()`

### `public final java.lang.String toString()`
