# Supervisor Channel Observation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #97 — Supervisor observation via ChannelProjection for aggregation/termination
**Issue group:** #97

**Goal:** Provide a composable, consumer-facing observation API (`ChannelObserver<S>`) that lets execution drivers observe inter-agent channel communication via projections and use the projected state in termination/aggregation decisions.

**Architecture:** `ChannelObserver<S>` implements both qhorus `MessageObserver` and blocks `EventSource`. It folds channel messages through a `ChannelProjection<S>` via `AtomicReference.updateAndGet()`, wakes ChoreographedDriver via DriverEvent signals, and provides factory methods for creating `TerminationCondition<T>` instances that read from the projected state. Additionally, `TerminationCondition<T>` gains `or()` and `and()` default combinator methods with priority-aware semantics (Escalate > Failed > Complete > Continue).

**Tech Stack:** Java 21, JUnit 5, AssertJ, Awaitility, qhorus-api (MessageObserver, ChannelProjection, MessageView, MessageReceivedEvent)

## Global Constraints

- No CDI/Quarkus runtime in tests — plain JUnit 5 with Mockito
- No `synchronized` blocks — use AtomicReference for thread safety (virtual thread pinning concern)
- Pre-release: breaking changes are acceptable
- `TerminationDecision` is a sealed interface with 4 variants: Continue, Complete, Failed, Escalate

---

### Task 1: TerminationCondition combinators

Add `or()`, `and()`, and priority helper methods to `TerminationCondition<T>`.

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/termination/TerminationCondition.java`
- Create: `blocks/src/test/java/io/casehub/blocks/agentic/termination/TerminationConditionTest.java`

**Interfaces:**
- Consumes: `TerminationDecision` (sealed: Continue, Complete, Failed, Escalate), `TerminationContext<T>`
- Produces: `TerminationCondition.or(TerminationCondition<T>)`, `TerminationCondition.and(TerminationCondition<T>)` — used by Task 2's termination convenience methods and consumer wiring

- [ ] **Step 1: Write failing tests for `or()` combinator**

```java
package io.casehub.blocks.agentic.termination;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TerminationConditionTest {

    private static final TerminationContext<String> CTX =
        new TerminationContext<>("state", 1, Duration.ZERO, List.of());

    private static TerminationCondition<String> returning(TerminationDecision decision) {
        return ctx -> decision;
    }

    @Test
    void orContinueAndContinueReturnsContinue() {
        var combined = returning(TerminationDecision.Continue.INSTANCE)
            .or(returning(TerminationDecision.Continue.INSTANCE));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void orCompleteAndContinueReturnsComplete() {
        var combined = returning(new TerminationDecision.Complete("a"))
            .or(returning(TerminationDecision.Continue.INSTANCE));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void orContinueAndCompleteReturnsComplete() {
        var combined = returning(TerminationDecision.Continue.INSTANCE)
            .or(returning(new TerminationDecision.Complete("b")));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void orCompleteAndEscalateReturnsEscalate() {
        var combined = returning(new TerminationDecision.Complete("a"))
            .or(returning(new TerminationDecision.Escalate("urgent")));
        var result = combined.evaluate(CTX);
        assertThat(result).isInstanceOf(TerminationDecision.Escalate.class);
        assertThat(((TerminationDecision.Escalate) result).reason()).isEqualTo("urgent");
    }

    @Test
    void orFailedAndCompleteReturnsFailed() {
        var combined = returning(new TerminationDecision.Failed("broken"))
            .or(returning(new TerminationDecision.Complete("ok")));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Failed.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=TerminationConditionTest`
Expected: FAIL — `or` method does not exist

- [ ] **Step 3: Write failing tests for `and()` combinator**

Add to `TerminationConditionTest`:

```java
@Test
void andContinueAndCompleteReturnsContinue() {
    var combined = returning(TerminationDecision.Continue.INSTANCE)
        .and(returning(new TerminationDecision.Complete("a")));
    assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Continue.class);
}

@Test
void andCompleteAndContinueReturnsContinue() {
    var combined = returning(new TerminationDecision.Complete("a"))
        .and(returning(TerminationDecision.Continue.INSTANCE));
    assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Continue.class);
}

@Test
void andCompleteAndCompleteReturnsComplete() {
    var combined = returning(new TerminationDecision.Complete("a"))
        .and(returning(new TerminationDecision.Complete("b")));
    assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Complete.class);
}

@Test
void andCompleteAndEscalateReturnsEscalate() {
    var combined = returning(new TerminationDecision.Complete("ok"))
        .and(returning(new TerminationDecision.Escalate("urgent")));
    assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Escalate.class);
}

@Test
void andCompleteAndFailedReturnsFailed() {
    var combined = returning(new TerminationDecision.Complete("ok"))
        .and(returning(new TerminationDecision.Failed("broken")));
    assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Failed.class);
}
```

- [ ] **Step 4: Implement `or()`, `and()`, and priority helpers**

Replace the content of `TerminationCondition.java` with:

```java
package io.casehub.blocks.agentic.termination;

