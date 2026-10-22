# io.casehub.agency.orchestration.execution.ExecutionModel

**Package:** `io.casehub.agency.orchestration.execution`

**Kind:** `record`

## Fields

### `activation` (`io.casehub.agency.orchestration.activation.ActivationRule<T>`)

### `aggregation` (`io.casehub.agency.orchestration.aggregation.AggregationStrategy<T>`)

### `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>>`)

### `decomposition` (`DecompositionStrategy<T>`)

### `failurePolicy` (`io.casehub.agency.core.FailurePolicy`)

### `listeners` (`java.util.List<io.casehub.agency.orchestration.execution.ExecutionEventListener>`)

### `patternType` (`io.casehub.agency.orchestration.execution.PatternType`)

### `routing` (`io.casehub.agency.orchestration.routing.RoutingStrategy<T>`)

### `task` (`java.lang.String`)

### `termination` (`io.casehub.agency.orchestration.termination.TerminationCondition<T>`)

## Record Components

### `activation` (`io.casehub.agency.orchestration.activation.ActivationRule<T>`)

### `aggregation` (`io.casehub.agency.orchestration.aggregation.AggregationStrategy<T>`)

### `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>>`)

### `decomposition` (`DecompositionStrategy<T>`)

### `failurePolicy` (`io.casehub.agency.core.FailurePolicy`)

### `listeners` (`java.util.List<io.casehub.agency.orchestration.execution.ExecutionEventListener>`)

### `patternType` (`io.casehub.agency.orchestration.execution.PatternType`)

### `routing` (`io.casehub.agency.orchestration.routing.RoutingStrategy<T>`)

### `task` (`java.lang.String`)

### `termination` (`io.casehub.agency.orchestration.termination.TerminationCondition<T>`)

## Constructors

### `public ExecutionModel(io.casehub.agency.orchestration.routing.RoutingStrategy<T> routing, DecompositionStrategy<T> decomposition, io.casehub.agency.orchestration.activation.ActivationRule<T> activation, io.casehub.agency.orchestration.aggregation.AggregationStrategy<T> aggregation, io.casehub.agency.orchestration.termination.TerminationCondition<T> termination, java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>> candidateSupplier, io.casehub.agency.core.FailurePolicy failurePolicy, java.util.List<io.casehub.agency.orchestration.execution.ExecutionEventListener> listeners, java.lang.String task)`

#### Parameters

- `routing` (`io.casehub.agency.orchestration.routing.RoutingStrategy<T>`)
- `decomposition` (`DecompositionStrategy<T>`)
- `activation` (`io.casehub.agency.orchestration.activation.ActivationRule<T>`)
- `aggregation` (`io.casehub.agency.orchestration.aggregation.AggregationStrategy<T>`)
- `termination` (`io.casehub.agency.orchestration.termination.TerminationCondition<T>`)
- `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>>`)
- `failurePolicy` (`io.casehub.agency.core.FailurePolicy`)
- `listeners` (`java.util.List<io.casehub.agency.orchestration.execution.ExecutionEventListener>`)
- `task` (`java.lang.String`)

### `public ExecutionModel(io.casehub.agency.orchestration.routing.RoutingStrategy<T> routing, DecompositionStrategy<T> decomposition, io.casehub.agency.orchestration.activation.ActivationRule<T> activation, io.casehub.agency.orchestration.aggregation.AggregationStrategy<T> aggregation, io.casehub.agency.orchestration.termination.TerminationCondition<T> termination, java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>> candidateSupplier, io.casehub.agency.core.FailurePolicy failurePolicy, java.util.List<io.casehub.agency.orchestration.execution.ExecutionEventListener> listeners, java.lang.String task, io.casehub.agency.orchestration.execution.PatternType patternType)`

#### Parameters

- `routing` (`io.casehub.agency.orchestration.routing.RoutingStrategy<T>`)
- `decomposition` (`DecompositionStrategy<T>`)
- `activation` (`io.casehub.agency.orchestration.activation.ActivationRule<T>`)
- `aggregation` (`io.casehub.agency.orchestration.aggregation.AggregationStrategy<T>`)
- `termination` (`io.casehub.agency.orchestration.termination.TerminationCondition<T>`)
- `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>>`)
- `failurePolicy` (`io.casehub.agency.core.FailurePolicy`)
- `listeners` (`java.util.List<io.casehub.agency.orchestration.execution.ExecutionEventListener>`)
- `task` (`java.lang.String`)
- `patternType` (`io.casehub.agency.orchestration.execution.PatternType`)

## Methods

### `public io.casehub.agency.orchestration.activation.ActivationRule<T> activation()`

### `public io.casehub.agency.orchestration.aggregation.AggregationStrategy<T> aggregation()`

### `public java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>> candidateSupplier()`

### `public DecompositionStrategy<T> decomposition()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.agency.core.FailurePolicy failurePolicy()`

### `public final int hashCode()`

### `public java.util.List<io.casehub.agency.orchestration.execution.ExecutionEventListener> listeners()`

### `public io.casehub.agency.orchestration.execution.PatternType patternType()`

### `public io.casehub.agency.orchestration.routing.RoutingStrategy<T> routing()`

### `public java.lang.String task()`

### `public io.casehub.agency.orchestration.termination.TerminationCondition<T> termination()`

### `public final java.lang.String toString()`
