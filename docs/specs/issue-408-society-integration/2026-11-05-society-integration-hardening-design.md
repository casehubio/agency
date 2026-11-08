# Society Integration Hardening — Design Specification

**Epic:** casehubio/agency#408
**Child issues:** #409, #410, #411, #412, #413
**Date:** 2026-11-05
**Decisions:** `decisions.md`
**Authoritative design:** `2026-11-02-self-organising-agent-society-design.md` (engine workspace)

## 1. Purpose

Agency's society integration has stubbed implementations and disconnected
primitives. HivePatternAdapter returns SUCCESS without calling orchestration
patterns, CoalitionProposal isn't wired to GroupLifecycleService, and the role
model diverges from eidos. This spec defines the agency-side implementation
approach for hardening these integration points.

## 2. Scope

Five child issues plus structural refactoring surfaced during design review:

| # | Title | Effort | Decision |
|---|-------|--------|----------|
| #412 | TopologyContext gains groupId/tenancyId | XS | D4 |
| #409 | HivePatternAdapter → generic OrchestrationPatternExecutor | M | D1, D8, D9 |
| #411 | RolePolicySpec compiles to eidos RoleSpec | S | D3 |
| #413 | YAML-to-runtime TopologyFactory | M | D5 |
| #410 | BottomUpFormationOrchestrator | M | D2 |
| — | Topology structural refactoring | M | D1, D6, D7, D10 |

CentralisedTopology\<C\> is excluded from the builder migration — its
swarm-specific reasoner wiring (RoutingReasoner, AggregationReasoner,
ProvisioningReasoner) is too entangled to extract in this epic.

## 3. TopologyBuilder — Composable Configuration (D1, D6, D7)

### 3.1 Problem

Six topology classes share three orthogonal concerns tangled in each:

1. **Loop planning** — how many decision loops, with what parameters
2. **Outcome execution** — what happens when a loop produces a DecisionOutcome
3. **Degradation** — how the topology responds to pressure

The degradation cascade logic is copy-pasted across HiveTopology,
HierarchicalTopology, HybridTopology, and GraduatedTopology. Outcome
execution is stubbed everywhere.

### 3.2 Design

A `TopologyBuilder` in `orchestration.topology` produces `CoordinationTopology`
instances from configuration. Factory methods encode well-known topology
configurations:

```java
public class TopologyBuilder {

    // Core: loop descriptors + executor + optional degradation + optional feedback
    static CoordinationTopology create(
            TopologyType type,
            Map<String, AgentRole> agentRoles,
            LoopPlanner planner,
            DecisionOutcomeExecutor executor,
            @Nullable DegradationCascadeFactory cascadeFactory,
            @Nullable FeedbackHandler feedbackHandler);

    // Factory methods for well-known configurations
    static CoordinationTopology hive(
            Map<String, AgentRole> agentRoles,
            OrchestrationPatternType patternType);

    static CoordinationTopology distributed(
            Map<String, AgentRole> agentRoles);

    static CoordinationTopology hierarchical(
            Map<String, AgentRole> agentRoles,
            Map<String, String> parentMap);

    static CoordinationTopology hybrid(
            Map<String, AgentRole> agentRoles);

    static CoordinationTopology graduated(
            Map<String, AgentRole> agentRoles);
}
```

Each factory method returns `CoordinationTopology` (D7) — no concrete types
are exposed. The factory methods internally compose a `LoopPlanner`,
`DecisionOutcomeExecutor`, and optional `DegradationCascade`.

### 3.3 LoopPlanner

```java
@FunctionalInterface
interface LoopPlanner {
    LoopPlan plan(TopologyContext context);
}

record LoopPlan(
    List<DecisionLoopDescriptor> descriptors,
    Map<String, DegradationTrigger> triggers,
    Map<String, ContentSummariser<ObservationEvent, DecisionOutcome>> heuristics
) {}
```