public interface TerminationCondition<T> {
    TerminationDecision evaluate(TerminationContext<T> context);

    default TerminationCondition<T> or(TerminationCondition<T> other) {
        return ctx -> {
            var first = this.evaluate(ctx);
            if (first instanceof TerminationDecision.Continue) {
                return other.evaluate(ctx);
            }
            var second = other.evaluate(ctx);
            if (second instanceof TerminationDecision.Continue) {
                return first;
            }
            return higherPriority(first, second);
        };
    }

    default TerminationCondition<T> and(TerminationCondition<T> other) {
        return ctx -> {
            var first = this.evaluate(ctx);
            if (first instanceof TerminationDecision.Continue) return first;
            var second = other.evaluate(ctx);
            if (second instanceof TerminationDecision.Continue) return second;
            return higherPriority(first, second);
        };
    }

    private static TerminationDecision higherPriority(
            TerminationDecision a, TerminationDecision b) {
        return priority(a) >= priority(b) ? a : b;
    }

    private static int priority(TerminationDecision d) {
        return switch (d) {
            case TerminationDecision.Escalate e -> 3;
            case TerminationDecision.Failed f -> 2;
            case TerminationDecision.Complete c -> 1;
            case TerminationDecision.Continue ignored -> 0;
        };
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=TerminationConditionTest`
Expected: ALL PASS

- [ ] **Step 6: Run full test suite to check for regressions**

Run: `mvn --batch-mode test -pl blocks`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add blocks/src/main/java/io/casehub/blocks/agentic/termination/TerminationCondition.java blocks/src/test/java/io/casehub/blocks/agentic/termination/TerminationConditionTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#97): add or()/and() combinators to TerminationCondition"
```

---

### Task 2: ChannelObserver core — projection folding, EventSource, termination factory

The main type: `ChannelObserver<S>` with MessageObserver + EventSource implementation, projection state management, error handling, reset, subscribe guard, and termination convenience methods.

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/channel/ChannelObserver.java`
- Create: `blocks/src/test/java/io/casehub/blocks/agentic/channel/ChannelObserverTest.java`

**Interfaces:**
- Consumes: `ChannelProjection<S>` (qhorus), `MessageObserver` (qhorus), `MessageReceivedEvent` (qhorus), `MessageView` (qhorus), `EventSource` + `EventSource.Cancellation` (blocks), `DriverEvent` (blocks), `TerminationCondition<T>` with `or()`/`and()` (from Task 1), `TerminationDecision` (blocks)
- Produces: `ChannelObserver<S>` — `of(projection, channelName)`, `builder(projection)`, `currentState()`, `reset()`, `terminateWhen(Predicate<S>)`, `asTermination(Function<S, TerminationDecision>)`, `onMessage(MessageReceivedEvent)`, `subscribe(Consumer<DriverEvent>)`, `channels()`

- [ ] **Step 1: Write failing test — projection folding via onMessage()**

```java
package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.model.DriverEvent;
import io.casehub.blocks.agentic.model.EventSource;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelObserverTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();

    private static final ChannelProjection<List<String>> COLLECTING_PROJECTION =
        new ChannelProjection<>() {
            @Override
            public List<String> identity() { return List.of(); }

            @Override
            public List<String> apply(List<String> state, MessageView message) {
                var next = new ArrayList<>(state);
                next.add(message.content());
                return List.copyOf(next);
            }
        };

    private static MessageReceivedEvent event(String content) {
        return new MessageReceivedEvent(
            1L, "test-channel", CHANNEL_ID, "tenant",
            MessageType.STATUS, "agent-1", null, null,
            null, Instant.now(), content, null);
    }

    @Test
    void identityStateOnConstruction() {
        var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
        assertThat(observer.currentState()).isEmpty();
    }

    @Test
    void onMessageFoldsProjection() {
        var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
        observer.onMessage(event("hello"));
        assertThat(observer.currentState()).containsExactly("hello");
    }

    @Test
    void multipleMessagesFoldIncrementally() {
        var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
        observer.onMessage(event("first"));
        observer.onMessage(event("second"));
        assertThat(observer.currentState()).containsExactly("first", "second");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=ChannelObserverTest`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement ChannelObserver core**

Create `blocks/src/main/java/io/casehub/blocks/agentic/channel/ChannelObserver.java`:

```java
package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.model.DriverEvent;
import io.casehub.blocks.agentic.model.EventSource;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ChannelObserver<S> implements MessageObserver, EventSource {

    private static final System.Logger LOG =
        System.getLogger(ChannelObserver.class.getName());

    private final ChannelProjection<S> projection;
    private final AtomicReference<S> state;
    private final Set<String> channelNames;
    private volatile Consumer<DriverEvent> sink;

    private ChannelObserver(ChannelProjection<S> projection,
                            Set<String> channelNames) {
        this.projection = projection;
        this.state = new AtomicReference<>(projection.identity());
        this.channelNames = Set.copyOf(channelNames);
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        try {
            var messageView = toMessageView(event);
            state.updateAndGet(s -> projection.apply(s, messageView));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                "ChannelObserver: projection failed for channel "
                + event.channelId() + ": " + e.getMessage());
            var s = sink;
            if (s != null) {
                s.accept(DriverEvent.signal("projection-error:" + event.channelId()));
            }
            return;
        }
        var s = sink;
        if (s != null) {
            s.accept(DriverEvent.signal("channel:" + event.channelId()));
        }
    }

    @Override
    public Set<String> channels() {
        return channelNames;
    }

    @Override
    public Scope scope() {
        return Scope.LOCAL;
    }

    @Override
    public Cancellation subscribe(Consumer<DriverEvent> sink) {
        if (this.sink != null) {
            throw new IllegalStateException(
                "ChannelObserver already has an active subscriber");
        }
        this.sink = sink;
        return Cancellation.of(() -> this.sink = null);
    }

    public S currentState() {
        return state.get();
    }

    public void reset() {
        state.set(projection.identity());
    }

    public <T> TerminationCondition<T> terminateWhen(Predicate<S> condition) {
        return ctx -> condition.test(currentState())
            ? new TerminationDecision.Complete("Channel observation")
            : TerminationDecision.Continue.INSTANCE;
    }

    public <T> TerminationCondition<T> asTermination(
            Function<S, TerminationDecision> evaluator) {
        return ctx -> evaluator.apply(currentState());
    }

    public static <S> ChannelObserver<S> of(ChannelProjection<S> projection,
                                             String channelName) {
        return new ChannelObserver<>(projection, Set.of(channelName));
    }

    public static <S> Builder<S> builder(ChannelProjection<S> projection) {
        return new Builder<>(projection);
    }

    public static final class Builder<S> {
        private final ChannelProjection<S> projection;
        private final Set<String> channelNames = new LinkedHashSet<>();

        private Builder(ChannelProjection<S> projection) {
            this.projection = projection;
        }

        public Builder<S> channel(String channelName) {
            channelNames.add(channelName);
            return this;
        }

        public ChannelObserver<S> build() {
            if (channelNames.isEmpty()) {
                throw new IllegalStateException("At least one channel required");
            }
            return new ChannelObserver<>(projection, channelNames);
        }
    }

    private static MessageView toMessageView(MessageReceivedEvent event) {
        return new MessageView(
            event.messageId(), event.channelId(), event.senderId(),
            event.messageType(), event.content(), event.correlationId(),
            null, event.target(), event.topic(),
            List.of(), event.actorType(), event.occurredAt(),
            null, 0);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=ChannelObserverTest`
Expected: ALL PASS

- [ ] **Step 5: Write remaining tests — EventSource, reset, subscribe guard, error handling, termination convenience, builder**

Add to `ChannelObserverTest`:

```java
// --- EventSource contract ---

@Test
void subscribeStoresSinkAndPostsDriverEvents() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
    var events = new ArrayList<DriverEvent>();
    observer.subscribe(events::add);

    observer.onMessage(event("hello"));

    assertThat(events).hasSize(1);
    assertThat(events.get(0).source()).startsWith("channel:");
}

@Test
void onMessageWithoutSinkFoldsWithoutSignaling() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
    observer.onMessage(event("hello"));

    assertThat(observer.currentState()).containsExactly("hello");
}

@Test
void cancellationClearsSink() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
    var events = new ArrayList<DriverEvent>();
    var cancellation = observer.subscribe(events::add);

    cancellation.cancel();
    observer.onMessage(event("after-cancel"));

    assertThat(observer.currentState()).containsExactly("after-cancel");
    assertThat(events).isEmpty();
}

@Test
void subscribeWithExistingSubscriberThrows() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
    observer.subscribe(e -> {});

