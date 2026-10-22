# io.casehub.agency.agentic.model.ExecutionDriver

**Package:** `io.casehub.agency.agentic.model`

**Kind:** `interface`

## Methods

### `public abstract Uni<java.lang.Void> cancel()`

### `public abstract Uni<io.casehub.agency.agentic.model.ExecutionResult> execute(io.casehub.agency.agentic.model.ExecutionModel<T> model, T initialContext)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<T>`)
- `initialContext` (`T`)

### `public abstract io.casehub.agency.agentic.model.ExecutionState state()`
