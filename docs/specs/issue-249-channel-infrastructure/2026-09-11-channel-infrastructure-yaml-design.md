# Channel Infrastructure — YAML Surface

**Issue:** casehubio/agency#249
**Date:** 2026-09-11
**Scope:** 12 capabilities across §4 (Conversation), §5 (Negotiation), §16 (Channel) → spec records, compilers, termination type extensions

## Problem

Multi-agent conversations, negotiations, and channel wiring are all declarative in
structure but require Java construction. The conversation compiler gap prevents
YAML-only conversation setup. The negotiation gap is worse — `NegotiationDefinition` exists
but has no compiler, and three termination types (`AcceptedTermination`,
`TerminalOutcomeTermination`, `DeadlineTermination`) are missing from `TerminationSpec`.

## Solution

Three sub-areas in one issue:
1. **Negotiation:** extend `TerminationSpec` with 3 negotiation-specific variants +
   build `NegotiationCompiler` (NegotiationSpec → CompiledNegotiation)
2. **Channel + Conversation:** `AgentParticipantSpec`, `ChannelBindingSpec`,
   `ChannelExecutionStrategySpec` (conversation only), `ConversationCompiler` →
   `CompiledConversation` (declarative config; consumer wires with CDI)
3. **Progress:** named progress renderer types in TerminationConditionRegistry

## Area 1: Negotiation

### New TerminationSpec Variants

Add three variants to the existing `TerminationSpec` sealed interface:

```java
record Accepted() implements TerminationSpec {}

record TerminalOutcome() implements TerminationSpec {}

record Deadline(Duration timeout) implements TerminationSpec {}
```

YAML:
```yaml
termination:
  - type: accepted          # stateless
  - type: terminal-outcome  # stateless
  - type: deadline
    timeout: PT30M           # Duration → compiled to Instant at use time
```

Add to `@JsonSubTypes`:
```java
@Type(value = TerminationSpec.Accepted.class, name = "accepted"),
@Type(value = TerminationSpec.TerminalOutcome.class, name = "terminal-outcome"),
@Type(value = TerminationSpec.Deadline.class, name = "deadline")
```

The `TerminationConditionRegistry` needs new factories for these types. Since they
produce `TerminationCondition<NegotiationState>` (not `ConversationState`), the
registry must handle the type parameter difference. The existing registry already
uses raw types internally — these are additive.

### NegotiationCompiler

```java
public class NegotiationCompiler {

    private final TerminationConditionRegistry terminationRegistry;

    public CompiledNegotiation compile(NegotiationSpec spec) {
        var acceptance = compileAcceptance(spec.acceptance());
        var termination = compileTermination(spec.termination());
        var projection = new NegotiationProjection(spec.parties(), acceptance);
        return new CompiledNegotiation(projection, termination, spec.parties());
    }
}
```

### CompiledNegotiation

```java
public record CompiledNegotiation(
    NegotiationProjection projection,
    @Nullable TerminationCondition<NegotiationState> termination,
    Set<String> parties) {}
```

The compiler resolves `AcceptancePolicySpec` to `AcceptancePolicy` (Unanimous, Majority,
Threshold) and composes `TerminationSpec` list into `NegotiationCompositeTermination`.

### AcceptancePolicySpec compilation

`AcceptancePolicySpec` already exists. The compiler maps:
- `unanimous` → `new UnanimousAcceptance()`
- `majority` → `new MajorityAcceptance()`
- `threshold` → `new ThresholdAcceptance(spec.threshold())`

This compilation logic lives in `NegotiationCompiler` — it's the only consumer.

## Area 2: Channel + Conversation

### AgentParticipantSpec

```java
// agentic-yaml/spec/world/ or spec/ — it's shared across conversation and channel
public record AgentParticipantSpec(
    AgentRefSpec agent,
    String role,
    String systemPrompt) {}
```

YAML:
```yaml
participants:
  - agent:
      type: worker
      name: analyst
    role: analyst
    systemPrompt: "You are a financial analyst..."
```

The compiler resolves `AgentRefSpec` → `AgentRef` (using the existing `PatternCompiler`
agent resolution) and constructs `AgentParticipant`.

### ChannelBindingSpec

```java
public record ChannelBindingSpec(
    @Nullable String channelId,
    String semantic) {}
```

YAML:
```yaml
channel:
  channelId: "550e8400-e29b-41d4-a716-446655440000"  # optional, auto-generated if absent
  semantic: DELIBERATION
```

`channelId` is optional — if absent, the compiler generates a UUID. `semantic` is
a `ChannelSemantic` enum string (DELIBERATION, NEGOTIATION, etc.).

