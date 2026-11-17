# Routing Enrichment & Accountability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enrich the RoutingCandidate→RoutingContext→RoutingDecision→ExecutionEventListener data flow with task descriptions, state rendering, reason preservation, judge-driven debate convergence, HTN pre-decomposition, and persistent audit listeners.

**Architecture:** Three layers build on each other: (1) data model enrichment on ExecutionModel, RoutingCandidate, RoutingDecision; (2) pattern wiring for debate judge and HTN root task; (3) accountability listeners for EventLog and ledger audit. Type parameter T propagates typed from execution entry point through all SPIs — serialization to String happens only at the LLM API boundary.

**Tech Stack:** Java 21, Mutiny (Uni), JUnit 5, Mockito, AssertJ, JSpecify annotations

**Spec:** `docs/superpowers/specs/2026-07-01-routing-enrichment-accountability-design.md`

## Global Constraints

- Blocks depends on engine-api, eidos-api, worker-api, work-api at compile. Engine/qhorus at test only.
- No Quarkus runtime in tests — plain JUnit 5 + Mockito.
- `@Nullable` = `org.jspecify.annotations.Nullable` (JSpecify 1.0.0). New dependency.
- Package `agentic.listener` for listener classes. `agentic.termination` for JudgeConvergence.
- All existing pattern builder tests must keep passing after each task.
- Run `mvn --batch-mode test` after each task to verify no regressions.

---

### Task 1: ExecutionModel task field + null validation + JSpecify dependency

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/io/casehub/blocks/agentic/model/ExecutionModel.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/AbstractPatternBuilder.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/model/OrchestratedDriverTest.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/model/ChoreographedDriverTest.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/model/AbstractExecutionDriverTest.java`
- Test: `src/test/java/io/casehub/blocks/agentic/model/ExecutionModelTest.java` (new)

**Interfaces:**
- Produces: `ExecutionModel<T>` with 9-field constructor (+ `task` as 9th field), null validation on all fields
- Produces: `AbstractPatternBuilder.task(String)` setter, default `"execution"`

- [ ] **Step 1: Add JSpecify dependency to pom.xml**

Add to the dependencies section (compile scope):

```xml
<dependency>
  <groupId>org.jspecify</groupId>
  <artifactId>jspecify</artifactId>
  <version>1.0.0</version>
