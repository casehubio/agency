# Multi-Swarm Coordination — Design Spec

**Issue:** agency#363 (deferred from #285)
**Branch:** issue-364-swarm-coordination
**Date:** 2026-11-07

## Overview

Coordination across multiple swarm instances running within the same case or across cases. Two use cases for v1 — same-case specialisation and hierarchical composition — served by a single layered abstraction. Cross-case collaboration is designed-for at the SPI level (`allActive()`, cross-case `SwarmDescriptor`) but deferred from v1 implementation (no concrete use case yet).

The design follows two architectural principles:
1. **Tiered coordination** — each coordination concern uses the cheapest mechanism that can handle it, from zero-cost broadcast (L0) to expensive LLM mediation (L3).
2. **Mechanical base, LLM enhancement** — the core coordination works without LLMs using rules + Bayesian BJT. LLM features activate optionally for L3 judgment calls.

## Engine/Agency Boundary

Engine owns identity, lifecycle, and raw events. Agency owns coordination logic.

### Engine-api additions

**`SwarmDescriptor`** — identity record for a swarm instance:
```java
public record SwarmDescriptor(
    UUID swarmId,
    UUID caseId,
    TopologyType topologyType,
    Set<String> capabilities,
    Set<String> agentIds,
    @Nullable UUID parentSwarmId  // for hierarchical composition
) {}
```

**`MultiSwarmRegistry`** — SPI for lifecycle management with listener notification:
```java
public interface MultiSwarmRegistry {
    void register(SwarmDescriptor descriptor);
    void deregister(UUID swarmId);
    void updateInterests(UUID swarmId, Set<String> capabilities);
    List<SwarmDescriptor> activeSwarms(UUID caseId);
    List<SwarmDescriptor> allActive();  // cross-case support (deferred from v1)
    List<SwarmDescriptor> children(UUID parentSwarmId);
    @Nullable SwarmDescriptor get(UUID swarmId);
    void addListener(RegistryListener listener);
}

public interface RegistryListener {
    void onRegistered(SwarmDescriptor descriptor);
    void onDeregistered(UUID swarmId);
    void onInterestsUpdated(UUID swarmId, Set<String> capabilities);
}
```

The listener pattern follows the established convention (`SwarmCoordinationListener`, `AgentWorkLifecycleListener`). `MultiSwarmTopology` registers as a `RegistryListener` during construction to receive notification when swarms register/deregister, enabling lazy creation and cleanup of per-swarm `OnlineParameterUpdater` instances and bus subscriptions.

**`SwarmCoordinationListener` extension** — add `swarmId` to all 7 callbacks:
```java
default void onSwarmInitialised(UUID caseId, UUID swarmId, SwarmProgress progress) {}
default void onRoleChanged(UUID caseId, UUID swarmId, List<DetectedRole> roles, SwarmProgress progress) {}
default void onTeamChanged(UUID caseId, UUID swarmId, List<DetectedTeam> teams, SwarmProgress progress) {}
default void onProgressUpdated(UUID caseId, UUID swarmId, SwarmProgress progress) {}
default void onMemberCompleted(UUID caseId, UUID swarmId, String agentId) {}
default void onProvisioningChanged(UUID caseId, UUID swarmId, int currentSize, int maxSize) {}
default void onTelemetrySnapshot(UUID caseId, UUID swarmId, List<SwarmTelemetry> telemetry) {}
```

**Migration path for `swarmId` addition:**
1. `SwarmProvisioner` (engine-runtime) generates a `UUID swarmId` per provisioning cycle and passes it through all listener callbacks.
2. `SwarmCoordinationListenerBridge` (agency) threads `swarmId` through to `SwarmEventSource`.
3. All 6 `SwarmCoordinationEvent` records gain a `swarmId` field.
4. All 6 `CoordinationTopology` implementations update their `onSwarmEvent()` pattern matches. Existing single-swarm topologies ignore the `swarmId` — they continue to operate on the single swarm in their scope.
5. `SwarmEventSource` gains a `swarmId` field.

This is a breaking API change. All call sites update mechanically — no design decisions at each site.

**`TopologyType.MULTI_SWARM`** — new enum value in engine-api:
```java
MULTI_SWARM {
    public DecisionAuthority deriveAuthority(StructuralRole role) {
        return switch (role) {
            case INITIATOR -> DecisionAuthority.CENTRAL;
            case CONTRACTOR -> DecisionAuthority.LOCAL;
            case PEER -> DecisionAuthority.COORDINATED;
        };
    }
}
```

