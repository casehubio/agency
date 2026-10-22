# io.casehub.agency.agentic.listener.OrchestrationEventType

**Package:** `io.casehub.agency.agentic.listener`

**Kind:** `enum`

Orchestration-level event types produced by blocks' execution drivers.
Maps to `CaseHubEventType` at the EventLog sink — the mapping is owned
by the sink implementation, not by blocks. When engine#626 adds orchestration
types to `CaseHubEventType`, the sink maps directly.

## Enum Constants

### `ACTIVATION_EVALUATED` (`io.casehub.agency.agentic.listener.OrchestrationEventType`)

### `AGENT_DISPATCHED` (`io.casehub.agency.agentic.listener.OrchestrationEventType`)

### `AGENT_FAILED` (`io.casehub.agency.agentic.listener.OrchestrationEventType`)

### `AGENT_RESULT` (`io.casehub.agency.agentic.listener.OrchestrationEventType`)

### `AGGREGATION_COMPLETED` (`io.casehub.agency.agentic.listener.OrchestrationEventType`)

### `EXECUTION_COMPLETED` (`io.casehub.agency.agentic.listener.OrchestrationEventType`)

### `EXECUTION_STARTED` (`io.casehub.agency.agentic.listener.OrchestrationEventType`)

### `ROUTING_DECISION` (`io.casehub.agency.agentic.listener.OrchestrationEventType`)

### `TERMINATION_EVALUATED` (`io.casehub.agency.agentic.listener.OrchestrationEventType`)

## Constructors

### `private OrchestrationEventType()`

## Methods

### `public static io.casehub.agency.agentic.listener.OrchestrationEventType valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.agency.agentic.listener.OrchestrationEventType[] values()`
