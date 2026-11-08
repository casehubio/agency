# Channel Infrastructure YAML Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #249 — feat: Channel infrastructure — YAML surface for multi-agent conversations
**Issue group:** #249

**Goal:** Close all 12 YAML surface gaps in §4 (Conversation), §5 (Negotiation), and §16 (Channel) — new termination types, spec records, two compilers, and coverage matrix update.

**Architecture:** Extend `TerminationSpec` with 3 negotiation variants. Build `NegotiationCompiler` (NegotiationSpec → CompiledNegotiation via NegotiationProjection). Add `AgentParticipantSpec`, `ChannelBindingSpec`, `ChannelExecutionStrategySpec` (conversation only), `ConversationProtocolSpec`, `ProgressRendererSpec`. Build `ConversationCompiler` (→ CompiledConversation with declarative config; consumer wires with CDI). All types in existing `spec`/`compiler` packages.

**Tech Stack:** Java 17 records, Jackson YAML, existing registries (TurnPolicyRegistry, TerminationConditionRegistry, EpistemicRuleRegistry, ConvergencePolicyRegistry, AcceptancePolicyRegistry), JUnit 5, AssertJ

## Global Constraints

- Spec records in `io.casehub.blocks.agentic.yaml.spec` (existing package)
- Compilers in `io.casehub.blocks.agentic.yaml.compiler` (existing package)
- No new compile dependencies
- Extend existing `TerminationSpec` sealed interface (add 3 variants + 3 `@JsonSubTypes` entries)
- Reuse existing registries — no new registries needed
- `DeadlineTermination` takes `Duration` in YAML → compiled to `Instant` by adding duration to current time

---

## Batch 1: Negotiation — termination types + compiler

### Task 1: Negotiation termination variants in TerminationSpec

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/TerminationSpec.java`
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/TerminationConditionRegistry.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/StrategySpecSerializationTest.java` (or create new test file)
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/TerminationConditionRegistryTest.java`

**Interfaces:**
- Produces: `TerminationSpec.Accepted()`, `TerminationSpec.TerminalOutcome()`, `TerminationSpec.Deadline(Duration timeout)` — all deserialized via `@JsonSubTypes` names `"accepted"`, `"terminal-outcome"`, `"deadline"`
- Produces: `TerminationConditionRegistry.resolve()` handles all 3 new types → `AcceptedTermination`, `TerminalOutcomeTermination`, `DeadlineTermination`

- [ ] **Step 1: Write failing serialization tests**

Add to `StrategySpecSerializationTest` (or create `NegotiationTerminationSpecTest`):

```java
@Test
void acceptedTerminationSpec() throws Exception {
    var yaml = "type: accepted";
    var spec = mapper.readValue(yaml, TerminationSpec.class);
    assertThat(spec).isInstanceOf(TerminationSpec.Accepted.class);
}

@Test
void terminalOutcomeSpec() throws Exception {
    var yaml = "type: terminal-outcome";
    var spec = mapper.readValue(yaml, TerminationSpec.class);
    assertThat(spec).isInstanceOf(TerminationSpec.TerminalOutcome.class);
}

