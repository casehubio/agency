# io.casehub.agency.agentic.model.AgentInvoker

**Package:** `io.casehub.agency.agentic.model`

**Kind:** `interface`

## Methods

### `public static io.casehub.agency.agentic.model.AgentInvoker<T> defaultInvoker()`

### `public abstract Uni<io.casehub.agency.agentic.AgentResult> invoke(io.casehub.agency.agentic.AgentRef agent, T state)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `state` (`T`)

### `private static io.casehub.agency.agentic.AgentResult invokeComposed(io.casehub.agency.agentic.AgentRef agent, io.casehub.agency.agentic.AgentRef.ComposedAgent composed, T state, java.time.Instant start)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `composed` (`io.casehub.agency.agentic.AgentRef.ComposedAgent`)
- `state` (`T`)
- `start` (`java.time.Instant`)

### `public default io.casehub.agency.agentic.model.AgentInvoker<T> withFallback(io.casehub.agency.agentic.model.AgentInvoker<T> fallback)`

#### Parameters

- `fallback` (`io.casehub.agency.agentic.model.AgentInvoker<T>`)
