# Agentic Pattern YAML Expansion — Orchestration Topologies, Routing, Termination

**Issue:** #165
**Date:** 2026-09-07
**Parent:** casehubio/parent#422 — TypeScript Programming Model (Phase 2)

## Overview

Makes all agentic orchestration patterns YAML-expressible by introducing typed spec records as a canonical data model for the YAML pathway. YAML deserializes to spec records via Jackson. A compiler resolves spec records to live runtime objects (`ExecutionModel`). Java builders continue to produce `ExecutionModel` directly — the two pathways are peers that converge on the same runtime model. MVEL3 expressions handle predicates in YAML. victools generates JSON/YAML schema from spec records for IDE completion, validation, and LLM prompt generation.

Follows the DSL-STYLE-GUIDE principle: "Three pathways, one family: pure YAML, pure Java (DSL + annotations), and hybrid. Each pathway is first-class — they are peer representations of the same models." The shared model is `ExecutionModel`, not the spec records — spec records are the YAML pathway's internal representation, following the summarisation-yaml precedent where `PipelineDefinition` serves the same role.

The typescript-programming-model spec (2026-08-23) audited 111 patterns: 75 fully YAML-expressible (strategy name + config), 6 partially expressible (config + expression language for predicates), and 22 code-only (inherently procedural, stateful, or requiring custom SPI logic). This spec covers the YAML-expressible patterns for the 8 core orchestration topologies and their associated concerns.

## Architecture

```
PatternSpec (YAML pathway data model — typed strategy refs + MVEL expressions)
  ├── YAML deserialization              ← Jackson → PatternSpec
  ├── PatternCompiler                   ← resolves refs + expressions → ExecutionModel
  └── victools schema generation        ← generates JSON/YAML schema from PatternSpec

Java builders                           ← produce ExecutionModel directly (unchanged)
```

### Layer 1: Spec Records

Purpose-built sealed record hierarchies with typed strategy references and MVEL expression strings. Each strategy family is a sealed interface with per-strategy subtypes, enabling discriminated union schema generation via victools `SealedHierarchyModule`.

Each spec area gets its own record hierarchy:

| Area | Root Spec | Subtypes / Nested Specs |
|------|-----------|------------------------|
| Patterns (8 topologies) | `PatternDefinition` (sealed) | `SupervisorSpec`, `SequenceSpec`, `LoopSpec`, `ParallelSpec`, `VotingSpec`, `DebateSpec`, `ConditionalSpec`, `HtnSpec` |
| Routing | `RoutingSpec` (sealed) | `FirstMatchRoutingSpec`, `RoundRobinRoutingSpec`, `SequentialRoutingSpec`, `LlmSelectedRoutingSpec`, `SelectAllRoutingSpec` |
| Termination | `TerminationSpec` (sealed) | `MaxIterationsSpec`, `GoalReachedSpec`, `JudgeConvergenceSpec`, `AllAgreedSpec`, `SupervisorTermSpec`, `ContestedSpec`, `ConvergenceTermSpec`, `SinglePassSpec`, `AgentCountSpec` |
| Aggregation | `AggregationSpec` (sealed) | `PassThroughSpec`, `CollectAllSpec`, `MajorityVoteSpec`, `AuctionSpec` |
| Activation | `ActivationSpec` (sealed) | `OnDispatchSpec`, `MaxIterationsActivationSpec` |
| Decomposition | `DecompositionSpec` (sealed) | `IdentitySpec`, `StaticSpec`, `LlmSpec`, `HybridSpec`, `HeuristicSpec`, `GoapSpec`, `SequenceStrategySpec`, `CapabilityDependencySpec`, `ForwardReasoningSpec` |
| Failure policy | `FailurePolicy` | Reused directly — already a pure data record |
| Agent reference | `AgentRefSpec` (sealed) | `WorkerAgentSpec`, `ChannelAgentSpec`, `HumanAgentSpec`, `ExternalAgentSpec`, `ComposedAgentSpec` |
| Judgment | `JudgmentSpec` | `TriggerSpec`, `CallerSpec`, `AgreementSpec` |
| Negotiation | `NegotiationDefinition` | `AcceptancePolicySpec` |
| Conversation | `ConversationDefinition` | `TurnPolicySpec`, `EpistemicRuleSpec`, `ConvergencePolicySpec` |
| Normative | `ConflictResolutionSpec` (sealed) | Per-strategy subtypes |

