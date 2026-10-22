# io.casehub.agency.orchestration.pattern.AbstractPatternBuilder

**Package:** `io.casehub.agency.orchestration.pattern`

**Kind:** `class`

## Fields

### `activation` (`io.casehub.agency.orchestration.activation.ActivationRule<T>`)

### `aggregation` (`io.casehub.agency.orchestration.aggregation.AggregationStrategy<T>`)

### `backend` (`io.casehub.agency.orchestration.execution.ExecutionBackend<T>`)

### `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.agency.core.RoutingCandidate>>`)

### `decomposition` (`DecompositionStrategy<T>`)

### `failurePolicy` (`io.casehub.agency.core.FailurePolicy`)

### `listeners` (`java.util.List<io.casehub.agency.orchestration.execution.ExecutionEventListener>`)

### `patternType` (`io.casehub.agency.orchestration.execution.PatternType`)

### `routing` (`io.casehub.agency.orchestration.routing.RoutingStrategy<T>`)

### `task` (`java.lang.String`)

### `termination` (`io.casehub.agency.orchestration.termination.TerminationCondition<T>`)

## Constructors

### `public AbstractPatternBuilder()`

## Methods

### `public B activate(io.casehub.agency.orchestration.activation.ActivationRule<T> activation)`

#### Parameters

- `activation` (`io.casehub.agency.orchestration.activation.ActivationRule<T>`)

### `protected B agents(io.casehub.agency.core.AgentRef[] agents)`

#### Parameters

- `agents` (`io.casehub.agency.core.AgentRef[]`)

### `protected B agents(io.casehub.agency.core.RoutingCandidate[] candidates)`

#### Parameters

- `candidates` (`io.casehub.agency.core.RoutingCandidate[]`)

### `public B aggregate(io.casehub.agency.orchestration.aggregation.AggregationStrategy<T> aggregation)`

#### Parameters

- `aggregation` (`io.casehub.agency.orchestration.aggregation.AggregationStrategy<T>`)

### `public B backend(io.casehub.agency.orchestration.execution.ExecutionBackend<T> backend)`

#### Parameters

- `backend` (`io.casehub.agency.orchestration.execution.ExecutionBackend<T>`)

### `public io.casehub.agency.orchestration.execution.ExecutionModel<T> build()`

### `public B decompose(DecompositionStrategy<T> decomposition)`

#### Parameters

- `decomposition` (`DecompositionStrategy<T>`)

### `public Uni<io.casehub.agency.orchestration.execution.ExecutionResult> execute(T initialContext)`

#### Parameters

- `initialContext` (`T`)

### `public B listener(io.casehub.agency.orchestration.execution.ExecutionEventListener listener)`

#### Parameters

- `listener` (`io.casehub.agency.orchestration.execution.ExecutionEventListener`)

### `public B maxAgentRetries(int max)`

#### Parameters

- `max` (`int`)

### `public B onDeadlock(io.casehub.agency.core.FailurePolicy.AggregationFailureAction action)`

#### Parameters

- `action` (`io.casehub.agency.core.FailurePolicy.AggregationFailureAction`)

### `public B onRoutingFailure(io.casehub.agency.core.FailurePolicy.RoutingFailureAction action)`

#### Parameters

- `action` (`io.casehub.agency.core.FailurePolicy.RoutingFailureAction`)

### `public B route(io.casehub.agency.orchestration.routing.RoutingStrategy<T> routing)`

#### Parameters

- `routing` (`io.casehub.agency.orchestration.routing.RoutingStrategy<T>`)

### `public B task(java.lang.String task)`

#### Parameters

- `task` (`java.lang.String`)

### `public B terminate(io.casehub.agency.orchestration.termination.TerminationCondition<T> termination)`

#### Parameters

- `termination` (`io.casehub.agency.orchestration.termination.TerminationCondition<T>`)
