# casehub-agency -- Contributor Guide

> Internals, architecture, and extension points for platform builders working on casehub-agency itself.

**GitHub:** [casehubio/agency](https://github.com/casehubio/agency)

---

## Module Structure

Single module -- `casehub-agency` is a flat library, not a multi-module reactor. Single artifact: `casehub-agency`.

| Path | Contents |
|------|----------|
| `agency-core/src/main/java/io/casehub/agency/communication/dispatch/` | Channel dispatch utilities -- message meta, context tracking, bounded projection, agent dispatch |
| `agency-core/src/main/java/io/casehub/agency/communication/channel/summary/` | Channel summary integration -- `ContentSummariser<Message>` wiring + `SummaryUpdateHook` adapter |
| `agency-core/src/main/java/io/casehub/agency/deliberation/conversation/` | Structured conversation protocol -- projections, fold state, rendering, epistemic common ground, convergence detection (27 types) |
| `agency-core/src/main/java/io/casehub/agency/core/` | Compositional agentic orchestration -- five SPIs, execution drivers, pattern builders (ten sub-packages) |
| `agency-core/src/main/java/io/casehub/agency/orchestration/activation/` | Activation SPI: `ActivationRule`, `ActivationContext`, `Activation` factory, implementations |
| `agency-core/src/main/java/io/casehub/agency/orchestration/aggregation/` | Aggregation SPI: `AggregationStrategy`, `AggregationContext`, `AggregationResult`, `Aggregation` factory, implementations |
| `agency-core/src/main/java/io/casehub/agency/orchestration/decomposition/` | Decomposition: `Decomposition`/`Tasks` factories, strategies, heuristics, task types (21 files) |
| `agency-core/src/main/java/io/casehub/agency/orchestration/listener/` | Accountability listeners: `EventLogListener`, `LedgerExecutionListener`, `MetricsListener`, `OrchestrationEventType` |
| `agency-core/src/main/java/io/casehub/agency/orchestration/execution/` | Execution model: drivers, invokers, backends, state machine (11 files) |
| `agency-core/src/main/java/io/casehub/agency/orchestration/pattern/` | Pattern DSL: `Patterns` entry point, 8 builders + `AbstractPatternBuilder` base class |
| `agency-core/src/main/java/io/casehub/agency/orchestration/plan/` | `ExecutionPlan<T>` -- DAG record with cycle validation |
| `agency-core/src/main/java/io/casehub/agency/orchestration/routing/` | Routing SPI: `RoutingStrategy`, `RoutingDecision`, `Routing` factory, 4 implementations + `StageGate`/`StageAwareCandidateSupplier` |
| `agency-core/src/main/java/io/casehub/agency/orchestration/termination/` | Termination SPI: `TerminationCondition`, `TerminationDecision`, `Termination` factory, 4 implementations |
| `agency-core/src/main/java/io/casehub/agency/governance/routing/` | Trust routing audit types -- compliance records for routing decisions |
| `agency-core/src/main/java/io/casehub/agency/governance/routing/agent/` | AI-powered AgentRoutingStrategy implementations -- LLM-reasoned and CBR-evidence agent selection (15 files) |
| `agency-core/src/main/java/io/casehub/agency/summarisation/` | Temporal abstraction framework + content summarisation SPI -- event levels, windowed accumulation, pluggable summarisation (14 files) |
| `agency-core/src/main/java/io/casehub/agency/perception/llm/` | LLM-backed `ContentSummariser<T>` -- generic synthesis via `AgentProvider` |
| `agency-core/src/main/java/io/casehub/agency/perception/observation/` | Observation accumulator -- tiered, demand-driven rendering for LLM agent prompts (4 files) |
| `agency-core/src/main/java/io/casehub/agency/perception/observation/affordance/` | Affordance grounding -- per-entity observation rendering for LLM agents (5 files) |
| `agency-core/src/main/java/io/casehub/agency/social/cognition/` | Cognition integration -- `CognitionAvatarAdapter` (`AvatarCognition` impl delegating to neocortex `CognitionCore`) |

Tests mirror source layout under `src/test/java/`. Integration test examples exist in:
- `summarisation/examples/clinical/` -- L1-L4 clinical pipeline (vital readings -> care phases -> narratives)
- `summarisation/examples/logistics/` -- L1-L4 logistics pipeline (package scans -> anomalies -> hub phases -> narratives)
- `summarisation/examples/decision/` -- two-level decision narrative pipeline (trading signals -> step summaries -> trade narratives)
- `summarisation/examples/channel/` -- tiered channel summary end-to-end
- `summarisation/examples/multiagent/` -- multi-agent observation pipeline with partitioned observation service
- `agentic/decomposition/examples/incident/` -- HTN hybrid decomposition for incident response

## Internal Architecture

### Execution Driver Internals

`AbstractExecutionDriver<T>` is the base class for both `OrchestratedDriver` and `ChoreographedDriver`. It implements a five-phase iteration loop:

1. **Route** -- invoke `RoutingStrategy.route()` with current candidates and state
2. **Activate** -- evaluate `ActivationRule.shouldActivate()` for each selected agent
3. **Dispatch** -- invoke agents via `AgentInvoker` (handles ExternalAgent, ComposedAgent natively; ChannelAgent/WorkerAgent/HumanAgent require custom invokers)
4. **Aggregate** -- run `AggregationStrategy.aggregate()` on collected results
5. **Terminate** -- evaluate `TerminationCondition.evaluate()` to decide Continue/Complete/Failed/Escalate

Key internal state:
- `AtomicReference<ExecutionState>` -- sealed 7-state machine (Idle -> Running -> WaitingForAgent -> ... -> Complete/Faulted/Cancelled)
- `Map<AgentRef, Integer> activationWeights` -- per-agent activation tracking
- `Map<AgentRef, Integer> consecutiveIdleCounts` -- idle detection for activation context
- `volatile boolean cancelled` -- cooperative cancellation

`OrchestratedDriver` runs the loop imperatively (while loop, transitions to Running each iteration). `ChoreographedDriver` runs it reactively (transitions to WaitingForEvent between iterations).

### Pattern Builder Architecture

All 8 pattern builders extend `AbstractPatternBuilder<T, B>` (CRTP pattern). The base class provides:
- Fluent setters for all five SPIs plus failure policy, listeners, task description, backend
- `build()` -- assembles an `ExecutionModel<T>` with the configured SPIs
- `execute(T)` -- builds and runs via the configured `ExecutionBackend` (defaults to `OrchestratedDriver`)
- `agents(AgentRef...)` / `agents(RoutingCandidate...)` -- sets the candidate supplier

Each builder overrides defaults for its pattern topology:
| Builder | Default Routing | Default Aggregation | Default Termination |
|---------|----------------|--------------------|--------------------|
| `SupervisorBuilder` | FirstMatch or LlmSelected | PassThrough | MaxIterations(10) |
| `SequenceBuilder` | Sequential | PassThrough | After N iterations |
| `LoopBuilder` | RoundRobin | PassThrough | MaxIterations(default 10) |
| `ParallelBuilder` | Select all | CollectAll | After 1 iteration |
| `VotingBuilder` | Select all | MajorityVote | After 1 iteration |
| `DebateBuilder` | RoundRobin | CollectAll | MaxIterations or JudgeConvergence |
| `ConditionalBuilder` | FirstMatch | PassThrough | After 1 iteration |
| `HtnBuilder` | Sequential | CollectAll | After 1 iteration (dynamically adjusted) |

`HtnBuilder` is special: its `execute(T)` override flattens the HTN tree via decomposition, builds a dynamic `ExecutionModel` with agent count derived from the plan, then runs via `OrchestratedDriver`.

### Agentic Decomposition Strategies

Nine decomposition strategies with increasing sophistication:

| Strategy | Approach | Key Internal Detail |
|----------|----------|---------------------|
| `IdentityDecomposition` | Pass-through -- wraps single task as-is | Throws on CompoundTask input |
| `StaticDecomposition` | Pre-defined task breakdown via `DecompositionMethod` | First-match guard evaluation; pre-filters methods by `estimatedCost`/`estimatedDuration` against `context.constraints()` |
| `SequenceStrategy` | Package-private: resolves children sequentially using composed decomposer | Used internally by `Tasks.compound()` and `Tasks.decompose()` |
| `ForwardReasoningDecomposition` | SHOP-style forward reasoning -- applies `PrimitiveTask.effect()` to projected state | Uses `UnaryOperator<T> stateCopier` to clone state before effect application |
| `LlmDecomposition` | Recursive multi-level LLM planning via `maxDepth` | Parses JSON: `[{"agent":..., "task":..., "rationale":...}]` for leaves, `{"subtask":..., "description":...}` for compounds |
| `HybridDecomposition` | Static-first with LLM fallback on `NoMethodMatchedException` | Passes `staticFailureHint` (failed method's task name + method count) to LLM context |
| `CapabilityDependencyDecomposition` | GOAP backward-chaining from goal state | Matches goal types to agent capabilities via preconditions/effects, builds dependency DAG via `CapabilityDependencyContext` |
| `HeuristicDecomposition` | Ranked method selection via `DecompositionHeuristic<T>` | Tries methods in score order, backtracks on `NoMethodMatchedException`. Uses `ScoredMethod<T>` |
| `CompositeHeuristic` | Weighted combination of multiple `DecompositionHeuristic<T>` | Min-max normalisation across delegates. Inner: `WeightedHeuristic<T>(heuristic, weight)` |

**Heuristic implementations:**
- `StructuralCostHeuristic<T>` -- tree cost estimation. Leaf = 1.0, compound = min of methods, opaque = configurable. Scores by negative cost (lower cost = better).
- `LlmDecompositionHeuristic<T>` -- asks LLM to score methods 0.0-1.0 via JSON response. Falls back to equal scores on failure.

**Task types:**
- `PrimitiveTask<T>` -- leaf with precondition, effect, and output contract. Effect: `Consumer<T>` applied to state during forward reasoning.
- `PlannedTask<T>` -- leaf with rationale. Created by LLM decomposition. No precondition/effect.
- Both implement `TaskNode.LeafTask<T>` (from engine-api) and carry an `AgentRef`.

**DSL factories:**
- `Decomposition` -- strategy-level factories: `none()`, `staticTree()`, `goap()`, `hybrid()`, `heuristic()`. Also `method()`, `sequence()`, `primitive()`, `compound()`, `planned()`.
- `Tasks` -- tree-construction-level factories: `primitive()` (4 overloads with effect/description), `planned()` (2 overloads), `compound()` (2 overloads: with methods or with children), `decompose()` (2 overloads: with/without guard predicate).

### Conversation Projection and Fold Architecture

`ConversationProjection` is the abstract base for channel projections. It implements `ChannelProjection<ConversationState>` and delegates to `ConversationFold` for state transitions. The dispatch logic:

1. Parse metadata from message body via `ChannelMessageMeta.parseMeta(sentinel(), content)`
2. Extract entry type from metadata (`ConversationProtocol.ENTRY_TYPE`)
3. If entry type is in `skipEntryTypes()` set, skip the message
4. Dispatch infrastructure types directly: MEMO -> `ConversationFold.addMemo()`, SUB_TASK_REQUEST -> `requestSubTask()`, SUB_TASK_FINDING -> `completeSubTask()`, SUB_TASK_ERROR -> `errorSubTask()`, FLAG_HUMAN -> `flagHuman()`
5. For domain types: if `isPointInitiator(entryType)` -> `createPoint()`, otherwise -> `respondToPoint()` with `statusAfter(entryType)`

`ConversationFold` is purely functional -- every method takes a `ConversationState` and returns a new `ConversationState`. No I/O, no parsing. Eight operations: createPoint, respondToPoint, reprioritisePoint, flagHuman, addMemo, requestSubTask, completeSubTask, errorSubTask.

### Epistemic Common Ground Pipeline

Three-stage analysis pipeline:

1. **CommonGroundAnalyser.analyse()** -- iterates all conversation points, builds `ParticipantContext` for each (who participated, responded, acknowledged, completed, disputed, failed), applies the `EpistemicRule` to classify each point as ESTABLISHED/PENDING/DISPUTED, produces `CommonGroundState`.

2. **ConvergenceAnalyser.analyse()** -- builds `ConvergenceContext` (similarity metrics, staleness measures, message-type distribution), delegates to `ConvergencePolicy.evaluate()` for final signal.

3. **ConvergenceTermination** -- bridges into the agentic termination SPI. Extracts `ConversationState` from execution state via `stateExtractor`, runs both analysers, maps CONSENSUS/DIMINISHING_RETURNS to `Complete`, DEADLOCK to `Escalate`, others to `Continue`.

**Built-in EpistemicRules:** `explicitAcknowledgement(minParticipants)` -- N distinct responders needed. `tacitAcceptance(windowRounds)` -- silence after N rounds = established. `commitmentResolution()` -- DONE message = established.

**Built-in ConvergencePolicies:** `structural(threshold, staleRounds)` -- Jaccard similarity + staleness. `commonGroundRatio(consensusThreshold, deadlockRatio)` -- established/total ratio. `composite(policies...)` -- picks highest-severity signal.

### Agentic Accountability Listeners

Three listeners implementing `ExecutionEventListener`:

| Listener | Sink Type | Purpose |
|----------|-----------|---------|
| `EventLogListener` | `EventSink` (@FunctionalInterface: `record(OrchestrationEventType, Map<String, Object>)`) | Operational audit |
| `LedgerExecutionListener` | `LedgerSink` (@FunctionalInterface: `record(OrchestrationEventType, actorId, actorRole, Map<String, Object>)`) | EU AI Act Art.12 compliance audit |
| `MetricsListener` | OTel `Meter` | Telemetry: agent duration histogram, routing/activation counters, failure counter, execution duration/iterations histograms |

`OrchestrationEventType` enum: `EXECUTION_STARTED`, `ROUTING_DECISION`, `ACTIVATION_EVALUATED`, `AGENT_DISPATCHED`, `AGENT_RESULT`, `AGENT_FAILED`, `AGGREGATION_COMPLETED`, `TERMINATION_EVALUATED`, `EXECUTION_COMPLETED`.

### Routing Agent Internals

`RoutingSupport` is the package-private utility shared by both `LlmAgentRoutingStrategy` and `CbrAgentRoutingStrategy`. Private constructor. Key internal operations:

- **Trust filtering** via `applyTrustFilter()` -- four-phase trust maturity model using optional `TrustCandidateClassifier`, `TrustScoreSource`, and `TrustRoutingPolicyProvider`. Returns sealed `TrustFilterOutcome`:
  - `Proceed(eligible, classified)` -- trust check passed, continue with filtered candidates
  - `Decided(RoutingResult)` -- terminal decision (escalation or classifier fallback)
- **Prompt construction** via `buildUserPrompt()` and `buildCandidateCard()` -- formats capability + context + candidate cards
- **LLM invocation** via `invokeAndCollect()` -- invokes `AgentProvider`, collects `TextDelta` events
- **Response parsing** via `parseSelection()` and `extractAgentName()` -- handles JSON with markdown fences

**Five `RoutingSignalProvider` implementations:**
- `PlanCompositionAnalyser` (id: `"plan-composition"`) -- case-level outcomes in multi-step plans. Requires planTrace.size() >= 2. Weighted by `CbrCaseOutcomeWeights`.
- `PredecessorAnalyser` (id: `"predecessor"`) -- immediate predecessor context in historical plan traces. Records predecessor (capability:worker) pair in signal reason.
- `CoordinationSignalProvider` (id: `"coordination"`) -- team composition outcomes with adaptation-guided retrieval. Calculates team overlap with eligible pool. Weighted by `CoordinationOutcomeWeights`.
- `DispositionAwareRouting` (id: `"disposition"`) -- personality/disposition match scoring. Extracts `DispositionProfile` from case context JSON at `_routing.disposition.<capabilityName>` or `_routing.disposition.default`. Each `DispositionAxis` is weighted; exact match = 1.0, missing = 0.5, mismatch = 0.0.
- `CbrRoutingPromptSection` -- not a signal provider, but a `RoutingPromptSection` that renders per-agent CBR statistics for LLM routing prompts.

**Outcome weight SPIs (all have `@DefaultBean @ApplicationScoped` defaults):**
- `CbrOutcomeWeights` -- step-level `Map<RoutingOutcome, Double>`. Default: SUCCESS=1.0, GATE_EXPIRED=0.5, GATE_REJECTED=0.25, FAILURE=0.0.
- `CbrCaseOutcomeWeights` -- case-level `Map<String, Double>`. Default: COMPLETED=1.0, FAULTED=0.2, CANCELLED=0.0.
- `CoordinationOutcomeWeights` -- case-level `Map<String, Double>`. Default: COMPLETED=1.0, FAULTED=0.2, CANCELLED=0.0.

Domain repos override these by providing `@ApplicationScoped` beans without `@DefaultBean`.

### Summarisation Pipeline Internals

**Core pipeline:** `EventAccumulator` (synchronized buffer) -> optional `Compactor<E>` (pre-processing) -> `Summariser<IN, OUT>` (async transformation) -> `EventStreamBus` (pub/sub distribution).

`SummarisationRunner` orchestrates this for flat event streams. `KeyedSummarisationRunner` adds grouping via `KeyedAccumulator` (groups by key, drains completed/stale groups independently). Both implement `Tickable` -- the common `tick()`/`flush()` contract used by `CompiledPipeline` for polymorphic runner dispatch.

**Builder API:** Both runners offer a builder for opt-in SPI extensions: `SummarisationRunner.builder(summariser, bus, level).emissionPolicy(...).stateStore(...).stateKeyResolver(...).outputProcessor(...).build()`. Legacy `WindowPolicy` constructors are unchanged. The builder requires exactly one of `windowPolicy()` or `emissionPolicy()`.

**Stateful summarisation:** Both runners detect `StatefulSummariser` via `instanceof` and manage per-partition (SummarisationRunner, keyed by `tenancyId`) or per-key (KeyedSummarisationRunner, keyed by group key `K`) state in a `ConcurrentHashMap`. `ContentSummariser<T, R>.asSummariser()` bridges to `StatefulSummariser<T, R, R>` -- the `R previous` parameter carries accumulated state across batches. `KeyedSummarisationRunner.evictState(K)` removes per-key state for explicit lifecycle cleanup (e.g. case close). Optional `StateStore<S>` provides persistent state via write-through cache -- reads check in-memory first, fall back to `StateStore.load()`, writes go to both.

**Emission gating:** `EmissionPolicy<IN, S>` replaces `WindowPolicy` as the gating mechanism when state-aware decisions are needed (e.g. novelty checks comparing new input against existing state). `WindowPolicy` is wrapped internally as `EmissionPolicy` via the builder. `anyOf()`/`allOf()` static factories for composition.

**Output processing:** `OutputProcessor<OUT, S>` is called after `summarise()` and before output bus publish. Use for post-summarisation transformations (e.g. pruning, filtering, enrichment).

**Tick semantics:** `tick(now)` checks if the `WindowPolicy` (legacy path) or `EmissionPolicy` (builder path) is satisfied, drains if ready, runs compactor + summariser + output processor, publishes to output bus. `flush()` does an unconditional drain regardless of policy -- use at shutdown. Both return `CompletionStage<Void>` for async summarisers.

**Content summarisation layer:** `ContentSummariser<T, R>` is a higher-level contract decoupled from `LevelEvent` wrappers. Generified result type `R` (not tied to `SummaryResult`). `asSummariser()` bridges to `StatefulSummariser` for pipeline use with state. `TieredContentSummariser<T>` routes to different strategies based on batch size. `VerbatimContentSummariser<T>` renders each item as a bullet point (no LLM). `LlmContentSummariser<T>` uses `AgentProvider` with configurable EDIT/APPEND mode.

### Summarisation Observation Pipeline

Terminal consumer of the summarisation pipeline -- tiered, demand-driven rendering for LLM agent prompts.

- `ObservationAccumulator<E>` -- thread-safe buffer with demand-driven drain via `ObservationRenderer`. Tracks `lastDrainTimestamp` for inter-observation timing.
- `TieredObservationRenderer<E>` -- three-tier rendering:
  - Below `verbatimThreshold`: individual events with timestamps
  - Between thresholds: grouped by key with group summaries
  - Above `groupedThreshold`: delegated to `Summariser<E, String>` for LLM/algorithmic summarisation
  - `withHeaderFormatter()` for custom header formatting
- `PartitionedObservationService<E, K>` -- multi-observer context management. Events routed to observers and partitions via `VisibilityPolicy`. On drain, returns current partition's result plus cached "remembered" results from other visible partitions. Uses `ConcurrentHashMap` for observer state.

### Affordance Rendering

`AffordanceRenderer` renders entity-grounded observations for LLM agents:
- `renderEntities(List<ObservableEntity>)` -- identity + affordance bracket notation (e.g., `Entity: name [action1, action2]`)
- `renderObservation(List<ObservationSection>)` -- assembles sections (EntityGroup / TextBlock / ItemList) into plain text
- `renderActionVocabulary(header, List<ActionDescriptor>)` -- formats available action types with descriptions and parameter formats
- `withHeaderFormatter(Function<String, String>)` -- returns a new renderer with custom header formatting

`ObservationSection` is a sealed interface with three variants: `EntityGroup`, `TextBlock`, `ItemList`. Static factories: `entities()`, `text()`, `items()`.

### Cognition Integration

Agency retains a thin integration layer for wiring neocortex's `CognitionCore` into the speech pipeline. All orchestrators, prompt sections, drives, goals, narratives, and social emergence types now live in `casehub-neocortex-cognition`.

**CognitionAvatarAdapter** (`AvatarCognition` impl): delegates to neocortex `CognitionCore` for all cognitive operations. Five methods: `wrapAssembler()` enriches prompts via `CognitionRenderContext`, `initialize()` hydrates orchestrator state, `tick()` drives the cognition cycle (plus optional `CognitiveEvolutionAdapter`), `evaluateProactive()` delegates to `ProactiveSpeechSupport`, `recordInteraction()` dispatches per-turn signals. Optional `CognitiveGoalOrchestrator` integration consumes goal decay revisions and transitions eidos goal lifecycle state.

**Cognitive evolution ticker** (`social.cognition.evolution`): wires engine's `EvolutionTicker` into the cognitive agent's tick cycle. `CognitiveEvolutionAdapter` uses CURIOSITY drive intensity to modulate tick frequency; `CircuitBreakerMoodMapper` feeds circuit breaker state to PAD mood signals. `EvolutionTickBridge` (`@FunctionalInterface`) is the consumer binding point.

**Memory hygiene evolution loop** (`social.memory.evolution`): registers memory hygiene as a participant in the engine's continuous evolution pipeline. `MemoryHygieneProposalSource` proposes tuning when quality drops. `MemoryHygieneRegressionEvaluator` monitors component scores. `MemoryHygieneCapabilityArea` provides landscape position mapping. Core memory hygiene implementation lives in neocortex `cognition` module.

## Multi-Swarm Coordination Architecture

The multi-swarm coordination system enables coordination across multiple swarm instances via a tiered protocol. Engine owns identity and lifecycle; agency owns coordination intelligence.

| Layer | What it does |
|-------|-------------|
| L0 -- Signal routing | `CoordinationBus<MultiSwarmSignal>` predicate-filtered pub/sub with ring-buffer replay. `MultiSwarmListenerBridge` transforms engine `SwarmCoordinationListener` events into `MultiSwarmSignal` instances. |
| L1 -- Relevance scoring | `RelevanceScorer` interface with two strategies: `WeightedRelevanceScorer` (default, linear + online learning) and `BjtRelevanceScorer` (Bayesian junction tree, models conditional independence). `EscalationGate` interface, same two strategies. Builder: `.weightedScoring()` or `.bjtScoring()`. Both are generalised -- no per-domain training needed. |
| L2 -- Conflict detection | `BeliefSet<SwarmConclusion>` with AGM belief revision. `DomainConsistencyChecker` detects structural negation in `entity:state` format -- domains with different conclusion formats plug in their own `ConsistencyChecker<SwarmConclusion>`. |
| L3 -- LLM mediation | Optional. When `agentProvider()` is set on the builder, enables LLM-backed conflict resolution for cases L2 cannot resolve mechanically. |

**Engine/agency boundary:** Engine-api provides `SwarmDescriptor` (swarm identity), `MultiSwarmRegistry` SPI (registration/lookup), `SwarmCoordinationListener` swarmId overloads (lifecycle events), and `CrossSwarmConvergenceEvaluator` SPI. Engine runtime-core provides `InMemoryMultiSwarmRegistry` and `AllConvergedEvaluator`. Agency provides all coordination intelligence via `MultiSwarmTopology`.

**Composition root:** `MultiSwarmTopology` implements both `CoordinationTopology` (topology lifecycle) and `RegistryListener` (dynamic scorer/gate creation). The builder wires L0-L2 automatically. Registered via `MultiSwarmTopologyBuilder.build()` which also calls `registry.addListener(topology)`.

**YAML surface:** `TopologySpec.MultiSwarm` with optional `MultiSwarmCoordinationSpec` thresholds. `MultiSwarmTopologyCompiler.compile()` produces a `CompiledMultiSwarm` from the spec.

**Generalisation:** The scoring layer (L1) is domain-agnostic. All four input features -- capability overlap (Jaccard), domain tag match, signal confidence, historical relevance -- are structural properties of the inter-swarm relationship, not domain-specific knowledge. No per-domain training is needed. The BJT CPT encodes general principles validated across 22K input points. Online learning (when neocortex#856 is resolved) fine-tunes for specific deployments but is not required.

**Convention dependency:** `DomainConsistencyChecker` (L2) assumes conclusions follow `entity:state` format for structural negation detection. This is the only convention-dependent component. Domains using different formats implement their own `ConsistencyChecker<SwarmConclusion>` -- the builder accepts any implementation via `.consistencyChecker()`.

**Known limitation:** BJT online learning is disabled pending neocortex#856 (`OnlineParameterUpdater.update()` corrupts junction tree inference). BJT inference without learning works correctly. `WeightedRelevanceScorer` provides working online learning as the default strategy.

## Trust Routing Architecture

The trust routing system spans four layers -- agency owns AI-powered routing strategies and compliance audit types.

| Layer | Owner | What it does |
|-------|-------|-------------|
| Score computation | **ledger** | `TrustScoreRoutingPublisher` computes trust scores from ledger entries and publishes them. |
| Policy configuration | **engine-api** | `TrustRoutingPolicyProvider` SPI + `TrustRoutingPolicyKeys` + `TrustRoutingPolicyResolver`. Domain repos implement the provider. |
| Classical strategy execution | **engine** | `TrustWeightedAgentStrategy` (engine-ledger) applies trust scores. `SemanticAgentRoutingStrategy` (engine-ai) adds embedding-based re-ranking. |
| AI-powered strategy execution | **agency** (routing.agent) | `LlmAgentRoutingStrategy` (LLM reasoning), `CbrAgentRoutingStrategy` (case-based evidence). Both optionally compose with trust classification via `Instance<TrustCandidateClassifier>`. |
| Compliance audit types | **agency** (routing) | `RoutingDecisionRecord`, `TrustRoutingRequirement`, `RequirementStatus` -- audit trail records. |

Domain repos (aml, devtown, clinical, life, ops) implement `TrustRoutingPolicyProvider` from engine-api -- they configure policy parameters, not compute scores or execute routing.

## Consolidation Epic

Epic #28 tracks extraction of shared patterns from domain repos into agency.

| # | Title | Status | Destination |
|---|-------|--------|-------------|
| #17 | Trust routing YAML | Done | agency |
| #22 | Debate channel infrastructure | Done | agency |
| #23 | Oversight gate lifecycle + risk classification | Done | engine-api / openclaw |
| #24 | Universal pluggable routing strategy | Moved -> engine#634 | engine |
| #30 | AI routing strategy impls (trust, LLM, CBR) | Done | agency |
| #25 | Worker data coordination (DataExchange/DataChannel) | Moved -> engine#633 | engine |
| #27 | Layered event summarisation | Done | agency |

## Depended On By

| Repo | What it uses |
|------|-------------|
| casehub-drafthouse | Channel + conversation -- DebateProtocol delegates to `ConversationConstants`, DebateChannelProjection extends `ConversationProjection`, `ChannelAgentDispatcher`, `BoundedProjectionDecorator`, `ContextTracker` |
| casehub-engine | `ExecutionModel`, `ExecutionDriver`, `ExecutionResult`, `AgentRef`, `RoutingCandidate`, agentic SPIs |
| casehub-quarkmind | Summarisation: `SummarisationRunner`, `EventStreamBus`, `EventAccumulator`, `WindowPolicy`, `Summariser`, `EventLevel`, `LevelEvent` |

## Design Documents

- [Agentic orchestration research](https://raw.githubusercontent.com/casehubio/agency/main/docs/agentic-orchestration-research.md)
- [Execution backend architecture](https://raw.githubusercontent.com/casehubio/agency/main/docs/execution-backend-architecture.md)
- [Unified execution model](https://raw.githubusercontent.com/casehubio/agency/main/docs/unified-execution-model.md)
- [Conversation projections vs chat logs](https://raw.githubusercontent.com/casehubio/agency/main/docs/conversation-projections-vs-chat-logs.md)
