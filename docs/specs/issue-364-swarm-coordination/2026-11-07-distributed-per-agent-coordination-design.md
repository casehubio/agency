# Distributed Per-Agent LLM Coordination

**Issue:** casehubio/agency#361
**Epic:** casehubio/agency#364 (Swarm Coordination)
**Engine complement:** casehubio/engine#1314 (per-agent stigmergy observation feed and write-back SPI)
**Date:** 2026-11-07

## Overview

Evolve swarm coordination from a centralised coordinator model (one LLM makes all decisions) to a distributed model where each swarm member has its own lightweight LLM decision loop. Agents coordinate indirectly through stigmergy — depositing and reading signals in the shared environment — rather than through a central authority.

The per-agent decision loop infrastructure already exists (`AgentDecisionLoop`, `DecisionLoopFactory`, `DecisionLoopDescriptor`, `DecisionOutcome`). The `DistributedTopology` class exists as a skeleton with empty `onSwarmEvent()` and `onFeedback()` methods. This design fills in the LLM wiring layer.

## Architecture

### Core Principle: Agents Signal, Engine Executes

The fundamental shift from centralised to distributed:

- **Centralised**: LLM → decision → execute directly on shared state
- **Distributed**: LLM → signal → environment → emergent behaviour

Agents never directly mutate shared state (task assignment, swarm size, global synthesis). They emit stigmergy signals via `DecisionOutcome.WriteBack`. The engine handles serialisation, consensus detection, and execution.

### Decision Distribution

All three centralised decision types become distributed:

| Decision | Centralised (v1) | Distributed (v2) |
|----------|------------------|-------------------|
| **Routing** | `SwarmRoutingStrategy` — one LLM picks entire team | Each agent observes environment and picks its own task. Deposits `in-progress:task-id` signal. Engine's work state FSM serialises conflicting claims. |
| **Aggregation** | `CollectiveReasoningAggregation` — one LLM synthesizes all outputs | Each agent reads peer observations from neighbourhood. Produces local synthesis as an observation. Gossip propagation achieves eventual consistency. Engine's convergence evaluator detects agreement. |
| **Provisioning** | `SwarmProvisioningAdvisorImpl` — one LLM decides grow/shrink | Each agent emits `need-capacity:capability` or `excess-capacity:capability` signals. Engine's `detectSignalConsensus()` triggers provisioning when threshold is met. Signal reinforcement is idempotent — no conflicting decisions. |

### Conflict Resolution

| Conflict | Resolution Mechanism |
|----------|---------------------|
| Duplicate task selection | Engine's `AgentWorkState` FSM (`transitionWorkState()`) serialises assignment. Only one agent transitions to ASSIGNED. Others see the state change as an observation event and re-reason on next tick. |
| Contradictory provisioning | Signal reinforcement is idempotent. Multiple agents emitting `need-capacity:web-scraping` produces one strong signal, not multiple provisioning requests. Engine's `detectSignalConsensus()` triggers action only when threshold is met. |
| Inconsistent world views | Not a bug — it's the coordination medium. Each agent's local synthesis becomes an observation for peers. Information propagates via gossip. Engine's `SwarmConvergenceEvaluator` detects when syntheses converge. |

## Components

### 1. `DistributedAgentReasoner` — per-agent LLM reasoner

`ContentSummariser<ObservationEvent, DecisionOutcome>` that takes per-agent observations and produces decisions via LLM.