Multi-swarm is a meta-topology that composes child topologies but is itself a topology type — it defines how agents across multiple swarms coordinate. `HYBRID` semantics for authority derivation (all roles valid).

**Move `SwarmCoordinationEvent`** sealed hierarchy from agency to engine-api — it contains only engine-api types.

**`CrossSwarmConvergenceEvaluator`** — SPI for structural convergence:
```java
@FunctionalInterface
public interface CrossSwarmConvergenceEvaluator {
    ConvergenceSignal evaluate(Map<UUID, SwarmProgress> swarmProgress);
}
```

### Engine runtime additions

- In-memory `MultiSwarmRegistry` implementation (per-case scoped in v1; `allActive()` returns only the current case's swarms)
- `SwarmProvisioner` generates `swarmId` per provisioning and passes through all `SwarmCoordinationListener` callbacks
- Default `CrossSwarmConvergenceEvaluator` — all swarms individually converged = overall converged

### Agency additions

Everything below sits in a new package: `io.casehub.agency.orchestration.multiswarm`.

## Tiered Coordination Protocol

### L0 — Shared Observation Space

All swarms publish progress and findings to a shared `CoordinationBus<MultiSwarmSignal>`. Pure rule-based predicate matching on capability tags for routing.

**`CoordinationBus<S>`** — purpose-built coordination signal bus (not the summarisation `EventStreamBus`):
```java
public class CoordinationBus<S> {
    void subscribe(Predicate<S> filter, Consumer<S> callback);
    void publish(S signal);
    void unsubscribe(Consumer<S> callback);
    List<S> replayBuffer();  // bounded ring buffer for cold start
}
```

Design differences from `EventStreamBus`:
- No `LevelEvent` wrapper — signals are published and received as-is (no cargo-culted `EventLevel`/`tenancyId`)
- Async delivery — `publish()` dispatches to subscribers via a shared executor (non-blocking for the publisher)
- `unsubscribe()` for cleanup on swarm deregistration
- Built-in bounded ring buffer for cold start replay (configurable size, independent of summarisation drain lifecycle)

**`MultiSwarmSignal`** — sealed signal hierarchy with typed payloads:
```java
public sealed interface MultiSwarmSignal {
    UUID sourceSwarmId();
    Set<String> domainTags();
    long timestamp();

    record Finding(UUID sourceSwarmId, Set<String> domainTags, long timestamp,
                   String domain, String conclusion, double confidence,
                   Map<String, String> evidence) implements MultiSwarmSignal {}

    record Progress(UUID sourceSwarmId, Set<String> domainTags, long timestamp,
                    SwarmProgress progress) implements MultiSwarmSignal {}

    record ResourceClaim(UUID sourceSwarmId, Set<String> domainTags, long timestamp,
                         Set<String> agentIds, Set<String> capabilities) implements MultiSwarmSignal {}

    record Completion(UUID sourceSwarmId, Set<String> domainTags, long timestamp,
                      String domain, String resultSummary) implements MultiSwarmSignal {}

    record Handoff(UUID sourceSwarmId, Set<String> domainTags, long timestamp,
                   String domain, Map<String, String> context,
                   Set<String> requiredCapabilities) implements MultiSwarmSignal {}
}
```

Domain tags use eidos `VocabularyTerm` references for cross-swarm compatibility. Capability tags use the existing engine-api capability vocabulary.

Each swarm subscribes to the bus with a predicate based on its declared interests. Zero inference cost.

**Cold start:** On late registration, replay recent signals from the `CoordinationBus` ring buffer. The ring buffer is owned by the coordination bus, sized via builder configuration, and is independent of the summarisation pipeline's `EventAccumulator` drain lifecycle.

### Event-to-Signal Transformation

`MultiSwarmListenerBridge` transforms `SwarmCoordinationEvent` records into `MultiSwarmSignal` variants. The `domainTags` for all signals come from `SwarmDescriptor.capabilities()` looked up in the `MultiSwarmRegistry` by `swarmId`.

| SwarmCoordinationEvent | MultiSwarmSignal | Notes |
|------------------------|-----------------|-------|
| `SwarmInitialised(swarmId, progress)` | `Progress(swarmId, capabilities, now, progress)` | Swarm becoming available is a progress update |
| `ProgressUpdated(swarmId, progress)` | `Progress(swarmId, capabilities, now, progress)` | Direct mapping |
| `RoleChanged(swarmId, roles, progress)` | `Progress(swarmId, capabilities, now, progress)` | Role detection is a progress update |
| `TeamChanged(swarmId, teams, progress)` | `Progress(swarmId, capabilities, now, progress)` | Team formation is a progress update |
| `MemberCompleted(swarmId, agentId)` | `Completion(swarmId, capabilities, now, "agent", agentId)` | Agent completion within a swarm |
| `ProvisioningChanged` | Not published | Internal resource management, not cross-swarm coordination |
| `TelemetrySnapshot` | Not published | Monitoring, not coordination |

Domain-specific signals (`Finding`, `ResourceClaim`, `Handoff`) are NOT derived from lifecycle events. They are surfaced by wrapping each child topology's `DecisionOutcomeExecutor` with a `CrossSwarmOutcomeDecorator`:

```java
public class CrossSwarmOutcomeDecorator implements DecisionOutcomeExecutor {
    private final DecisionOutcomeExecutor delegate;
    private final CoordinationBus<MultiSwarmSignal> bus;
    private final MultiSwarmRegistry registry;
    private final UUID swarmId;

    public CompletionStage<CoordinationFeedback> execute(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        if (outcome instanceof DecisionOutcome.WriteBack wb) {
            extractCrossSwarmSignals(wb).forEach(bus::publish);
        }
        return delegate.execute(agentId, outcome, ctx);
    }
}
```

**Wiring:** `MultiSwarmTopology` constructs child topologies (via `MultiSwarmTopologyCompiler` or builder). When each child topology's `configure()` returns a `CoordinationPlan`, the multi-swarm topology wraps the plan's `executor()` with `CrossSwarmOutcomeDecorator` before passing the plan to the engine. This is transparent to child topologies — no API changes to `TopologyContext`, existing builders, or the `DecisionOutcomeExecutor` contract.

The decorator inspects `WriteBack` outcomes for cross-swarm-relevant content: observations whose domain tags overlap with other registered swarms' capabilities (checked via `MultiSwarmRegistry`). Matching observations are published as `MultiSwarmSignal.Finding` or `MultiSwarmSignal.Handoff` to the `CoordinationBus`. The original executor runs unmodified — the decorator only adds the publication side-effect.

### L1 — Bayesian BJT Relevance Scoring

When an L0 signal passes the predicate filter, the BJT scores its relevance to the receiving swarm.

**BJT runtime objects:**

Per-swarm-pair relevance scoring uses `OnlineParameterUpdater` from neocortex inference-bayes:
- One `OnlineParameterUpdater` per receiving swarm, constructed from a `BayesNetwork` defining the relevance model
- `OnlineParameterUpdater.getInstance()` provides the `BayesInstance` for inference
- `OnlineParameterUpdater.update()` handles online learning (already `synchronized` — thread-safe)

`MultiAgentComposition` (neocortex) provides network structure metadata — which swarms are "agents" in the composition, what common knowledge variables they share. It is not an inference engine; it describes the composition structure that informs how junction trees relate.

**Network structure per receiving swarm:**
```
BayesNetwork with nodes:
  - CapabilityOverlap  — fraction of shared capabilities between source and receiver
  - DomainMatch        — domain tag intersection (vocabulary-backed term equivalence)
  - SignalStrength      — source signal confidence
  - HistoricalRelevance — past signals from this source that led to action
  - Relevant           — target query node
```

The `BayesInstance` (from `OnlineParameterUpdater.getInstance()`) computes `P(Relevant | observations)` via `setLikelyhood()` + `globalUpdate()` + `marginalize("Relevant")`. Signals above a configurable threshold are **amplified** — passed to the meta-coordination loop as `ObservationEvent` instances with elevated priority.

**Learning:** When an amplified signal leads to a useful action (positive feedback) or is ignored (negative), the `OnlineParameterUpdater.update()` adjusts CPTs with exponential decay. Future similar signals score higher or lower accordingly.

**Thread safety:** `BayesInstance.globalUpdate()` mutates internal `CliqueState` and `SeparatorState` arrays and is not thread-safe. Each receiving swarm's relevance scoring is single-threaded: signals for a given receiver are processed sequentially by that receiver's scoring thread. The `OnlineParameterUpdater` is `synchronized` for learning updates.

### L1→L2 — Bayesian Escalation Gate

A separate `OnlineParameterUpdater` (per receiving swarm) accumulates evidence for escalation:
```
BayesNetwork with nodes:
  - ConflictIndicators    — contradictory conclusions from different swarms
  - ResourceContention    — overlapping agent claims
  - DependencySignals     — one swarm's output is another's input
  - EscalateNeeded        — target query node
```

Multiple weak signals update the posterior incrementally via `OnlineParameterUpdater.update()`. Escalation triggers when `P(EscalateNeeded)` (via `marginalize("EscalateNeeded")`) crosses threshold. This naturally handles the "three weak signals = one strong signal" pattern.

### L2 — Conflict Detection + Direct Notification

When the escalation gate triggers, two things happen:

1. **BeliefSet conflict detection:** Each swarm's conclusions are modelled as `Belief<SwarmConclusion>` in a `BeliefSet<SwarmConclusion>`.

**`SwarmConclusion`** — the type parameter driving L2:
```java
public record SwarmConclusion(
    UUID swarmId,
    String domain,
    String conclusion,
    double confidence
) {}
```

**Mapping to `Belief<SwarmConclusion>`:**
- `key()` — `swarmId + ":" + domain` (unique per swarm per domain)
- `entrenchment()` — derived from `confidence` as `(int)(confidence * 100)`. Higher confidence = higher entrenchment = harder to contract.

**`DomainConsistencyChecker`** — new type in `io.casehub.agency.orchestration.multiswarm`:
```java
public class DomainConsistencyChecker implements ConsistencyChecker<SwarmConclusion> {
    private final VocabularyRegistry registry;

    public DomainConsistencyChecker(VocabularyRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean isConsistent(BeliefSet<SwarmConclusion> beliefs) {
        var byDomain = beliefs.all().stream()
            .collect(Collectors.groupingBy(b -> b.value().domain()));
        return byDomain.values().stream().noneMatch(group ->
            group.stream().anyMatch(a -> group.stream().anyMatch(b ->
                !a.key().equals(b.key())
                && contradict(a.value(), b.value())
            ))
        );
    }

    private boolean contradict(SwarmConclusion a, SwarmConclusion b) {
        // domain serves as vocabulary URI for term resolution
        String vocabUri = a.domain();
        var termA = registry.resolve(vocabUri, a.conclusion());
        var termB = registry.resolve(vocabUri, b.conclusion());
        if (termA.isPresent() && termB.isPresent()) {
            // VocabularyTerm.opposite() returns the antonym if one exists
            return termA.get().opposite()
                .map(opp -> opp.value().equals(termB.get().value()))
                .orElse(false)
                || termB.get().opposite()
                    .map(opp -> opp.value().equals(termA.get().value()))
                    .orElse(false);
        }
        // terms don't resolve to vocabulary — fall back to structural negation
        return structuralNegation(a.conclusion(), b.conclusion());
    }

    private static boolean structuralNegation(String a, String b) {
        int colonA = a.indexOf(':'), colonB = b.indexOf(':');
        if (colonA < 0 || colonB < 0) return false;
        return a.substring(0, colonA).equals(b.substring(0, colonB))
            && !a.substring(colonA + 1).equals(b.substring(colonB + 1));
    }
}
```

**Contradiction algorithm:** `SwarmConclusion.domain` serves as the vocabulary URI for term resolution via `VocabularyRegistry.resolve(vocabUri, value)`. This is a natural mapping — domains in the multi-swarm context correspond to vocabulary namespaces (e.g., domain "system-health" maps to the `system-health` vocabulary). Three levels of contradiction detection:

1. **Vocabulary antonym** — both conclusions resolve to `VocabularyTerm` and one is the `opposite()` of the other (e.g., "healthy"/"failing" declared as antonyms in the vocabulary). Checked bidirectionally since `opposite()` may not be symmetric.
2. **Structural negation** — conclusions follow "entity:state" pattern; same entity prefix with different state suffix is contradictory (e.g., "node-7:healthy" vs "node-7:degraded"). Used as fallback when vocabulary resolution fails.
3. **Custom** — the builder's `consistencyChecker()` method accepts any `ConsistencyChecker<SwarmConclusion>` for domains with specialized semantics that don't fit either strategy.

Custom contradiction logic is supplied via the builder's `consistencyChecker()` method for domains with specialized semantics.

**Conflict detection uses `BeliefSet.revise()`** (not `contract()`): `revise()` takes the new belief and a `ConsistencyChecker`, detects inconsistency, and performs minimum contraction to restore consistency — removing least-entrenched beliefs first. The delta between the pre-revision and post-revision belief sets identifies exactly which beliefs were contracted and why, providing the structured input for L3.

**Feedback loop:** BeliefSet contraction outcomes feed back into the escalation gate's `OnlineParameterUpdater`: if L2 confirms a conflict, the escalation gate's prior for that swarm pair is updated to escalate faster next time. False alarms update the prior to be more conservative.

2. **Direct notification:**

```java
public record CrossSwarmNotification(
    UUID sourceSwarmId,
    UUID targetSwarmId,
    NotificationType type,  // CONFLICT, DEPENDENCY, RESOURCE_CONTENTION, HANDOFF
    MultiSwarmSignal trigger,  // the signal that triggered escalation (typed, not Object)
    double confidence       // from BJT posterior
) {}
```

**`MultiAgentComposition` (neocortex):** Provides the composition structure for multi-swarm junction trees. Common knowledge variables are shared across the composition. `BeliefDivergence` detection (KL divergence between swarm junction trees via `NetworkComposition` slot comparison) feeds into BeliefSet revision — high divergence on shared variables indicates a conflict worth escalating.

### L3 — LLM-Mediated Resolution

When L2 confirms a conflict or joint decision is needed, open a channel with a protocol:

- **Conflict resolution:** `NegotiationProtocol` between swarm coordinators. LLM reasons about each swarm's position, proposes resolution.
- **Joint decision:** `ConversationProtocol` with convergence detection.
- **Resource negotiation:** `NegotiationProtocol` with `AcceptancePolicy`.

The `ExplanationEngine` from neocortex provides the LLM mediator with ranked evidence influence — which signals most strongly support each swarm's position.

**Budget:** L3 calls are governed by the existing `AutonomyBudgetPool`. When budget is exhausted, fall back to heuristic resolution (see Degradation).

**Heuristic fallback per conflict type (when L3 unavailable or budget exhausted):**
1. **Conflict:** Both conclusions retained with `DISPUTED` tag. Neither overwrites the other. Escalation to human via `OversightGateService`.
2. **Resource contention:** First-claimer wins. Contending swarm notified with alternative capabilities.
3. **Joint decision:** Defer to swarm with higher `SwarmProgress.consensusScore()`. Lower-scoring swarm receives the decision as external constraint.
4. **Handoff:** Retry with exponential backoff. After 3 failures, mark as blocked and notify both swarms.

## MultiSwarmTopology

Single `CoordinationTopology` implementation with optional LLM enhancement.

```java
public class MultiSwarmTopology implements CoordinationTopology, RegistryListener {
    private final MultiSwarmRegistry registry;
    private final CoordinationBus<MultiSwarmSignal> signalBus;
    private final ConcurrentMap<UUID, OnlineParameterUpdater> relevanceUpdaters;
    private final ConcurrentMap<UUID, OnlineParameterUpdater> escalationUpdaters;
    private final BayesNetwork relevanceNetwork;     // template for per-swarm instances
    private final BayesNetwork escalationNetwork;    // template for per-swarm instances
    private final @Nullable MultiAgentComposition composition;
    private volatile BeliefSet<SwarmConclusion> beliefSet;
    private final ConsistencyChecker<SwarmConclusion> consistencyChecker;
    private final @Nullable AgentProvider agentProvider;  // builder-provided, controls L3 enablement
}
```

### `configure(TopologyContext)` — meta-coordination plan

`MultiSwarmTopology.configure()` creates a single meta-coordination decision loop. The L0→L1→L2 tiered flow is NOT a replacement for the decision loop — it is the observation processing pipeline that filters and enriches signals before they reach the loop as `ObservationEvent` instances. The loop decides what to do about escalated signals.

```java
public CoordinationPlan configure(TopologyContext context) {
    var pool = new AutonomyBudgetPool(context.budgetPolicy(),
                                      context.caseId() + "-MULTI_SWARM");

    var coordinator = new DecisionLoopDescriptor(
        "multi-swarm-coordinator",
        ObservationStrategy.EVENT_DRIVEN,
        DecisionScope.FULL_AUTONOMY,
        CommunicationMode.BROADCAST,
        BudgetPriority.RESERVED,
        List.of("cross-swarm-signals", "conflict-state", "convergence-progress"));

    DecisionOutcomeExecutor executor = this::executeCoordination;
    return new CoordinationPlan(List.of(coordinator), executor, pool);
}
```

**Loop semantics:**
- The meta-coordination loop receives `ObservationEvent` instances produced by the L1/L2 pipeline: amplified signals (from L1), escalation events (from L1→L2 gate), and conflict reports (from L2 BeliefSet revision).
- The loop's heuristic summariser (`MultiSwarmHeuristicSummariser`) handles L0–L2 mechanically — it applies the BJT scoring, escalation gating, and BeliefSet conflict detection without LLM involvement. This means the full tiered protocol works even when no `AgentProvider` is configured.
- When L3 mediation is needed and `agentProvider != null`, the loop's LLM summariser reasons about the conflict and produces a `DecisionOutcome` for resolution.

**`DecisionOutcomeExecutor` for multi-swarm:**
```java
private CompletionStage<CoordinationFeedback> executeCoordination(
        String agentId, DecisionOutcome outcome, TopologyContext ctx) {
    return switch (outcome) {
        case DecisionOutcome.WriteBack wb ->
            publishNotifications(wb);  // CrossSwarmNotification via CoordinationBus
        case DecisionOutcome.Composite c ->
            executeAll(c.outcomes(), ctx);  // L3 resolution may produce multiple outcomes
        default ->
            CompletableFuture.completedFuture(
                new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
    };
}
```

**Child topology composition:** Each child swarm has its own `CoordinationTopology` that produces its own `CoordinationPlan`. The multi-swarm plan handles ONLY cross-swarm coordination; child plans handle intra-swarm coordination. `MultiSwarmTopology` wraps each child plan's `DecisionOutcomeExecutor` with `CrossSwarmOutcomeDecorator` (see §Event-to-Signal Transformation) to surface domain-specific signals, then passes the wrapped plan to the engine. The engine runs child plans and the meta-coordination plan independently.

**`AgentProvider` resolution:** The builder's `@Nullable AgentProvider` controls L3 enablement. `TopologyContext.agentProvider()` is NOT used — multi-swarm follows the existing convention where strategies are wired at construction time (as `CentralisedTopology` does with its routing/aggregation strategies). If the builder's provider is null, L3 is disabled and the heuristic fallback handles all coordination mechanically.

### Builder

```java
public class MultiSwarmTopologyBuilder {
    // Required
    private MultiSwarmRegistry registry;
    private BayesNetwork relevanceNetwork;
    private BayesNetwork escalationNetwork;
    private ConsistencyChecker<SwarmConclusion> consistencyChecker;

    // Optional with defaults
    private double relevanceThreshold = 0.7;
    private double escalationThreshold = 0.8;
    private double convergenceThreshold = 0.85;
    private int replayBufferSize = 100;
    private @Nullable AgentProvider agentProvider;
    private @Nullable MultiAgentComposition composition;

    public MultiSwarmTopology build() {
        var topology = new MultiSwarmTopology(...);
        registry.addListener(topology);  // topology receives registration events
        return topology;
    }
}
```

The builder registers the topology as a `RegistryListener` on the registry at construction time. When a swarm registers, `onRegistered()` creates per-swarm `OnlineParameterUpdater` instances from the network templates. When a swarm deregisters, `onDeregistered()` triggers the cleanup lifecycle.

### Configuration shorthand

```java
MultiSwarmTopology.builder()
    .registry(registry)
    .relevanceNetwork(RelevanceNetworks.standard())
    .escalationNetwork(EscalationNetworks.standard())
    .consistencyChecker(new DomainConsistencyChecker(vocabRegistry))
    .relevanceThreshold(0.7)
    .escalationThreshold(0.8)
    .convergenceThreshold(0.85)
    .agentProvider(provider)  // optional — enables L3
    .build();
```

### Event Flow

1. Engine fires `SwarmCoordinationEvent` per swarm (now with `swarmId`)
2. `MultiSwarmListenerBridge` (agency, implements `SwarmCoordinationListener`) receives events from all swarms
3. Lifecycle events → `MultiSwarmSignal.Progress`/`Completion` (see §Event-to-Signal Transformation table)
4. Domain-specific signals → surfaced by `CrossSwarmOutcomeDecorator` wrapping child topology executors
5. All signals published to shared `CoordinationBus` (L0)
6. Each swarm's `OnlineParameterUpdater` → `BayesInstance` scores incoming signals (L1)
7. Amplified signals → `ObservationEvent` fed to the meta-coordination loop
8. Escalation gate → L2 `BeliefSet.revise()` conflict detection + `CrossSwarmNotification`
9. On confirmed conflict → L3 LLM mediation via channel (if `agentProvider` available), else heuristic fallback

### Swarm Deregistration Cleanup

Triggered by `RegistryListener.onDeregistered(UUID swarmId)`:
1. Remove the swarm's subscriptions from `CoordinationBus` (via `unsubscribe()`)
2. Dispose the swarm's relevance and escalation `OnlineParameterUpdater` instances
3. Remove the swarm's entries from the `BeliefSet` (contract all beliefs keyed to that swarm)
4. Cancel any pending L3 negotiations involving the swarm
5. Clean up `CrossSwarmNotification` history referencing the swarm

### Thread Safety

| Component | Strategy |
|-----------|----------|
| `CoordinationBus` | `CopyOnWriteArrayList` for subscriptions, async publish via executor |
| `OnlineParameterUpdater` | Already `synchronized` for `update()`. Per-swarm instances avoid global bottleneck. |
| `BayesInstance` | Single-threaded per receiving swarm — signals processed sequentially per receiver |
| `BeliefSet` | Immutable. Current reference via `volatile` field with compare-and-swap update pattern |
| `MultiSwarmRegistry` | `ConcurrentHashMap` for in-memory implementation. Listener notifications on the caller's thread. |

## Hierarchical Composition

When `SwarmDescriptor.parentSwarmId` is non-null, the swarm participates in a hierarchical composition. Hierarchical multi-swarm differs from agent-level `HierarchicalTopology` — the latter coordinates agents within a single swarm via parent-child agent relationships. Multi-swarm hierarchical composition coordinates swarm instances as units.

**Scope of tiered protocol:**
- **Sibling↔sibling:** L0–L3 applies between siblings (children of the same parent). Siblings see each other's signals on the `CoordinationBus` (L0) and coordinate through the full tiered protocol — L1 relevance scoring, L1→L2 escalation, L2 conflict detection, L3 mediation.
- **Parent↔child:** Observer pattern. Parent subscribes to all child signals at L0 with no predicate filtering and no L1 relevance scoring — all child outputs are relevant to the parent's scope. Parent does not relay signals between siblings; sibling-to-sibling coordination goes directly via the bus.
- **Child→parent escalation:** A child swarm's L2 conflict can escalate to the parent's L3 scope. The parent's mediator has visibility into all children and can resolve cross-child conflicts with broader context.

**Convergence:** `CrossSwarmConvergenceEvaluator` treats hierarchical relationships as **bottom-up** — a parent swarm cannot converge until all children have converged. Sibling convergence is independent.

## YAML Surface

```yaml
topology:
  pattern: multi-swarm
  authority: coordinator
  swarms:
    analysis:
      pattern: swarm
      capabilities: [data-analysis, pattern-detection]
    remediation:
      pattern: hive
      capabilities: [remediation, verification]
      roles: [analyst, verifier]
  coordination:
    relevanceThreshold: 0.7
    escalationThreshold: 0.8
    convergenceThreshold: 0.85
```

**`TopologySpec.MultiSwarm`** added to the existing sealed interface in `agentic-yaml` module (`io.casehub.agency.agentic.yaml.spec.TopologySpec`), alongside the existing variants (Swarm, Hive, Mesh, Many, Distributed):
```java
record MultiSwarm(
    @Nullable String authority,
    Map<String, TopologySpec> swarms,
    @Nullable MultiSwarmCoordinationSpec coordination
) implements TopologySpec {}

record MultiSwarmCoordinationSpec(
    @Nullable Double relevanceThreshold,
    @Nullable Double escalationThreshold,
    @Nullable Double convergenceThreshold
) {}
```

`TopologySpec` lives in the `agentic-yaml` module (not `agency-core`). The sealed interface already exists with 5 variants. Adding `MultiSwarm` follows the established pattern — new variant, new `@Type` annotation in the `@JsonSubTypes` list.

`MultiSwarmCoordinationSpec` is a new record in `agentic-yaml` — NOT the platform's `io.casehub.platform.compiler.CoordinationSpec` (which lives in `casehub-platform-playbook-compiler` and serves a different purpose).

**`MultiSwarmTopologyCompiler`** compiles the YAML spec into a `MultiSwarmTopology`, recursively compiling child swarm specs via the existing topology compilers (`HiveTopologyCompiler`, `DistributedTopologyCompiler`, etc.).

## Degradation

| Failure | Fallback |
|---------|----------|
| BJT propagation error | Fall back to L0 predicate matching only — signals pass at **normal** priority (no amplification). Over-inclusive but bounded by the predicate filter. |
| BeliefSet contraction error | Skip conflict detection, escalate directly to L3 (over-escalation, safe) |
| L3 LLM budget exhausted | Per-type heuristic resolution (see §L3 fallback table) |
| L3 LLM invocation error | Log + per-type heuristic resolution. Swarms proceed with best-effort fallback. |

The `MultiSwarmRegistry` is a required dependency of `MultiSwarmTopology` — registry unavailability is a construction-time error (builder rejects null), not a runtime degradation scenario.

## Existing Topology Extraction

As part of this work, the `SwarmCoordinationEvent` sealed hierarchy moves from agency to engine-api. All existing topology implementations in agency update their imports. This is the first step toward cleaner engine/agency separation for topology infrastructure.

Post-implementation audit to verify the seam is clean and identify further extraction candidates.

## Test Strategy

Filed as agency#423. Behavioral e2e tests across all tiers:
- L0 signal delivery, filtering, cold start replay, and event-to-signal transformation
- L1 BJT relevance scoring with evidence accumulation via `OnlineParameterUpdater`
- L1→L2 escalation gate threshold crossing
- L2 `BeliefSet.revise()` conflict detection with `DomainConsistencyChecker`
- L2 contradiction detection: `VocabularyTerm.opposite()` antonym path, structural negation fallback, custom checker
- L3 LLM mediation (mocked AgentProvider)
- `configure()` plan creation and meta-coordination loop lifecycle
- Full tiered path scenarios (handoff, conflict, hierarchical, resource contention)
- Degradation fallbacks (L1 failure → L0-only, L3 budget exhaustion → heuristic)
- Registry lifecycle: registration notification → updater creation, deregistration → cleanup
- Thread safety: concurrent signal publishing and scoring
- Hierarchical composition: parent observer, sibling tiered protocol, bottom-up convergence
- `CrossSwarmOutcomeDecorator`: child topology outcome interception and signal publication

No mocks of core coordination types. Mock only external boundaries (AgentProvider, engine SPIs).

## References

- agency#363 — issue
- agency#423 — behavioral e2e test issue
- agency#285 — original issue (deferred)
- `SwarmCoordinatorBuilder.java` — single-swarm builder pattern
- `CentralisedTopology.java` — centralised coordination pattern (configure() creates 3 loops)
- `DistributedTopology.java` — distributed coordination pattern (configure() creates per-agent loops)
- `CoordinationPlan.java` — plan record (loopDescriptors, executor, pool, triggers, heuristics)
- `TopologyContext.java` — context record (caseId, descriptor, agentProvider, loopFactory, budgetPolicy, degradationPolicy)
- `DecisionLoopDescriptor.java` — loop descriptor record (name, observationStrategy, scope, communicationMode, budgetPriority, promptSectionNames)
- `DecisionOutcomeExecutor.java` — functional interface for outcome execution
- `TopologySpec.java` (agentic-yaml) — YAML sealed interface
- `SwarmCoordinationEvent.java` — event sealed hierarchy (to be moved to engine-api)
- `SwarmCoordinationListener.java` (engine-api) — lifecycle SPI
- `BeliefSet.java` — AGM belief revision
- `ConsistencyChecker.java` — functional interface for consistency checking
- `Belief.java` — belief record (key, value, entrenchment)
- `MultiAgentComposition` (neocortex inference-bayes) — network structure metadata
- `NetworkComposition` (neocortex inference-bayes) — slot/link DAG composition
- `BayesInstance` (neocortex inference-bayes) — incremental junction tree runtime
- `OnlineParameterUpdater` (neocortex inference-bayes) — streaming CPT updates (synchronized)
- `ExplanationEngine` (neocortex inference-bayes) — evidence influence ranking
- `NegotiationProtocol`/`ConversationProtocol` — channel protocols for L3
- `CoalitionProposal`/`CoalitionEvaluator` — potential cross-swarm team assembly
- `GraduatedTopology.java` — graduated autonomy (distinct concept — per-agent autonomy levels, not inter-swarm coordination tiers)