    assertThatThrownBy(() -> observer.subscribe(e -> {}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already has an active subscriber");
}

@Test
void subscribeAfterCancelSucceeds() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
    observer.subscribe(e -> {}).cancel();

    var events = new ArrayList<DriverEvent>();
    observer.subscribe(events::add);
    observer.onMessage(event("re-subscribed"));

    assertThat(events).hasSize(1);
}

// --- Reset ---

@Test
void resetClearsAccumulatedState() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
    observer.onMessage(event("accumulated"));
    assertThat(observer.currentState()).hasSize(1);

    observer.reset();
    assertThat(observer.currentState()).isEmpty();
}

// --- Error handling ---

@Test
void projectionExceptionLeavesStateUnchangedAndSignalsError() {
    ChannelProjection<String> failingProjection = new ChannelProjection<>() {
        @Override public String identity() { return "initial"; }
        @Override public String apply(String s, MessageView msg) {
            throw new RuntimeException("projection failure");
        }
    };

    var observer = ChannelObserver.of(failingProjection, "test-channel");
    var events = new ArrayList<DriverEvent>();
    observer.subscribe(events::add);

    observer.onMessage(event("bad"));

    assertThat(observer.currentState()).isEqualTo("initial");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).source()).startsWith("projection-error:");
}

@Test
void validMessageAfterProjectionErrorFoldsNormally() {
    var callCount = new AtomicInteger(0);
    ChannelProjection<String> sometimesFailingProjection = new ChannelProjection<>() {
        @Override public String identity() { return ""; }
        @Override public String apply(String s, MessageView msg) {
            if (callCount.incrementAndGet() == 1) {
                throw new RuntimeException("first call fails");
            }
            return s + msg.content();
        }
    };

    var observer = ChannelObserver.of(sometimesFailingProjection, "test-channel");
    observer.onMessage(event("fail"));
    observer.onMessage(event("succeed"));

    assertThat(observer.currentState()).isEqualTo("succeed");
}

