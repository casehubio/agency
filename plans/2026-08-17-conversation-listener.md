# ConversationListener SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #125 — feat: round listener SPI on ConversationOrchestrator — per-round state callback
**Issue group:** #125

**Goal:** Add a `ConversationListener` callback SPI to `ConversationOrchestrator` that fires after every termination check, giving consumers per-dispatch access to conversation state.

**Architecture:** Single `@FunctionalInterface` (`ConversationListener`) wired as an optional nullable field on `ConversationOrchestrator`. The existing 9-arg constructor is preserved via delegation to a new 10-arg constructor. The listener fires synchronously after `terminationCondition.evaluate()` in the `converse()` loop.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Mockito, Mutiny

## Global Constraints

- No new dependencies
- Source-compatible — existing 9-arg constructor callers unchanged
- No CDI beans — pure constructor wiring like other orchestration SPIs

---

## Batch 1: ConversationListener SPI

### Task 1: ConversationListener interface, orchestrator wiring, and tests

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/conversation/orchestration/ConversationListener.java`
- Modify: `blocks/src/main/java/io/casehub/blocks/conversation/orchestration/ConversationOrchestrator.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/conversation/orchestration/ConversationOrchestratorTest.java`

**Interfaces:**
- Produces: `ConversationListener` — `@FunctionalInterface` with `void onDispatch(ConversationState, TerminationDecision, int, Duration)`
- Produces: `ConversationOrchestrator` 10-arg constructor accepting `@Nullable ConversationListener` as last parameter

- [ ] **Step 1: Create `ConversationListener` interface**

Create `blocks/src/main/java/io/casehub/blocks/conversation/orchestration/ConversationListener.java`:

```java
package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationState;

import java.time.Duration;

@FunctionalInterface
public interface ConversationListener {
    void onDispatch(ConversationState state, TerminationDecision decision,
                    int dispatchCount, Duration elapsed);
}
```

- [ ] **Step 2: Write failing test — listener receives all dispatches**

Add a test-internal record and the first test to `ConversationOrchestratorTest`:

```java
record DispatchEvent(ConversationState state, TerminationDecision decision,
                     int dispatchCount, Duration elapsed) {}

@Test
void listener_receivesAllDispatches() {
    var observationService = createObservationService();
    var dispatched = new ArrayList<MessageView>();
    var callCount = new AtomicInteger();
    var events = new ArrayList<DispatchEvent>();

    AgentInvoker<String> invoker = (agent, prompt) -> {
        callCount.incrementAndGet();
        return Uni.createFrom().item(AgentResult.success(agent, "resp"));
    };

    var alice = new AgentParticipant(
            AgentRef.external("alice", i -> null), "REV", "");
    var bob = new AgentParticipant(
            AgentRef.external("bob", i -> null), "IMP", "");

    ResponseMessageBuilder responseBuilder = (agent, result, state) ->
            mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\nresp",
                    callCount.get() + 100);

    var orchestrator = new ConversationOrchestrator(
            projection, observationService,
            new RoundRobinTurnPolicy(),
            maxIterations(4),
            invoker,
            (agent, drain, state) -> "",
            responseBuilder,
            dispatched::add,
            List.of(alice, bob),
            (state, decision, count, elapsed) ->
                    events.add(new DispatchEvent(state, decision, count, elapsed))
    );

    var trigger = mockMessage("human", "Start", 0);
    orchestrator.converse(trigger).await().indefinitely();

    assertThat(events).hasSize(4);
    assertThat(events.stream().map(DispatchEvent::dispatchCount).toList())
            .containsExactly(1, 2, 3, 4);
}
```

Add `import java.time.Duration;` to the test file imports.

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=ConversationOrchestratorTest#listener_receivesAllDispatches`
Expected: FAIL — compilation error, no 10-arg constructor on `ConversationOrchestrator`

- [ ] **Step 4: Modify `ConversationOrchestrator` — add listener field, constructors, and fire point**

In `ConversationOrchestrator.java`:

**4a. Add import and field:**

Add `import org.jspecify.annotations.Nullable;` to imports.

Add field after the `participants` field:

```java
private final @Nullable ConversationListener listener;
```

**4b. Convert existing 9-arg constructor to delegating constructor:**

Replace the existing constructor body with delegation:

```java
public ConversationOrchestrator(
        ConversationProjection projection,
        PartitionedObservationService<MessageView, String> observationService,
        TurnPolicy turnPolicy,
        TerminationCondition<ConversationState> terminationCondition,
        AgentInvoker<String> agentInvoker,
        PromptAssembler promptAssembler,
        ResponseMessageBuilder responseBuilder,
        Consumer<MessageView> responseDispatcher,
        List<AgentParticipant> participants) {
    this(projection, observationService, turnPolicy, terminationCondition,
            agentInvoker, promptAssembler, responseBuilder, responseDispatcher,
            participants, null);
}
```

