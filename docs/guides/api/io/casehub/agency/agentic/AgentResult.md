# io.casehub.agency.agentic.AgentResult

**Package:** `io.casehub.agency.agentic`

**Kind:** `record`

## Fields

### `agent` (`io.casehub.agency.agentic.AgentRef`)

### `duration` (`java.time.Duration`)

### `output` (`java.lang.Object`)

### `status` (`io.casehub.agency.agentic.AgentResult.AgentResultStatus`)

## Record Components

### `agent` (`io.casehub.agency.agentic.AgentRef`)

### `duration` (`java.time.Duration`)

### `output` (`java.lang.Object`)

### `status` (`io.casehub.agency.agentic.AgentResult.AgentResultStatus`)

## Constructors

### `public AgentResult(io.casehub.agency.agentic.AgentRef agent, java.lang.Object output, java.time.Duration duration, io.casehub.agency.agentic.AgentResult.AgentResultStatus status)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `output` (`java.lang.Object`)
- `duration` (`java.time.Duration`)
- `status` (`io.casehub.agency.agentic.AgentResult.AgentResultStatus`)

## Methods

### `public io.casehub.agency.agentic.AgentRef agent()`

### `public static io.casehub.agency.agentic.AgentResult declined(io.casehub.agency.agentic.AgentRef agent, java.lang.Object reason)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `reason` (`java.lang.Object`)

### `public java.time.Duration duration()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public static io.casehub.agency.agentic.AgentResult failure(io.casehub.agency.agentic.AgentRef agent, java.lang.Object output)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `output` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Object output()`

### `public io.casehub.agency.agentic.AgentResult.AgentResultStatus status()`

### `public static io.casehub.agency.agentic.AgentResult success(io.casehub.agency.agentic.AgentRef agent, java.lang.Object output)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `output` (`java.lang.Object`)

### `public static io.casehub.agency.agentic.AgentResult timeout(io.casehub.agency.agentic.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)

### `public final java.lang.String toString()`
