# io.casehub.agency.agentic.routing.Routing

**Package:** `io.casehub.agency.agentic.routing`

**Kind:** `class`

## Constructors

### `private Routing()`

## Methods

### `public static io.casehub.agency.agentic.routing.FirstMatchRouting<T> firstMatch(java.util.function.Predicate<io.casehub.agency.agentic.RoutingCandidate> matcher)`

#### Parameters

- `matcher` (`java.util.function.Predicate<io.casehub.agency.agentic.RoutingCandidate>`)

### `public static io.casehub.agency.agentic.routing.LlmSelectedRouting<T> llmSelected(AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`AgentProvider`)

### `public static io.casehub.agency.agentic.routing.RoundRobinRouting<T> roundRobin()`

### `public static io.casehub.agency.agentic.routing.SequentialRouting<T> sequential()`