**4c. Add new 10-arg constructor with listener (move the original body here):**

```java
public ConversationOrchestrator(
        ConversationProjection projection,
        PartitionedObservationService<MessageView, String> observationService,
        TurnPolicy turnPolicy,
        TerminationCondition<ConversationState> terminationCondition,
        AgentInvoker<String> agentInvoker,
        PromptAssembler promptAssembler,
        ResponseMessageBuilder responseBuilder,
        Consumer<MessageView> responseDispatcher,
        List<AgentParticipant> participants,
        @Nullable ConversationListener listener) {
    this.projection = projection;
    this.observationService = observationService;
    this.turnPolicy = turnPolicy;
    this.terminationCondition = terminationCondition;
    this.agentInvoker = agentInvoker;
    this.promptAssembler = promptAssembler;
    this.responseBuilder = responseBuilder;
    this.responseDispatcher = responseDispatcher;
    this.participants = List.copyOf(participants);
    this.listener = listener;

    for (var p : this.participants) {
        observationService.addObserver(p.agentId(), p.agentId());
    }
}
```

**4d. Add listener fire point in `converse()` method:**

Replace the termination check block (lines 127-135):

```java
                    var termCtx = new TerminationContext<>(
                            state, dispatchCount,
                            Duration.between(start, Instant.now()),
                            List.copyOf(allResults));
                    finalDecision = terminationCondition.evaluate(termCtx);

                    if (!(finalDecision instanceof TerminationDecision.Continue)) {
                        break;
                    }
```

With:

```java
                    var elapsed = Duration.between(start, Instant.now());
                    var termCtx = new TerminationContext<>(
                            state, dispatchCount, elapsed,
                            List.copyOf(allResults));
                    finalDecision = terminationCondition.evaluate(termCtx);

                    if (listener != null) {
                        listener.onDispatch(state, finalDecision,
                                dispatchCount, elapsed);
                    }

                    if (!(finalDecision instanceof TerminationDecision.Continue)) {
                        break;
                    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest=ConversationOrchestratorTest#listener_receivesAllDispatches`
Expected: PASS

- [ ] **Step 6: Write test — listener receives final termination decision**

```java
@Test
void listener_receivesFinalTerminationDecision() {
    var observationService = createObservationService();
    var callCount = new AtomicInteger();
    var events = new ArrayList<DispatchEvent>();

    AgentInvoker<String> invoker = (agent, prompt) -> {
        callCount.incrementAndGet();
        return Uni.createFrom().item(AgentResult.success(agent, "resp"));
    };

    var alice = new AgentParticipant(
            AgentRef.external("alice", i -> null), "REV", "");

    ResponseMessageBuilder responseBuilder = (agent, result, state) ->
            mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\nresp",
                    callCount.get() + 100);

    TerminationCondition<ConversationState> stopAfterTwo = ctx -> {
        if (ctx.iterationCount() >= 2) {
            return new TerminationDecision.Complete("Done");
        }
        return TerminationDecision.Continue.INSTANCE;
    };

    var orchestrator = new ConversationOrchestrator(
            projection, observationService,
            new RoundRobinTurnPolicy(),
            stopAfterTwo,
            invoker,
            (agent, drain, state) -> "",
            responseBuilder,
            msg -> {},
            List.of(alice),
            (state, decision, count, elapsed) ->
                    events.add(new DispatchEvent(state, decision, count, elapsed))
    );

    var trigger = mockMessage("human", "Start", 0);
    orchestrator.converse(trigger).await().indefinitely();

    assertThat(events).isNotEmpty();
    var lastEvent = events.getLast();
    assertThat(lastEvent.decision())
            .isInstanceOf(TerminationDecision.Complete.class);
    assertThat(((TerminationDecision.Complete) lastEvent.decision()).result())
            .isEqualTo("Done");
}
```

Run: `mvn --batch-mode test -pl blocks -Dtest=ConversationOrchestratorTest#listener_receivesFinalTerminationDecision`
Expected: PASS

- [ ] **Step 7: Write test — listener not called on agent failure**

