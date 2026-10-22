# io.casehub.agency.agentic.routing.StageAwareCandidateSupplier

**Package:** `io.casehub.agency.agentic.routing`

**Kind:** `class`

## Fields

### `bindingNameResolver` (`java.util.function.Function<io.casehub.agency.agentic.AgentRef,java.lang.String>`)

### `delegate` (`java.util.function.Supplier<java.util.List<io.casehub.agency.agentic.RoutingCandidate>>`)

### `stageGate` (`io.casehub.agency.agentic.routing.StageGate`)

## Constructors

### `public StageAwareCandidateSupplier(java.util.function.Supplier<java.util.List<io.casehub.agency.agentic.RoutingCandidate>> delegate, io.casehub.agency.agentic.routing.StageGate stageGate, java.util.function.Function<io.casehub.agency.agentic.AgentRef,java.lang.String> bindingNameResolver)`

#### Parameters

- `delegate` (`java.util.function.Supplier<java.util.List<io.casehub.agency.agentic.RoutingCandidate>>`)
- `stageGate` (`io.casehub.agency.agentic.routing.StageGate`)
- `bindingNameResolver` (`java.util.function.Function<io.casehub.agency.agentic.AgentRef,java.lang.String>`)

## Methods

### `public java.util.List<io.casehub.agency.agentic.RoutingCandidate> get()`