Spec records are plain Java records with Jackson annotations. No functional interfaces. All fields are either primitives, strings, enums, nested spec records, or lists thereof.

**Strategy specs use sealed interfaces with `@JsonTypeInfo` discriminator:**

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = FirstMatchRoutingSpec.class, name = "first-match"),
    @Type(value = RoundRobinRoutingSpec.class, name = "round-robin"),
    @Type(value = SequentialRoutingSpec.class, name = "sequential"),
    @Type(value = LlmSelectedRoutingSpec.class, name = "llm-selected"),
    @Type(value = SelectAllRoutingSpec.class, name = "select-all")
})
public sealed interface RoutingSpec permits FirstMatchRoutingSpec, RoundRobinRoutingSpec,
        SequentialRoutingSpec, LlmSelectedRoutingSpec, SelectAllRoutingSpec {}

public record FirstMatchRoutingSpec(@Nullable String guard) implements RoutingSpec {}
public record RoundRobinRoutingSpec() implements RoutingSpec {}
public record LlmSelectedRoutingSpec() implements RoutingSpec {}
public record SelectAllRoutingSpec() implements RoutingSpec {}
```

**Pattern specs are sealed with shared concerns plus pattern-specific fields:**

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = SupervisorSpec.class, name = "supervisor"),
    @Type(value = DebateSpec.class, name = "debate"),
    @Type(value = LoopSpec.class, name = "loop"),
    @Type(value = ParallelSpec.class, name = "parallel"),
    @Type(value = VotingSpec.class, name = "voting"),
    @Type(value = ConditionalSpec.class, name = "conditional"),
    @Type(value = SequenceSpec.class, name = "sequence"),
    @Type(value = HtnSpec.class, name = "htn")
})
public sealed interface PatternSpec permits SupervisorSpec, DebateSpec, LoopSpec,
        ParallelSpec, VotingSpec, ConditionalSpec, SequenceSpec, HtnSpec {
    @Nullable RoutingSpec routing();
    @Nullable List<TerminationSpec> termination();
    @Nullable AggregationSpec aggregation();
    @Nullable ActivationSpec activation();
    @Nullable DecompositionSpec decomposition();
    @Nullable FailurePolicy failurePolicy();
    @Nullable String task();
    List<AgentRefSpec> agents();
}
```

**Pattern-specific fields on each topology:**

| Pattern | Specific fields | Notes |
|---------|----------------|-------|
| `SupervisorSpec` | (shared concerns only) | `stateRenderer` is Java-only — cannot be expressed in YAML |
| `DebateSpec` | `@Nullable AgentRefSpec judge`, `int maxRounds` | Judge and convergence are mutually exclusive |
| `LoopSpec` | `int maxIterations`, `@Nullable String exitCondition` | exitCondition is MVEL expression |
| `ConditionalSpec` | `List<BranchSpec> branches` | Each branch: `{condition: String, agent: AgentRefSpec}` |
| `HtnSpec` | `TaskNodeSpec rootTask` | Recursive tree — `TaskNodeSpec` sealed with `PrimitiveTaskSpec` and `CompoundTaskSpec` subtypes |
| `VotingSpec` | (shared concerns only) | Compiler defaults: select-all routing, single-pass termination, majority-vote aggregation |
| `ParallelSpec` | (shared concerns only) | Compiler defaults: select-all routing, single-pass termination, collect-all aggregation |
| `SequenceSpec` | (shared concerns only) | Compiler defaults: sequential routing, agent-count termination |

**Agent reference specs include descriptor support and composition:**

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = WorkerAgentSpec.class, name = "worker"),
    @Type(value = ChannelAgentSpec.class, name = "channel"),
    @Type(value = HumanAgentSpec.class, name = "human"),
    @Type(value = ExternalAgentSpec.class, name = "external"),
    @Type(value = ComposedAgentSpec.class, name = "composed")
})
public sealed interface AgentRefSpec permits WorkerAgentSpec, ChannelAgentSpec,
        HumanAgentSpec, ExternalAgentSpec, ComposedAgentSpec {
    String name();
    @Nullable String description();
    @Nullable List<String> capabilities();
}

