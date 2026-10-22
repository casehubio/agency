# io.casehub.agency.orchestration.decomposition.GoalOrientedDecomposition

**Package:** `io.casehub.agency.orchestration.decomposition`

**Kind:** `class`

## Constructors

### `public GoalOrientedDecomposition()`

## Methods

### `private java.util.List<io.casehub.agency.orchestration.decomposition.GoalOrientedDecomposition.GoapAction> buildActions(java.util.List<io.casehub.agency.core.RoutingCandidate> agents)`

#### Parameters

- `agents` (`java.util.List<io.casehub.agency.core.RoutingCandidate>`)

### `private DagPlan<TaskNode.LeafTask<T>> buildDag(java.util.List<io.casehub.agency.orchestration.decomposition.GoalOrientedDecomposition.GoapAction> selectedActions, java.util.Map<java.lang.String,io.casehub.agency.orchestration.decomposition.GoalOrientedDecomposition.GoapAction> typeProducers, java.util.Set<java.lang.String> availableTypes)`

#### Parameters

- `selectedActions` (`java.util.List<io.casehub.agency.orchestration.decomposition.GoalOrientedDecomposition.GoapAction>`)
- `typeProducers` (`java.util.Map<java.lang.String,io.casehub.agency.orchestration.decomposition.GoalOrientedDecomposition.GoapAction>`)
- `availableTypes` (`java.util.Set<java.lang.String>`)

### `public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> task, DecompositionContext<T> context)`

#### Parameters

- `task` (`TaskNode<T>`)
- `context` (`DecompositionContext<T>`)

### `private static io.casehub.agency.orchestration.decomposition.GoalOrientedDecomposition.GoapAction findProducer(java.lang.String type, java.util.List<io.casehub.agency.orchestration.decomposition.GoalOrientedDecomposition.GoapAction> actions)`

#### Parameters

- `type` (`java.lang.String`)
- `actions` (`java.util.List<io.casehub.agency.orchestration.decomposition.GoalOrientedDecomposition.GoapAction>`)

### `public java.lang.String id()`

### `private DagPlan<TaskNode.LeafTask<T>> plan(java.lang.String taskName, io.casehub.agency.orchestration.decomposition.GoapDecompositionContext<T> ctx)`

#### Parameters

- `taskName` (`java.lang.String`)
- `ctx` (`io.casehub.agency.orchestration.decomposition.GoapDecompositionContext<T>`)
