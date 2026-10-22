# io.casehub.agency.orchestration.routing.StageAwareCandidateSupplier

**Package:** `io.casehub.agency.orchestration.routing`

**Kind:** `class`

## Fields

### `bindingNameResolver` (`java.util.function.Function<io.casehub.agency.core.AgentRef,java.lang.String>`)

### `delegate` (`java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>>`)

### `stageGate` (`io.casehub.agency.orchestration.routing.StageGate`)

## Constructors

### `public StageAwareCandidateSupplier(java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>> delegate, io.casehub.agency.orchestration.routing.StageGate stageGate, java.util.function.Function<io.casehub.agency.core.AgentRef,java.lang.String> bindingNameResolver)`

#### Parameters

- `delegate` (`java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>>`)
- `stageGate` (`io.casehub.agency.orchestration.routing.StageGate`)
- `bindingNameResolver` (`java.util.function.Function<io.casehub.agency.core.AgentRef,java.lang.String>`)

## Methods

### `public java.util.List<io.casehub.agency.core.RoutingCandidate> get()`