public record ComposedAgentSpec(
    String name,
    @Nullable String description,
    @Nullable List<String> capabilities,
    PatternSpec pattern
) implements AgentRefSpec {}
```

The compiler constructs `RoutingCandidate(ref, descriptor)` from agent specs. When `description` or `capabilities` are present, the compiler creates an `AgentDescriptor` (from eidos-api) for the candidate. This enables LLM-based routing (`LlmSelectedRouting`) to reason about agent capabilities from YAML-declared patterns.

**`FailurePolicy` is used directly** — it is already a pure data record with no functional interfaces:

```java
// Existing record in blocks — used as-is in spec records, optional with defaults
public record FailurePolicy(
    RoutingFailureAction onRoutingFailure,      // FAIL, RETRY_BROADER, ESCALATE
    AggregationFailureAction onDeadlock,         // FAIL, ESCALATE, RETRY_DIFFERENT
    AgentRetryPolicy agentRetry,                 // maxRetries, backoff, strategy, onExhausted
    ReplanPolicy replanPolicy                    // maxReplans, fallbackAction
) {}
```

When absent from YAML, `FailurePolicy.defaults()` is used (FAIL on routing failures, FAIL on deadlock, 3 retries with fixed 1s backoff).

**Java-only fields (not in spec records, not YAML-expressible):**

| Field | Rationale |
|-------|-----------|
| `listeners: List<ExecutionEventListener>` | Functional interface — observability concern, not declarative config |
| `backend: ExecutionBackend<T>` | Infrastructure concern — resolved via ServiceLoader at runtime |
| `stateRenderer: Function<T, String>` (SupervisorBuilder) | Typed function — requires Java code for state serialization |

### Layer 2: Strategy Registries

Each SPI with concrete named implementations gets a registry. Follows the `SummariserRegistry` pattern from summarisation-yaml.

```java
public class RoutingStrategyRegistry {
    RoutingStrategy<?> resolve(RoutingSpec spec, ExpressionEngine engine);
}
```

Registries for all SPI families:

| Registry | Strategies |
|----------|-----------|
| `RoutingStrategyRegistry` | first-match, round-robin, sequential, llm-selected, select-all |
| `TerminationConditionRegistry` | max-iterations, goal-reached, judge-convergence, all-agreed, supervisor, contested, convergence, single-pass, agent-count |
| `AggregationStrategyRegistry` | pass-through, collect-all, majority-vote, auction |
| `ActivationRuleRegistry` | on-dispatch, max-iterations |
| `DecompositionStrategyRegistry` | identity, static, llm, hybrid, heuristic, goap, sequence, capability-dependency, forward-reasoning |
| `AcceptancePolicyRegistry` | unanimous, majority, threshold |
| `TurnPolicyRegistry` | round-robin, addressed, point-addressed, free |
| `ConvergencePolicyRegistry` | (empty — no implementations exist; extensible via CDI) |
| `ConflictResolutionRegistry` | priority, specificity, recency, most-restrictive, escalation |
| `EpistemicRuleRegistry` | explicit-acknowledgement, tacit-acceptance, commitment-resolution |
| `JudgmentTriggerRegistry` | always-yield, never-yield, iteration-based, confidence-threshold |
| `CallerStrategyRegistry` | single, fan-out, escalation-chain |

Registries are extensible — consumers register domain-specific implementations via CDI.

Note: `ConvergencePolicy` is a `@FunctionalInterface` with zero concrete implementations in the codebase. The registry exists as a CDI extension point but ships empty. Implementations are new feature work tracked in a follow-up issue.

Note: `EpistemicRule` is a `@FunctionalInterface` with factory methods in `EpistemicRules` (not named classes). The registry maps string names to these factory method invocations with config parameters (e.g., `explicit-acknowledgement` → `EpistemicRules.explicitAcknowledgement(minParticipants)`).

Note: Two `LlmDecomposition` classes exist — `io.casehub.agency.orchestration.decomposition.LlmDecomposition` (blocks) and `io.casehub.engine.planning.decomposition.LlmDecompositionStrategy` (engine). The `llm` registry name resolves the blocks variant. Engine strategies (`portfolio`, `explicit-htn`) are available as optional registrations when the engine planning module is on the classpath.

### Layer 3: Compilers

Compilers resolve spec records into live runtime objects. The pattern subsystem gets a compiler; conversation and negotiation subsystems are scoped out.

```java
public class PatternCompiler {
    ExecutionModel<?> compile(PatternSpec spec);
}
```

The compiler:
1. Reads the pattern type from the sealed interface subtype
2. Resolves strategy references via registries
3. Compiles MVEL expressions via ExpressionEngine
4. Applies pattern-specific defaults (e.g., select-all routing for VotingSpec, single-pass termination for ParallelSpec, agent-count termination for SequenceSpec)
5. Wires pattern-specific fields (judge for DebateSpec, rootTask for HtnSpec, branches for ConditionalSpec)
6. Constructs `RoutingCandidate` list with `AgentDescriptor` where agent specs include description/capabilities
7. Constructs the runtime `ExecutionModel` with live SPI instances

**Conversation and Negotiation compilers are out of scope.** These subsystems have fundamentally different runtime architectures:

- **Patterns** → `ExecutionModel` → `ExecutionDriver` (loop-based orchestration with routing/decomposition/activation/aggregation/termination cycle)
- **Conversation** → `ConversationOrchestrator` (event-driven with `ConversationProjection`, `TurnPolicy`, `TerminationCondition<ConversationState>`, `PromptAssembler`, `AgentInvoker<String>`, `ResponseMessageBuilder`, response dispatcher). Multiple infrastructure dependencies not declaratively configurable.
- **Negotiation** → `NegotiationFold` (pure state-transition functions) + `AcceptancePolicy`. No single "runtime object" equivalent — state is folded through static methods.

The spec records for conversation and negotiation (Layer 1) define the YAML surface. The compilers that wire them into runtime objects require separate design work to resolve the infrastructure injection model. Follow-up issues track this work.

### Layer 4: Expression Compilation

Predicate-accepting fields use MVEL3 expressions in YAML. Direction is YAML→Java only.

```yaml
routing:
  type: first-match
  guard: "priority > 5"

