# LLM Swarm Coordination with Experience Replay — Design Spec

**Issue:** casehubio/agency#285
**Parent epic:** casehubio/engine#1104 (Hive Mind)
**Depends on:** engine#1112 (Swarm execution model)

## Overview

LLM-enhanced swarm coordination that adds team formation reasoning, work distribution intelligence, collective observation synthesis, and experience replay from CBR plan traces. Builds on engine's mechanical swarm infrastructure (RoleTracker, TeamDetector, SwarmProvisioner) — engine handles emergence, agency adds intelligence.

**Research inputs:**
- SwarmExp (arXiv:2608.30661): experience extraction + replay improves swarm orchestration
- "Drop the Hierarchy and Roles" (arXiv:2603.28990): minimal scaffolding produces better LLM self-organisation

## Architecture

### Compositional Model

The swarm is the 9th `PatternDefinition` topology, compiled to an `ExecutionModel` using `ChoreographedDriver` for event-driven execution. A single `LlmSwarmCoordinator` composition root wires four capabilities:

```
                    ┌─────────────────────────────────┐
                    │       LlmSwarmCoordinator        │
                    │       (composition root)         │
                    └────┬──────┬──────┬──────┬───────┘
                         │      │      │      │
              ┌──────────┘      │      │      └──────────┐
              ▼                 ▼      ▼                 ▼
    SwarmRoutingStrategy   Experience  Collective    SwarmProvisioning
    (team formation +      Replay     Reasoning     AdvisorImpl
     work distribution)    Section    Aggregation   (scaling veto)
              │                │           │              │
              ▼                ▼           ▼              ▼
         AgentInvoker    ExperienceAnalyser  StructuredAgentInvoker  engine SPI
```

**Centralisation is a deliberate v1 trade-off.** Engine's mechanical RoleTracker/TeamDetector handle emergent role/team formation. The LLM coordinator reasons about what the mechanical layer detects — it does not replace distributed emergence with centralised control. Full distributed per-agent LLM coordination is a future evolution.

### Execution Flow

```
1. PatternCompiler compiles PatternDefinition.Swarm → ExecutionModel
2. ExecutionModel uses ChoreographedDriver with SwarmEventSource
3. Driver loop:
   a. SwarmEventSource delivers engine swarm lifecycle events as DriverEvents
   b. SwarmRoutingStrategy invokes coordinator LLM for team/work decisions
   c. AgentInvoker dispatches work to selected swarm members
   d. CollectiveReasoningAggregation synthesises member results via LLM
   e. SwarmConvergenceTermination checks swarm convergence via SwarmProgress metrics
   f. Loop back to (a) on next event
4. SwarmProvisioningAdvisorImpl provides LLM-reasoned scaling advice to engine
```

### SwarmContext State Evolution

`SwarmContext` is a mutable state holder (class, not record) that accumulates swarm state across driver iterations. `SwarmEventSource` updates the `SwarmContext` instance as `SwarmCoordinationEvent` instances arrive from the engine — role changes update `detectedRoles`, team changes update `detectedTeams`, progress updates refresh `swarmProgress`.

The same `SwarmContext` reference is passed as the `T context` parameter to every `executeIteration()` call in `ChoreographedDriver.runLoop()`. Because `AbstractExecutionDriver` passes context by reference and `SwarmContext` is mutable, the routing strategy always sees the current accumulated state without requiring context replacement.

```java
public class SwarmContext {
    private volatile List<DetectedRole> detectedRoles;
    private volatile List<DetectedTeam> detectedTeams;
    private volatile SwarmProgress swarmProgress;
    private volatile String activeStrategy;
    private volatile List<RetrievedExperience> experiences = List.of();

    // Updated by SwarmEventSource on each SwarmCoordinationEvent
    void onSwarmInitialised(List<RetrievedExperience> experiences, SwarmProgress progress) { ... }
    void onRoleChanged(List<DetectedRole> roles, SwarmProgress progress) { ... }
    void onTeamChanged(List<DetectedTeam> teams, SwarmProgress progress) { ... }
    void onProgressUpdated(SwarmProgress progress) { ... }
}
```

