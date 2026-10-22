# io.casehub.agency.agentic.pattern.DebateBuilder

**Package:** `io.casehub.agency.agentic.pattern`

**Kind:** `class`

## Fields

### `convergenceExplicitlySet` (`boolean`)

### `judge` (`io.casehub.agency.agentic.AgentRef`)

### `maxRounds` (`int`)

## Constructors

### `public DebateBuilder()`

## Methods

### `public io.casehub.agency.agentic.model.ExecutionModel<T> build()`

### `public io.casehub.agency.agentic.pattern.DebateBuilder<T> convergence(io.casehub.agency.agentic.termination.TerminationCondition<T> convergence)`

#### Parameters

- `convergence` (`io.casehub.agency.agentic.termination.TerminationCondition<T>`)

### `public io.casehub.agency.agentic.pattern.DebateBuilder<T> debaters(io.casehub.agency.agentic.AgentRef[] agents)`

#### Parameters

- `agents` (`io.casehub.agency.agentic.AgentRef[]`)

### `public io.casehub.agency.agentic.pattern.DebateBuilder<T> judge(io.casehub.agency.agentic.AgentRef judge)`

#### Parameters

- `judge` (`io.casehub.agency.agentic.AgentRef`)

### `public io.casehub.agency.agentic.pattern.DebateBuilder<T> maxRounds(int rounds)`

#### Parameters

- `rounds` (`int`)