termination:
  - type: convergence
    when: "establishedRatio > 0.8"
```

```java
// Java path — lambdas, unchanged
supervisor()
    .route(new FirstMatchRouting<>(c -> c.priority() > 5))
    .terminate(new ConvergenceTermination<>(s -> s.establishedRatio() > 0.8))
```

Both produce identical runtime objects.

**Expression compilation uses the platform `ExpressionEngine` SPI** (`io.casehub.platform.api.expression.ExpressionEngine` from `casehub-platform-api`):

1. **Collect** — all MVEL expression strings extracted from spec records during compilation
2. **Compile** — each unique expression compiled via `ExpressionEngine.compile(expression, contextType, resultType)` → `CompiledExpression<C, R>`
3. **Wire** — compiled expressions wired into runtime objects as `Predicate<T>` adapters

The platform `MvelExpressionEngine` (from `casehub-platform-expression`) handles MVEL compilation with internal caching. Identical expressions compile once.

**Two compilation modes:**
- **Compile-time** (Quarkus deployment module) — classpath YAML → compiled expressions during Maven build. Bad expressions fail the build. Extends SummarisationYamlProcessor pattern.
- **Runtime** (dynamic loading) — expressions compiled when YAML is loaded. Required for tenant-specific pipelines, hot-reload. Always available.

**jq** remains supported for data transformation expressions via yaml-core.

### Layer 5: Schema Generation

victools reflects on spec records (Layer 1) to generate JSON/YAML schema.

```java
public class AgencySchemaGenerator {
    // Reuses platform casehub-schema-generator modules:
    // - io.casehub.schema.generator.module.EnumInliningModule
    // - io.casehub.schema.generator.module.SealedHierarchyModule
    // - io.casehub.schema.generator.module.UnevaluatedPropertiesModule
    
