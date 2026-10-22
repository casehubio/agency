# io.casehub.agency.orchestration.pattern.HtnBuilder

**Package:** `io.casehub.agency.orchestration.pattern`

**Kind:** `class`

## Fields

### `rootTask` (`TaskNode<T>`)

## Constructors

### `public HtnBuilder()`

## Methods

### `public io.casehub.agency.orchestration.pattern.HtnBuilder<T> agents(io.casehub.agency.core.AgentRef[] agents)`

#### Parameters

- `agents` (`io.casehub.agency.core.AgentRef[]`)

### `public io.casehub.agency.orchestration.pattern.HtnBuilder<T> agents(io.casehub.agency.core.RoutingCandidate[] candidates)`

#### Parameters

- `candidates` (`io.casehub.agency.core.RoutingCandidate[]`)

### `public Uni<io.casehub.agency.orchestration.execution.ExecutionResult> execute(T initialContext)`

#### Parameters

- `initialContext` (`T`)

### `private Uni<DagPlan<TaskNode.LeafTask<T>>> flatten(TaskNode<T> node, T state)`

#### Parameters

- `node` (`TaskNode<T>`)
- `state` (`T`)

### `public io.casehub.agency.orchestration.pattern.HtnBuilder<T> rootTask(TaskNode<T> rootTask)`

#### Parameters

- `rootTask` (`TaskNode<T>`)