### ChannelExecutionStrategySpec

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = ChannelExecutionStrategySpec.ConversationStrategySpec.class,
          name = "conversation")
})
public sealed interface ChannelExecutionStrategySpec {

    record ConversationStrategySpec(
        @Nullable TurnPolicySpec turnPolicy,
        @Nullable List<TerminationSpec> termination,
        @Nullable EpistemicRuleSpec epistemicRule,
        @Nullable ConvergencePolicySpec convergencePolicy,
        List<AgentParticipantSpec> participants
    ) implements ChannelExecutionStrategySpec {}
}
```

FanIn and Barrier variants are code-only (take `Function` params). Only
`conversation` is YAML-expressible. The coverage matrix entry for
`ChannelExecutionStrategy` will become "Partial" — Conversation Done, FanIn/Barrier
code-only.

### ConversationCompiler

```java
public class ConversationCompiler {

    private final TerminationConditionRegistry terminationRegistry;

    public CompiledConversation compile(
            ChannelExecutionStrategySpec.ConversationStrategySpec spec) {
        return new CompiledConversation(
            compileTurnPolicy(spec.turnPolicy()),
            compileTermination(spec.termination()),
            compileParticipants(spec.participants()),
            compileEpistemicRule(spec.epistemicRule()),
            compileConvergencePolicy(spec.convergencePolicy()));
    }
}
```

### CompiledConversation

```java
public record CompiledConversation(
    TurnPolicy turnPolicy,
    @Nullable TerminationCondition<ConversationState> termination,
    List<AgentParticipant> participants,
    @Nullable EpistemicRule epistemicRule,
    @Nullable ConvergencePolicy convergencePolicy) {}
```

Consumer wires `ConversationOrchestrator` by combining `CompiledConversation` fields
with their CDI-injected `PromptAssembler`, `ResponseMessageBuilder`, `AgentInvoker`,
`ConversationProjection`, and `PartitionedObservationService`.

### ConversationProtocolSpec

```java
public record ConversationProtocolSpec(
    @Nullable String sentinel,
    @Nullable Set<String> entryTypes) {}
