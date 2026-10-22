# io.casehub.agency.core.AgentResult

**Package:** `io.casehub.agency.core`

**Kind:** `record`

## Fields

### `agent` (`io.casehub.agency.core.AgentRef`)

### `duration` (`java.time.Duration`)

### `output` (`java.lang.Object`)

### `status` (`io.casehub.agency.core.AgentResult.AgentResultStatus`)

## Record Components

### `agent` (`io.casehub.agency.core.AgentRef`)

### `duration` (`java.time.Duration`)

### `output` (`java.lang.Object`)

### `status` (`io.casehub.agency.core.AgentResult.AgentResultStatus`)

## Constructors

### `public AgentResult(io.casehub.agency.core.AgentRef agent, java.lang.Object output, java.time.Duration duration, io.casehub.agency.core.AgentResult.AgentResultStatus status)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `output` (`java.lang.Object`)
- `duration` (`java.time.Duration`)
- `status` (`io.casehub.agency.core.AgentResult.AgentResultStatus`)

## Methods

### `public io.casehub.agency.core.AgentRef agent()`

### `public static io.casehub.agency.core.AgentResult declined(io.casehub.agency.core.AgentRef agent, java.lang.Object reason)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `reason` (`java.lang.Object`)

### `public java.time.Duration duration()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public static io.casehub.agency.core.AgentResult failure(io.casehub.agency.core.AgentRef agent, java.lang.Object output)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `output` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Object output()`

### `public io.casehub.agency.core.AgentResult.AgentResultStatus status()`

### `public static io.casehub.agency.core.AgentResult success(io.casehub.agency.core.AgentRef agent, java.lang.Object output)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `output` (`java.lang.Object`)

### `public static io.casehub.agency.core.AgentResult timeout(io.casehub.agency.core.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)

### `public final java.lang.String toString()`
