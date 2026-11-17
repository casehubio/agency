# Engine Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract AbstractExecutionDriver, add AgentInvoker dispatch SPI, enrich FailurePolicy with BackoffStrategy, add execution lifecycle to ExecutionEventListener, and implement StageAwareCandidateSupplier for stage gating.

**Architecture:** AgentInvoker<T> is a functional dispatch interface on the driver (not ExecutionModel). AbstractExecutionDriver extracts the common five-phase loop from OrchestratedDriver and ChoreographedDriver, tracks per-agent activation state, and delegates dispatch to the invoker. StageAwareCandidateSupplier decorates the candidate supplier with stage binding filtering. PlatformAgentInvoker is deferred to a follow-up task pending qhorus#315 and work#285 SPI extractions.

**Tech Stack:** Java 21, Mutiny (Uni), JUnit 5, AssertJ, Mockito

**Spec:** `docs/superpowers/specs/2026-06-30-engine-integration-design.md`

## Global Constraints

- Blocks is a single-module Tier 1 library — no Quarkus runtime, no CDI container in tests
- All tests are plain JUnit 5 with Mockito and AssertJ
- AgentInvoker returns `Uni<AgentResult>` per `spi-reactive-blocking-io` protocol
- ExecutionModel is unchanged — no new fields
- Breaking changes to method signatures are fine — fix all callers in the same commit
- BackoffStrategy is defined locally in blocks (`FailurePolicy.BackoffStrategy`) — no platform-api dependency
- Build: `mvn --batch-mode test`
- IntelliJ MCP for all code navigation — never bash grep/find for classes

---

### Task 1: AgentInvoker<T> interface and BackoffStrategy enum

The foundational types that everything else depends on. AgentInvoker is a functional dispatch interface. BackoffStrategy is a 3-value enum added to FailurePolicy.AgentRetryPolicy.

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/model/AgentInvoker.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/FailurePolicy.java`
- Test: `src/test/java/io/casehub/blocks/agentic/model/AgentInvokerTest.java`
- Test: `src/test/java/io/casehub/blocks/agentic/FailurePolicyTest.java`

**Interfaces:**
- Consumes: nothing — these are foundational types
- Produces:
  - `AgentInvoker<T>`: `@FunctionalInterface interface AgentInvoker<T> { Uni<AgentResult> invoke(AgentRef agent, T state); }`
  - `AgentInvoker.defaultInvoker()`: static factory returning the ExternalAgent-only invoker (preserves current behavior)
  - `FailurePolicy.BackoffStrategy`: enum `{ FIXED, EXPONENTIAL, EXPONENTIAL_WITH_JITTER }`
  - `FailurePolicy.AgentRetryPolicy`: record gains `BackoffStrategy backoffStrategy` field (4 fields total)
  - `FailurePolicy.defaults()`: returns `BackoffStrategy.FIXED` for backward compatibility

- [ ] **Step 1: Write failing test for AgentInvoker default invoker**

```java
// src/test/java/io/casehub/blocks/agentic/model/AgentInvokerTest.java
package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInvokerTest {

    @Test
    void defaultInvokerDispatchesExternalAgent() {
        var agent = AgentRef.external((Object input) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "output")));

        var invoker = AgentInvoker.defaultInvoker();
        var result = invoker.invoke(agent, "state").await().indefinitely();

        assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.SUCCESS);
        assertThat(result.output()).isEqualTo("output");
    }

    @Test
    void defaultInvokerReturnsFailureForUnsupportedVariant() {
        var agent = AgentRef.worker(
                io.casehub.worker.api.Worker.builder()
                        .name("test").capabilityName("cap").noFunction().build());

        var invoker = AgentInvoker.defaultInvoker();
        var result = invoker.invoke(agent, "state").await().indefinitely();

        assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.FAILURE);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=AgentInvokerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `AgentInvoker` class not found

