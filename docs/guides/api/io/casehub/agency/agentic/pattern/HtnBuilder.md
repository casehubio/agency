# io.casehub.agency.agentic.pattern.HtnBuilder

**Package:** `io.casehub.agency.agentic.pattern`

**Kind:** `class`

## Fields

### `rootTask` (`TaskNode<T>`)

## Constructors

### `public HtnBuilder()`

## Methods

### `public io.casehub.agency.agentic.pattern.HtnBuilder<T> agents(io.casehub.agency.agentic.AgentRef[] agents)`

#### Parameters

- `agents` (`io.casehub.agency.agentic.AgentRef[]`)

### `public io.casehub.agency.agentic.pattern.HtnBuilder<T> agents(io.casehub.agency.agentic.RoutingCandidate[] candidates)`

#### Parameters

- `candidates` (`io.casehub.agency.agentic.RoutingCandidate[]`)

### `public Uni<io.casehub.agency.agentic.model.ExecutionResult> execute(T initialContext)`

#### Parameters

- `initialContext` (`T`)

### `private Uni<DagPlan<TaskNode.LeafTask<T>>> flatten(TaskNode<T> node, T state)`

#### Parameters

- `node` (`TaskNode<T>`)
- `state` (`T`)

### `public io.casehub.agency.agentic.pattern.HtnBuilder<T> rootTask(TaskNode<T> rootTask)`

#### Parameters

- `rootTask` (`TaskNode<T>`)