Each topology factory provides its own LoopPlanner:
- **hive**: 1 coordinator (RESERVED, EVENT_DRIVEN, BROADCAST) + N workers
  (NORMAL, DEMAND_DRIVEN, DIRECT_MESSAGING)
- **distributed**: N peer loops (NORMAL, EVENT_DRIVEN, STIGMERGY)
- **hierarchical**: N loops with depth-based scope/strategy/budget
- **hybrid**: 1 coordinator + N local loops
- **graduated**: phase-aware loops

### 3.4 DegradationCascade

Extracted from the 4× copy-pasted `onDegradation()` code. Built at
`configure()` time (not construction time) because it depends on
configure-time data: `CascadeThreshold` from `DegradationPolicy`,
loop priorities from the `LoopPlan`, and `CascadeDegradationTrigger`
instances shared with the `LoopPlanner`.

```java
class DegradationCascade {

    @FunctionalInterface
    interface CascadeBreachHandler {
        void onBreach(Set<String> degradedLoops,
                      List<CascadeDegradationTrigger> triggers);
    }

    DegradationCascade(
            CascadeThreshold threshold,
            Map<String, BudgetPriority> loopPriorities,
            List<CascadeDegradationTrigger> triggers,
            CascadeBreachHandler breachHandler);

    void onDegradation(SwarmDegradedEvent event);
}

@FunctionalInterface
interface DegradationCascadeFactory {
    DegradationCascade create(
            DegradationPolicy policy,
            LoopPlan plan,
            List<CascadeDegradationTrigger> sharedTriggers);
}
```

**Construction timing**: `ComposableTopology.configure()` calls
`LoopPlanner.plan()` first (which creates `CascadeDegradationTrigger`
instances inside its trigger map), then passes those same trigger
instances to `DegradationCascadeFactory.create()`. This resolves the
circular dependency — the planner creates the shared objects, the
cascade receives them.

The `CascadeBreachHandler` callback accommodates divergent cascade
responses:
- Hive: switch `OrchestrationPatternExecutor.patternType` (DEBATE→VOTING→
  SUPERVISOR) AND activate all `CascadeDegradationTrigger` instances.
  Both operations must happen atomically via the handler.
- Hierarchical/Hybrid: activate `CascadeDegradationTrigger` instances
- Graduated: force scope reduction via `GraduationState` transition
  (does NOT activate CascadeDegradationTrigger)

DistributedTopology has no cascade — passes `null` for cascadeFactory.

### 3.4.1 FeedbackHandler

```java
@FunctionalInterface
interface FeedbackHandler {
    void onFeedback(CoordinationFeedback feedback);
}
```

Accommodates topology-specific feedback processing:
- Graduated: `graduationState.recordFeedback()` for sliding-window
  scope adjustment (80% success → upgrade, 50% failure → downgrade,
  immediate downgrade on SCOPE_VIOLATION). This is the graduated
  topology's core behavioral differentiator.
- Hive: delegates to `OrchestrationPatternExecutor.onFeedback()`
  (currently no-op, becomes real when pattern execution is implemented)
- Other topologies: `null` (no feedback processing)

### 3.5 Concrete topology: ComposableTopology

A single implementation class backs all TopologyBuilder factory methods:

```java
class ComposableTopology implements CoordinationTopology {

    private final TopologyType type;
    private final Map<String, AgentRole> agentRoles;
    private final LoopPlanner planner;
    private final DecisionOutcomeExecutor executor;
    private final @Nullable DegradationCascadeFactory cascadeFactory;
    private final @Nullable FeedbackHandler feedbackHandler;

    // Built at configure() time, not construction time
    private volatile @Nullable DegradationCascade cascade;

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) { ... }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var plan = planner.plan(context);
        var pool = new AutonomyBudgetPool(
            context.budgetPolicy(), context.caseId() + "-" + type.name());
        if (cascadeFactory != null) {
            var sharedTriggers = extractCascadeTriggers(plan.triggers());
            this.cascade = cascadeFactory.create(
                context.degradationPolicy(), plan, sharedTriggers);
        }
        return new CoordinationPlan(
            plan.descriptors(), executor, pool,
            plan.triggers(), plan.heuristics());
    }

    @Override
    public void onSwarmEvent(SwarmCoordinationEvent event) { }

    @Override
    public void onFeedback(CoordinationFeedback feedback) {
        if (feedbackHandler != null) feedbackHandler.onFeedback(feedback);
    }

    @Override
    public void onDegradation(SwarmDegradedEvent event) {
        if (cascade != null) cascade.onDegradation(event);
    }
}
```

### 3.6 CoordinationTopology interface update (D10)

Add `describeTopology(UUID caseId)` to `CoordinationTopology`. All 6
implementations already define this method — the interface formalises the
existing convention.

### 3.7 Classes removed

After migration: `HiveTopology`, `DistributedTopology`, `HierarchicalTopology`,
`HybridTopology`, `GraduatedTopology`, `HivePatternAdapter`. Replaced by
`ComposableTopology` + `TopologyBuilder` factory methods.

## 4. OrchestrationPatternExecutor (D1, D8, D9)

### 4.1 Problem

`HivePatternAdapter.executeSupervisor()`, `executeDebate()`, and
`executeVoting()` all return `completedFuture(SUCCESS)` — no actual
orchestration pattern execution.

### 4.2 Design

`OrchestrationPatternExecutor` is a `DecisionOutcomeExecutor` implementation
that delegates to the pattern builders. It replaces `HivePatternAdapter`.

```java
class OrchestrationPatternExecutor implements DecisionOutcomeExecutor {

    private final AgentProvider agentProvider;
    private volatile OrchestrationPatternType patternType;

    OrchestrationPatternExecutor(
            AgentProvider agentProvider,
            OrchestrationPatternType patternType);

    @Override
    public CompletionStage<CoordinationFeedback> execute(
            String agentId, DecisionOutcome outcome, TopologyContext context) {
        if (outcome instanceof DecisionOutcome.ScopeGated) {
            return completedFuture(feedback(agentId, outcome, SCOPE_VIOLATION));
        }
        return switch (patternType) {
            case SUPERVISOR -> executeSupervisor(agentId, outcome, context);
            case DEBATE    -> executeDebate(agentId, outcome, context);
            case VOTING    -> executeVoting(agentId, outcome, context);
        };
    }
}
```

Each pattern method uses the pattern builders' SPI components (routing
strategies, aggregation strategies, termination conditions) to govern how
the outcome is processed. The executor does NOT build a full
`ExecutionModel<T>` and run it through `OrchestratedDriver` — that level
of machinery is for multi-step workflows, not single-outcome governance.
Instead, it composes the relevant strategies directly:
- **SUPERVISOR**: uses `LlmSelectedRouting` (via AgentProvider) to select
  which agent handles the outcome, invokes that agent, returns feedback
- **DEBATE**: uses `RoundRobinRouting` among participant agents, iterates
  until `JudgeConvergence` or max rounds, returns consensus feedback
- **VOTING**: routes outcome to all evaluator agents via broadcast routing,
  aggregates via `MajorityVote`, returns the aggregate decision as feedback

The `patternType` field is `volatile` — the hive degradation cascade can
atomically switch it (DEBATE → VOTING → SUPERVISOR) at runtime.

### 4.3 Scoping (D8)

`OrchestrationPatternType` is scoped to hive-type topologies. In YAML, the
`orchestration` field is nested under the hive block. Other topologies
use a default no-op `DecisionOutcomeExecutor` that returns SUCCESS
(preserving current stub behavior until real execution is implemented per
topology).

### 4.4 Sequencing (D9)

