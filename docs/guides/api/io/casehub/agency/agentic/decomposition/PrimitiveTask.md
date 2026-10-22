# io.casehub.agency.agentic.decomposition.PrimitiveTask

**Package:** `io.casehub.agency.agentic.decomposition`

**Kind:** `record`

## Fields

### `agent` (`io.casehub.agency.agentic.AgentRef`)

### `createdAt` (`java.time.Instant`)

### `description` (`java.lang.String`)

### `effect` (`java.util.function.Consumer<T>`)

### `id` (`java.lang.String`)

### `outputContract` (`io.casehub.agency.agentic.decomposition.OutputContract`)

### `precondition` (`java.util.function.Predicate<T>`)

## Record Components

### `agent` (`io.casehub.agency.agentic.AgentRef`)

### `createdAt` (`java.time.Instant`)

### `description` (`java.lang.String`)

### `effect` (`java.util.function.Consumer<T>`)

### `id` (`java.lang.String`)

### `outputContract` (`io.casehub.agency.agentic.decomposition.OutputContract`)

### `precondition` (`java.util.function.Predicate<T>`)

## Constructors

### `public PrimitiveTask(java.lang.String id, java.time.Instant createdAt, java.lang.String description, io.casehub.agency.agentic.AgentRef agent, java.util.function.Predicate<T> precondition, java.util.function.Consumer<T> effect)`

#### Parameters

- `id` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `description` (`java.lang.String`)
- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `precondition` (`java.util.function.Predicate<T>`)
- `effect` (`java.util.function.Consumer<T>`)

### `public PrimitiveTask(java.lang.String id, java.time.Instant createdAt, java.lang.String description, io.casehub.agency.agentic.AgentRef agent, java.util.function.Predicate<T> precondition, java.util.function.Consumer<T> effect, io.casehub.agency.agentic.decomposition.OutputContract outputContract)`

#### Parameters

- `id` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `description` (`java.lang.String`)
- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `precondition` (`java.util.function.Predicate<T>`)
- `effect` (`java.util.function.Consumer<T>`)
- `outputContract` (`io.casehub.agency.agentic.decomposition.OutputContract`)

## Methods

### `public io.casehub.agency.agentic.AgentRef agent()`

### `public java.time.Instant createdAt()`

### `public java.lang.String description()`

### `public java.util.function.Consumer<T> effect()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public ExecutorRef executor()`

### `public final int hashCode()`

### `public java.lang.String id()`

### `public io.casehub.agency.agentic.decomposition.OutputContract outputContract()`

### `public java.util.function.Predicate<T> precondition()`

### `public TaskStatus status()`

### `public final java.lang.String toString()`