// --- Termination convenience ---

@Test
void terminateWhenReturnsCompleteWhenPredicateTrue() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
    observer.onMessage(event("done"));

    TerminationCondition<String> condition =
        observer.terminateWhen(state -> state.contains("done"));
    var ctx = new io.casehub.blocks.agentic.termination.TerminationContext<>(
        "ignored", 1, Duration.ZERO, List.of());

    assertThat(condition.evaluate(ctx))
        .isInstanceOf(TerminationDecision.Complete.class);
}

@Test
void terminateWhenReturnsContinueWhenPredicateFalse() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");

    TerminationCondition<String> condition =
        observer.terminateWhen(state -> state.contains("done"));
    var ctx = new io.casehub.blocks.agentic.termination.TerminationContext<>(
        "ignored", 1, Duration.ZERO, List.of());

    assertThat(condition.evaluate(ctx))
        .isInstanceOf(TerminationDecision.Continue.class);
}

@Test
void asTerminationDelegatesToFunction() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
    observer.onMessage(event("alert"));

    TerminationCondition<Integer> condition = observer.asTermination(
        state -> state.contains("alert")
            ? new TerminationDecision.Escalate("alert found")
            : TerminationDecision.Continue.INSTANCE);
    var ctx = new io.casehub.blocks.agentic.termination.TerminationContext<>(
        42, 1, Duration.ZERO, List.of());

    assertThat(condition.evaluate(ctx))
        .isInstanceOf(TerminationDecision.Escalate.class);
}

// --- MessageObserver contract ---

@Test
void channelsReturnsConfiguredNames() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "my-channel");
    assertThat(observer.channels()).containsExactly("my-channel");
}