```

Config for `ConversationProjection` setup. `sentinel` is the `ChannelMessageMeta`
prefix (e.g., `"[CONV]"`). `entryTypes` declares recognised entry type names beyond
the infrastructure defaults. Both optional — defaults are sensible.

### Progress Renderer Types

`DefaultProgressRenderer` handles three shape types: `percentage`, `count`, `step`.
These are not types to be registered — they're string constants interpreted at
runtime. But we can declare which progress shape to use in YAML:

```java
public record ProgressRendererSpec(@Nullable String shape) {}
```

YAML: `progress: { shape: percentage }` or `progress: { shape: step }`.

The compiler validates the shape name against the known set. This is a thin config —
`DefaultProgressRenderer` already handles the dispatch.

## Package Layout

| Type | Package |
|------|---------|
| `AgentParticipantSpec` | `io.casehub.agency.core.yaml.spec` (shared) |
| `ChannelBindingSpec` | `io.casehub.agency.core.yaml.spec` |
| `ChannelExecutionStrategySpec` | `io.casehub.agency.core.yaml.spec` |
| `ConversationProtocolSpec` | `io.casehub.agency.core.yaml.spec` |
| `ProgressRendererSpec` | `io.casehub.agency.core.yaml.spec` |
| `TerminationSpec.Accepted/TerminalOutcome/Deadline` | `io.casehub.agency.core.yaml.spec` (existing) |
| `ConversationCompiler` | `io.casehub.agency.core.yaml.compiler` |
| `CompiledConversation` | `io.casehub.agency.core.yaml.compiler` |
| `NegotiationCompiler` | `io.casehub.agency.core.yaml.compiler` |
| `CompiledNegotiation` | `io.casehub.agency.core.yaml.compiler` |

New spec records go in the existing `spec` package (not a sub-package) since they're
shared across pattern types, like `ConversationDefinition` and `NegotiationDefinition` already are.

## Type Inventory

| # | Type | Fields | Maps to | Notes |
|---|------|--------|---------|-------|
| 1 | `TerminationSpec.Accepted` | 0 | `AcceptedTermination` | Stateless |
| 2 | `TerminationSpec.TerminalOutcome` | 0 | `TerminalOutcomeTermination` | Stateless |
| 3 | `TerminationSpec.Deadline` | 1 | `DeadlineTermination` | timeout Duration |
| 4 | `AgentParticipantSpec` | 3 | `AgentParticipant` | Wraps AgentRefSpec |
| 5 | `ChannelBindingSpec` | 2 | `ChannelBinding` | Optional UUID |
| 6 | `ChannelExecutionStrategySpec.ConversationStrategySpec` | 5 | `ChannelExecutionStrategy.Conversation` | Only YAML variant |
| 7 | `ConversationProtocolSpec` | 2 | Protocol config | sentinel + entry types |
| 8 | `ProgressRendererSpec` | 1 | Shape validation | Thin config |
| 9 | `CompiledConversation` | 5 | Output | Declarative conversation config |
| 10 | `CompiledNegotiation` | 3 | Output | Projection + termination |
| 11 | `ConversationCompiler` | — | Compiler | Wires turn policy, participants |
| 12 | `NegotiationCompiler` | — | Compiler | Wires projection, acceptance, termination |

**Total: 12 types, ~22 fields.**

## Dependencies

No new compile dependencies. `agentic-yaml` already depends on `casehub-agency`
(which contains all runtime types: `NegotiationProjection`, `AgentParticipant`,
`ChannelBinding`, `ChannelExecutionStrategy`, `ConversationOrchestrator`).

## Testing

### Serialization tests
- `TerminationSpec`: `accepted`, `terminal-outcome`, `deadline` YAML round-trip
- `AgentParticipantSpec`: full fields + minimal
- `ChannelBindingSpec`: with/without channelId
- `ChannelExecutionStrategySpec.ConversationStrategySpec`: with participants, turn policy, termination

### Compiler tests
- `NegotiationCompiler`: NegotiationSpec → CompiledNegotiation with all acceptance policies,
  composed termination, empty termination (null)
- `ConversationCompiler`: ConversationStrategySpec → CompiledConversation with turn policy,
  participants, termination, epistemic/convergence
- `DeadlineTermination` compilation: Duration → Instant conversion
- Validation: unknown acceptance policy → error, unknown termination type → error

### End-to-end tests
- Full NegotiationSpec YAML → compiled NegotiationProjection with mixed termination types
- Full ConversationStrategySpec YAML → CompiledConversation with participants resolved

## Coverage Matrix Update

After closing, update:

**§4 Conversation:**
| Capability | Before | After |
|-----------|--------|-------|
| Conversation compiler | Gap | Done |
| Agent participants | Gap | Done |
| Conversation protocol config | Gap | Done |
| Progress renderer types | Gap | Done |

**§5 Negotiation:**
| Capability | Before | After |
|-----------|--------|-------|
| NegotiationSpec | Spec only | Done |
| AcceptedTermination | Gap | Done |
| TerminalOutcomeTermination | Gap | Done |
| DeadlineTermination | Gap | Done |
| NegotiationProjection | Gap | Done |

**§16 Channel:**
| Capability | Before | After |
|-----------|--------|-------|
| ChannelBinding | Gap | Done |
| ChannelExecutionStrategy | Gap | Partial (Conversation Done; FanIn/Barrier code-only) |
| AgentParticipant | Gap | Done |
| ConversationProtocol config | Gap | Done |

## References

- `TerminationSpec.java` — existing sealed interface to extend
- `TerminationConditionRegistry.java` — registry for termination type compilation
- `AcceptedTermination.java` — runtime negotiation termination (stateless)
- `TerminalOutcomeTermination.java` — runtime negotiation termination (stateless)
- `DeadlineTermination.java` — runtime negotiation termination (Instant)
- `NegotiationSpec.java` — existing spec record (spec-only, no compiler)
- `NegotiationProjection.java` — runtime projection (parties + acceptance)
- `NegotiationCompositeTermination.java` — first-non-Continue-wins composition
- `AcceptancePolicySpec.java` — existing acceptance policy spec
- `AgentParticipant.java` — runtime participant record (AgentRef + role + systemPrompt)
- `AgentRefSpec.java` — existing agent ref spec (Worker/Channel/Human/External/Composed)
- `ChannelBinding.java` — runtime channel identity (UUID + ChannelSemantic)
- `ChannelExecutionStrategy.java` — sealed (Conversation/FanIn/Barrier)
- `ConversationConfig.java` — runtime conversation config record
- `ConversationOrchestrator.java` — runtime orchestrator (10 constructor args)
- `ConversationProtocol.java` — protocol constants
- `ConversationSpec.java` — existing conversation spec (turnPolicy, epistemicRule, convergencePolicy)
- `DefaultProgressRenderer.java` — runtime renderer (percentage/count/step shapes)
- `PatternCompiler.java` — agent resolution precedent
- `CognitionCompiler.java` — compiler pattern precedent (#247)
- `docs/yaml-coverage.md` §4, §5, §16 — gap inventory