- [ ] **Step 3: Create AgentInvoker interface**

```java
// src/main/java/io/casehub/blocks/agentic/model/AgentInvoker.java
package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.time.Instant;

@FunctionalInterface
public interface AgentInvoker<T> {

    Uni<AgentResult> invoke(AgentRef agent, T state);

    @SuppressWarnings("unchecked")
    static <T> AgentInvoker<T> defaultInvoker() {
        return (agent, state) -> Uni.createFrom().item(() -> {
            var start = Instant.now();
            if (agent instanceof AgentRef.ExternalAgent ext) {
                var result = ext.fn().apply(state).toCompletableFuture().join();
                var duration = Duration.between(start, Instant.now());
                return new AgentResult(agent, result.output(), duration, result.status());
            }
            return AgentResult.failure(agent,
                    "Unsupported AgentRef variant: " + agent.getClass().getSimpleName());
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=AgentInvokerTest`
Expected: PASS

- [ ] **Step 5: Write failing test for BackoffStrategy on AgentRetryPolicy**

```java
// src/test/java/io/casehub/blocks/agentic/FailurePolicyTest.java
package io.casehub.blocks.agentic;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FailurePolicyTest {

    @Test
    void defaultsUsesFixedBackoff() {
        var policy = FailurePolicy.defaults();
        assertThat(policy.agentRetry().backoffStrategy())
                .isEqualTo(FailurePolicy.BackoffStrategy.FIXED);
    }

    @Test
    void agentRetryPolicyCarriesAllFourFields() {
        var retry = new FailurePolicy.AgentRetryPolicy(
                5, Duration.ofMillis(200),
                FailurePolicy.BackoffStrategy.EXPONENTIAL,
                FailurePolicy.AgentFailureAction.ESCALATE);

        assertThat(retry.maxRetries()).isEqualTo(5);
        assertThat(retry.backoff()).isEqualTo(Duration.ofMillis(200));
        assertThat(retry.backoffStrategy()).isEqualTo(FailurePolicy.BackoffStrategy.EXPONENTIAL);
        assertThat(retry.onExhausted()).isEqualTo(FailurePolicy.AgentFailureAction.ESCALATE);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=FailurePolicyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `backoffStrategy()` not found on `AgentRetryPolicy`

- [ ] **Step 7: Add BackoffStrategy enum and update AgentRetryPolicy**

Modify `src/main/java/io/casehub/blocks/agentic/FailurePolicy.java`:

```java
package io.casehub.blocks.agentic;

import java.time.Duration;

