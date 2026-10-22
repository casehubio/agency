# io.casehub.agency.agentic.model.ExecutionBackend

**Package:** `io.casehub.agency.agentic.model`

**Kind:** `interface`

## Methods

### `public abstract Uni<io.casehub.agency.agentic.model.ExecutionResult> execute(io.casehub.agency.agentic.model.ExecutionModel<T> model, T initialContext)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `initialContext` (`T`)

### `public static io.casehub.agency.agentic.model.ExecutionBackend<T> orchestrated()`

### `public static io.casehub.agency.agentic.model.ExecutionBackend<T> orchestrated(io.casehub.agency.agentic.model.AgentInvoker<T> invoker)`

#### Parameters

- `invoker` (`io.casehub.agency.agentic.model.AgentInvoker<T>`)