Structural refactoring (TopologyBuilder, DegradationCascade extraction)
precedes real execution implementation. The pattern execution methods remain
stubs during structural migration. Once TopologyBuilder is in place,
`OrchestrationPatternExecutor` implementation can be done against the clean
surface.

## 5. TopologyContext Enrichment (D4)

Add `@Nullable String groupId` and `@Nullable String tenancyId`:

```java
public record TopologyContext(
        UUID caseId,
        TopologyDescriptor descriptor,
        AgentProvider agentProvider,
        DecisionLoopFactory loopFactory,
        AutonomyBudgetPolicy budgetPolicy,
        DegradationPolicy degradationPolicy,
        @Nullable String groupId,
        @Nullable String tenancyId
) { ... }
```

Nullable because case-scoped topologies (pre-society) legitimately have no
group. Existing callers pass `null` for both fields. Populated from the
society model when a topology is activated for a group.

## 6. RolePolicySpec Compilation (D3)

### 6.1 Problem

Agency's `TopologySpec.RolePolicySpec` (`obligations: [a, b]`,
`permissions: [x, y]`) is disconnected from eidos's `RoleSpec`
(`roleName`, `minCardinality`, `maxCardinality`, `incompatibleWith`,
`List<RolePolicy>`).

### 6.2 Design

`RolePolicyCompiler` in agentic-yaml compiles the YAML convenience type to
eidos canonical types:

```java
class RolePolicyCompiler {
    RoleSpec compile(String roleName, RolePolicySpec spec) {
        return new RoleSpec(
            roleName,
            null,  // roleVocabulary — optional, not in YAML shorthand
            spec.minAgents() != null ? spec.minAgents() : 0,
            spec.maxAgents() != null ? spec.maxAgents() : -1,
            spec.incompatibleWith() != null
                ? Set.copyOf(spec.incompatibleWith()) : Set.of(),
            compilePolicies(spec));
    }
}
```

