# io.casehub.agency.orchestration.routing.Routing

**Package:** `io.casehub.agency.orchestration.routing`

**Kind:** `class`

## Constructors

### `private Routing()`

## Methods

### `public static io.casehub.agency.orchestration.routing.FirstMatchRouting<T> firstMatch(java.util.function.Predicate<io.casehub.agency.core.RoutingCandidate> matcher)`

#### Parameters

- `matcher` (`java.util.function.Predicate<io.casehub.agency.core.RoutingCandidate>`)

### `public static io.casehub.agency.orchestration.routing.LlmSelectedRouting<T> llmSelected(AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`AgentProvider`)

### `public static io.casehub.agency.orchestration.routing.RoundRobinRouting<T> roundRobin()`

### `public static io.casehub.agency.orchestration.routing.SequentialRouting<T> sequential()`
