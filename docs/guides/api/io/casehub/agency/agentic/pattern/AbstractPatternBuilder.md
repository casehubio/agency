# io.casehub.agency.agentic.pattern.AbstractPatternBuilder

**Package:** `io.casehub.agency.agentic.pattern`

**Kind:** `class`

## Fields

### `activation` (`io.casehub.agency.agentic.activation.ActivationRule<T>`)

### `aggregation` (`io.casehub.agency.agentic.aggregation.AggregationStrategy<T>`)

### `backend` (`io.casehub.agency.agentic.model.ExecutionBackend<T>`)

### `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.agency.agentic.RoutingCandidate>>`)

### `decomposition` (`DecompositionStrategy<T>`)

### `failurePolicy` (`io.casehub.agency.agentic.FailurePolicy`)

### `listeners` (`java.util.List<io.casehub.agency.agentic.model.ExecutionEventListener>`)

### `patternType` (`io.casehub.agency.agentic.model.PatternType`)

### `routing` (`io.casehub.agency.agentic.routing.RoutingStrategy<T>`)

### `task` (`java.lang.String`)

### `termination` (`io.casehub.agency.agentic.termination.TerminationCondition<T>`)

## Constructors

### `public AbstractPatternBuilder()`

## Methods

### `public B activate(io.casehub.agency.agentic.activation.ActivationRule<T> activation)`

#### Parameters

- `activation` (`io.casehub.agency.agentic.activation.ActivationRule<T>`)

### `protected B agents(io.casehub.agency.agentic.AgentRef[] agents)`

#### Parameters

- `agents` (`io.casehub.agency.agentic.AgentRef[]`)

### `protected B agents(io.casehub.agency.agentic.RoutingCandidate[] candidates)`

#### Parameters

- `candidates` (`io.casehub.agency.agentic.RoutingCandidate[]`)

### `public B aggregate(io.casehub.agency.agentic.aggregation.AggregationStrategy<T> aggregation)`

#### Parameters

- `aggregation` (`io.casehub.agency.agentic.aggregation.AggregationStrategy<T>`)

### `public B backend(io.casehub.agency.agentic.model.ExecutionBackend<T> backend)`

#### Parameters

- `backend` (`io.casehub.agency.agentic.model.ExecutionBackend<T>`)

### `public io.casehub.agency.agentic.model.ExecutionModel<T> build()`

### `public B decompose(DecompositionStrategy<T> decomposition)`

#### Parameters

- `decomposition` (`DecompositionStrategy<T>`)

### `public Uni<io.casehub.agency.agentic.model.ExecutionResult> execute(T initialContext)`

#### Parameters

- `initialContext` (`T`)

### `public B listener(io.casehub.agency.agentic.model.ExecutionEventListener listener)`

#### Parameters

- `listener` (`io.casehub.agency.agentic.model.ExecutionEventListener`)

### `public B maxAgentRetries(int max)`

#### Parameters

- `max` (`int`)

### `public B onDeadlock(io.casehub.agency.agentic.FailurePolicy.AggregationFailureAction action)`

#### Parameters

- `action` (`io.casehub.agency.agentic.FailurePolicy.AggregationFailureAction`)

### `public B onRoutingFailure(io.casehub.agency.agentic.FailurePolicy.RoutingFailureAction action)`

#### Parameters

- `action` (`io.casehub.agency.agentic.FailurePolicy.RoutingFailureAction`)

### `public B route(io.casehub.agency.agentic.routing.RoutingStrategy<T> routing)`

#### Parameters

- `routing` (`io.casehub.agency.agentic.routing.RoutingStrategy<T>`)

### `public B task(java.lang.String task)`

#### Parameters

- `task` (`java.lang.String`)

### `public B terminate(io.casehub.agency.agentic.termination.TerminationCondition<T> termination)`

#### Parameters

- `termination` (`io.casehub.agency.agentic.termination.TerminationCondition<T>`)