</dependency>
```

- [ ] **Step 2: Write failing tests for ExecutionModel null validation**

Create `src/test/java/io/casehub/blocks/agentic/model/ExecutionModelTest.java`:

```java
package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionModelTest {

    private final Supplier<List<RoutingCandidate>> candidates = List::of;

    @Test
    void rejectsNullRouting() {
        assertThatThrownBy(() -> new ExecutionModel<>(
                null, new IdentityDecomposition<>(), new OnExplicitDispatch<>(),
                new PassThrough<>(), new MaxIterationsTermination<>(1),
                candidates, FailurePolicy.defaults(), List.of(), "test"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("routing");
    }

    @Test
    void rejectsNullTask() {
        assertThatThrownBy(() -> new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), candidates,
                FailurePolicy.defaults(), List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("task");
    }

    @Test
    void acceptsValidConstruction() {
        var model = new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), candidates,
                FailurePolicy.defaults(), List.of(), "my task");
        assertThat(model.task()).isEqualTo("my task");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=ExecutionModelTest`
Expected: compilation failure — ExecutionModel doesn't accept 9 args yet.

- [ ] **Step 4: Implement ExecutionModel changes**

Modify `src/main/java/io/casehub/blocks/agentic/model/ExecutionModel.java` — add `task` field as 9th parameter, add null checks for all fields:

```java
package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.ActivationRule;
import io.casehub.blocks.agentic.aggregation.AggregationStrategy;
import io.casehub.blocks.agentic.decomposition.DecompositionStrategy;
import io.casehub.blocks.agentic.routing.RoutingStrategy;
import io.casehub.blocks.agentic.termination.TerminationCondition;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public record ExecutionModel<T>(
        RoutingStrategy<T> routing,
        DecompositionStrategy<T> decomposition,
        ActivationRule<T> activation,
        AggregationStrategy<T> aggregation,
        TerminationCondition<T> termination,
        Supplier<List<RoutingCandidate>> candidateSupplier,
        FailurePolicy failurePolicy,
        List<ExecutionEventListener> listeners,
        String task
) {
    public ExecutionModel {
        Objects.requireNonNull(routing, "routing");
        Objects.requireNonNull(decomposition, "decomposition");
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(aggregation, "aggregation");
        Objects.requireNonNull(termination, "termination");
        Objects.requireNonNull(candidateSupplier, "candidateSupplier");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        listeners = List.copyOf(listeners);
        Objects.requireNonNull(task, "task");
    }
}
```

- [ ] **Step 5: Update AbstractPatternBuilder — add task field and update build()**

Modify `src/main/java/io/casehub/blocks/agentic/pattern/AbstractPatternBuilder.java`:

Add field `protected String task = "execution";` after the `listeners` field (around line 32).

Add setter method after the `listener()` method:

```java
public B task(String task) {
    this.task = task;
    return (B) this;
}
```

Update `build()` method to pass the 9th field:

```java
public ExecutionModel<T> build() {
    return new ExecutionModel<>(routing, decomposition, activation,
            aggregation, termination, candidateSupplier,
            failurePolicy, listeners, task);
}
```

- [ ] **Step 6: Set default task names in pattern builder constructors**

In each builder's constructor, add `this.task = "<name>";` as the first line:

- `SupervisorBuilder`: `this.task = "supervisor";`
- `DebateBuilder`: `this.task = "debate";`
- `HtnBuilder`: `this.task = "htn";`
- `SequenceBuilder`: `this.task = "sequence";`
- `LoopBuilder`: `this.task = "loop";`
- `ParallelBuilder`: `this.task = "parallel";`
- `VotingBuilder`: `this.task = "vote";`
- `ConditionalBuilder`: `this.task = "conditional";`

- [ ] **Step 7: Update ALL existing test direct ExecutionModel constructions**

Every `new ExecutionModel<String>(` in test files needs a 9th arg `"test"`. There are ~13 direct constructions across three test files:

- `OrchestratedDriverTest.java` — add `"test"` as 9th arg to each `new ExecutionModel<>()` call
- `ChoreographedDriverTest.java` — add `"test"` as 9th arg
- `AbstractExecutionDriverTest.java` — add `"test"` as 9th arg to each `new ExecutionModel<>()` call

Find each `new ExecutionModel<String>(` and add `, "test"` before the closing `)`. The pattern is:

```java
// Before:
var model = new ExecutionModel<String>(routing, decomp, activation,
        aggregation, termination, candidates, FailurePolicy.defaults(), List.of(listener));
// After:
var model = new ExecutionModel<String>(routing, decomp, activation,
        aggregation, termination, candidates, FailurePolicy.defaults(), List.of(listener), "test");
```

- [ ] **Step 8: Run all tests**

Run: `mvn --batch-mode test`
Expected: ALL tests pass including new ExecutionModelTest and all existing tests.

- [ ] **Step 9: Commit**

```
feat(#8): add task field to ExecutionModel with null validation

ExecutionModel<T> gains a 9th field `task` — a natural-language description
of what the execution is accomplishing. Compact constructor now validates
all SPI fields with Objects.requireNonNull.

AbstractPatternBuilder exposes task(String) setter; each pattern builder
sets a sensible default. JSpecify dependency added for @Nullable annotations.
```

---

### Task 2: RoutingCandidate + RoutingDecision — data model enrichment

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/RoutingCandidate.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/routing/RoutingDecision.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/RoutingCandidateTest.java`
- Test: `src/test/java/io/casehub/blocks/agentic/routing/RoutingDecisionTest.java` (new)

**Interfaces:**
- Produces: `RoutingCandidate(AgentRef ref, @Nullable AgentDescriptor descriptor)` with null-check on ref
- Produces: `RoutingDecision.Selected(List<AgentRef> agents, @Nullable String reason)` with convenience constructor

- [ ] **Step 1: Write failing tests**

Add to `RoutingCandidateTest.java`:

```java
@Test
void rejectsNullRef() {
    assertThatThrownBy(() -> new RoutingCandidate(null, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("ref");
}

@Test
void acceptsNullDescriptor() {
    var candidate = new RoutingCandidate(
            AgentRef.external(x -> CompletableFuture.completedFuture(AgentResult.success(null, "ok"))),
            null);
    assertThat(candidate.descriptor()).isNull();
}
```

Create `src/test/java/io/casehub/blocks/agentic/routing/RoutingDecisionTest.java`:

```java
package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingDecisionTest {

    @Test
    void selectedCarriesReason() {
        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var selected = new RoutingDecision.Selected(List.of(agent), "best domain match");
        assertThat(selected.reason()).isEqualTo("best domain match");
        assertThat(selected.agents()).hasSize(1);
    }

    @Test
    void selectedConvenienceConstructorHasNullReason() {
        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var selected = new RoutingDecision.Selected(List.of(agent));
        assertThat(selected.reason()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest="RoutingCandidateTest,RoutingDecisionTest"`
Expected: compilation failure — RoutingCandidate has no null-check, Selected has no reason field.

- [ ] **Step 3: Implement RoutingCandidate changes**

```java
package io.casehub.blocks.agentic;

import io.casehub.eidos.api.AgentDescriptor;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A candidate for agent routing — pairs a dispatch reference with an optional identity profile.
 *
 * @param ref        the agent dispatch reference — never null
 * @param descriptor the agent's identity profile for routing metadata.
 *                   Nullable — pattern builders that accept bare {@link AgentRef}
 *                   create candidates without descriptors. Routing strategies
 *                   must handle null descriptors gracefully.
 */
public record RoutingCandidate(AgentRef ref, @Nullable AgentDescriptor descriptor) {
    public RoutingCandidate {
        Objects.requireNonNull(ref, "ref");
    }
}
```

- [ ] **Step 4: Implement RoutingDecision.Selected changes**

```java
package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface RoutingDecision
        permits RoutingDecision.Selected, RoutingDecision.Unresolvable,
                RoutingDecision.Escalate {

    record Selected(List<AgentRef> agents, @Nullable String reason) implements RoutingDecision {
        public Selected { agents = List.copyOf(agents); }
        public Selected(List<AgentRef> agents) { this(agents, null); }
    }

    record Unresolvable(String reason) implements RoutingDecision {}
    record Escalate(String reason) implements RoutingDecision {}
}
```

- [ ] **Step 5: Update existing code that constructs Selected**

All existing `RoutingDecision.Selected` constructions use `new RoutingDecision.Selected(List.of(...))` — the convenience constructor handles these without changes. Verify by searching for `Selected(` in routing implementations:

- `FirstMatchRouting.java` — uses `new RoutingDecision.Selected(List.of(...))`  ✓ (convenience)
- `RoundRobinRouting.java` — same ✓
- `SequentialRouting.java` — same ✓
- `LlmSelectedRouting.java` — same ✓ (will add reason in Task 4)

- [ ] **Step 6: Run all tests**

Run: `mvn --batch-mode test`
Expected: ALL tests pass.

- [ ] **Step 7: Commit**

```
feat(#8,#11): @Nullable on RoutingCandidate.descriptor, reason field on RoutingDecision.Selected

RoutingCandidate now null-checks ref and annotates descriptor with @Nullable.
RoutingDecision.Selected gains a reason field for LLM routing rationale
preservation. Non-LLM strategies use the convenience constructor (null reason).
```

---

### Task 3: Driver infrastructure — model.task(), agentName(), FQN fixes

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/model/AbstractExecutionDriver.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/model/ExecutionEventListener.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/model/OrchestratedDriver.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/model/ChoreographedDriver.java`
- Test: `src/test/java/io/casehub/blocks/agentic/model/ExecutionEventListenerTest.java` (new)

**Interfaces:**
- Produces: `ExecutionEventListener.agentName(AgentRef)` — static naming utility
- Produces: `AbstractExecutionDriver` uses `model.task()` in RoutingContext

- [ ] **Step 1: Write failing test for agentName**

Create `src/test/java/io/casehub/blocks/agentic/model/ExecutionEventListenerTest.java`:

```java
package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerFunction;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionEventListenerTest {

    @Test
    void agentNameReturnsWorkerName() {
        var worker = Worker.builder()
                .name("clinical-reviewer")
                .capabilityName("review")
                .function(WorkerFunction.NONE)
                .build();
        assertThat(ExecutionEventListener.agentName(AgentRef.worker(worker)))
                .isEqualTo("clinical-reviewer");
    }

    @Test
    void agentNameReturnsChannelId() {
        var id = UUID.randomUUID();
        var agent = AgentRef.channel(id, null);
        assertThat(ExecutionEventListener.agentName(agent))
                .isEqualTo("channel:" + id);
    }

    @Test
    void agentNameDistinguishesExternalAgents() {
        var a = AgentRef.external(x -> CompletableFuture.completedFuture(AgentResult.success(null, "a")));
        var b = AgentRef.external(x -> CompletableFuture.completedFuture(AgentResult.success(null, "b")));
        var nameA = ExecutionEventListener.agentName(a);
        var nameB = ExecutionEventListener.agentName(b);
        assertThat(nameA).startsWith("external:");
        assertThat(nameB).startsWith("external:");
        assertThat(nameA).isNotEqualTo(nameB);
    }

    @Test
    void agentNameReturnsHuman() {
        assertThat(ExecutionEventListener.agentName(AgentRef.human(null)))
                .isEqualTo("human");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=ExecutionEventListenerTest`
Expected: compilation failure — `agentName` doesn't exist on ExecutionEventListener.

- [ ] **Step 3: Add agentName to ExecutionEventListener**

Add at the bottom of `ExecutionEventListener.java`, before the closing `}`:

```java
    static String agentName(AgentRef agent) {
        if (agent instanceof AgentRef.WorkerAgent w) return w.worker().name();
        if (agent instanceof AgentRef.ChannelAgent c) return "channel:" + c.channelId();
        if (agent instanceof AgentRef.ExternalAgent e) return "external:" + Integer.toHexString(System.identityHashCode(e));
        if (agent instanceof AgentRef.HumanAgent) return "human";
        if (agent instanceof AgentRef.ComposedAgent) return "composed";
        return agent.getClass().getSimpleName();
    }
```

Add import: `import io.casehub.blocks.agentic.AgentRef;` (if not present).

- [ ] **Step 4: Update AbstractExecutionDriver to use model.task()**

In `AbstractExecutionDriver.java`, line 95, change:

```java
// Before:
var routingCtx = new RoutingContext<>("task", candidates, context);
// After:
var routingCtx = new RoutingContext<>(model.task(), candidates, context);
```

- [ ] **Step 5: Fix FQN imports in OrchestratedDriver and ChoreographedDriver**

In `OrchestratedDriver.java` line 36, change:
```java
// Before:
var allResults = new ArrayList<io.casehub.blocks.agentic.AgentResult>();
// After:
var allResults = new ArrayList<AgentResult>();
```
Add import: `import io.casehub.blocks.agentic.AgentResult;`

Same change in `ChoreographedDriver.java` line 43.

- [ ] **Step 6: Run all tests**

Run: `mvn --batch-mode test`
Expected: ALL tests pass.

- [ ] **Step 7: Commit**

```
feat(#8): agentName utility on ExecutionEventListener, model.task() in routing context

ExecutionEventListener gains static agentName(AgentRef) for consistent agent
naming across listeners. AbstractExecutionDriver uses model.task() instead of
hard-coded "task" literal. FQN imports fixed in both drivers.
```

---

### Task 4: LlmSelectedRouting — state rendering, enriched cards, reason extraction

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/routing/LlmSelectedRouting.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/SupervisorBuilder.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/routing/LlmSelectedRoutingTest.java`
- Test: `src/test/java/io/casehub/blocks/agentic/pattern/SupervisorBuilderTest.java` (existing — add tests)

**Interfaces:**
- Consumes: `RoutingDecision.Selected(agents, reason)` from Task 2
- Produces: `LlmSelectedRouting(AgentProvider, Function<T, String>)` constructor
- Produces: `SupervisorBuilder.stateRenderer(Function<T, String>)` setter

- [ ] **Step 1: Write failing tests for LlmSelectedRouting enrichment**

Add to `LlmSelectedRoutingTest.java`:

```java
@Test
void includesStateInPromptViaRenderer() {
    // Setup: routing with a custom state renderer
    var promptCapture = new AtomicReference<String>();
    var provider = createCapturingProvider(promptCapture);
    var routing = new LlmSelectedRouting<Map<String, Object>>(provider,
            state -> "completed: " + state.get("done"));

    var candidate = new RoutingCandidate(
            AgentRef.external(x -> completedFuture(AgentResult.success(null, "ok"))),
            null);
    var context = new RoutingContext<>("review code",
            List.of(candidate), Map.of("done", "static analysis"));

    routing.route(context).await().indefinitely();

    assertThat(promptCapture.get()).contains("Current state:");
    assertThat(promptCapture.get()).contains("completed: static analysis");
}

@Test
void includesWorkerDescriptionInAgentCard() {
    var promptCapture = new AtomicReference<String>();
    var provider = createCapturingProvider(promptCapture);
    var routing = new LlmSelectedRouting<String>(provider);

    var worker = Worker.builder()
            .name("code-reviewer")
            .capabilityName("review")
            .function(WorkerFunction.NONE)
            .description("Reviews code for bugs, style, and security issues")
            .build();
    var candidate = new RoutingCandidate(AgentRef.worker(worker), null);
    var context = new RoutingContext<>("review PR", List.of(candidate), "state");

    routing.route(context).await().indefinitely();

    assertThat(promptCapture.get()).contains("Reviews code for bugs, style, and security issues");
}

@Test
void extractsReasonFromLlmResponse() {
    var provider = createProviderReturning(
            "{\"agent\": \"reviewer\", \"reason\": \"best domain match for Java code\"}");
    var routing = new LlmSelectedRouting<String>(provider);

    var candidate = new RoutingCandidate(
            AgentRef.external(x -> completedFuture(AgentResult.success(null, "ok"))),
            AgentDescriptor.builder().agentId("a1").name("reviewer").slot("review").tenancyId("t1").build());
    var context = new RoutingContext<>("task", List.of(candidate), "state");

    var decision = routing.route(context).await().indefinitely();

    assertThat(decision).isInstanceOf(RoutingDecision.Selected.class);
    var selected = (RoutingDecision.Selected) decision;
    assertThat(selected.reason()).isEqualTo("best domain match for Java code");
}
```

The helper methods `createCapturingProvider` and `createProviderReturning` mock the AgentProvider to capture the prompt or return a fixed response. Implement them using a mock AgentProvider that captures the user message from AgentSessionConfig.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=LlmSelectedRoutingTest`
Expected: compilation failure — new constructor doesn't exist, extractReason doesn't exist.

- [ ] **Step 3: Implement LlmSelectedRouting changes**

Full rewrite of `LlmSelectedRouting.java` with:
- `Function<T, String> stateRenderer` constructor parameter
- Convenience constructor defaulting to `Object::toString`
- `buildUserPrompt` includes state via renderer
- `buildAgentCard` includes `Worker.description` from WorkerAgent
- `parseSelection` extracts reason via `extractReason`
- New `extractReason(String text)` method

Key changes (see spec §4 for full code):
- Constructor: `public LlmSelectedRouting(AgentProvider agentProvider, Function<T, String> stateRenderer)`
- In `buildUserPrompt`: add state section using `stateRenderer.apply(context.state())`
- In `buildAgentCard`: check `candidate.ref() instanceof AgentRef.WorkerAgent w` for description
- In `parseSelection`: call `extractReason(text)` and pass to `new RoutingDecision.Selected(agents, reason)`
- New method `extractReason(String text)` — same parsing pattern as `extractAgentName` but for "reason" field

- [ ] **Step 4: Implement SupervisorBuilder stateRenderer setter**

In `SupervisorBuilder.java`, add field and setter:

```java
private AgentProvider agentProvider;
private Function<T, String> stateRenderer;
```

Store agentProvider in the existing constructor:

```java
public SupervisorBuilder(AgentProvider agentProvider) {
    this();
    this.agentProvider = agentProvider;
    this.routing = new LlmSelectedRouting<>(agentProvider);
}
```

Add setter that eagerly applies:

```java
public SupervisorBuilder<T> stateRenderer(Function<T, String> stateRenderer) {
    this.stateRenderer = stateRenderer;
    if (agentProvider != null) {
        this.routing = new LlmSelectedRouting<>(agentProvider, stateRenderer);
    }
    return this;
}
```

Add import for `java.util.function.Function`.

- [ ] **Step 5: Write SupervisorBuilder stateRenderer test**

Add to `SupervisorBuilderTest.java`:

```java
@Test
void stateRendererThenRouteUsesCustomRoute() {
    var customRouting = new FirstMatchRouting<String>(c -> true);
    var model = Patterns.<String>supervisor(mockProvider)
            .stateRenderer(Object::toString)
            .route(customRouting)
            .agents(agent)
            .build();
    assertThat(model.routing()).isSameAs(customRouting);
}

@Test
void routeThenStateRendererUsesLlmRouting() {
    var customRouting = new FirstMatchRouting<String>(c -> true);
    var model = Patterns.<String>supervisor(mockProvider)
            .route(customRouting)
            .stateRenderer(Object::toString)
            .agents(agent)
            .build();
    assertThat(model.routing()).isInstanceOf(LlmSelectedRouting.class);
}
```

- [ ] **Step 6: Run all tests**

Run: `mvn --batch-mode test`
Expected: ALL tests pass.

- [ ] **Step 7: Commit**

```
feat(#11): LlmSelectedRouting state rendering, enriched agent cards, reason extraction

LlmSelectedRouting<T> gains Function<T, String> stateRenderer — typed state
converted to String at the LLM API boundary only. Agent cards now include
Worker.description. Reason field extracted from LLM JSON response and carried
on RoutingDecision.Selected.

SupervisorBuilder exposes stateRenderer() setter with eager application
(last-writer-wins with route()).
```

---

### Task 5: JudgeConvergence + DebateBuilder — debate pattern wiring

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/termination/JudgeConvergence.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/DebateBuilder.java`
- Test: `src/test/java/io/casehub/blocks/agentic/termination/JudgeConvergenceTest.java` (new)
- Modify: `src/test/java/io/casehub/blocks/agentic/pattern/DebateBuilderTest.java`

**Interfaces:**
- Consumes: `AgentInvoker<T>` from `agentic.model`
- Consumes: `TerminationContext<T>` from `agentic.termination`
- Produces: `JudgeConvergence<T> implements TerminationCondition<T>`

- [ ] **Step 1: Write failing test for JudgeConvergence**

Create `src/test/java/io/casehub/blocks/agentic/termination/JudgeConvergenceTest.java`:

```java
package io.casehub.blocks.agentic.termination;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgeConvergenceTest {

    @Test
    void convergesWhenJudgeReturnsSuccess() {
        var judge = AgentRef.external((List<AgentResult> results) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "CONVERGED")));

        var termination = new JudgeConvergence<String>(judge, 10);
        var context = new TerminationContext<>("state", 2, Duration.ZERO, List.of(
                AgentResult.success(null, "position A"),
                AgentResult.success(null, "position B")));

        var decision = termination.evaluate(context).await().indefinitely();
        assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
        assertThat(((TerminationDecision.Complete) decision).result()).isEqualTo("CONVERGED");
    }

    @Test
    void continuesWhenJudgeReturnsDeclined() {
        var judge = AgentRef.external((List<AgentResult> results) ->
                CompletableFuture.completedFuture(AgentResult.declined(null, "still diverging")));

        var termination = new JudgeConvergence<String>(judge, 10);
        var context = new TerminationContext<>("state", 1, Duration.ZERO, List.of());

        var decision = termination.evaluate(context).await().indefinitely();
        assertThat(decision).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void safetyCapTerminatesAtMaxIterations() {
        var judge = AgentRef.external((List<AgentResult> results) ->
                CompletableFuture.completedFuture(AgentResult.declined(null, "never converges")));

        var termination = new JudgeConvergence<String>(judge, 3);
        var context = new TerminationContext<>("state", 3, Duration.ZERO, List.of());

        var decision = termination.evaluate(context).await().indefinitely();
        assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void convenienceConstructorRejectsNonExternalAgent() {
        var worker = io.casehub.worker.api.Worker.builder()
                .name("judge").capabilityName("judge")
                .function(io.casehub.worker.api.WorkerFunction.NONE).build();
        assertThatThrownBy(() -> new JudgeConvergence<>(AgentRef.worker(worker), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=JudgeConvergenceTest`
Expected: compilation failure — JudgeConvergence doesn't exist.

- [ ] **Step 3: Implement JudgeConvergence**

Create `src/main/java/io/casehub/blocks/agentic/termination/JudgeConvergence.java` — full implementation as shown in spec §5.

- [ ] **Step 4: Write failing DebateBuilder tests**

Add to `DebateBuilderTest.java`:

```java
@Test
void judgeTerminatesDebateOnConvergence() {
    var log = new ArrayList<String>();
    var optimist = AgentRef.external((Object in) -> {
        log.add("optimist");
        return CompletableFuture.completedFuture(AgentResult.success(null, "approve"));
    });
    var pessimist = AgentRef.external((Object in) -> {
        log.add("pessimist");
        return CompletableFuture.completedFuture(AgentResult.success(null, "approve"));
    });
    var judge = AgentRef.external((List<AgentResult> results) -> {
        log.add("judge");
        return CompletableFuture.completedFuture(AgentResult.success(null, "CONVERGED"));
    });

    var result = Patterns.<Object>debate()
            .debaters(optimist, pessimist)
            .judge(judge)
            .maxRounds(10)
            .task("review PR")
            .execute("review")
            .await().indefinitely();

    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    assertThat(log).contains("optimist", "pessimist", "judge");
}

@Test
void judgeAndConvergenceAreMutuallyExclusive() {
    var judge = AgentRef.external((List<AgentResult> r) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    assertThatThrownBy(() -> Patterns.debate()
            .judge(judge)
            .convergence(new MaxIterationsTermination<>(5))
            .build())
            .isInstanceOf(IllegalStateException.class);
}

@Test
void maxRoundsAfterConvergenceClearsFlag() {
    var judge = AgentRef.external((List<AgentResult> r) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    // Should not throw — maxRounds clears convergenceExplicitlySet
    var model = Patterns.debate()
            .convergence(new MaxIterationsTermination<>(5))
            .maxRounds(10)
            .judge(judge)
            .build();
    assertThat(model.termination()).isInstanceOf(JudgeConvergence.class);
}
```

- [ ] **Step 5: Implement DebateBuilder changes**

Full rewrite of `DebateBuilder.java` with:
- Default aggregation `CollectAll` instead of `PassThrough`
- `convergenceExplicitlySet` flag
- `convergence()` sets the flag
- `maxRounds()` clears the flag
- `build()` override: check mutual exclusivity, wire judge into JudgeConvergence

See spec §5 for full code.

- [ ] **Step 6: Run all tests**

Run: `mvn --batch-mode test`
Expected: ALL tests pass.

- [ ] **Step 7: Commit**

```
feat(#8): wire DebateBuilder.judge() via JudgeConvergence termination

JudgeConvergence<T> invokes a judge agent with accumulated debate results
and maps the response to continue/converge. DebateBuilder.build() wires
the judge when set, enforces judge/convergence mutual exclusivity, and
defaults aggregation to CollectAll.
```

---

### Task 6: HtnBuilder — root task wiring via pre-decomposition

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/HtnBuilder.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/pattern/HtnBuilderTest.java`

**Interfaces:**
- Consumes: `TaskNode<T>`, `DecompositionMethod<T>`, `DecompositionContext<T>` from `agentic.decomposition`
- Consumes: `SequentialRouting<T>` from `agentic.routing`
- Produces: `HtnBuilder.rootTask(TaskNode<T>)` setter (renamed from `task()`)

- [ ] **Step 1: Write failing HTN test**

Add to `HtnBuilderTest.java`:

```java
@Test
void decomposesAndExecutesTaskTreeInOrder() {
    var log = new ArrayList<String>();
    var build = AgentRef.external((Object in) -> {
        log.add("build");
        return CompletableFuture.completedFuture(AgentResult.success(null, "built"));
    });
    var test = AgentRef.external((Object in) -> {
        log.add("test");
        return CompletableFuture.completedFuture(AgentResult.success(null, "tested"));
    });
    var deploy = AgentRef.external((Object in) -> {
        log.add("deploy");
        return CompletableFuture.completedFuture(AgentResult.success(null, "deployed"));
    });

    var rootTask = new TaskNode.CompoundTask<>("deploy-app", List.of(
            new DecompositionMethod<>(
                    state -> true,
                    (compound, ctx) -> Uni.createFrom().item(List.of(
                            new TaskNode.PrimitiveTask<>(build, s -> true, s -> {}),
                            new TaskNode.PrimitiveTask<>(test, s -> true, s -> {}),
                            new TaskNode.PrimitiveTask<>(deploy, s -> true, s -> {}))))));

    var result = Patterns.<String>htn()
            .rootTask(rootTask)
            .task("deploy application")
            .execute("deploy v2.0")
            .await().indefinitely();

    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    assertThat(log).containsExactly("build", "test", "deploy");
}

@Test
void guardSelectsDecompositionMethodBasedOnState() {
    var log = new ArrayList<String>();
    var hotfix = AgentRef.external((Object in) -> {
        log.add("hotfix");
        return CompletableFuture.completedFuture(AgentResult.success(null, "applied"));
    });
    var fullDeploy = AgentRef.external((Object in) -> {
        log.add("full-deploy");
        return CompletableFuture.completedFuture(AgentResult.success(null, "deployed"));
    });

    var rootTask = new TaskNode.CompoundTask<String>("deploy", List.of(
            new DecompositionMethod<>(
                    (String s) -> s.contains("hotfix"),
                    (compound, ctx) -> Uni.createFrom().item(List.of(
                            new TaskNode.PrimitiveTask<>(hotfix, s -> true, s -> {})))),
            new DecompositionMethod<>(
                    (String s) -> true,
                    (compound, ctx) -> Uni.createFrom().item(List.of(
                            new TaskNode.PrimitiveTask<>(fullDeploy, s -> true, s -> {}))))));

    var result = Patterns.<String>htn()
            .rootTask(rootTask)
            .task("deploy")
            .execute("hotfix for bug #99")
            .await().indefinitely();

    assertThat(log).containsExactly("hotfix");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=HtnBuilderTest`
Expected: compilation failure — `rootTask()` method doesn't exist.

- [ ] **Step 3: Implement HtnBuilder changes**

Full rewrite of `HtnBuilder.java` with:
- Rename `task(TaskNode<T>)` to `rootTask(TaskNode<T>)`
- Default routing → `SequentialRouting`
- Default aggregation → `CollectAll`
- Override `execute()` for pre-decomposition without mutating builder state
- Private `flatten()` method

See spec §6 for full code.

- [ ] **Step 4: Run all tests**

Run: `mvn --batch-mode test`
Expected: ALL tests pass.

- [ ] **Step 5: Commit**

```
feat(#8): wire HtnBuilder.rootTask() via pre-decomposition at execute()

HtnBuilder.task() renamed to rootTask() (avoids collision with
AbstractPatternBuilder.task(String)). execute() eagerly decomposes the task
tree using initial state, extracts PrimitiveTask agents as candidates, and
wires sequential execution. Builder state is not mutated — execute() builds
a local model.
```

---

### Task 7: Accountability listeners — OrchestrationEventType, EventLogListener, LedgerExecutionListener

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/listener/OrchestrationEventType.java`
- Create: `src/main/java/io/casehub/blocks/agentic/listener/EventLogListener.java`
- Create: `src/main/java/io/casehub/blocks/agentic/listener/LedgerExecutionListener.java`
- Test: `src/test/java/io/casehub/blocks/agentic/listener/EventLogListenerTest.java` (new)
- Test: `src/test/java/io/casehub/blocks/agentic/listener/LedgerExecutionListenerTest.java` (new)

**Interfaces:**
- Consumes: `ExecutionEventListener` from `agentic.model`
- Consumes: `RoutingDecision.Selected.reason()` from Task 2
- Consumes: `ExecutionEventListener.agentName()` from Task 3
- Produces: `EventLogListener.EventSink` functional interface
- Produces: `LedgerExecutionListener.LedgerSink` functional interface
- Produces: `OrchestrationEventType` enum

- [ ] **Step 1: Write failing EventLogListener tests**

Create `src/test/java/io/casehub/blocks/agentic/listener/EventLogListenerTest.java`:

```java
package io.casehub.blocks.agentic.listener;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class EventLogListenerTest {

    @Test
    void recordsRoutingDecisionWithReason() {
        var recorded = new ArrayList<Map.Entry<OrchestrationEventType, Map<String, Object>>>();
        var listener = new EventLogListener((type, payload) ->
                recorded.add(Map.entry(type, payload)));

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var selected = new RoutingDecision.Selected(List.of(agent), "domain match");

        listener.onRoutingDecision(selected, List.of());

        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getKey()).isEqualTo(OrchestrationEventType.ROUTING_DECISION);
        assertThat(recorded.get(0).getValue()).containsEntry("reason", "domain match");
    }

    @Test
    void recordsAgentResultWithOutputSummary() {
        var recorded = new ArrayList<Map.Entry<OrchestrationEventType, Map<String, Object>>>();
        var listener = new EventLogListener((type, payload) ->
                recorded.add(Map.entry(type, payload)));

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var result = new AgentResult(agent, "analysis complete", Duration.ofMillis(250),
                AgentResult.AgentResultStatus.SUCCESS);

        listener.onAgentResult(result);

        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getKey()).isEqualTo(OrchestrationEventType.AGENT_RESULT);
        assertThat(recorded.get(0).getValue())
                .containsEntry("status", "SUCCESS")
                .containsEntry("outputType", "String")
                .containsEntry("outputSummary", "analysis complete");
    }

    @Test
    void recordsAllEventTypes() {
        var types = new ArrayList<OrchestrationEventType>();
        var listener = new EventLogListener((type, payload) -> types.add(type));

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var model = io.casehub.blocks.agentic.pattern.Patterns.<String>supervisor()
                .agents(agent).task("test").build();

        listener.onExecutionStart(model);
        listener.onRoutingDecision(new RoutingDecision.Selected(List.of(agent)), List.of());
        listener.onActivation(agent, true);
        listener.onAgentDispatched(agent);
        listener.onAgentResult(AgentResult.success(agent, "ok"));
        listener.onAggregation(new io.casehub.blocks.agentic.aggregation.AggregationResult.Resolved(List.of()));
        listener.onTermination(new io.casehub.blocks.agentic.termination.TerminationDecision.Complete("done"));
        listener.onExecutionComplete(new io.casehub.blocks.agentic.model.ExecutionResult.Completed("done"));

        assertThat(types).containsExactly(
                OrchestrationEventType.EXECUTION_STARTED,
                OrchestrationEventType.ROUTING_DECISION,
                OrchestrationEventType.ACTIVATION_EVALUATED,
                OrchestrationEventType.AGENT_DISPATCHED,
                OrchestrationEventType.AGENT_RESULT,
                OrchestrationEventType.AGGREGATION_COMPLETED,
                OrchestrationEventType.TERMINATION_EVALUATED,
                OrchestrationEventType.EXECUTION_COMPLETED);
    }
}
```

- [ ] **Step 2: Write failing LedgerExecutionListener tests**

Create `src/test/java/io/casehub/blocks/agentic/listener/LedgerExecutionListenerTest.java`:

```java
package io.casehub.blocks.agentic.listener;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerExecutionListenerTest {

    record LedgerRecord(OrchestrationEventType type, String actorId,
                        String actorRole, Map<String, Object> data) {}

    @Test
    void carriesActorIdentityOnEveryEntry() {
        var records = new ArrayList<LedgerRecord>();
        var listener = new LedgerExecutionListener(
                (type, actorId, role, data) ->
                        records.add(new LedgerRecord(type, actorId, role, data)),
                "supervisor-123");

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        listener.onRoutingDecision(
                new RoutingDecision.Selected(List.of(agent), "reason"), List.of());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).actorId()).isEqualTo("supervisor-123");
        assertThat(records.get(0).actorRole()).isEqualTo("orchestration-router");
    }

    @Test
    void recordsEscalationReasonOnCompletion() {
        var records = new ArrayList<LedgerRecord>();
        var listener = new LedgerExecutionListener(
                (type, actorId, role, data) ->
                        records.add(new LedgerRecord(type, actorId, role, data)),
                "supervisor-123");

        listener.onExecutionComplete(
                new io.casehub.blocks.agentic.model.ExecutionResult.Escalated("human needed"));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).data()).containsEntry("reason", "human needed");
    }

    @Test
    void recordsAgentResultWithOutputSummary() {
        var records = new ArrayList<LedgerRecord>();
        var listener = new LedgerExecutionListener(
                (type, actorId, role, data) ->
                        records.add(new LedgerRecord(type, actorId, role, data)),
                "supervisor-123");

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var result = new AgentResult(agent, "analysis done",
                java.time.Duration.ofMillis(100), AgentResult.AgentResultStatus.SUCCESS);
        listener.onAgentResult(result);

        assertThat(records.get(0).data()).containsEntry("outputType", "String");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest="EventLogListenerTest,LedgerExecutionListenerTest"`
Expected: compilation failure — classes don't exist.

- [ ] **Step 4: Create OrchestrationEventType**

Create `src/main/java/io/casehub/blocks/agentic/listener/OrchestrationEventType.java`:

```java
package io.casehub.blocks.agentic.listener;

/**
 * Orchestration-level event types produced by blocks' execution drivers.
 * Maps to {@code CaseHubEventType} at the EventLog sink — the mapping is owned
 * by the sink implementation, not by blocks. When engine#626 adds orchestration
 * types to {@code CaseHubEventType}, the sink maps directly.
 */
public enum OrchestrationEventType {
    EXECUTION_STARTED,
    ROUTING_DECISION,
    ACTIVATION_EVALUATED,
    AGENT_DISPATCHED,
    AGENT_RESULT,
    AGENT_FAILED,
    AGGREGATION_COMPLETED,
    TERMINATION_EVALUATED,
    EXECUTION_COMPLETED
}
```

- [ ] **Step 5: Create EventLogListener**

Create `src/main/java/io/casehub/blocks/agentic/listener/EventLogListener.java` — full implementation as shown in spec §7 (including output summary in onAgentResult and truncate helper).

- [ ] **Step 6: Create LedgerExecutionListener**

Create `src/main/java/io/casehub/blocks/agentic/listener/LedgerExecutionListener.java` — full implementation as shown in spec §7 (including output summary in onAgentResult and truncate helper).

- [ ] **Step 7: Run all tests**

Run: `mvn --batch-mode test`
Expected: ALL tests pass.

- [ ] **Step 8: Commit**

```
feat(#12): EventLogListener and LedgerExecutionListener for orchestration audit

OrchestrationEventType enum defines blocks-local event taxonomy.
EventLogListener records all events for operational observability via
consumer-provided EventSink. LedgerExecutionListener records the full
decision-dispatch-result chain for EU AI Act Art.12 compliance via
consumer-provided LedgerSink. Both record agent output summaries and
carry routing reasons from RoutingDecision.Selected.
```

---

## Self-Review Checklist

- [x] Spec §1 (ExecutionModel task + null) → Task 1
- [x] Spec §2 (RoutingCandidate @Nullable) → Task 2
- [x] Spec §3 (RoutingDecision reason) → Task 2
- [x] Spec §4 (LlmSelectedRouting state/cards/reason) → Task 4
- [x] Spec §4 (SupervisorBuilder stateRenderer) → Task 4
- [x] Spec §5 (JudgeConvergence + DebateBuilder) → Task 5
- [x] Spec §6 (HtnBuilder rootTask) → Task 6
- [x] Spec §7 (OrchestrationEventType + EventLogListener + LedgerExecutionListener) → Task 7
- [x] Spec §8 (FQN imports) → Task 3
- [x] Spec §8 (#8.2 no action) → documented in spec, no task
- [x] AbstractExecutionDriver model.task() → Task 3
- [x] ExecutionEventListener agentName() → Task 3
- [x] Pattern builder task defaults → Task 1
- [x] All new types in correct packages → verified
- [x] All tests are TDD (write failing test → implement → verify)
- [x] No placeholders, TODOs, or vague steps
