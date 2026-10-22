# io.casehub.agency.agentic.decomposition.PlannedTask

**Package:** `io.casehub.agency.agentic.decomposition`

**Kind:** `record`

## Fields

### `agent` (`io.casehub.agency.agentic.AgentRef`)

### `createdAt` (`java.time.Instant`)

### `description` (`java.lang.String`)

### `id` (`java.lang.String`)

### `outputContract` (`io.casehub.agency.agentic.decomposition.OutputContract`)

### `rationale` (`java.lang.String`)

## Record Components

### `agent` (`io.casehub.agency.agentic.AgentRef`)

### `createdAt` (`java.time.Instant`)

### `description` (`java.lang.String`)

### `id` (`java.lang.String`)

### `outputContract` (`io.casehub.agency.agentic.decomposition.OutputContract`)

### `rationale` (`java.lang.String`)

## Constructors

### `public PlannedTask(java.lang.String id, java.time.Instant createdAt, java.lang.String description, io.casehub.agency.agentic.AgentRef agent, java.lang.String rationale)`

#### Parameters

- `id` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `description` (`java.lang.String`)
- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `rationale` (`java.lang.String`)

### `public PlannedTask(java.lang.String id, java.time.Instant createdAt, java.lang.String description, io.casehub.agency.agentic.AgentRef agent, java.lang.String rationale, io.casehub.agency.agentic.decomposition.OutputContract outputContract)`

#### Parameters

- `id` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `description` (`java.lang.String`)
- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `rationale` (`java.lang.String`)
- `outputContract` (`io.casehub.agency.agentic.decomposition.OutputContract`)

## Methods

### `public io.casehub.agency.agentic.AgentRef agent()`

### `public java.time.Instant createdAt()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public ExecutorRef executor()`

### `public final int hashCode()`

### `public java.lang.String id()`

### `public io.casehub.agency.agentic.decomposition.OutputContract outputContract()`

### `public java.lang.String rationale()`

### `public TaskStatus status()`

### `public final java.lang.String toString()`
