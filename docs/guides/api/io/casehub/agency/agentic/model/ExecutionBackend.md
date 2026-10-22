# io.casehub.agency.orchestration.execution.ExecutionBackend

**Package:** `io.casehub.agency.orchestration.execution`

**Kind:** `interface`

## Methods

### `public abstract Uni<io.casehub.agency.orchestration.execution.ExecutionResult> execute(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, T initialContext)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `initialContext` (`T`)

### `public static io.casehub.agency.orchestration.execution.ExecutionBackend<T> orchestrated()`

### `public static io.casehub.agency.orchestration.execution.ExecutionBackend<T> orchestrated(io.casehub.agency.orchestration.execution.AgentInvoker<T> invoker)`

#### Parameters

- `invoker` (`io.casehub.agency.orchestration.execution.AgentInvoker<T>`)
