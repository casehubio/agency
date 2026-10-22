# io.casehub.agency.agentic.routing.FirstMatchRouting

**Package:** `io.casehub.agency.agentic.routing`

**Kind:** `class`

## Fields

### `matcher` (`java.util.function.Predicate<io.casehub.agency.agentic.RoutingCandidate>`)

## Constructors

### `public FirstMatchRouting(java.util.function.Predicate<io.casehub.agency.agentic.RoutingCandidate> matcher)`

#### Parameters

- `matcher` (`java.util.function.Predicate<io.casehub.agency.agentic.RoutingCandidate>`)

## Methods

### `public Uni<io.casehub.agency.agentic.routing.RoutingDecision> route(io.casehub.agency.agentic.routing.RoutingContext<T> context)`

#### Parameters

- `context` (`io.casehub.agency.agentic.routing.RoutingContext<T>`)