    JsonNode generate(Class<?> rootType);
}
```

Sealed interface hierarchies (Layer 1) produce discriminated union schemas via `SealedHierarchyModule` — each strategy subtype gets its own schema with the `type` discriminator field. This gives:
- IDE completion (VS Code, IntelliJ YAML plugin) with per-strategy field suggestions
- YAML validation at parse time with strategy-specific constraints
- LLM prompt generation (schema-constrained output)
- Drift detection (CI regenerates and diffs)

## YAML Shape

Following the pattern-to-YAML mapping from the typescript-programming-model spec:

```yaml
# Supervisor pattern with routing, termination, failure policy, and agents
pattern:
  type: supervisor
  routing:
    type: first-match
    guard: "priority > 5"
  termination:
    - type: max-iterations
      iterations: 20
    - type: convergence
      threshold: 0.8
  aggregation:
    type: collect-all
  failurePolicy:
    onRoutingFailure: RETRY_BROADER
    onDeadlock: ESCALATE
    agentRetry:
      maxRetries: 5
      backoff: PT2S
      backoffStrategy: EXPONENTIAL
      onExhausted: ESCALATE
  agents:
    - name: analyst
      type: worker
      description: "Analyses input data"
      capabilities: [data-analysis, pattern-recognition]
    - name: reviewer
      type: worker
      description: "Reviews analysis"

# Debate pattern with judge and maxRounds
pattern:
  type: debate
  maxRounds: 7
  judge:
    name: arbiter
    type: worker
    description: "Resolves disputed points"
  agents:
    - name: proponent
      type: worker
    - name: opponent
      type: worker

# Loop pattern with exit condition (MVEL expression)
pattern:
  type: loop
  maxIterations: 15
  exitCondition: "qualityScore > 0.9"
  agents:
    - name: improver
      type: worker

# Conditional pattern with branches
pattern:
  type: conditional
  branches:
    - condition: "category == 'medical'"
      agent:
        name: medical-expert
        type: worker
    - condition: "category == 'legal'"
      agent:
        name: legal-expert
        type: worker

# Composed agent — pattern nesting
pattern:
  type: supervisor
  agents:
    - name: research-team
      type: composed
      pattern:
        type: parallel
        agents:
          - name: web-researcher
            type: worker
          - name: db-researcher
            type: worker

# Negotiation with acceptance policy
negotiation:
  parties: [buyer, seller]
  acceptance:
    type: majority
  termination:
    - type: max-iterations
      iterations: 10

# Conversation with turn policy
conversation:
  turnPolicy:
    type: round-robin
  epistemicRule:
    type: explicit-acknowledgement
    minParticipants: 2
```

## Module Structure

New code lives in a new `agentic-yaml` Maven module (parallel to `summarisation-yaml`):

```
casehub-agency-agentic-yaml/
  src/main/java/io/casehub/agency/agentic/yaml/
    spec/           ← Layer 1: spec records (PatternSpec, RoutingSpec, etc.)
    registry/       ← Layer 2: strategy registries
    compiler/       ← Layer 3: PatternCompiler
    expression/     ← Layer 4: ExpressionEngine integration
    schema/         ← Layer 5: AgencySchemaGenerator + SchemaModules
    
casehub-agency-agentic-yaml-deployment/
  src/main/java/io/casehub/agency/agentic/yaml/deployment/
    AgenticYamlProcessor.java     ← Quarkus build extension
    AgenticRecorder.java          ← runtime bean registration