`SwarmEventSource` holds a reference to the `SwarmContext` and updates it before delivering each `DriverEvent` to the `ChoreographedDriver`'s event queue. This ensures the context is always fresh when `SwarmRoutingStrategy.route()` reads it in the next iteration.

### Engine Integration

Agency implements two engine-api SPIs:

| SPI | Purpose | Contract |
|-----|---------|----------|
| `SwarmProvisioningAdvisor` | Scaling veto | `advise(ProvisioningContext) → ProvisioningAdvice` (shouldProvision boolean + adjusted capabilities/policy) |
| `SwarmCoordinationListener` (NEW — needed in engine-api) | Event bridge | Receives swarm lifecycle events (role changes, team changes, progress updates) without depending on engine-runtime types |

`SwarmProvisioningAdvisor` is a narrow concern: should we add/remove agents? The coordination intelligence (team formation, work distribution, collective reasoning, adaptive strategy) lives in the coordinator's own prompt/invocation pipeline.

**Event bridge (speculative — depends on engine#1112):** `SwarmCoordinationListener` is a proposed new SPI in engine-api that engine-runtime's `SwarmProvisioner` would call when swarm state changes. This API does not yet exist; engine#1112 is open. If the engine team ships a different event contract, `SwarmEventSource` acts as the adapter layer — its responsibility is translating whatever engine events arrive into `DriverEvent` instances. The adapter pattern isolates the rest of the swarm coordination design from engine API changes. Agency implements `SwarmEventSource` (an `EventSource` impl) that wraps this listener and translates events to `DriverEvent` instances for the `ChoreographedDriver`'s blocking queue. This keeps the dependency direction clean: agency depends on engine-api, engine-runtime depends on engine-api, neither depends on the other.

```java
// New in engine-api (engine#1112 follow-up)
@FunctionalInterface
public interface SwarmCoordinationListener {
    void onSwarmEvent(SwarmCoordinationEvent event);
}

// engine-api — event types visible to both engine-runtime and agency
public sealed interface SwarmCoordinationEvent {
    record SwarmInitialised(List<RetrievedExperience> experiences, SwarmProgress progress) implements SwarmCoordinationEvent {}
    record RoleChanged(List<DetectedRole> roles, SwarmProgress progress) implements SwarmCoordinationEvent {}
    record TeamChanged(List<DetectedTeam> teams, SwarmProgress progress) implements SwarmCoordinationEvent {}
    record ProgressUpdated(SwarmProgress progress) implements SwarmCoordinationEvent {}
    record MemberCompleted(String agentId, Object result) implements SwarmCoordinationEvent {}
    record ProvisioningChanged(int currentSize, int maxSize) implements SwarmCoordinationEvent {}
}
```

## New Types

### Package: `io.casehub.agency.orchestration.swarm`

Location: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/`

| Class | What it does |
|-------|-------------|
| `LlmSwarmCoordinator` | Composition root. Wires SwarmRoutingStrategy, CollectiveReasoningAggregation, SwarmExperienceSection, SwarmProvisioningAdvisorImpl. Manages coordinator prompt assembly. Not CDI-managed — constructed by PatternCompiler via SwarmCoordinatorBuilder. |
| `SwarmCoordinatorBuilder` | Builder API for constructing LlmSwarmCoordinator. Mirrors the PatternDefinition.Swarm record fields. Used by PatternCompiler and Java consumers. |
| `SwarmRoutingStrategy` | `RoutingStrategy<SwarmContext>` implementation. Invokes coordinator LLM with full agent roster (or CoalitionEvaluator-scored shortlist for large rosters), experience replay context, and current swarm state. Returns `RoutingDecision.SwarmAssignment` carrying both the flat agent list (for dispatch compatibility) and structured team assignments. **Pre-filter heuristic** (when agent count > `preFilterThreshold`): capability-based greedy construction — group agents by declared capabilities, greedily build `preFilterThreshold` candidate `CoalitionProposal` instances by selecting agents that maximise uncovered required capabilities, score each via `CoalitionEvaluator.evaluate()`, present the top-scoring proposals to the LLM. This avoids combinatorial blowup (linear in capability clusters, not C(n,k) in roster size). |
| `SwarmContext` | Mutable state holder (class, not record) carrying swarm-specific data: detected roles, detected teams, swarm progress, active coordination strategy, pre-retrieved CBR experiences. Updated by `SwarmEventSource` as `SwarmCoordinationEvent` instances arrive. The same instance is passed to every driver iteration, ensuring fresh state without context replacement. See §SwarmContext State Evolution. |
| `CollectiveReasoningAggregation` | `AggregationStrategy<SwarmContext>` implementation. Receives `AggregationContext<SwarmContext>` (same `T` as all other `ExecutionModel` components). Collects AgentResults from swarm members, reads swarm state from `context.state()`, invokes LLM via `StructuredAgentInvoker` with synthesis-focused prompt (not summarisation). Produces `AggregationResult.Resolved(swarmAggregationState)` — `SwarmAggregationState` is the output value, not the type parameter. |
| `SwarmAggregationState` | Record: collective analysis output — agreements, disagreements, complementary observations, confidence, minority viewpoints. |
| `SwarmExperienceSection` | Coordinator prompt section — contributes experience replay context to the coordinator's system prompt. Reads pre-retrieved experiences from `SwarmContext` and uses `ExperienceAnalyser` static methods for per-agent success rate analysis, formats as coordination pattern examples. Configurable via `ExperienceReplayConfig`. Not engine-api's `RoutingPromptSection` — the coordinator has its own prompt assembly. Implements a local `@FunctionalInterface SwarmPromptSection` (`String contribute(SwarmContext)`). |
| `SwarmPromptSection` | `@FunctionalInterface`: `String contribute(SwarmContext)`. Coordinator prompt composition SPI — each section contributes text for the coordinator's system prompt. Multiple sections assembled by LlmSwarmCoordinator. |
| `ExperienceReplayConfig` | Record: `maxTraces` (int, default 5), `minSimilarity` (double, default 0.3). YAML-configurable. |
| `SwarmProvisioningAdvisorImpl` | `SwarmProvisioningAdvisor` implementation. Receives `ProvisioningContext` (carrying `SwarmBootstrapContext` with engine's view: activeSignals, currentRoles, currentTeams, swarmProgress, triggeringRequest) and holds a reference to the coordinator's `SwarmContext`. Both use the same engine-api types (`DetectedRole`, `DetectedTeam`, `SwarmProgress`), so bridging is direct field access — no lossy mapping. The LLM prompt includes both the engine's provisioning context and the coordinator's accumulated state (team assignments, coordination strategy, experience data). Falls back to heuristic (budget-based) on LLM failure. |
| `SwarmEventSource` | `EventSource` implementation. Wraps `SwarmCoordinationListener` (engine-api SPI), translates `SwarmCoordinationEvent` to `DriverEvent`. Registered with engine at swarm startup. |
| `SwarmInvocationPolicy` | Record: `maxCallsPerExecution` (int), `maxTokensPerExecution` (long), `provisioningReserve` (int, default 3), `warnAfter` (Duration), `errorAfter` (Duration). Extends the latency-only `LlmInvocationPolicy` concept with cost budgets. Global budget shared across all four LLM capabilities, with a per-capability exemption: provisioning advice retains a reserved call count (`provisioningReserve`) that non-provisioning capabilities cannot consume. This ensures safety-critical scaling decisions always have LLM capacity. |
| `SwarmInvocationTracker` | Thread-safe counter tracking LLM calls and tokens consumed within a swarm execution. Checked before each coordinator invocation. When budget exhausted, triggers heuristic fallback — except for provisioning advice, which draws from its reserved allocation. |
| `SwarmConvergenceTermination` | `TerminationCondition<SwarmContext>` implementation. Three-outcome evaluation: (1) `consensusScore >= threshold AND stabilityScore >= threshold` → `TerminationDecision.Complete` (converged); (2) `stabilityScore >= threshold AND consensusScore < threshold` → `TerminationDecision.Escalate` (deadlocked — stable but disagreeing, wastes LLM budget to continue); (3) otherwise → `Continue` (still evolving). Follows the same pattern as `ConvergenceTermination.toDecision()` which returns `Escalate` for `DEADLOCK`. Does NOT use `ConvergenceTermination` (which requires `ConversationState` and the epistemic/convergence subsystem). Constructed with a `threshold` (double). The `PatternCompiler.resolveTermination()` handles `TerminationSpec.Convergence` for `PatternDefinition.Swarm` by constructing this type instead of throwing `UnsupportedOperationException`. |
| `SwarmFallbackStrategy` | Heuristic fallback implementations for when LLM is unavailable or budget exhausted: `CoalitionEvaluator` for team formation, `CollectAll` for aggregation, budget-based logic for provisioning advice. |

### Modified type: `RoutingDecision` (agency-core)

Add a new sealed variant to `RoutingDecision` (also add `SwarmAssignment` to the existing `permits` clause: `permits Selected, Unresolvable, Escalate, SwarmAssignment`):

```java
record SwarmAssignment(
    List<AgentRef> agents,
    Map<String, List<AgentRef>> teams,
    @Nullable String strategy,
    @Nullable String reason
) implements RoutingDecision {
    public SwarmAssignment {
        agents = List.copyOf(agents);
        teams = Map.copyOf(teams);
    }
}
```

`agents` provides the flat dispatch list for `AbstractExecutionDriver.dispatchAgents()` compatibility. `teams` carries structured team assignment metadata for listeners, metrics, and `CollectiveReasoningAggregation` (which can synthesise per-team before cross-team). `strategy` is the coordinator's selected coordination approach (e.g., "divide-and-conquer", "sequential-review").

`AbstractExecutionDriver.executeIteration()` already handles `RoutingDecision.Selected`; it needs a new case for `SwarmAssignment` that reads `agents()` for dispatch. The `SwarmAssignment` case is structurally identical to `Selected` for dispatch but carries richer metadata for listeners.

### Package: `io.casehub.agency.agentic.yaml` (additions)

| Class | What it does |
|-------|-------------|
| `PatternDefinition.Swarm` | New sealed record variant. Topology-specific fields: `ExperienceReplaySpec experience`, `@Nullable String coordinationModel`, `@Nullable SwarmInvocationPolicySpec invocationPolicy`, `int preFilterThreshold` (default 12). |
| `ExperienceReplaySpec` | Record: `int maxTraces` (default 5), `double minSimilarity` (default 0.3). |
| `SwarmInvocationPolicySpec` | Record: `int maxCalls` (default 50), `long maxTokens` (default 500_000), `int provisioningReserve` (default 3), `@Nullable String warnAfter`, `@Nullable String errorAfter`. |

### Enum additions

| Enum | New value |
|------|-----------|
| `PatternType` | `SWARM` (not workflow-shaped) |
| `CapabilityType` | `SWARM_COORDINATION` under `CapabilityCategory.ORCHESTRATION` |

New switch cases required:
- `PatternType.toCapabilityType()`: `SWARM → CapabilityType.SWARM_COORDINATION`
- `CapabilityType.toPatternType()`: `SWARM_COORDINATION → Optional.of(PatternType.SWARM)`

## YAML Surface

```yaml
type: swarm
name: incident-response-swarm
description: Coordinated incident response team
coordinationModel: claude-sonnet-4-5
preFilterThreshold: 12

experience:
  maxTraces: 5
  minSimilarity: 0.3

invocationPolicy:
  maxCalls: 50
  maxTokens: 500000
  provisioningReserve: 3
  warnAfter: 5s
  errorAfter: 30s

agents:
  - name: analyst
    description: Analyses incident data
    capabilities: [data-analysis, pattern-recognition]
  - name: responder
    description: Executes remediation steps
    capabilities: [incident-response, system-admin]
  - name: communicator
    description: Manages stakeholder communication
    capabilities: [communication, reporting]

termination:
  - type: convergence
    threshold: 0.85
  - type: max-iterations
    maxIterations: 20

judgment:
  type: convergence
  threshold: 0.9
```

The standard 12 common fields are available. Mechanical swarm tuning (maxSwarmSize, teamAffinityThreshold, roleSimilarityThreshold, detectionInterval) is configured via engine's `SwarmConfig`, not duplicated here. `SwarmConfig` is an engine-level concern — it's provided to the engine at case/environment level through engine's own configuration mechanisms (case definition, engine bootstrap), separate from agency pattern compilation. The agency YAML pattern defines the LLM coordination layer; the engine's mechanical infrastructure parameters live in engine's configuration domain. This is the same separation as other topologies: a supervisor YAML pattern doesn't configure the engine's routing infrastructure.

## Java Builder API

```java
Patterns.swarm()
    .name("incident-response-swarm")
    .coordinationModel("claude-sonnet-4-5")
    .preFilterThreshold(12)
    .experience(new ExperienceReplayConfig(5, 0.3))
    .invocationPolicy(new SwarmInvocationPolicy(50, 500_000, 3,
        Duration.ofSeconds(5), Duration.ofSeconds(30)))
    .agents(analyst, responder, communicator)
    .termination(new SwarmConvergenceTermination(0.85)
        .or(new MaxIterationsTermination<>(20)))
    .build();
```

## Experience Replay

`SwarmExperienceSection` implements `SwarmPromptSection` and injects historical coordination patterns into the coordinator's system prompt.

**Retrieval pipeline:** Experiences are delivered to agency through the engine-api SPI boundary — agency never calls `CbrRetrievalService` directly (it lives in engine-runtime-core, which agency cannot depend on). The mechanism: `SwarmCoordinationEvent` includes a `SwarmInitialised` bootstrap event that carries pre-retrieved `List<RetrievedExperience>`, delivered via `SwarmCoordinationListener` at swarm startup. The engine retrieves experiences using its `CbrRetrievalService` pipeline with the swarm's task description and agent roster capabilities as query context, then passes the results through the event bridge. `SwarmEventSource` stores these in `SwarmContext.experiences` on receipt. This follows the same pattern as `AgentRoutingContext.experiences()` — the engine retrieves, agency consumes — and CBR ADR 0001's single retrieval pipeline principle.

**Analysis (agency-side):** `SwarmExperienceSection` reads pre-retrieved experiences from `SwarmContext` and:

1. Calls `ExperienceAnalyser.workerSuccessRates()` with the pre-retrieved experiences and eligible agent IDs to compute per-agent historical performance
2. Filters experiences by `minSimilarity` threshold
3. Extracts team-level coordination patterns: which agent combinations succeeded, what roles emerged, how work was distributed
4. Formats top N traces (`maxTraces`) as structured examples in the coordinator prompt

The section is assembled into the coordinator's prompt alongside swarm state context (detected roles, teams, progress). This follows the same composition pattern as `CbrRoutingPromptSection` in the routing pipeline — analysis-only, operating on pre-retrieved data.

## Collective Reasoning

`CollectiveReasoningAggregation` implements `AggregationStrategy<SwarmContext>`. The type parameter must be `SwarmContext` (matching `ExecutionModel<SwarmContext>`) — `SwarmAggregationState` is the output wrapped in `AggregationResult.Resolved`, not the type parameter. Unlike existing mechanical aggregations (PassThrough, CollectAll, MajorityVote), this is the first LLM-powered aggregation strategy.

The synthesis prompt is explicitly multi-perspective analysis, not summarisation:
- What do the swarm members agree on?
- Where do they disagree, and what evidence supports each position?
- What does each member observe that others don't (complementary observations)?
- What is the collective confidence level?
- Are there minority viewpoints that should be preserved?

The output is a `SwarmAggregationState` record that the coordinator uses for adaptive decisions.

## Adaptive Coordination

The coordinator adapts swarm strategy by re-reasoning when `SwarmCoordinationEvent` instances arrive via `SwarmEventSource`. Events are coalesced by `EventConcurrencyPolicy.coalesceBySource()` to prevent LLM call storms when rapid state changes occur.

Adaptation decisions the coordinator can make:
- Re-assign work between swarm members based on progress and role changes
- Request provisioning/deprovisioning via `SwarmProvisioningAdvisorImpl`
- Adjust coordination approach based on convergence signals
- Flag deadlocks or stalls for escalation

The coordinator does NOT have a fixed phase model (form → distribute → observe → adapt). It receives events and reasons about what coordination action, if any, is needed. This aligns with the "minimal scaffolding" principle.

## Cost Controls and Failure Degradation

### Cost Controls (D11)

`SwarmInvocationPolicy` provides per-execution budgets:
- `maxCallsPerExecution`: hard limit on LLM invocations across all four capabilities
- `maxTokensPerExecution`: hard limit on total tokens consumed
- `warnAfter` / `errorAfter`: latency thresholds per individual call

`SwarmInvocationTracker` is a thread-safe counter checked before each coordinator invocation. When budget is exhausted, the tracker returns a signal that triggers `SwarmFallbackStrategy`.

### Failure Degradation (D12)

When the coordinator LLM is unavailable (network failure, rate limit, context overflow) or budget is exhausted:

| Capability | Heuristic fallback |
|-----------|-------------------|
| Team formation | `CoalitionEvaluator` — capability-coverage scoring |
| Collective reasoning | `CollectAll` — pass-through without synthesis |
| Adaptive coordination | No-op — swarm continues without re-coordination |
| Provisioning advice | Budget-based heuristic — provision if under budget and capabilities needed |

The swarm continues operating with reduced intelligence. Observability events are delivered via a new `default void onSwarmDegraded(SwarmDegradedEvent event) {}` callback on `ExecutionEventListener` — backward-compatible since all methods on the interface have default implementations. `SwarmDegradedEvent` records (new, in `io.casehub.agency.orchestration.swarm`) signal which capability degraded and whether fallback is active. Agency's own event model; no dependency on engine-runtime-core's `SwarmEvent`.

## PatternCompiler Changes

`PatternCompiler.compile()` gets a new case in each resolver:

| Resolver | Swarm default |
|----------|---------------|
| `resolvePatternType()` | `PatternType.SWARM` |
| `resolveRouting()` | `SwarmRoutingStrategy` (constructed with coordinator, experience section, invocation tracker) |
| `resolveAggregation()` | `CollectiveReasoningAggregation` (constructed with coordinator LLM config) |
| `resolveTermination()` | `SwarmConvergenceTermination` — when the `PatternDefinition` is `Swarm`, the compiler handles `TerminationSpec.Convergence` directly (constructing `SwarmConvergenceTermination` with the threshold) instead of delegating to `TerminationConditionRegistry` (which throws `UnsupportedOperationException` for `Convergence`). Same pattern as `TerminationSpec.JudgeConvergence` handling. |

The compiler constructs `LlmSwarmCoordinator` via `SwarmCoordinatorBuilder`, wiring all components before producing the `ExecutionModel`.

## Test Strategy

| Test | What it verifies |
|------|-----------------|
| `PatternDefinitionSerializationTest` | YAML round-trip for `type: swarm` with all fields |
| `PatternCompilerTest` | Swarm compilation produces correct ExecutionModel with SwarmRoutingStrategy and CollectiveReasoningAggregation |
| `SwarmRoutingStrategyTest` | Team formation with mock AgentProvider — batch reasoning path and pre-filter fallback path |
| `CollectiveReasoningAggregationTest` | Multi-agent result synthesis with mock LLM — verifies structured output (agreements, disagreements, complementary observations) |
| `SwarmExperienceSectionTest` | Experience replay formatting from mock ExperienceAnalyser data |
| `SwarmProvisioningAdvisorImplTest` | Provisioning veto reasoning + heuristic fallback on LLM failure |
| `SwarmEventSourceTest` | SwarmCoordinationEvent → DriverEvent translation |
| `SwarmInvocationTrackerTest` | Budget enforcement — call counting, token tracking, exhaustion signal |
| `SwarmConvergenceTerminationTest` | Convergence evaluation against SwarmProgress.consensusScore() and stabilityScore() thresholds |
| `SwarmFallbackStrategyTest` | Heuristic fallback behaviour for each capability |
| `LlmSwarmCoordinatorTest` | Integration test — full coordinator loop with mock agents and mock LLM |

All tests use plain JUnit 5 + Mockito (no CDI container, per project conventions).

## Files Changed

### New files (agency-core)
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/LlmSwarmCoordinator.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmCoordinatorBuilder.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmRoutingStrategy.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmContext.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/CollectiveReasoningAggregation.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmAggregationState.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmExperienceSection.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/ExperienceReplayConfig.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmProvisioningAdvisorImpl.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmEventSource.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmInvocationPolicy.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmInvocationTracker.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmConvergenceTermination.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmDegradedEvent.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmFallbackStrategy.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmPromptSection.java`
- `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmBuilder.java`

### New files (agentic-yaml)
- `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/ExperienceReplaySpec.java`
- `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/SwarmInvocationPolicySpec.java`

### Modified files (agency-core)
- `RoutingDecision.java` — add `SwarmAssignment` to sealed `permits` clause and add the new record variant
- `AbstractExecutionDriver.java` — add `SwarmAssignment` case to `instanceof` chain in `executeIteration()` (reads `agents()` for dispatch, same as `Selected`; without this, `SwarmAssignment` silently falls through with no dispatch)
- `ExecutionEventListener.java` — add `default void onSwarmDegraded(SwarmDegradedEvent event) {}` callback for degraded-mode observability
- `Patterns.java` — add `swarm()` builder method (9th topology, alongside existing `supervisor()`, `sequence()`, `loop()`, `parallel()`, `voting()`, `debate()`, `conditional()`, `htn()`)

### Modified files (agentic-yaml)
- `PatternDefinition.java` — add `Swarm` sealed record variant + `@Type` annotation
- `PatternCompiler.java` — add swarm cases to all resolver switches
- `PatternType.java` — add `SWARM` enum value
- `CapabilityType.java` — add `SWARM_COORDINATION` enum value

### New test files
- `agency/src/test/java/io/casehub/agency/orchestration/swarm/` — 11 test classes as listed above
- `agentic-yaml/src/test/resources/patterns/swarm.yaml` — YAML fixture

### Modified test files
- `PatternDefinitionSerializationTest.java` — add "swarm" to `@ValueSource` + field test
- `PatternCompilerTest.java` — add swarm compilation test

### Cross-repo (engine-api — separate issue/PR)
- New `SwarmCoordinationListener` SPI interface
- New `SwarmCoordinationEvent` sealed interface with 6 event types (including `SwarmInitialised` bootstrap event carrying pre-retrieved experiences)

## Out of Scope

- **Distributed per-agent LLM coordination** (agency#361) — future evolution where each swarm member has its own lightweight LLM decision loop
- **SwarmCoordinationListener implementation in engine-runtime** (engine#1259) — engine#1112 follow-up work; agency provides the interface contract
- **Prompt tuning/optimisation** (agency#362) — the coordinator prompts will use sensible defaults; optimisation via the existing prompt optimisation framework is future work
- **Multi-swarm coordination** (agency#363) — coordination across multiple swarm instances

## References

- engine#1112 — Swarm execution model (mechanical infrastructure)
- engine#1104 — Hive Mind epic (parent)
- `PatternDefinition.java` — existing topology sealed record pattern
- `PatternCompiler.java` — spec → ExecutionModel compilation
- `ChoreographedDriver.java` — event-driven execution driver
- `SwarmConfig` (engine-api) — mechanical swarm configuration
- `SwarmProvisioningAdvisor` (engine-api) — provisioning veto SPI
- `ExperienceAnalyser` (engine-api) — CBR plan trace analysis utility
- `CoalitionEvaluator` (agency deliberation/coalition) — heuristic team scoring
- `CollectAll` (agency orchestration/aggregation) — pass-through aggregation
- `LlmInvocationPolicy` (agency stigmergy/llm) — latency threshold pattern
- `EventSource` / `EventConcurrencyPolicy` (agency orchestration/execution) — event-driven infrastructure
- `StructuredAgentInvoker` (agency core/invoke) — LLM invocation utility
- CBR ADR 0001 (agency docs/adr) — single retrieval pipeline principle
- `CbrRoutingPromptSection` (agency routing/agent) — CBR prompt section pattern
- SwarmExp (arXiv:2608.30661) — experience extraction + replay
- "Drop the Hierarchy and Roles" (arXiv:2603.28990) — minimal scaffolding
