# io.casehub.agency.orchestration.execution.AgentInvoker

**Package:** `io.casehub.agency.orchestration.execution`

**Kind:** `interface`

## Methods

### `public static io.casehub.agency.orchestration.execution.AgentInvoker<T> defaultInvoker()`

### `public abstract Uni<io.casehub.agency.core.AgentResult> invoke(io.casehub.agency.core.AgentRef agent, T state)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `state` (`T`)

### `private static io.casehub.agency.core.AgentResult invokeComposed(io.casehub.agency.core.AgentRef agent, io.casehub.agency.core.AgentRef.ComposedAgent composed, T state, java.time.Instant start)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `composed` (`io.casehub.agency.core.AgentRef.ComposedAgent`)
- `state` (`T`)
- `start` (`java.time.Instant`)

### `public default io.casehub.agency.orchestration.execution.AgentInvoker<T> withFallback(io.casehub.agency.orchestration.execution.AgentInvoker<T> fallback)`

#### Parameters

- `fallback` (`io.casehub.agency.orchestration.execution.AgentInvoker<T>`)
