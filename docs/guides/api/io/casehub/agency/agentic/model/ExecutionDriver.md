# io.casehub.agency.orchestration.execution.ExecutionDriver

**Package:** `io.casehub.agency.orchestration.execution`

**Kind:** `interface`

## Methods

### `public abstract Uni<java.lang.Void> cancel()`

### `public abstract Uni<io.casehub.agency.orchestration.execution.ExecutionResult> execute(io.casehub.agency.orchestration.execution.ExecutionModel<T> model, T initialContext)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<T>`)
- `initialContext` (`T`)

### `public abstract io.casehub.agency.orchestration.execution.ExecutionState state()`
