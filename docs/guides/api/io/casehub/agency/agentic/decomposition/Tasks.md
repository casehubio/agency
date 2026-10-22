# io.casehub.agency.orchestration.decomposition.Tasks

**Package:** `io.casehub.agency.orchestration.decomposition`

**Kind:** `class`

## Constructors

### `private Tasks()`

## Methods

### `public static TaskNode.CompoundTask<T> compound(java.lang.String name, DecompositionMethod<T>[] methods)`

#### Parameters

- `name` (`java.lang.String`)
- `methods` (`DecompositionMethod<T>[]`)

### `public static TaskNode.CompoundTask<T> compound(java.lang.String name, TaskNode<T>[] children)`

#### Parameters

- `name` (`java.lang.String`)
- `children` (`TaskNode<T>[]`)

### `public static DecompositionMethod<T> decompose(TaskNode.LeafTask<T>[] subtasks)`

#### Parameters

- `subtasks` (`TaskNode.LeafTask<T>[]`)

### `public static DecompositionMethod<T> decompose(java.util.function.Predicate<T> guard, TaskNode.LeafTask<T>[] subtasks)`

#### Parameters

- `guard` (`java.util.function.Predicate<T>`)
- `subtasks` (`TaskNode.LeafTask<T>[]`)

### `private static java.lang.String generateId()`

### `public static io.casehub.agency.orchestration.decomposition.PlannedTask<T> planned(java.lang.String description, io.casehub.agency.core.AgentRef agent)`

#### Parameters

- `description` (`java.lang.String`)
- `agent` (`io.casehub.agency.core.AgentRef`)

### `public static io.casehub.agency.orchestration.decomposition.PlannedTask<T> planned(java.lang.String description, io.casehub.agency.core.AgentRef agent, java.lang.String rationale)`

#### Parameters

- `description` (`java.lang.String`)
- `agent` (`io.casehub.agency.core.AgentRef`)
- `rationale` (`java.lang.String`)

### `public static io.casehub.agency.orchestration.decomposition.PrimitiveTask<T> primitive(io.casehub.agency.core.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)

### `public static io.casehub.agency.orchestration.decomposition.PrimitiveTask<T> primitive(io.casehub.agency.core.AgentRef agent, java.util.function.Consumer<T> effect)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `effect` (`java.util.function.Consumer<T>`)

### `public static io.casehub.agency.orchestration.decomposition.PrimitiveTask<T> primitive(java.lang.String description, io.casehub.agency.core.AgentRef agent)`

#### Parameters

- `description` (`java.lang.String`)
- `agent` (`io.casehub.agency.core.AgentRef`)

### `public static io.casehub.agency.orchestration.decomposition.PrimitiveTask<T> primitive(java.lang.String description, io.casehub.agency.core.AgentRef agent, java.util.function.Consumer<T> effect)`

#### Parameters

- `description` (`java.lang.String`)
- `agent` (`io.casehub.agency.core.AgentRef`)
- `effect` (`java.util.function.Consumer<T>`)
