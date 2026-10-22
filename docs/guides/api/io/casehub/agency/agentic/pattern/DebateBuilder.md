# io.casehub.agency.orchestration.pattern.DebateBuilder

**Package:** `io.casehub.agency.orchestration.pattern`

**Kind:** `class`

## Fields

### `convergenceExplicitlySet` (`boolean`)

### `judge` (`io.casehub.agency.core.AgentRef`)

### `maxRounds` (`int`)

## Constructors

### `public DebateBuilder()`

## Methods

### `public io.casehub.agency.orchestration.execution.ExecutionModel<T> build()`

### `public io.casehub.agency.orchestration.pattern.DebateBuilder<T> convergence(io.casehub.agency.orchestration.termination.TerminationCondition<T> convergence)`

#### Parameters

- `convergence` (`io.casehub.agency.orchestration.termination.TerminationCondition<T>`)

### `public io.casehub.agency.orchestration.pattern.DebateBuilder<T> debaters(io.casehub.agency.core.AgentRef[] agents)`

#### Parameters

- `agents` (`io.casehub.agency.core.AgentRef[]`)

### `public io.casehub.agency.orchestration.pattern.DebateBuilder<T> judge(io.casehub.agency.core.AgentRef judge)`

#### Parameters

- `judge` (`io.casehub.agency.core.AgentRef`)

### `public io.casehub.agency.orchestration.pattern.DebateBuilder<T> maxRounds(int rounds)`

#### Parameters

- `rounds` (`int`)