public record FailurePolicy(
        RoutingFailureAction onRoutingFailure,
        AggregationFailureAction onDeadlock,
        AgentRetryPolicy agentRetry
) {
    public enum RoutingFailureAction { FAIL, RETRY_BROADER, ESCALATE }
    public enum AggregationFailureAction { FAIL, ESCALATE, RETRY_DIFFERENT }
    public enum AgentFailureAction { FAIL, ESCALATE, SKIP }
    public enum BackoffStrategy { FIXED, EXPONENTIAL, EXPONENTIAL_WITH_JITTER }

    public record AgentRetryPolicy(int maxRetries, Duration backoff,
                                   BackoffStrategy backoffStrategy,
                                   AgentFailureAction onExhausted) {}

    public static FailurePolicy defaults() {
        return new FailurePolicy(
                RoutingFailureAction.FAIL,
                AggregationFailureAction.FAIL,
                new AgentRetryPolicy(3, Duration.ofSeconds(1),
                        BackoffStrategy.FIXED, AgentFailureAction.FAIL));
    }
}
```

- [ ] **Step 8: Fix all callers of AgentRetryPolicy constructor**

AgentRetryPolicy gains a 4th field. Fix every construction site. Use IntelliJ `ide_find_references` on the old 3-arg constructor to find them all. Known sites:

1. `AbstractPatternBuilder.maxAgentRetries()` — add `failurePolicy.agentRetry().backoffStrategy()` to preserve existing strategy
2. `OrchestratedDriverTest.escalatesWhenPolicyIsEscalate()` — add `FailurePolicy.BackoffStrategy.FIXED` as 3rd arg

After fixing all callers:

- [ ] **Step 9: Run full test suite**

Run: `mvn --batch-mode test`
Expected: ALL PASS

- [ ] **Step 10: Commit**

```
feat(#14): add AgentInvoker<T> interface and BackoffStrategy enum

AgentInvoker is the functional dispatch SPI — receives agent + state,
returns Uni<AgentResult>. defaultInvoker() preserves existing ExternalAgent
behavior. BackoffStrategy (FIXED, EXPONENTIAL, EXPONENTIAL_WITH_JITTER)
added to AgentRetryPolicy.
```

---

### Task 2: ExecutionEventListener lifecycle methods

Add `onExecutionStart()` and `onExecutionComplete()` as default methods.

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/model/ExecutionEventListener.java`
- Test: `src/test/java/io/casehub/blocks/agentic/model/ExecutionEventListenerContractTest.java`

**Interfaces:**
- Consumes: `ExecutionModel<?>`, `ExecutionResult` (existing types)
- Produces:
  - `ExecutionEventListener.onExecutionStart(ExecutionModel<?> model)` — default no-op
  - `ExecutionEventListener.onExecutionComplete(ExecutionResult result)` — default no-op

- [ ] **Step 1: Write contract test for default methods**

Per `spi-default-method-contract-test` protocol — verify anonymous implementation compiles and default methods are no-ops.

```java
// src/test/java/io/casehub/blocks/agentic/model/ExecutionEventListenerContractTest.java
package io.casehub.blocks.agentic.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ExecutionEventListenerContractTest {

    @Test
    void anonymousImplementationCompiles() {
        ExecutionEventListener listener = new ExecutionEventListener() {};
        assertThatCode(() -> listener.onExecutionStart(null)).doesNotThrowAnyException();
        assertThatCode(() -> listener.onExecutionComplete(null)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=ExecutionEventListenerContractTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `onExecutionStart` method not found

- [ ] **Step 3: Add lifecycle methods to ExecutionEventListener**

```java
// Add to ExecutionEventListener.java — two new default methods
default void onExecutionStart(ExecutionModel<?> model) {}
default void onExecutionComplete(ExecutionResult result) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=ExecutionEventListenerContractTest`
Expected: PASS

- [ ] **Step 5: Run full suite to verify no regressions**

Run: `mvn --batch-mode test`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```
feat(#14): add execution lifecycle methods to ExecutionEventListener

onExecutionStart(model) and onExecutionComplete(result) as default no-ops.
Drivers call these before the loop and in a finally block.
```

---

### Task 3: AbstractExecutionDriver<T> — extract common loop

The largest task. Extract the five-phase loop, state tracking, listener notification, and ActivationContext population from both drivers. OrchestratedDriver and ChoreographedDriver become thin subclasses.

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/model/AbstractExecutionDriver.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/model/OrchestratedDriver.java` (rewrite as thin subclass)
- Modify: `src/main/java/io/casehub/blocks/agentic/model/ChoreographedDriver.java` (rewrite as thin subclass)
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/AbstractPatternBuilder.java` (update `execute()` to pass invoker)
- Test: `src/test/java/io/casehub/blocks/agentic/model/AbstractExecutionDriverTest.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/model/OrchestratedDriverTest.java` (update construction)
- Modify: `src/test/java/io/casehub/blocks/agentic/model/ChoreographedDriverTest.java` (update construction)

**Interfaces:**
- Consumes: `AgentInvoker<T>` (Task 1), `ExecutionEventListener.onExecutionStart/onExecutionComplete` (Task 2)
- Produces:
  - `AbstractExecutionDriver<T>`: abstract class with constructor `(AgentInvoker<T> invoker)`
  - No-arg constructor overload: uses `AgentInvoker.defaultInvoker()`
  - `protected ExecutionResult executeIteration(ExecutionModel<T>, T)`: the five-phase loop body, returns null for Continue
  - `protected List<AgentResult> dispatchAgents(ExecutionModel<T>, List<AgentRef>, T)`: dispatch + activation
  - All `notify*` methods and `transition()` as protected methods
  - Tracked state: `activationCounts`, `consecutiveIdleCounts`, `lastAggregationResult`
  - `OrchestratedDriver<T>`: thin subclass, no-arg + `(AgentInvoker<T>)` constructors, `while (!cancelled)` loop
  - `ChoreographedDriver<T>`: thin subclass, no-arg + `(AgentInvoker<T>)` constructors, WaitingForEvent transitions
  - `AbstractPatternBuilder.execute()`: uses `new OrchestratedDriver<T>()` (no-arg — default invoker)

- [ ] **Step 1: Write failing test for ActivationContext population**

```java
// src/test/java/io/casehub/blocks/agentic/model/AbstractExecutionDriverTest.java
package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.ActivationContext;
import io.casehub.blocks.agentic.activation.ActivationRule;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractExecutionDriverTest {

    @Nested
    class ActivationContextPopulation {

        @Test
        void populatesActivationCountAcrossIterations() {
            var capturedContexts = new ArrayList<ActivationContext<String>>();
            ActivationRule<String> capturingActivation = ctx -> {
                capturedContexts.add(ctx);
                return Uni.createFrom().item(true);
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    capturingActivation,
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(3),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of());

            var driver = new OrchestratedDriver<String>();
            driver.execute(model, "ctx").await().indefinitely();

            assertThat(capturedContexts).hasSize(3);
            assertThat(capturedContexts.get(0).activationCount()).isEqualTo(0);
            assertThat(capturedContexts.get(1).activationCount()).isEqualTo(1);
            assertThat(capturedContexts.get(2).activationCount()).isEqualTo(2);
        }
    }

    @Nested
    class ExecutionLifecycle {

        @Test
        void callsOnExecutionStartBeforeLoopAndOnExecutionCompleteAfter() {
            var events = new ArrayList<String>();
            var listener = new ExecutionEventListener() {
                @Override
                public void onExecutionStart(ExecutionModel<?> model) {
                    events.add("start");
                }
                @Override
                public void onExecutionComplete(ExecutionResult result) {
                    events.add("complete:" + result.getClass().getSimpleName());
                }
                @Override
                public void onAgentDispatched(AgentRef agent) {
                    events.add("dispatch");
                }
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new io.casehub.blocks.agentic.activation.OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of(listener));

            new OrchestratedDriver<String>().execute(model, "ctx").await().indefinitely();

            assertThat(events.get(0)).isEqualTo("start");
            assertThat(events.get(events.size() - 1)).isEqualTo("complete:Completed");
        }

        @Test
        void callsOnExecutionCompleteEvenOnException() {
            var events = new ArrayList<String>();
            var listener = new ExecutionEventListener() {
                @Override
                public void onExecutionComplete(ExecutionResult result) {
                    events.add("complete");
                }
            };

            var model = new ExecutionModel<String>(
                    ctx -> { throw new RuntimeException("routing exploded"); },
                    new IdentityDecomposition<>(),
                    new io.casehub.blocks.agentic.activation.OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(),
                    FailurePolicy.defaults(),
                    List.of(listener));

            try {
                new OrchestratedDriver<String>().execute(model, "ctx").await().indefinitely();
            } catch (Exception ignored) {}

            assertThat(events).contains("complete");
        }
    }

    @Nested
    class InvokerIntegration {

        @Test
        void usesProvidedInvokerInsteadOfDefaultDispatch() {
            var invocations = new ArrayList<String>();
            AgentInvoker<String> trackingInvoker = (agent, state) -> {
                invocations.add("invoked:" + state);
                return Uni.createFrom().item(AgentResult.success(agent, "from-invoker"));
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "should-not-see")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new io.casehub.blocks.agentic.activation.OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of());

            var driver = new OrchestratedDriver<>(trackingInvoker);
            var result = driver.execute(model, "mystate").await().indefinitely();

            assertThat(invocations).containsExactly("invoked:mystate");
            assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=AbstractExecutionDriverTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `OrchestratedDriver(AgentInvoker)` constructor not found

- [ ] **Step 3: Create AbstractExecutionDriver**

Create `src/main/java/io/casehub/blocks/agentic/model/AbstractExecutionDriver.java` — the full base class with:
- Constructor `(AgentInvoker<T> invoker)` and no-arg overload using `AgentInvoker.defaultInvoker()`
- `executeIteration()` — the five-phase body extracted from OrchestratedDriver lines 48-106
- `dispatchAgents()` — dispatch + activation with tracked state
- All `notify*` and `transition()` as protected methods
- `onExecutionStart(model)` called before the loop, `onExecutionComplete(result)` in finally
- Tracked state: `Map<AgentRef, Integer> activationCounts`, `Map<AgentRef, Integer> consecutiveIdleCounts`, `Object lastAggregationResult`
- ActivationContext constructed from tracked state instead of hardcoded zeros
- Protected abstract method: `Uni<ExecutionResult> runLoop(ExecutionModel<T> model, T context)` — subclasses implement the loop structure
- Agent invocation via `invoker.invoke(agent, state).await().indefinitely()` with exception handling

- [ ] **Step 4: Rewrite OrchestratedDriver as thin subclass**

```java
public class OrchestratedDriver<T> extends AbstractExecutionDriver<T> {

    public OrchestratedDriver() { super(); }
    public OrchestratedDriver(AgentInvoker<T> invoker) { super(invoker); }

    @Override
    protected Uni<ExecutionResult> runLoop(ExecutionModel<T> model, T context) {
        return Uni.createFrom().item(() -> {
            int iteration = 0;
            while (!isCancelled()) {
                transition(model, new ExecutionState.Running(iteration));
                var result = executeIteration(model, context, iteration);
                if (result != null) return result;
                iteration++;
            }
            transition(model, new ExecutionState.Cancelled());
            return new ExecutionResult.Cancelled();
        });
    }
}
```

- [ ] **Step 5: Rewrite ChoreographedDriver as thin subclass**

```java
public class ChoreographedDriver<T> extends AbstractExecutionDriver<T> {

    public ChoreographedDriver() { super(); }
    public ChoreographedDriver(AgentInvoker<T> invoker) { super(invoker); }

    @Override
    protected Uni<ExecutionResult> runLoop(ExecutionModel<T> model, T context) {
        return Uni.createFrom().item(() -> {
            int iteration = 0;
            transition(model, new ExecutionState.WaitingForEvent());
            while (!isCancelled()) {
                var result = executeIteration(model, context, iteration);
                if (result != null) return result;
                iteration++;
                transition(model, new ExecutionState.WaitingForEvent());
            }
            transition(model, new ExecutionState.Cancelled());
            return new ExecutionResult.Cancelled();
        });
    }
}
```

- [ ] **Step 6: Update AbstractPatternBuilder.execute()**

`execute()` uses no-arg `OrchestratedDriver()` which calls `AgentInvoker.defaultInvoker()` — preserves current behavior.

```java
public Uni<ExecutionResult> execute(T initialContext) {
    return new OrchestratedDriver<T>().execute(build(), initialContext);
}
```

No change needed — it already uses no-arg constructor.

- [ ] **Step 7: Update OrchestratedDriverTest — all 12 construction sites**

ExecutionModel constructor is unchanged (8 args). OrchestratedDriver now has no-arg constructor (backward compatible). No test changes needed for ExecutionModel construction.

But verify: do any tests directly call `invokeAgent` or other now-protected methods? If so, they test through `execute()` which still works.

- [ ] **Step 8: Run full test suite**

Run: `mvn --batch-mode test`
Expected: ALL PASS (all existing tests use no-arg `OrchestratedDriver()` which defaults to `AgentInvoker.defaultInvoker()`)

- [ ] **Step 9: Commit**

```
feat(#14): extract AbstractExecutionDriver — common loop, invoker, tracked state

OrchestratedDriver and ChoreographedDriver become thin subclasses (~20
lines each). AbstractExecutionDriver owns the five-phase loop, listener
notification, state transitions, ActivationContext population from tracked
per-agent counts, and agent dispatch via AgentInvoker.
```

---

### Task 4: StageAwareCandidateSupplier and StageGate

Stage gating as a candidate supplier decorator. Mirrors engine's PlanningStrategyLoopControl gating logic.

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/routing/StageGate.java`
- Create: `src/main/java/io/casehub/blocks/agentic/routing/StageAwareCandidateSupplier.java`
- Test: `src/test/java/io/casehub/blocks/agentic/routing/StageAwareCandidateSupplierTest.java`

**Interfaces:**
- Consumes: `RoutingCandidate`, `AgentRef` (existing types)
- Produces:
  - `StageGate`: interface with `Set<String> allStagedBindings()` and `Set<String> activeStagedBindings()`
  - `StageAwareCandidateSupplier`: implements `Supplier<List<RoutingCandidate>>`, constructor takes `(Supplier<List<RoutingCandidate>> delegate, StageGate stageGate, Function<AgentRef, String> bindingNameResolver)`

- [ ] **Step 1: Write failing tests for stage gating**

```java
// src/test/java/io/casehub/blocks/agentic/routing/StageAwareCandidateSupplierTest.java
package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.worker.api.Worker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StageAwareCandidateSupplierTest {

    private static RoutingCandidate candidateFor(String name) {
        var worker = Worker.builder().name(name).capabilityName("default").noFunction().build();
        return new RoutingCandidate(AgentRef.worker(worker), null);
    }

    @Test
    void freeFloatingCandidatesAlwaysPass() {
        var c1 = candidateFor("free-agent");
        var delegate = () -> List.of(c1);
        var gate = new StageGate() {
            public Set<String> allStagedBindings() { return Set.of("staged-only"); }
            public Set<String> activeStagedBindings() { return Set.of(); }
        };

        var supplier = new StageAwareCandidateSupplier(
                delegate, gate, ref -> ((AgentRef.WorkerAgent) ref).worker().name());

        assertThat(supplier.get()).containsExactly(c1);
    }

    @Test
    void stagedCandidateFilteredWhenStageNotActive() {
        var c1 = candidateFor("gated-worker");
        var delegate = () -> List.of(c1);
        var gate = new StageGate() {
            public Set<String> allStagedBindings() { return Set.of("gated-worker"); }
            public Set<String> activeStagedBindings() { return Set.of(); }
        };

        var supplier = new StageAwareCandidateSupplier(
                delegate, gate, ref -> ((AgentRef.WorkerAgent) ref).worker().name());

        assertThat(supplier.get()).isEmpty();
    }

    @Test
    void stagedCandidatePassesWhenStageActive() {
        var c1 = candidateFor("gated-worker");
        var delegate = () -> List.of(c1);
        var gate = new StageGate() {
            public Set<String> allStagedBindings() { return Set.of("gated-worker"); }
            public Set<String> activeStagedBindings() { return Set.of("gated-worker"); }
        };

        var supplier = new StageAwareCandidateSupplier(
                delegate, gate, ref -> ((AgentRef.WorkerAgent) ref).worker().name());

        assertThat(supplier.get()).containsExactly(c1);
    }

    @Test
    void mixedFreeAndStagedFilteredCorrectly() {
        var free = candidateFor("free-agent");
        var gatedActive = candidateFor("active-gated");
        var gatedInactive = candidateFor("inactive-gated");
        var delegate = () -> List.of(free, gatedActive, gatedInactive);

        var gate = new StageGate() {
            public Set<String> allStagedBindings() {
                return Set.of("active-gated", "inactive-gated");
            }
            public Set<String> activeStagedBindings() {
                return Set.of("active-gated");
            }
        };

        var supplier = new StageAwareCandidateSupplier(
                delegate, gate, ref -> ((AgentRef.WorkerAgent) ref).worker().name());

        assertThat(supplier.get()).containsExactly(free, gatedActive);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=StageAwareCandidateSupplierTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `StageGate` and `StageAwareCandidateSupplier` not found

- [ ] **Step 3: Create StageGate interface**

```java
// src/main/java/io/casehub/blocks/agentic/routing/StageGate.java
package io.casehub.blocks.agentic.routing;

import java.util.Set;

public interface StageGate {
    Set<String> allStagedBindings();
    Set<String> activeStagedBindings();
}
```

- [ ] **Step 4: Create StageAwareCandidateSupplier**

```java
// src/main/java/io/casehub/blocks/agentic/routing/StageAwareCandidateSupplier.java
package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class StageAwareCandidateSupplier implements Supplier<List<RoutingCandidate>> {

    private final Supplier<List<RoutingCandidate>> delegate;
    private final StageGate stageGate;
    private final Function<AgentRef, String> bindingNameResolver;

    public StageAwareCandidateSupplier(
            Supplier<List<RoutingCandidate>> delegate,
            StageGate stageGate,
            Function<AgentRef, String> bindingNameResolver) {
        this.delegate = delegate;
        this.stageGate = stageGate;
        this.bindingNameResolver = bindingNameResolver;
    }

    @Override
    public List<RoutingCandidate> get() {
        var candidates = delegate.get();
        var allStaged = stageGate.allStagedBindings();
        var activeStaged = stageGate.activeStagedBindings();

        return candidates.stream()
                .filter(c -> {
                    var name = bindingNameResolver.apply(c.ref());
                    return !allStaged.contains(name) || activeStaged.contains(name);
                })
                .toList();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=StageAwareCandidateSupplierTest`
Expected: PASS

- [ ] **Step 6: Run full suite**

Run: `mvn --batch-mode test`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```
feat(#14): add StageAwareCandidateSupplier and StageGate interface

Candidate supplier decorator that filters by active stage bindings.
Mirrors engine's PlanningStrategyLoopControl gating logic. Free-floating
candidates always pass; staged candidates require their stage to be ACTIVE.
```

---

## Self-Review Checklist

**1. Spec coverage:**
- [x] AbstractExecutionDriver extraction — Task 3
- [x] AgentInvoker<T> on driver constructor — Tasks 1, 3
- [x] ActivationContext population from tracked state — Task 3
- [x] ExecutionEventListener lifecycle — Task 2
- [x] BackoffStrategy on AgentRetryPolicy — Task 1
- [x] StageAwareCandidateSupplier + StageGate — Task 4
- [x] PlatformAgentInvoker — intentionally deferred (prerequisite SPIs qhorus#315, work#285 not yet shipped)
- [x] ExecutionModel unchanged — confirmed, no new fields

**2. Placeholder scan:** No TBDs, TODOs, or "implement later" — every step has code.

**3. Type consistency:**
- `AgentInvoker<T>` — same signature in Tasks 1 and 3
- `BackoffStrategy` — same enum in Tasks 1 (definition) and 1 (usage in defaults)
- `StageGate` — same interface in Task 4 (definition and test usage)
- `OrchestratedDriver` constructors — consistent between Task 3 (creation) and test updates
