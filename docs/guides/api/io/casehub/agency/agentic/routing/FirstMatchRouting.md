# io.casehub.agency.orchestration.routing.FirstMatchRouting

**Package:** `io.casehub.agency.orchestration.routing`

**Kind:** `class`

## Fields

### `matcher` (`java.util.function.Predicate<io.casehub.agency.core.RoutingCandidate>`)

## Constructors

### `public FirstMatchRouting(java.util.function.Predicate<io.casehub.agency.core.RoutingCandidate> matcher)`

#### Parameters

- `matcher` (`java.util.function.Predicate<io.casehub.agency.core.RoutingCandidate>`)

## Methods

### `public Uni<io.casehub.agency.orchestration.routing.RoutingDecision> route(io.casehub.agency.orchestration.routing.RoutingContext<T> context)`

#### Parameters

- `context` (`io.casehub.agency.orchestration.routing.RoutingContext<T>`)