```java
@Test
void listener_notCalledOnAgentFailure() {
    var observationService = createObservationService();
    var events = new ArrayList<DispatchEvent>();

    var alice = new AgentParticipant(
            AgentRef.external("alice", i -> null), "REV", "");

    AgentInvoker<String> invoker = (agent, prompt) ->
            Uni.createFrom().item(AgentResult.failure(agent, "LLM error"));

    var orchestrator = new ConversationOrchestrator(
            projection, observationService,
            new FreeTurnPolicy(),
            maxIterations(10),
            invoker,
            (agent, drain, state) -> "",
            (agent, result, state) -> mockMessage(agent.agentId(), "resp", 999),
            msg -> {},
            List.of(alice),
            (state, decision, count, elapsed) ->
                    events.add(new DispatchEvent(state, decision, count, elapsed))
    );

    var trigger = mockMessage("human", "Start", 0);
    var outcome = orchestrator.converse(trigger).await().indefinitely();

    assertThat(outcome.dispatchCount()).isEqualTo(1);
    assertThat(events).isEmpty();
}
```

Run: `mvn --batch-mode test -pl blocks -Dtest=ConversationOrchestratorTest#listener_notCalledOnAgentFailure`
Expected: PASS

- [ ] **Step 8: Write test — null listener backward compatibility**

```java
@Test
void nullListener_noNPE() {
    var observationService = createObservationService();
    var dispatched = new ArrayList<MessageView>();
    var callCount = new AtomicInteger();

    AgentInvoker<String> invoker = (agent, prompt) -> {
        callCount.incrementAndGet();
        return Uni.createFrom().item(AgentResult.success(agent, "resp"));
    };

    var alice = new AgentParticipant(
            AgentRef.external("alice", i -> null), "REV", "");

    ResponseMessageBuilder responseBuilder = (agent, result, state) ->
            mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\nresp",
                    callCount.get() + 100);

    var orchestrator = new ConversationOrchestrator(
            projection, observationService,
            new RoundRobinTurnPolicy(),
            maxIterations(2),
            invoker,
            (agent, drain, state) -> "",
            responseBuilder,
            dispatched::add,
            List.of(alice)
    );

    var trigger = mockMessage("human", "Start", 0);
    var outcome = orchestrator.converse(trigger).await().indefinitely();

    assertThat(outcome.dispatchCount()).isEqualTo(2);
}
```

Run: `mvn --batch-mode test -pl blocks -Dtest=ConversationOrchestratorTest#nullListener_noNPE`
Expected: PASS

- [ ] **Step 9: Write test — elapsed monotonically increasing**

```java
@Test
void listener_elapsedMonotonicallyIncreasing() {
    var observationService = createObservationService();
    var callCount = new AtomicInteger();
    var events = new ArrayList<DispatchEvent>();

    AgentInvoker<String> invoker = (agent, prompt) -> {
        callCount.incrementAndGet();
        return Uni.createFrom().item(AgentResult.success(agent, "resp"));
    };

    var alice = new AgentParticipant(
            AgentRef.external("alice", i -> null), "REV", "");

    ResponseMessageBuilder responseBuilder = (agent, result, state) ->
            mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\nresp",
                    callCount.get() + 100);

    var orchestrator = new ConversationOrchestrator(
            projection, observationService,
            new RoundRobinTurnPolicy(),
            maxIterations(3),
            invoker,
            (agent, drain, state) -> "",
            responseBuilder,
            msg -> {},
            List.of(alice),
            (state, decision, count, elapsed) ->
                    events.add(new DispatchEvent(state, decision, count, elapsed))
    );

    var trigger = mockMessage("human", "Start", 0);
    orchestrator.converse(trigger).await().indefinitely();

    assertThat(events).hasSizeGreaterThanOrEqualTo(2);
    for (int i = 1; i < events.size(); i++) {
        assertThat(events.get(i).elapsed())
                .isGreaterThanOrEqualTo(events.get(i - 1).elapsed());
    }
}
```

Run: `mvn --batch-mode test -pl blocks -Dtest=ConversationOrchestratorTest#listener_elapsedMonotonicallyIncreasing`
Expected: PASS

- [ ] **Step 10: Run full test suite**

Run: `mvn --batch-mode test -pl blocks`
Expected: All tests PASS (existing tests unchanged, new tests green)

- [ ] **Step 11: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/conversation/orchestration/ConversationListener.java
git add blocks/src/main/java/io/casehub/blocks/conversation/orchestration/ConversationOrchestrator.java
git add blocks/src/test/java/io/casehub/blocks/conversation/orchestration/ConversationOrchestratorTest.java
git commit -m "feat(#125): add ConversationListener SPI for per-dispatch state callbacks

Refs #125"
```

## References

- [2026-08-17-conversation-listener-design.md] — design spec this plan implements
- [ConversationOrchestrator.java:127-135] — termination check block modified for listener fire point
- [ConversationOrchestrator.java:38-61] — constructor modified for listener wiring
- [ExecutionEventListener.java] — existing listener pattern (referenced, not reused)
- [ConversationOrchestratorTest.java] — existing test patterns reused
- [GitHub #125] — focal issue
- [GitHub fsitrading#26] — downstream consumer