The YAML shorthand maps:
- `obligations: [a, b]` → `RoleSpec.obligations(List.of("a", "b"))` (via
  RoleSpec's dedicated obligations field)
- `permissions: [x, y]` → `RoleSpec.permissions(List.of("x", "y"))` (via
  RoleSpec's dedicated permissions field)
- Structured `policies:` block → `List<RolePolicy>` with explicit
  `PolicyKind` (ELIGIBILITY, OBLIGATION, PERMISSION) and `expectations`

### 6.3 YAML schema additions

`TopologySpec.RolePolicySpec` gains optional fields:

```java
record RolePolicySpec(
    @Nullable List<String> obligations,
    @Nullable List<String> permissions,
    @Nullable Integer minAgents,
    @Nullable Integer maxAgents,
    @Nullable List<String> incompatibleWith
) { ... }
```

### 6.4 Dependency

Compilation requires eidos `org-api` — specifically `RoleSpec`, `RolePolicy`,
`PolicyKind`. The agentic-yaml module adds `casehub-eidos-org-api` as a
provided dependency.

## 7. Bottom-Up Formation Orchestrator (D2)

### 7.1 Problem

CoalitionProposal, NegotiationProtocol, and GroupLifecycleService exist but
are not composed. An agent can't propose a group, negotiate with peers, and
have it created.

### 7.2 Design

`BottomUpFormationOrchestrator` in agency-core composes the full formation
flow:

```java
class BottomUpFormationOrchestrator {

    private final CoalitionEvaluator evaluator;
    private final GroupLifecycleService lifecycleService;
    private final ChannelManager channelManager;

    CompletionStage<FormationResult> propose(
            CoalitionProposal proposal,
            CoalitionContext context,
            String specId,
            String tenancyId) {
        // 1. Evaluate coalition viability
        CoalitionScore score = evaluator.evaluate(proposal, context);
        if (!score.isViable()) return completedFuture(
            FormationResult.rejected(score.reason()));

        // 2. Create negotiation channel + attach NegotiationProtocol
        // 3. Proposed members negotiate via channel
        // 4. On acceptance: translate to GroupLifecycleService
        //    - specId derived from coalition's task context
        //    - tenancyId from parameter
        //    - proposerAgentId from coalition initiator
        // 5. lifecycleService.proposeGroup() → approveProposal()
        // 6. Return FormationResult.accepted(groupInstanceId)
    }
}
```

### 7.3 Translation layer

CoalitionProposal's model (taskId, requiredCapabilities, proposedMembers)
must be translated to GroupLifecycleService's model (specId, tenancyId,
proposerAgentId):
- `specId`: the OrganizationalUnit specId that declares the group template
  this coalition is forming under. Passed explicitly as a parameter to
  `propose()` — the caller determines which org unit spec governs this
  formation (e.g., a "dev-guild" or "ops-squad" spec from the YAML seed).
- `tenancyId`: from TopologyContext (D4) or passed explicitly
- `proposerAgentId`: the first member in `CoalitionProposal.proposedMembers()`
  that initiated the proposal

### 7.4 FormationResult

```java
sealed interface FormationResult {
    record Accepted(String groupInstanceId) implements FormationResult {}
    record Rejected(String reason) implements FormationResult {}
    record NegotiationFailed(String reason) implements FormationResult {}
    record Timeout() implements FormationResult {}
}
```

### 7.5 CoalitionProposal cleanup

Remove `coveredCapabilities()` — it currently returns member names as a
proxy. Capability resolution is the evaluator's responsibility via
`CoalitionContext.capabilitiesOf()`.

### 7.6 Dependency

Agency-core adds `casehub-eidos-org-api` as a provided dependency (one-line
POM addition) for the `GroupLifecycleService` parameter type.

## 8. YAML-to-Runtime TopologyFactory (D5)

### 8.1 Design

`TopologyFactory` in agentic-yaml bridges `TopologySpec` variants to
`TopologyBuilder` factory calls:

```java
class TopologyFactory {

    private final RolePolicyCompiler rolePolicyCompiler;

    CoordinationTopology create(TopologySpec spec) {
        return switch (spec) {
            case TopologySpec.Swarm s -> TopologyBuilder.distributed(
                    resolveRoles(s));
            case TopologySpec.Hive h -> TopologyBuilder.hive(
                    compileRoles(h),
                    resolvePatternType(h.orchestration()));
            case TopologySpec.Mesh m -> TopologyBuilder.distributed(
                    resolveRoles(m));
            case TopologySpec.Many m -> TopologyBuilder.distributed(
                    Map.of());
        };
    }
    // TopologyContext is NOT available at factory time — the returned
    // CoordinationTopology receives it later via configure(TopologyContext)

    private Map<String, AgentRole> compileRoles(TopologySpec.Hive hive) {
        // Uses rolePolicyCompiler to compile RolePolicySpec → RoleSpec
        // Maps role names to AgentRole instances
    }

    private OrchestrationPatternType resolvePatternType(
            @Nullable String orchestration) {
        if (orchestration == null) return OrchestrationPatternType.SUPERVISOR;
        return switch (orchestration) {
            case "supervisor" -> OrchestrationPatternType.SUPERVISOR;
            case "debate"     -> OrchestrationPatternType.DEBATE;
            case "voting"     -> OrchestrationPatternType.VOTING;
            default -> throw new IllegalArgumentException(
                "Unknown orchestration: " + orchestration);
        };
    }
}
```

Registered as a step in the agentic-yaml compilation pipeline (greenfield —
the registration point will be designed during implementation).

**YAML coverage**: Hierarchical, Hybrid, and Graduated topologies have no
YAML representation. This is intentional — these topologies require
structural configuration (parent maps, phase definitions) that doesn't
map cleanly to simple YAML fields. They remain programmatic-only.

**Swarm vs Mesh**: Both currently map to `TopologyBuilder.distributed()`.
The design spec distinguishes them by runtime primitives (Swarm →
stigmergy signal filtering, Mesh → Qhorus peer messaging). This
distinction is not yet reflected in `TopologyBuilder` — Swarm and Mesh
produce identical `DistributedTopology` instances. Differentiating their
runtime behavior is tracked by the design spec's engine#1284 (TopologyProvider
SPI), not this epic.

