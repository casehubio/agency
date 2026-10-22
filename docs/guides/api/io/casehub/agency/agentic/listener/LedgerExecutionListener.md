# io.casehub.agency.agentic.listener.LedgerExecutionListener

**Package:** `io.casehub.agency.agentic.listener`

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

### `sink` (`io.casehub.agency.agentic.listener.LedgerExecutionListener.LedgerSink`)

### `supervisorActorId` (`java.lang.String`)

## Constructors

### `public LedgerExecutionListener(io.casehub.agency.agentic.listener.LedgerExecutionListener.LedgerSink sink, java.lang.String supervisorActorId)`

#### Parameters

- `sink` (`io.casehub.agency.agentic.listener.LedgerExecutionListener.LedgerSink`)
- `supervisorActorId` (`java.lang.String`)

## Methods

### `public void onActivation(io.casehub.agency.agentic.AgentRef agent, boolean activated)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `activated` (`boolean`)

### `public void onAgentDispatched(io.casehub.agency.agentic.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)

### `public void onAgentResult(io.casehub.agency.agentic.AgentResult result)`

#### Parameters

- `result` (`io.casehub.agency.agentic.AgentResult`)

### `public void onExecutionComplete(io.casehub.agency.agentic.model.ExecutionResult result, java.time.Duration executionDuration, int iterationCount)`

#### Parameters

- `result` (`io.casehub.agency.agentic.model.ExecutionResult`)
- `executionDuration` (`java.time.Duration`)
- `iterationCount` (`int`)

### `public void onExecutionStart(io.casehub.agency.agentic.model.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<?>`)

### `public void onFailure(io.casehub.agency.agentic.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `agent` (`io.casehub.agency.agentic.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `public void onRoutingDecision(io.casehub.agency.agentic.routing.RoutingDecision decision, java.util.List<io.casehub.agency.agentic.RoutingCandidate> candidates)`

#### Parameters

- `decision` (`io.casehub.agency.agentic.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.agency.agentic.RoutingCandidate>`)

### `public void onTermination(io.casehub.agency.agentic.termination.TerminationDecision decision)`

#### Parameters

- `decision` (`io.casehub.agency.agentic.termination.TerminationDecision`)

### `private static java.lang.String truncate(java.lang.String s, int max)`

#### Parameters

- `s` (`java.lang.String`)
- `max` (`int`)
