# io.casehub.agency.orchestration.listener.LedgerExecutionListener

**Package:** `io.casehub.agency.orchestration.listener`

**Kind:** `class`

Persists compliance-grade audit entries via a consumer-provided sink.
Each entry carries actor identity and role for EU AI Act Art.12 regulatory traceability.

<p>Records the full decision-dispatch-result chain: routing decisions with LLM reasoning,
activation evaluations, agent dispatches and results, termination decisions,
escalation events, and failures. This chain closes the Art.12 audit gap — you know
what was decided (routing), what was activated, what was dispatched, what each agent
returned, and why execution terminated.

<p>Sink implementations must be non-blocking — use in-memory buffers or async
queues. The sink is called synchronously from the driver loop; blocking I/O
in the sink stalls the execution.

## Fields

### `sink` (`io.casehub.agency.orchestration.listener.LedgerExecutionListener.LedgerSink`)

### `supervisorActorId` (`java.lang.String`)

## Constructors

### `public LedgerExecutionListener(io.casehub.agency.orchestration.listener.LedgerExecutionListener.LedgerSink sink, java.lang.String supervisorActorId)`

#### Parameters

- `sink` (`io.casehub.agency.orchestration.listener.LedgerExecutionListener.LedgerSink`)
- `supervisorActorId` (`java.lang.String`)

## Methods

### `public void onActivation(io.casehub.agency.core.AgentRef agent, boolean activated)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `activated` (`boolean`)

### `public void onAgentDispatched(io.casehub.agency.core.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)

### `public void onAgentResult(io.casehub.agency.core.AgentResult result)`

#### Parameters

- `result` (`io.casehub.agency.core.AgentResult`)

### `public void onExecutionComplete(io.casehub.agency.orchestration.execution.ExecutionResult result, java.time.Duration executionDuration, int iterationCount)`

#### Parameters

- `result` (`io.casehub.agency.orchestration.execution.ExecutionResult`)
- `executionDuration` (`java.time.Duration`)
- `iterationCount` (`int`)

### `public void onExecutionStart(io.casehub.agency.orchestration.execution.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<?>`)

### `public void onFailure(io.casehub.agency.core.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `agent` (`io.casehub.agency.core.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `public void onRoutingDecision(io.casehub.agency.orchestration.routing.RoutingDecision decision, java.util.List<io.casehub.agency.core.RoutingCandidate> candidates)`

#### Parameters

- `decision` (`io.casehub.agency.orchestration.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.agency.core.RoutingCandidate>`)

### `public void onTermination(io.casehub.agency.orchestration.termination.TerminationDecision decision)`

#### Parameters

- `decision` (`io.casehub.agency.orchestration.termination.TerminationDecision`)

### `private static java.lang.String truncate(java.lang.String s, int max)`

#### Parameters

- `s` (`java.lang.String`)
- `max` (`int`)