```

## Dependencies

**agentic-yaml module:**
- Compile: `casehub-agency` (spec records reference blocks types — AgentRef, FailurePolicy, PatternType), `jackson-dataformat-yaml`
- Provided: `casehub-platform-api` (`io.casehub.platform.api.expression.ExpressionEngine` SPI), `quarkus-core` (recorder)
- Test: JUnit 5, AssertJ, `casehub-platform-expression` (MvelExpressionEngine)

**agentic-yaml-deployment module:**
- Compile: `casehub-agency-agentic-yaml`, `quarkus-core-deployment`

**Schema generation:**
- Compile: `casehub-schema-generator` (platform — `io.casehub.schema.generator.module.EnumInliningModule`, `SealedHierarchyModule`, `UnevaluatedPropertiesModule`)
- Test: `victools-jsonschema-generator`

## Scope

### In scope

| Area | Spec Records | Registry | Compiler | Tests |
|------|-------------|----------|----------|-------|
| Patterns (8 topologies) | Yes | — (pattern type dispatch via sealed interface) | PatternCompiler | YAML fixtures per topology |
| Routing (5 strategies) | Yes | RoutingStrategyRegistry | Part of PatternCompiler | Per-strategy round-trip |
| Termination (9 conditions) | Yes | TerminationConditionRegistry | Part of PatternCompiler | Per-condition round-trip |
| Aggregation (4 strategies) | Yes | AggregationStrategyRegistry | Part of PatternCompiler | Per-strategy round-trip |
| Activation (2 rules) | Yes | ActivationRuleRegistry | Part of PatternCompiler | Per-rule round-trip |
| Decomposition (9 strategies) | Yes | DecompositionStrategyRegistry | Part of PatternCompiler | Per-strategy round-trip |
| Failure policy | Yes (reuses FailurePolicy record) | — | Part of PatternCompiler | Round-trip with defaults |
| Judgment | Yes | JudgmentTrigger/Caller/Agreement registries | JudgmentCompiler | Judgment policy round-trip |
| Negotiation | Spec records only | AcceptancePolicyRegistry | Deferred — follow-up issue | Spec record serialization |
| Conversation | Spec records only | TurnPolicy/Epistemic registries | Deferred — follow-up issue | Spec record serialization |
| Normative | Yes | ConflictResolutionRegistry | NormativeCompiler | Resolution round-trip |
| Expression compilation | Platform ExpressionEngine SPI | — | Via ExpressionEngine | Expression evaluation tests |
| Schema generation | victools on sealed spec records | — | — | Schema drift test |

### Not in scope

| Item | Rationale | Tracking |
|------|-----------|----------|
| Conversation compiler | Different runtime architecture (event-driven ConversationOrchestrator with infrastructure dependencies) | Follow-up issue |
| Negotiation compiler | Different runtime architecture (NegotiationFold + AcceptancePolicy, no single runtime object) | Follow-up issue |
| ConvergencePolicy implementations | @FunctionalInterface with zero concrete implementations — new feature work | Follow-up issue |
| Social cognition orchestrators | Complex lifecycle, no declarative equivalent (22 code-only patterns per TS spec audit) | Follow-up issue |
| TS CDK generation (L2) | Separate issue, depends on this work | parent#422 |
| Custom functional SPIs | No concrete implementations to register | Follow-up issue |

## Testing Strategy

1. **Round-trip tests** — YAML → spec record → compiler → runtime object → verify behavior matches direct Java construction
2. **Schema drift test** — victools generates schema, CI compares to committed schema (`git diff --exit-code`)
3. **Expression tests** — MVEL expressions evaluate correctly via platform ExpressionEngine SPI
4. **Registry extensibility** — consumer-registered strategies resolve correctly
5. **Pattern-specific tests** — each topology's specific fields (judge, maxRounds, exitCondition, branches, rootTask) produce correct runtime configuration
6. **Failure policy tests** — explicit policy YAML produces correct FailurePolicy; absent policy defaults to `FailurePolicy.defaults()`
7. **Composition tests** — ComposedAgentSpec with nested PatternSpec produces correct `AgentRef.ComposedAgent` with nested `ExecutionModel`
8. **Descriptor tests** — agent specs with description/capabilities produce `RoutingCandidate` with `AgentDescriptor`

## References

- parent/docs/DSL-STYLE-GUIDE.md — "peer representations of the same models"
- specs/main/2026-08-23-typescript-programming-model-design.md — pattern-to-YAML mapping table, expressiveness audit (111 patterns: 75 YAML, 6 partial, 22 code-only)
- specs/issue-233-summarisation-yaml-surface/2026-09-05-summarisation-yaml-surface-design.md — PipelineDefinition + PipelineCompiler precedent
- engine/generator/CaseHubSchemaGenerator.java — victools + SchemaModules approach
- neocortex#246 — API-to-YAML audit template
- platform#279 — SealedHierarchyModule promotion (done)