**Input** (`ObservationEvent` batches):
- Agent's role and team context
- Visible stigmergy signals (from `AgentObservationProvider`, engine#1314)
- Peer work states (from `AgentWorkLifecycleListener` events)
- Swarm-level progress (from `SwarmCoordinationListener` events)

**Output** (`DecisionOutcome`):
- `WriteBack` — signals to deposit, observations to publish, context to update
- `Route` — task selection (mapped to signal deposit)
- `Provision` — capacity signal (mapped to `need-capacity`/`excess-capacity` signal)

**Prompt structure**: System prompt describes the agent's role, available actions (signal types), and the stigmergy coordination model. User prompt contains the current observation batch rendered by `LoopPromptSection` instances.

**LLM response**: Structured JSON parsed via `StructuredAgentInvoker.invokeRaw()`:
```json
{
  "signals": [{"name": "need-capacity:analysis", "strength": 0.8}],
  "taskClaim": "task-42",
  "synthesis": "Observations from agents A and B converge on...",
  "reasoning": "Brief explanation of decision"
}
```

**Fallback**: On LLM failure or budget exhaustion, falls back to no-op (emit no signals). The heuristic summariser in `DecisionLoopFactory` handles degradation.

### 2. Prompt Sections — per-agent context assembly

Four `LoopPromptSection` implementations registered in `PromptSectionRegistry`:

| Section | Name | Content |
|---------|------|---------|
| `RoleContextSection` | `"role-context"` | Agent's assigned role, team membership, capabilities |
| `StigmergySignalSection` | `"stigmergy-signals"` | Visible signals from `AgentObservationProvider` — name, strength, sources, decay state |
| `PeerStateSection` | `"peer-state"` | Peer agents' work states (IDLE/ASSIGNED/EXECUTING), recent task completions, active claims |
| `SwarmProgressSection` | `"swarm-progress"` | Swarm-level metrics: exploration pace, consensus, stability, detected roles/teams |

The `DecisionLoopDescriptor` carries `promptSectionNames` — `DistributedTopology.configure()` registers all four by default.

### 3. `DistributedTopology` — filled-in skeleton

Extends the existing skeleton with:

**`onSwarmEvent(SwarmCoordinationEvent)`**: Routes events to per-agent loops as `ObservationEvent` instances. All agents receive swarm-level events (roles, teams, progress). `MemberCompleted` and `ProvisioningChanged` events are also routed.

**`onFeedback(CoordinationFeedback)`**: Routes feedback to the originating agent's loop. `FeedbackSignal.SCOPE_VIOLATION` logs a warning and suppresses the decision. `FeedbackSignal.SUCCESS` is a no-op (the signal was accepted).

**`configure(TopologyContext)`**: Creates per-agent `DecisionLoopDescriptor` instances with `FULL_AUTONOMY` / `STIGMERGY` / `EVENT_DRIVEN`. Wires the `DistributedAgentReasoner` as the LLM summariser. Registers prompt sections. Creates budget pool with `NORMAL` priority for all agents.

### 4. `DistributedTopologyBuilder` — Java builder DSL

Fluent builder following the `SwarmCoordinatorBuilder` pattern:

```java
var topology = new DistributedTopologyBuilder()
    .agentProvider(agentProvider)
    .agentRoles(Map.of("analyst", AgentRole.of(...), "researcher", AgentRole.of(...)))
    .budgetPolicy(AutonomyBudgetPolicy.defaults())
    .convergenceThreshold(0.8)
    .promptCustomiser(customiser)
    .addPromptSection(customSection)
    .buildTopology();
```

Configuration options:
- `agentProvider(AgentProvider)` — required, LLM provider
- `agentRoles(Map<String, AgentRole>)` — required, per-agent role definitions
- `budgetPolicy(AutonomyBudgetPolicy)` — optional, defaults to `AutonomyBudgetPolicy.defaults()`
- `convergenceThreshold(double)` — optional, defaults to 0.8
- `promptCustomiser(SystemPromptCustomiser)` — optional, for A/B variant testing
- `addPromptSection(LoopPromptSection)` — optional, additional prompt sections
- `agentModelOverride(String agentId, String modelId)` — optional, per-agent model override

### 5. YAML Surface — `TopologySpec.Distributed`

New variant in `TopologySpec` sealed hierarchy:

```yaml
topology:
  type: distributed
  convergenceThreshold: 0.8
  agents:
    analyst:
      role: analysis
      capabilities: [data-analysis, pattern-recognition]
      model: claude-haiku  # optional per-agent model override
    researcher:
      role: research
      capabilities: [web-search, document-retrieval]
  budget:
    maxTokensPerTick: 10000
    maxConcurrentCalls: 4
```

Compiled by `DistributedTopologyCompiler` following the `HiveTopologyCompiler` pattern. Deployment wired via `TopologyBuildItem` (same as Hive).

### 6. Write-Back Bridge — `StigmergyWriteBackHandler`

Bridges `DecisionOutcome.WriteBack` to engine's `StigmergyWriter` SPI (engine#1314):

- `SignalDeposit` → `StigmergyWriter.depositSignal()`
- `Observation` → `StigmergyWriter.writeObservation()`
- `ContextWrite` → `StigmergyWriter.writeContext()`

Registered as a subscriber on the `DecisionLoopFactory`'s output bus. Each `WriteBack` outcome triggers the corresponding engine write.

### 7. `AgentWorkLifecycleBridge` — per-agent event source

`AgentWorkLifecycleListener` implementation that converts per-agent engine events into `ObservationEvent` instances and routes them to the corresponding `AgentDecisionLoop`.

Maps:
- `onWorkStateChanged` → `ObservationEvent("peer-state-change", ...)`
- `onTaskAssigned` → `ObservationEvent("task-assigned", ...)`
- `onRoleAdopted/Released` → `ObservationEvent("role-change", ...)`

## Data Flow

```
Engine                          Agency
──────                          ──────
SignalRegistry ──────────────→ AgentObservationProvider ──→ StigmergySignalSection
ObservationRegistry ─────────→                              (per-agent prompt)
AgentWorkLifecycleListener ──→ AgentWorkLifecycleBridge ──→ PeerStateSection
SwarmCoordinationListener ───→ DistributedTopology ───────→ SwarmProgressSection
                                       │
                                       ↓
                              AgentDecisionLoop (per agent)
                                       │
                              DistributedAgentReasoner (LLM)
                                       │
                                       ↓
                              DecisionOutcome.WriteBack
                                       │
                              StigmergyWriteBackHandler
                                       │
                                       ↓
StigmergyWriter ←────────────────────────
  depositSignal()
  writeObservation()
  writeContext()
```

## Testing Strategy

- **Unit tests** for `DistributedAgentReasoner` — mock `AgentProvider`, verify prompt assembly, verify JSON parsing, verify fallback on error
- **Unit tests** for each prompt section — verify rendering with various observation states
- **Unit tests** for `DistributedTopology.onSwarmEvent()` — verify event routing to per-agent loops
- **Unit tests** for `StigmergyWriteBackHandler` — mock `StigmergyWriter`, verify signal/observation/context writes
- **Unit tests** for `DistributedTopologyBuilder` — verify composition and defaults
- **Unit tests** for `DistributedTopologyCompiler` — verify YAML parsing and compilation
- **Integration test** — multi-agent scenario with mock engine: verify signal propagation, task claiming, convergence detection

All tests are plain JUnit 5 + Mockito (no Quarkus runtime, consistent with existing test patterns).

## Dependencies

**Compile-time (agency-core):**
- Existing: `AgentDecisionLoop`, `DecisionLoopFactory`, `DecisionOutcome`, `SummarisationRunner`, `ContentSummariser`, `StructuredAgentInvoker`
- New (provided): `AgentObservationProvider` (engine#1314), `StigmergyWriter` (engine#1314). Both are provided dependencies — `StigmergyWriteBackHandler` and observation feed are no-ops when the engine SPIs are absent (`Instance<T>.isResolvable()` guard).

**YAML module (agentic-yaml):**
- Existing: `TopologySpec` sealed hierarchy, `PatternCompiler`
- New: `TopologySpec.Distributed` variant, `DistributedTopologyCompiler`

**Deployment module (agentic-yaml-deployment):**
- Existing: `TopologyBuildItem`, deployment build steps
- New: Distributed topology extraction + recorder

## Out of Scope

- Graduated autonomy (per-role decision scope) — use `GraduatedTopology` for that
- Hierarchical observation routing — covered by `HierarchicalTopology` (#405)
- Multi-swarm coordination — covered by #363 (next in queue)
- Per-agent prompt optimisation — future evolution; current prompt optimisation framework works at the signature level

## References

- `DistributedTopology.java` — existing skeleton
- `AgentDecisionLoop.java` — per-agent loop infrastructure
- `DecisionLoopFactory.java` — loop creation with budget/degradation
- `SwarmCoordinatorBuilder.java` — centralised builder pattern to follow
- `HiveTopologyCompiler.java` — YAML compilation pattern to follow
- `CentralisedTopology.java` — centralised topology for contrast
- `ObservationActionPipeline.java` — two-phase observation→action pattern
- `SwarmRoutingStrategy.java` — centralised routing (LLM prompt pattern)
- `CollectiveReasoningAggregation.java` — centralised aggregation (LLM prompt pattern)
- `SwarmProvisioningAdvisorImpl.java` — centralised provisioning (LLM prompt pattern)
- `StigmergyCoordinator.java` (engine) — pattern detection, signal consensus
- `SwarmProvisioner.java` (engine) — provisioning execution, `need-capacity` signal checks
- engine#1314 — per-agent stigmergy observation feed and write-back SPI