@Test
void deadlineSpec() throws Exception {
    var yaml = """
            type: deadline
            timeout: PT30M
            """;
    var spec = mapper.readValue(yaml, TerminationSpec.class);
    assertThat(spec).isInstanceOf(TerminationSpec.Deadline.class);
    assertThat(((TerminationSpec.Deadline) spec).timeout())
            .isEqualTo(java.time.Duration.ofMinutes(30));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=StrategySpecSerializationTest`
Expected: Compilation failure — new types don't exist.

- [ ] **Step 3: Add 3 variants to TerminationSpec**

Add to `@JsonSubTypes`:
```java
@Type(value = TerminationSpec.Accepted.class, name = "accepted"),
@Type(value = TerminationSpec.TerminalOutcome.class, name = "terminal-outcome"),
@Type(value = TerminationSpec.Deadline.class, name = "deadline")
```

Add records:
```java
record Accepted() implements TerminationSpec {}
record TerminalOutcome() implements TerminationSpec {}
record Deadline(Duration timeout) implements TerminationSpec {}
```

Add `import java.time.Duration;` to the file.

- [ ] **Step 4: Write failing registry resolution tests**

Add to `TerminationConditionRegistryTest`:

```java
@Test
void resolvesAccepted() {
    var condition = registry.resolve(new TerminationSpec.Accepted(), null);
    assertThat(condition).isInstanceOf(AcceptedTermination.class);
}

@Test
void resolvesTerminalOutcome() {
    var condition = registry.resolve(new TerminationSpec.TerminalOutcome(), null);
    assertThat(condition).isInstanceOf(TerminalOutcomeTermination.class);
}

@Test
void resolvesDeadline() {
    var condition = registry.resolve(new TerminationSpec.Deadline(Duration.ofMinutes(30)), null);
    assertThat(condition).isInstanceOf(DeadlineTermination.class);
}
```

- [ ] **Step 5: Add resolution cases to TerminationConditionRegistry**

Add to the `resolve()` switch:
```java
case TerminationSpec.Accepted ignored ->
        (TerminationCondition<T>) new AcceptedTermination();
case TerminationSpec.TerminalOutcome ignored ->
        (TerminationCondition<T>) new TerminalOutcomeTermination();
case TerminationSpec.Deadline dl ->
        (TerminationCondition<T>) new DeadlineTermination(
                java.time.Instant.now().plus(dl.timeout()));
```

Add imports: `AcceptedTermination`, `TerminalOutcomeTermination`, `DeadlineTermination` from `io.casehub.blocks.negotiation`.

- [ ] **Step 6: Run all tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add agentic-yaml/src/
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#249): negotiation termination types — accepted, terminal-outcome, deadline Refs #249"
```

### Task 2: NegotiationCompiler + CompiledNegotiation

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/CompiledNegotiation.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/NegotiationCompiler.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/compiler/NegotiationCompilerTest.java`

**Interfaces:**
- Consumes: `TerminationConditionRegistry.resolve()` from Task 1, `AcceptancePolicySpec`, `NegotiationSpec`
- Produces: `NegotiationCompiler(TerminationConditionRegistry, @Nullable ExpressionEngine)` with `CompiledNegotiation compile(NegotiationSpec)`
- Produces: `CompiledNegotiation(NegotiationProjection, @Nullable TerminationCondition<NegotiationState>, Set<String>)`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.registry.TerminationConditionRegistry;
import io.casehub.blocks.agentic.yaml.spec.NegotiationSpec;
import io.casehub.blocks.negotiation.NegotiationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NegotiationCompilerTest {

    private ObjectMapper mapper;
    private NegotiationCompiler compiler;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        compiler = new NegotiationCompiler(new TerminationConditionRegistry(), null);
    }

    @Test
    void compilesWithUnanimousAcceptance() throws Exception {
        var yaml = """
                parties:
                  - buyer
                  - seller
                acceptance:
                  type: unanimous
                """;
        var spec = mapper.readValue(yaml, NegotiationSpec.class);
        var compiled = compiler.compile(spec);
        assertThat(compiled.parties()).containsExactlyInAnyOrder("buyer", "seller");
        assertThat(compiled.projection()).isNotNull();
        assertThat(compiled.termination()).isNull();
    }

    @Test
    void compilesWithThresholdAcceptance() throws Exception {
        var yaml = """
                parties:
                  - a
                  - b
                  - c
                acceptance:
                  type: threshold
                  minAcceptances: 2
                """;
        var spec = mapper.readValue(yaml, NegotiationSpec.class);
        var compiled = compiler.compile(spec);
        assertThat(compiled.parties()).hasSize(3);
    }

    @Test
    void compilesWithTermination() throws Exception {
        var yaml = """
                parties:
                  - buyer
                  - seller
                acceptance:
                  type: unanimous
                termination:
                  - type: accepted
                  - type: max-rounds
                    iterations: 10
                """;
        var spec = mapper.readValue(yaml, NegotiationSpec.class);
        var compiled = compiler.compile(spec);
        assertThat(compiled.termination()).isNotNull();
    }

    @Test
    void compilesWithDeadlineTermination() throws Exception {
        var yaml = """
                parties:
                  - buyer
                  - seller
                acceptance:
                  type: majority
                termination:
                  - type: deadline
                    timeout: PT1H
                  - type: terminal-outcome
                """;
        var spec = mapper.readValue(yaml, NegotiationSpec.class);
        var compiled = compiler.compile(spec);
        assertThat(compiled.termination()).isNotNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=NegotiationCompilerTest`
Expected: Compilation failure.

- [ ] **Step 3: Create CompiledNegotiation**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.negotiation.NegotiationProjection;
import io.casehub.blocks.negotiation.NegotiationState;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public record CompiledNegotiation(
        NegotiationProjection projection,
        @Nullable TerminationCondition<NegotiationState> termination,
        Set<String> parties) {}
```

- [ ] **Step 4: Create NegotiationCompiler**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.yaml.registry.TerminationConditionRegistry;
import io.casehub.blocks.agentic.yaml.spec.AcceptancePolicySpec;
import io.casehub.blocks.agentic.yaml.spec.NegotiationSpec;
import io.casehub.blocks.agentic.yaml.spec.TerminationSpec;
import io.casehub.blocks.negotiation.*;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class NegotiationCompiler {

    private final TerminationConditionRegistry terminationRegistry;
    private final @Nullable ExpressionEngine expressionEngine;

    public NegotiationCompiler(TerminationConditionRegistry terminationRegistry,
                                @Nullable ExpressionEngine expressionEngine) {
        this.terminationRegistry = terminationRegistry;
        this.expressionEngine = expressionEngine;
    }

    public CompiledNegotiation compile(NegotiationSpec spec) {
        var acceptance = compileAcceptance(spec.acceptance());
        var termination = compileTermination(spec.termination());
        var projection = new NegotiationProjection(spec.parties(), acceptance);
        return new CompiledNegotiation(projection, termination, spec.parties());
    }

    private AcceptancePolicy compileAcceptance(AcceptancePolicySpec spec) {
        return switch (spec) {
            case AcceptancePolicySpec.Unanimous ignored -> new UnanimousAcceptance();
            case AcceptancePolicySpec.Majority ignored -> new MajorityAcceptance();
            case AcceptancePolicySpec.Threshold t -> new ThresholdAcceptance(t.minAcceptances());
        };
    }

    @SuppressWarnings("unchecked")
    private @Nullable TerminationCondition<NegotiationState> compileTermination(
            @Nullable List<TerminationSpec> specs) {
        if (specs == null || specs.isEmpty()) return null;
        if (specs.size() == 1) {
            return terminationRegistry.resolve(specs.get(0), expressionEngine);
        }
        var conditions = specs.stream()
                .<TerminationCondition<NegotiationState>>map(
                        s -> terminationRegistry.resolve(s, expressionEngine))
                .toList();
        return new NegotiationCompositeTermination(
                conditions.toArray(new TerminationCondition[0]));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=NegotiationCompilerTest`
Expected: All 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add agentic-yaml/src/
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#249): NegotiationCompiler + CompiledNegotiation Refs #249"
```

---

## Batch 2: Channel + Conversation — spec records + compiler

### Task 3: AgentParticipantSpec, ChannelBindingSpec, ConversationProtocolSpec, ProgressRendererSpec

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/AgentParticipantSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ChannelBindingSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ConversationProtocolSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ProgressRendererSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/ChannelExecutionStrategySpec.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/ChannelSpecSerializationTest.java`

**Interfaces:**
- Consumes: `AgentRefSpec` (existing), `TurnPolicySpec` (existing), `TerminationSpec` (existing), `EpistemicRuleSpec` (existing), `ConvergencePolicySpec` (existing)
- Produces: `AgentParticipantSpec(AgentRefSpec agent, String role, String systemPrompt)`
- Produces: `ChannelBindingSpec(@Nullable String channelId, String semantic)`
- Produces: `ConversationProtocolSpec(@Nullable String sentinel, @Nullable Set<String> entryTypes)`
- Produces: `ProgressRendererSpec(@Nullable String shape)`
- Produces: `ChannelExecutionStrategySpec` sealed with `ConversationStrategySpec`

- [ ] **Step 1: Write failing serialization tests**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void agentParticipantSpec() throws Exception {
        var yaml = """
                agent:
                  type: worker
                  name: analyst
                role: analyst
                systemPrompt: You are a financial analyst.
                """;
        var spec = mapper.readValue(yaml, AgentParticipantSpec.class);
        assertThat(spec.agent()).isNotNull();
        assertThat(spec.role()).isEqualTo("analyst");
        assertThat(spec.systemPrompt()).isEqualTo("You are a financial analyst.");
    }

    @Test
    void channelBindingSpecWithId() throws Exception {
        var yaml = """
                channelId: "550e8400-e29b-41d4-a716-446655440000"
                semantic: DELIBERATION
                """;
        var spec = mapper.readValue(yaml, ChannelBindingSpec.class);
        assertThat(spec.channelId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(spec.semantic()).isEqualTo("DELIBERATION");
    }

    @Test
    void channelBindingSpecWithoutId() throws Exception {
        var yaml = "semantic: NEGOTIATION";
        var spec = mapper.readValue(yaml, ChannelBindingSpec.class);
        assertThat(spec.channelId()).isNull();
        assertThat(spec.semantic()).isEqualTo("NEGOTIATION");
    }

    @Test
    void conversationProtocolSpec() throws Exception {
        var yaml = """
                sentinel: "[CONV]"
                entryTypes:
                  - PROPOSAL
                  - COUNTER
                """;
        var spec = mapper.readValue(yaml, ConversationProtocolSpec.class);
        assertThat(spec.sentinel()).isEqualTo("[CONV]");
        assertThat(spec.entryTypes()).containsExactlyInAnyOrder("PROPOSAL", "COUNTER");
    }

    @Test
    void progressRendererSpec() throws Exception {
        var yaml = "shape: percentage";
        var spec = mapper.readValue(yaml, ProgressRendererSpec.class);
        assertThat(spec.shape()).isEqualTo("percentage");
    }

    @Test
    void conversationStrategySpec() throws Exception {
        var yaml = """
                type: conversation
                turnPolicy:
                  type: round-robin
                termination:
                  - type: max-iterations
                    iterations: 10
                participants:
                  - agent:
                      type: worker
                      name: alice
                    role: analyst
                    systemPrompt: You are Alice.
                  - agent:
                      type: worker
                      name: bob
                    role: reviewer
                    systemPrompt: You are Bob.
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        assertThat(spec).isInstanceOf(ChannelExecutionStrategySpec.ConversationStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        assertThat(conv.participants()).hasSize(2);
        assertThat(conv.turnPolicy()).isNotNull();
        assertThat(conv.termination()).hasSize(1);
    }

    @Test
    void conversationStrategySpecMinimal() throws Exception {
        var yaml = """
                type: conversation
                participants:
                  - agent:
                      type: worker
                      name: solo
                    role: agent
                    systemPrompt: You are an agent.
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        assertThat(conv.turnPolicy()).isNull();
        assertThat(conv.termination()).isNull();
        assertThat(conv.epistemicRule()).isNull();
        assertThat(conv.convergencePolicy()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=ChannelSpecSerializationTest`
Expected: Compilation failure.

- [ ] **Step 3: Create the 5 spec records**

`AgentParticipantSpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec;

public record AgentParticipantSpec(
        AgentRefSpec agent,
        String role,
        String systemPrompt) {}
```

`ChannelBindingSpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

public record ChannelBindingSpec(
        @Nullable String channelId,
        String semantic) {}
```

`ConversationProtocolSpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;
import java.util.Set;

public record ConversationProtocolSpec(
        @Nullable String sentinel,
        @Nullable Set<String> entryTypes) {}
```

`ProgressRendererSpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

public record ProgressRendererSpec(@Nullable String shape) {}
```

`ChannelExecutionStrategySpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.List;

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

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=ChannelSpecSerializationTest`
Expected: All 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add agentic-yaml/src/
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#249): AgentParticipantSpec, ChannelBindingSpec, ChannelExecutionStrategySpec, ConversationProtocolSpec, ProgressRendererSpec Refs #249"
```

### Task 4: ConversationCompiler + CompiledConversation

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/CompiledConversation.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/ConversationCompiler.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/compiler/ConversationCompilerTest.java`

**Interfaces:**
- Consumes: `TurnPolicyRegistry`, `TerminationConditionRegistry`, `EpistemicRuleRegistry`, `ConvergencePolicyRegistry` (all existing). `AgentParticipantSpec`, `ChannelExecutionStrategySpec.ConversationStrategySpec` from Task 3.
- Produces: `ConversationCompiler(TurnPolicyRegistry, TerminationConditionRegistry, EpistemicRuleRegistry, ConvergencePolicyRegistry, @Nullable ExpressionEngine)` with `CompiledConversation compile(ConversationStrategySpec)`
- Produces: `CompiledConversation(TurnPolicy, @Nullable TerminationCondition<ConversationState>, List<AgentParticipant>, @Nullable EpistemicRule, @Nullable ConvergencePolicy)`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.registry.*;
import io.casehub.blocks.agentic.yaml.spec.ChannelExecutionStrategySpec;
import io.casehub.blocks.conversation.orchestration.RoundRobinTurnPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationCompilerTest {

    private ObjectMapper mapper;
    private ConversationCompiler compiler;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        compiler = new ConversationCompiler(
                new TurnPolicyRegistry(),
                new TerminationConditionRegistry(),
                new EpistemicRuleRegistry(),
                new ConvergencePolicyRegistry(),
                null);
    }

    @Test
    void compilesMinimalConversation() throws Exception {
        var yaml = """
                type: conversation
                participants:
                  - agent:
                      type: worker
                      name: alice
                    role: analyst
                    systemPrompt: You are Alice.
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        var compiled = compiler.compile(conv);
        assertThat(compiled.participants()).hasSize(1);
        assertThat(compiled.participants().get(0).role()).isEqualTo("analyst");
        assertThat(compiled.participants().get(0).agentId()).isEqualTo("alice");
        assertThat(compiled.turnPolicy()).isNotNull();
        assertThat(compiled.termination()).isNull();
        assertThat(compiled.epistemicRule()).isNull();
        assertThat(compiled.convergencePolicy()).isNull();
    }

    @Test
    void compilesWithTurnPolicyAndTermination() throws Exception {
        var yaml = """
                type: conversation
                turnPolicy:
                  type: round-robin
                termination:
                  - type: max-iterations
                    iterations: 5
                participants:
                  - agent:
                      type: worker
                      name: a
                    role: r
                    systemPrompt: p
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        var compiled = compiler.compile(conv);
        assertThat(compiled.turnPolicy()).isInstanceOf(RoundRobinTurnPolicy.class);
        assertThat(compiled.termination()).isNotNull();
    }

    @Test
    void compilesWithEpistemicAndConvergence() throws Exception {
        var yaml = """
                type: conversation
                epistemicRule:
                  type: explicit-acknowledgement
                  minParticipants: 2
                convergencePolicy:
                  type: structural
                  similarityThreshold: 0.8
                  staleRounds: 3
                participants:
                  - agent:
                      type: worker
                      name: a
                    role: r
                    systemPrompt: p
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        var compiled = compiler.compile(conv);
        assertThat(compiled.epistemicRule()).isNotNull();
        assertThat(compiled.convergencePolicy()).isNotNull();
    }

    @Test
    void compilesMultipleParticipants() throws Exception {
        var yaml = """
                type: conversation
                participants:
                  - agent:
                      type: worker
                      name: alice
                    role: analyst
                    systemPrompt: Analyst prompt
                  - agent:
                      type: worker
                      name: bob
                    role: reviewer
                    systemPrompt: Reviewer prompt
                  - agent:
                      type: human
                      name: carol
                    role: supervisor
                    systemPrompt: Supervisor prompt
                """;
        var spec = mapper.readValue(yaml, ChannelExecutionStrategySpec.class);
        var conv = (ChannelExecutionStrategySpec.ConversationStrategySpec) spec;
        var compiled = compiler.compile(conv);
        assertThat(compiled.participants()).hasSize(3);
        assertThat(compiled.participants().get(2).agentId()).isEqualTo("carol");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=ConversationCompilerTest`
Expected: Compilation failure.

- [ ] **Step 3: Create CompiledConversation**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.conversation.ConvergencePolicy;
import io.casehub.blocks.conversation.EpistemicRule;
import io.casehub.blocks.conversation.orchestration.AgentParticipant;
import io.casehub.blocks.conversation.orchestration.TurnPolicy;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record CompiledConversation(
        TurnPolicy turnPolicy,
        @Nullable TerminationCondition<ConversationState> termination,
        List<AgentParticipant> participants,
        @Nullable EpistemicRule epistemicRule,
        @Nullable ConvergencePolicy convergencePolicy) {}
```

- [ ] **Step 4: Create ConversationCompiler**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.yaml.registry.*;
import io.casehub.blocks.agentic.yaml.spec.*;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.conversation.ConvergencePolicy;
import io.casehub.blocks.conversation.EpistemicRule;
import io.casehub.blocks.conversation.orchestration.*;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class ConversationCompiler {

    private final TurnPolicyRegistry turnPolicyRegistry;
    private final TerminationConditionRegistry terminationRegistry;
    private final EpistemicRuleRegistry epistemicRuleRegistry;
    private final ConvergencePolicyRegistry convergencePolicyRegistry;
    private final @Nullable ExpressionEngine expressionEngine;

    public ConversationCompiler(
            TurnPolicyRegistry turnPolicyRegistry,
            TerminationConditionRegistry terminationRegistry,
            EpistemicRuleRegistry epistemicRuleRegistry,
            ConvergencePolicyRegistry convergencePolicyRegistry,
            @Nullable ExpressionEngine expressionEngine) {
        this.turnPolicyRegistry = turnPolicyRegistry;
        this.terminationRegistry = terminationRegistry;
        this.epistemicRuleRegistry = epistemicRuleRegistry;
        this.convergencePolicyRegistry = convergencePolicyRegistry;
        this.expressionEngine = expressionEngine;
    }

    public CompiledConversation compile(
            ChannelExecutionStrategySpec.ConversationStrategySpec spec) {
        return new CompiledConversation(
                compileTurnPolicy(spec.turnPolicy()),
                compileTermination(spec.termination()),
                compileParticipants(spec.participants()),
                compileEpistemicRule(spec.epistemicRule()),
                compileConvergencePolicy(spec.convergencePolicy()));
    }

    private TurnPolicy compileTurnPolicy(@Nullable TurnPolicySpec spec) {
        if (spec == null) return new RoundRobinTurnPolicy();
        return turnPolicyRegistry.resolve(spec);
    }

    @SuppressWarnings("unchecked")
    private @Nullable TerminationCondition<ConversationState> compileTermination(
            @Nullable List<TerminationSpec> specs) {
        if (specs == null || specs.isEmpty()) return null;
        if (specs.size() == 1) {
            return terminationRegistry.resolve(specs.get(0), expressionEngine);
        }
        var conditions = specs.stream()
                .<TerminationCondition<ConversationState>>map(
                        s -> terminationRegistry.resolve(s, expressionEngine))
                .toList();
        return conditions.get(0).and(conditions.subList(1, conditions.size())
                .toArray(new TerminationCondition[0]));
    }

    private List<AgentParticipant> compileParticipants(
            List<AgentParticipantSpec> specs) {
        return specs.stream()
                .map(this::compileParticipant)
                .toList();
    }

    private AgentParticipant compileParticipant(AgentParticipantSpec spec) {
        AgentRef ref = resolveAgentRef(spec.agent());
        return new AgentParticipant(ref, spec.role(), spec.systemPrompt());
    }

    private AgentRef resolveAgentRef(AgentRefSpec spec) {
        return switch (spec) {
            case AgentRefSpec.Worker w -> new AgentRef.WorkerAgent(w.name(), w.description());
            case AgentRefSpec.Channel c -> new AgentRef.ChannelAgent(c.name(), c.description());
            case AgentRefSpec.Human h -> new AgentRef.HumanAgent(h.name(), h.description());
            case AgentRefSpec.External e -> new AgentRef.ExternalAgent(e.name(), e.description());
            case AgentRefSpec.Composed comp -> new AgentRef.ComposedAgent(comp.name(), comp.description());
        };
    }

    private @Nullable EpistemicRule compileEpistemicRule(
            @Nullable EpistemicRuleSpec spec) {
        if (spec == null) return null;
        return epistemicRuleRegistry.resolve(spec);
    }

    private @Nullable ConvergencePolicy compileConvergencePolicy(
            @Nullable ConvergencePolicySpec spec) {
        if (spec == null) return null;
        return convergencePolicyRegistry.resolve(spec);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=ConversationCompilerTest`
Expected: All 4 tests PASS.

- [ ] **Step 6: Run all agentic-yaml tests**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add agentic-yaml/src/
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#249): ConversationCompiler + CompiledConversation Refs #249"
```

---

## Batch 3: Coverage matrix + full build

### Task 5: Coverage matrix update + full build verification

**Files:**
- Modify: `docs/yaml-coverage.md` — update §4, §5, §16 status

- [ ] **Step 1: Update coverage matrix**

**§4 Conversation:** Change 4 Gap entries to Done:
- Conversation compiler → Done (#249)
- Agent participants → Done (#249)
- Conversation protocol config → Done (#249)
- Progress renderer types → Done (#249)

**§5 Negotiation:** Change all gap/spec-only entries:
- NegotiationSpec → Done (#249, was Spec only)
- AcceptedTermination → Done (#249)
- TerminalOutcomeTermination → Done (#249)
- DeadlineTermination → Done (#249)
- NegotiationProjection → Done (#249)

**§16 Channel:**
- ChannelBinding → Done (#249)
- ChannelExecutionStrategy → Partial (#249 — Conversation Done; FanIn/Barrier code-only)
- AgentParticipant → Done (#249)
- ConversationProtocol config → Done (#249)

Update summary table row counts and total. Recalculate coverage percentage.

- [ ] **Step 2: Run build of changed modules**

Run: `mvn --batch-mode test -pl agentic-yaml,agentic-yaml-deployment`
Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add docs/yaml-coverage.md
git -C /Users/mdproctor/claude/casehub/blocks commit -m "docs(#249): update coverage matrix §4, §5, §16 — channel infrastructure Done Refs #249"
```

---

## References

- [2026-09-11-channel-infrastructure-yaml-design.md] — design spec this plan implements
- [TerminationSpec.java] — sealed interface to extend with 3 negotiation variants
- [TerminationConditionRegistry.java] — registry resolve() switch to extend
- [NegotiationSpec.java] — existing spec record (spec-only → compiled)
- [NegotiationProjection.java] — runtime projection (parties + acceptance)
- [AcceptancePolicySpec.java] — existing acceptance policy spec
- [AgentParticipant.java] — runtime participant record
- [AgentRefSpec.java] — existing agent ref spec
- [ChannelBinding.java] — runtime channel binding
- [ChannelExecutionStrategy.java] — runtime sealed (Conversation/FanIn/Barrier)
- [ConversationSpec.java] — existing conversation spec
- [TurnPolicySpec.java] — existing turn policy spec
- [PatternCompiler.java] — agent ref resolution precedent
- [docs/yaml-coverage.md] §4, §5, §16 — gap inventory
- [GitHub #249] — focal issue