@Test
void scopeReturnsLocal() {
    var observer = ChannelObserver.of(COLLECTING_PROJECTION, "test-channel");
    assertThat(observer.scope()).isEqualTo(MessageObserver.Scope.LOCAL);
}

// --- Builder ---

@Test
void builderMultipleChannels() {
    var observer = ChannelObserver.builder(COLLECTING_PROJECTION)
        .channel("channel-a")
        .channel("channel-b")
        .build();
    assertThat(observer.channels()).containsExactlyInAnyOrder("channel-a", "channel-b");
}

@Test
void builderEmptyThrows() {
    assertThatThrownBy(() -> ChannelObserver.builder(COLLECTING_PROJECTION).build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("At least one channel");
}

@Test
void builderDeduplicatesChannelNames() {
    var observer = ChannelObserver.builder(COLLECTING_PROJECTION)
        .channel("same")
        .channel("same")
        .build();
    assertThat(observer.channels()).hasSize(1);
}
```

- [ ] **Step 6: Run all tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=ChannelObserverTest`
Expected: ALL PASS

- [ ] **Step 7: Run full test suite**

Run: `mvn --batch-mode test -pl blocks`
Expected: ALL PASS

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add blocks/src/main/java/io/casehub/blocks/agentic/channel/ChannelObserver.java blocks/src/test/java/io/casehub/blocks/agentic/channel/ChannelObserverTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#97): add ChannelObserver for supervisor channel observation"
```

---

### Task 3: Integration test — ChannelObserver with ChoreographedDriver

Verify the full integration: observer as EventSource wakes ChoreographedDriver, termination reads projected state.

**Files:**
- Create: `blocks/src/test/java/io/casehub/blocks/agentic/channel/ChannelObserverIntegrationTest.java`

**Interfaces:**
- Consumes: `ChannelObserver<S>` (Task 2), `ChoreographedDriver<T>`, `ExecutionModel<T>`, `EventConcurrencyPolicy`, `TerminationCondition<T>` with `or()` (Task 1), `MaxIterationsTermination`, `AgentRef`, `AgentResult`, `RoutingCandidate`, `FailurePolicy`

- [ ] **Step 1: Write integration test — observer wakes driver and terminates on projection state**

```java
package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.model.ChoreographedDriver;
import io.casehub.blocks.agentic.model.EventConcurrencyPolicy;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelObserverIntegrationTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();

    private static final ChannelProjection<List<String>> COLLECTING =
        new ChannelProjection<>() {
            @Override public List<String> identity() { return List.of(); }
            @Override public List<String> apply(List<String> state, MessageView msg) {
                var next = new ArrayList<>(state);
                next.add(msg.content());
                return List.copyOf(next);
            }
        };

    private static MessageReceivedEvent event(String content) {
        return new MessageReceivedEvent(
            1L, "test-channel", CHANNEL_ID, "tenant",
            MessageType.STATUS, "agent-1", null, null,
            null, Instant.now(), content, null);
    }

    @Test
    void observerAsEventSourceWakesDriverAndTerminatesOnProjectionState() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");

        var callCount = new AtomicInteger(0);
        var agent = AgentRef.external((Object input) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(
                AgentResult.success(null, "done"));
        });

        var termination = observer.<String>terminateWhen(
                state -> state.contains("stop"))
            .or(new MaxIterationsTermination<>(10));

        var model = new ExecutionModel<>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            termination,
            () -> List.of(new RoutingCandidate(agent, null)),
            FailurePolicy.defaults(),
            List.of(), "test");

        var driver = new ChoreographedDriver<>(
            AgentInvoker.<String>defaultInvoker(),
            EventConcurrencyPolicy.serialize(),
            observer);

        new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            observer.onMessage(event("hello"));
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            observer.onMessage(event("stop"));
        }).start();

        var result = driver.execute(model, "initial").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(observer.currentState()).containsExactly("hello", "stop");
        assertThat(callCount.get()).isGreaterThanOrEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest=ChannelObserverIntegrationTest`
Expected: PASS

- [ ] **Step 3: Run full test suite**

Run: `mvn --batch-mode test -pl blocks`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add blocks/src/test/java/io/casehub/blocks/agentic/channel/ChannelObserverIntegrationTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "test(#97): integration test for ChannelObserver with ChoreographedDriver"
```