## 9. Implementation Sequence (D9)

Refactor-first: structural cleanup before execution implementation.

| Phase | What | Issues |
|-------|------|--------|
| 1 | TopologyContext enrichment (+groupId, +tenancyId) | #412 |
| 2 | Extract DegradationCascade, create ComposableTopology + TopologyBuilder | D1, D6 |
| 3 | Migrate 5 topologies to TopologyBuilder factory methods | D6 |
| 4 | Implement OrchestrationPatternExecutor (real delegation) | #409 |
| 5 | RolePolicySpec → RoleSpec compiler | #411 |
| 6 | TopologyFactory (YAML → runtime) | #413 |
| 7 | BottomUpFormationOrchestrator | #410 |
| 8 | CoordinationTopology interface update (describeTopology) | D10 |

Phase 1 is independent (XS). Phases 2-3 are the structural refactoring.
Phase 4 implements real execution against the clean surface. Phases 5-7 can
proceed in parallel after phase 3. Phase 8 is a one-line interface change
done alongside phase 2.

## 10. Test Strategy

- **TopologyBuilder factory methods**: verify each factory produces a
  `CoordinationTopology` that configures correctly (loop descriptors match
  expected count, budget priorities, degradation triggers)
- **DegradationCascade**: extract existing cascade tests from 4 topology test
  classes into `DegradationCascadeTest`. Verify cascade threshold breaching,
  polymorphic breach handling, DistributedTopology (no cascade) behavior
- **OrchestrationPatternExecutor**: test each pattern type delegates to its
  builder. Test ScopeGated short-circuit. Test volatile pattern type switching
  during degradation cascade
- **RolePolicyCompiler**: test mapping from YAML shorthand to eidos RoleSpec.
  Edge cases: empty obligations, both obligations and permissions, with/without
  cardinality, incompatible_with
- **BottomUpFormationOrchestrator**: test full flow with mock
  GroupLifecycleService, mock ChannelManager. Test failure modes:
  non-viable coalition, negotiation timeout, negotiation rejection
- **TopologyFactory**: test each TopologySpec variant produces correct
  TopologyBuilder call. Test orchestration field parsing. Test role compilation
  integration
- **Existing topology tests**: migrate to use TopologyBuilder factory methods.
  Behavior must be identical — these are refactoring tests, not new behavior

## References

- `2026-11-02-self-organising-agent-society-design.md` — authoritative design spec (engine workspace)
- `HiveTopology.java` — current stub implementation
- `HivePatternAdapter.java` — stub adapter (lines 34-49)
- `CentralisedTopology.java` — recently genericised, excluded from migration
- `HierarchicalTopology.java`, `HybridTopology.java`, `GraduatedTopology.java`, `DistributedTopology.java` — copy-pasted degradation cascade
- `TopologyContext.java` — missing groupId/tenancyId
- `TopologySpec.java` — YAML spec with RolePolicySpec diverging from eidos
- `CoalitionProposal.java` — coveredCapabilities() stub
- `RoleSpec.java`, `RolePolicy.java`, `PolicyKind.java` — eidos org-api canonical types
- `GroupLifecycleService.java` — eidos org-api lifecycle interface
- `SupervisorBuilder.java`, `DebateBuilder.java`, `VotingBuilder.java` — pattern builders
- `DecisionOutcomeExecutor.java` — functional interface for outcome execution
- casehubio/agency#409, #410, #411, #412, #413 — child issues
- casehubio/desiredstate#230 — parent epic
