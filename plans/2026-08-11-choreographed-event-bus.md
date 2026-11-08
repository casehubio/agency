# ChoreographedDriver Event-Bus Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #19 — feat: ChoreographedDriver event-bus integration for ChannelAgent and HumanAgent
**Issue group:** #19

**Goal:** Make ChoreographedDriver genuinely event-driven — dormant between iterations, woken by external events via a composable concurrency policy.

**Architecture:** ChoreographedDriver gains a BlockingQueue fed by EventSource subscriptions. A composable EventConcurrencyPolicy controls how queued events are consumed between iterations. The internal loop remains blocking (virtual thread), and AbstractExecutionDriver, OrchestratedDriver, and all five SPIs stay unchanged.

**Tech Stack:** Java 21+ (virtual threads), JUnit 5, AssertJ, Awaitility, Mockito

## Global Constraints

- All new types go in `io.casehub.blocks.agentic.model` package
- No new compile dependencies — only java.util.concurrent, jspecify (already present)
- No CDI annotations — blocks is a plain Java library
- Tests use plain JUnit 5 + AssertJ + Awaitility (existing test deps)
- All existing ChoreographedDriverTest tests must pass without modification

---

### Task 1: DriverEvent Record

**Files:**
- Create: `blocks/blocks/src/main/java/io/casehub/blocks/agentic/model/DriverEvent.java`
- Test: `blocks/blocks/src/test/java/io/casehub/blocks/agentic/model/DriverEventTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `DriverEvent(String source, Instant timestamp, @Nullable Object payload)`, convenience constructors `DriverEvent(String)` and `DriverEvent(String, Object)`, static factories `signal(String)` and `timer()`

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.blocks.agentic.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DriverEventTest {

    @Test
    void signalFactoryCreatesEventWithSource() {
        var event = DriverEvent.signal("channel");
        assertThat(event.source()).isEqualTo("channel");
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.payload()).isNull();
    }

    @Test
    void timerFactoryCreatesTimerEvent() {
        var event = DriverEvent.timer();
        assertThat(event.source()).isEqualTo("timer");
        assertThat(event.payload()).isNull();
    }

    @Test
    void compactConstructorWithSourceOnly() {
        var event = new DriverEvent("test");
        assertThat(event.source()).isEqualTo("test");
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.payload()).isNull();
    }

    @Test
    void compactConstructorWithPayload() {
        var event = new DriverEvent("test", "data");
        assertThat(event.source()).isEqualTo("test");
        assertThat(event.payload()).isEqualTo("data");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=DriverEventTest`
Expected: FAIL — `DriverEvent` does not exist

- [ ] **Step 3: Write the implementation**

```java
package io.casehub.blocks.agentic.model;

import org.jspecify.annotations.Nullable;
import java.time.Instant;

public record DriverEvent(
    String source,
    Instant timestamp,
    @Nullable Object payload
) {
    public DriverEvent(String source) {
        this(source, Instant.now(), null);
    }

    public DriverEvent(String source, Object payload) {
        this(source, Instant.now(), payload);
    }

    public static DriverEvent signal(String source) {
        return new DriverEvent(source);
    }

    public static DriverEvent timer() {
        return new DriverEvent("timer");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest=DriverEventTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add blocks/src/main/java/io/casehub/blocks/agentic/model/DriverEvent.java blocks/src/test/java/io/casehub/blocks/agentic/model/DriverEventTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#19): add DriverEvent typed event record

Refs casehubio/blocks#19"
```

---

### Task 2: EventSource SPI

**Files:**
- Create: `blocks/blocks/src/main/java/io/casehub/blocks/agentic/model/EventSource.java`
- Test: `blocks/blocks/src/test/java/io/casehub/blocks/agentic/model/EventSourceTest.java`

**Interfaces:**
- Consumes: `DriverEvent` (Task 1)
- Produces: `EventSource` (functional interface with `Cancellation subscribe(Consumer<DriverEvent>)`), `EventSource.Cancellation` (nested interface), static factories `merge(EventSource...)` and `ticker(Duration, ScheduledExecutorService)`

- [ ] **Step 1: Write the failing tests**

```java
package io.casehub.blocks.agentic.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class EventSourceTest {

    @Test
    void subscribeDeliversEventsToSink() {
        var received = new ArrayList<DriverEvent>();
        EventSource source = sink -> {
            sink.accept(DriverEvent.signal("test"));
            return EventSource.Cancellation.of(() -> {});
        };

        source.subscribe(received::add);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).source()).isEqualTo("test");
    }

    @Test
    void cancellationStopsDelivery() {
        var cancelled = new AtomicBoolean(false);
        EventSource source = sink -> EventSource.Cancellation.of(() -> cancelled.set(true));

        var cancellation = source.subscribe(e -> {});
        assertThat(cancelled.get()).isFalse();
        cancellation.cancel();
        assertThat(cancelled.get()).isTrue();
    }

    @Test
    void mergeDeliversFromAllSources() {
        var received = new ArrayList<DriverEvent>();
        EventSource a = sink -> {
            sink.accept(DriverEvent.signal("a"));
            return EventSource.Cancellation.of(() -> {});
        };
        EventSource b = sink -> {
            sink.accept(DriverEvent.signal("b"));
            return EventSource.Cancellation.of(() -> {});
        };

        EventSource.merge(a, b).subscribe(received::add);
        assertThat(received).extracting(DriverEvent::source)
            .containsExactly("a", "b");
    }

    @Test
    void mergeCancelsAllSources() {
        var cancelledA = new AtomicBoolean(false);
        var cancelledB = new AtomicBoolean(false);
        EventSource a = sink -> EventSource.Cancellation.of(() -> cancelledA.set(true));
        EventSource b = sink -> EventSource.Cancellation.of(() -> cancelledB.set(true));

        var cancellation = EventSource.merge(a, b).subscribe(e -> {});
        cancellation.cancel();
        assertThat(cancelledA.get()).isTrue();
        assertThat(cancelledB.get()).isTrue();
    }

    @Test
    void tickerEmitsPeriodicEvents() {
        var executor = Executors.newSingleThreadScheduledExecutor();
        try {
            var received = new CopyOnWriteArrayList<DriverEvent>();
            var cancellation = EventSource.ticker(Duration.ofMillis(50), executor)
                .subscribe(received::add);

            await().atMost(Duration.ofSeconds(2))
                .until(() -> received.size() >= 3);

            cancellation.cancel();
            assertThat(received).allMatch(e -> "timer".equals(e.source()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void compositeCancellationCancelsAll() {
        var flags = new boolean[]{false, false, false};
        var cancellations = List.of(
            EventSource.Cancellation.of(() -> flags[0] = true),
            EventSource.Cancellation.of(() -> flags[1] = true),
            EventSource.Cancellation.of(() -> flags[2] = true)
        );

        EventSource.Cancellation.composite(cancellations).cancel();
        assertThat(flags).containsExactly(true, true, true);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=EventSourceTest`
Expected: FAIL — `EventSource` does not exist

- [ ] **Step 3: Write the implementation**

```java
package io.casehub.blocks.agentic.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@FunctionalInterface
public interface EventSource {

    Cancellation subscribe(Consumer<DriverEvent> sink);

    interface Cancellation {
        void cancel();

        static Cancellation of(Runnable action) {
            return action::run;
        }

        static Cancellation composite(List<Cancellation> cancellations) {
            return () -> cancellations.forEach(Cancellation::cancel);
        }
    }

    static EventSource merge(EventSource... sources) {
        return sink -> {
            var cancellations = new ArrayList<Cancellation>();
            for (var source : sources) {
                cancellations.add(source.subscribe(sink));
            }
            return Cancellation.composite(cancellations);
        };
    }

    static EventSource ticker(Duration interval, ScheduledExecutorService executor) {
        return sink -> {
            var future = executor.scheduleAtFixedRate(
                () -> sink.accept(DriverEvent.timer()),
                interval.toMillis(), interval.toMillis(),
                TimeUnit.MILLISECONDS);
            return Cancellation.of(() -> future.cancel(false));
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest=EventSourceTest`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add blocks/src/main/java/io/casehub/blocks/agentic/model/EventSource.java blocks/src/test/java/io/casehub/blocks/agentic/model/EventSourceTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#19): add EventSource SPI with merge and ticker factories

Refs casehubio/blocks#19"
```

---

### Task 3: EventConcurrencyPolicy

**Files:**
- Create: `blocks/blocks/src/main/java/io/casehub/blocks/agentic/model/EventConcurrencyPolicy.java`
- Test: `blocks/blocks/src/test/java/io/casehub/blocks/agentic/model/EventConcurrencyPolicyTest.java`

**Interfaces:**
- Consumes: `DriverEvent` (Task 1)
- Produces: `EventConcurrencyPolicy` (interface with `List<DriverEvent> awaitEvents(BlockingQueue<DriverEvent>)`), static factories `serialize()`, `coalesce()`, `coalesce(Duration)`, `coalesceBySource()`, default method `then(EventConcurrencyPolicy)`

- [ ] **Step 1: Write the failing tests**

```java
package io.casehub.blocks.agentic.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

class EventConcurrencyPolicyTest {

    @Test
    void serializeTakesOneEvent() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        queue.add(DriverEvent.signal("a"));
        queue.add(DriverEvent.signal("b"));
        queue.add(DriverEvent.signal("c"));

        var events = EventConcurrencyPolicy.serialize().awaitEvents(queue);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).source()).isEqualTo("a");
        assertThat(queue).hasSize(2);
    }

    @Test
    void coalesceDrainsAllPending() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        queue.add(DriverEvent.signal("a"));
        queue.add(DriverEvent.signal("b"));
        queue.add(DriverEvent.signal("c"));

        var events = EventConcurrencyPolicy.coalesce().awaitEvents(queue);
        assertThat(events).hasSize(3);
        assertThat(queue).isEmpty();
    }

    @Test
    void coalesceBlocksUntilFirstEvent() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        var thread = new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            queue.add(DriverEvent.signal("delayed"));
        });
        thread.start();

        var events = EventConcurrencyPolicy.coalesce().awaitEvents(queue);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).source()).isEqualTo("delayed");
    }

    @Test
    void coalesceBySourceKeepsLastPerSource() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        queue.add(new DriverEvent("timer", "tick-1"));
        queue.add(new DriverEvent("channel", "msg-1"));
        queue.add(new DriverEvent("timer", "tick-2"));
        queue.add(new DriverEvent("channel", "msg-2"));

        var events = EventConcurrencyPolicy.coalesceBySource().awaitEvents(queue);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(DriverEvent::source)
            .containsExactly("timer", "channel");
        assertThat(events).extracting(DriverEvent::payload)
            .containsExactly("tick-2", "msg-2");
    }

    @Test
    void coalesceWithWindowCollectsWithinDuration() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        queue.add(DriverEvent.signal("first"));

        var thread = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            queue.add(DriverEvent.signal("second"));
        });
        thread.start();

        var events = EventConcurrencyPolicy.coalesce(Duration.ofMillis(200))
            .awaitEvents(queue);
        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void thenChainsPolicies() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        queue.add(new DriverEvent("timer", "tick-1"));
        queue.add(new DriverEvent("channel", "msg"));
        queue.add(new DriverEvent("timer", "tick-2"));

        var policy = EventConcurrencyPolicy.coalesce()
            .then(EventConcurrencyPolicy.coalesceBySource());

        var events = policy.awaitEvents(queue);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(DriverEvent::payload)
            .containsExactly("tick-2", "msg");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=EventConcurrencyPolicyTest`
Expected: FAIL — `EventConcurrencyPolicy` does not exist

- [ ] **Step 3: Write the implementation**

```java
package io.casehub.blocks.agentic.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public interface EventConcurrencyPolicy {

    List<DriverEvent> awaitEvents(BlockingQueue<DriverEvent> queue)
            throws InterruptedException;

    default EventConcurrencyPolicy then(EventConcurrencyPolicy next) {
        var first = this;
        return queue -> {
            var events = first.awaitEvents(queue);
            var staging = new LinkedBlockingQueue<DriverEvent>();
            staging.addAll(events);
            return next.awaitEvents(staging);
        };
    }

    static EventConcurrencyPolicy serialize() {
        return queue -> List.of(queue.take());
    }

    static EventConcurrencyPolicy coalesce() {
        return queue -> {
            var first = queue.take();
            var batch = new ArrayList<DriverEvent>();
            batch.add(first);
            queue.drainTo(batch);
            return batch;
        };
    }

    static EventConcurrencyPolicy coalesce(Duration window) {
        return queue -> {
            var first = queue.take();
            var batch = new ArrayList<DriverEvent>();
            batch.add(first);
            var deadline = System.nanoTime() + window.toNanos();
            while (System.nanoTime() < deadline) {
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                var next = queue.poll(remaining, TimeUnit.NANOSECONDS);
                if (next == null) break;
                batch.add(next);
            }
            return batch;
        };
    }

    static EventConcurrencyPolicy coalesceBySource() {
        return queue -> {
            var first = queue.take();
            var bySource = new LinkedHashMap<String, DriverEvent>();
            bySource.put(first.source(), first);
            var remaining = new ArrayList<DriverEvent>();
            queue.drainTo(remaining);
            for (var e : remaining) {
                bySource.put(e.source(), e);
            }
            return List.copyOf(bySource.values());
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest=EventConcurrencyPolicyTest`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add blocks/src/main/java/io/casehub/blocks/agentic/model/EventConcurrencyPolicy.java blocks/src/test/java/io/casehub/blocks/agentic/model/EventConcurrencyPolicyTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#19): add EventConcurrencyPolicy with serialize, coalesce, and composition

Refs casehubio/blocks#19"
```

---

### Task 4: ChoreographedDriver Event-Driven Mode

**Files:**
- Modify: `blocks/blocks/src/main/java/io/casehub/blocks/agentic/model/ChoreographedDriver.java`
- Modify: `blocks/blocks/src/test/java/io/casehub/blocks/agentic/model/ChoreographedDriverTest.java`

**Interfaces:**
- Consumes: `DriverEvent` (Task 1), `EventSource` (Task 2), `EventConcurrencyPolicy` (Task 3), `AgentInvoker<T>` (existing)
- Produces: modified `ChoreographedDriver<T>` with new constructors `(AgentInvoker<T>, EventConcurrencyPolicy, EventSource...)`, methods `signal(DriverEvent)`, `signal(String)`, overridden `cancel()`; legacy no-arg and `(AgentInvoker<T>)` constructors preserved

- [ ] **Step 1: Write the failing tests for event-driven mode**

Add these tests to the existing `ChoreographedDriverTest.java`:

```java
@Test
void eventDrivenModeWaitsForEvents() {
    var callCount = new AtomicInteger(0);
    var agent = AgentRef.external((Object input) -> {
        callCount.incrementAndGet();
        return CompletableFuture.completedFuture(AgentResult.success(null, "done"));
    });
    var candidate = new RoutingCandidate(agent, null);

    var model = new ExecutionModel<String>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(2),
            () -> List.of(candidate),
            FailurePolicy.defaults(),
            List.of(), "test");

    var driver = new ChoreographedDriver<>(
        AgentInvoker.defaultInvoker(),
        EventConcurrencyPolicy.serialize(),
        sink -> {
            new Thread(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                sink.accept(DriverEvent.signal("trigger-1"));
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                sink.accept(DriverEvent.signal("trigger-2"));
            }).start();
            return EventSource.Cancellation.of(() -> {});
        });

    var result = driver.execute(model, "state").await().indefinitely();

    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    assertThat(callCount.get()).isEqualTo(2);
}

@Test
void signalWakesDriverDirectly() {
    var callCount = new AtomicInteger(0);
    var agent = AgentRef.external((Object input) -> {
        callCount.incrementAndGet();
        return CompletableFuture.completedFuture(AgentResult.success(null, "done"));
    });
    var candidate = new RoutingCandidate(agent, null);

    var model = new ExecutionModel<String>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(1),
            () -> List.of(candidate),
            FailurePolicy.defaults(),
            List.of(), "test");

    var driver = new ChoreographedDriver<>(
        AgentInvoker.defaultInvoker(),
        EventConcurrencyPolicy.serialize());

    var future = CompletableFuture.supplyAsync(() ->
        driver.execute(model, "state").await().indefinitely());

    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    driver.signal("manual");

    var result = future.join();
    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    assertThat(callCount.get()).isEqualTo(1);
}

@Test
void cancelDuringEventWaitExitsCleanly() {
    var agent = AgentRef.external((Object input) ->
        CompletableFuture.completedFuture(AgentResult.success(null, "done")));
    var candidate = new RoutingCandidate(agent, null);

    var model = new ExecutionModel<String>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(100),
            () -> List.of(candidate),
            FailurePolicy.defaults(),
            List.of(), "test");

    // Non-emitting EventSource — driver enters event-driven mode and blocks on queue.take()
    var driver = new ChoreographedDriver<>(
        AgentInvoker.defaultInvoker(),
        EventConcurrencyPolicy.serialize(),
        sink -> EventSource.Cancellation.of(() -> {}));

    var future = CompletableFuture.supplyAsync(() ->
        driver.execute(model, "state").await().indefinitely());

    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    driver.cancel().await().indefinitely();

    var result = future.join();
    assertThat(result).isInstanceOf(ExecutionResult.Cancelled.class);
}

@Test
void legacyNoArgConstructorLoopsContinuously() {
    // Existing test — verifies backward compatibility
    var callCount = new AtomicInteger(0);
    var agent = AgentRef.external((Object input) -> {
        callCount.incrementAndGet();
        return CompletableFuture.completedFuture(AgentResult.success(null, "reactive"));
    });
    var candidate = new RoutingCandidate(agent, null);

    var model = new ExecutionModel<String>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(3),
            () -> List.of(candidate),
            FailurePolicy.defaults(),
            List.of(), "test");

    var driver = new ChoreographedDriver<String>();
    var result = driver.execute(model, "state").await().indefinitely();

    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    assertThat(callCount.get()).isEqualTo(3);
}

@Test
void eventSourceCancelledOnDriverCompletion() {
    var sourceCancel = new AtomicBoolean(false);
    var agent = AgentRef.external((Object input) ->
        CompletableFuture.completedFuture(AgentResult.success(null, "done")));
    var candidate = new RoutingCandidate(agent, null);

    var model = new ExecutionModel<String>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(1),
            () -> List.of(candidate),
            FailurePolicy.defaults(),
            List.of(), "test");

    var driver = new ChoreographedDriver<>(
        AgentInvoker.defaultInvoker(),
        EventConcurrencyPolicy.serialize(),
        sink -> {
            sink.accept(DriverEvent.signal("go"));
            return EventSource.Cancellation.of(() -> sourceCancel.set(true));
        });

    driver.execute(model, "state").await().indefinitely();
    assertThat(sourceCancel.get()).isTrue();
}

@Test
void eventsQueuedDuringIterationProcessedByPolicyAfterCompletion() {
    var iterationCount = new AtomicInteger(0);
    var agent = AgentRef.external((Object input) -> {
        iterationCount.incrementAndGet();
        // Slow agent — events will queue during dispatch
        var future = new CompletableFuture<AgentResult>();
        new Thread(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            future.complete(AgentResult.success(null, "done"));
        }).start();
        return future;
    });
    var candidate = new RoutingCandidate(agent, null);

    var model = new ExecutionModel<String>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(2),
            () -> List.of(candidate),
            FailurePolicy.defaults(),
            List.of(), "test");

    // coalesceBySource: multiple timer events during iteration merge into one
    var driver = new ChoreographedDriver<>(
        AgentInvoker.defaultInvoker(),
        EventConcurrencyPolicy.coalesceBySource(),
        sink -> {
            // Emit first trigger immediately, then burst during iteration
            sink.accept(DriverEvent.signal("start"));
            new Thread(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                sink.accept(new DriverEvent("timer", "tick-1"));
                sink.accept(new DriverEvent("timer", "tick-2"));
                sink.accept(new DriverEvent("timer", "tick-3"));
            }).start();
            return EventSource.Cancellation.of(() -> {});
        });

    var result = driver.execute(model, "state").await().indefinitely();
    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    assertThat(iterationCount.get()).isEqualTo(2);
}

@Test
void cancelDuringAgentDispatchExitsAfterIteration() {
    var dispatchStarted = new AtomicBoolean(false);
    var agent = AgentRef.external((Object input) -> {
        dispatchStarted.set(true);
        var future = new CompletableFuture<AgentResult>();
        new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            future.complete(AgentResult.success(null, "done"));
        }).start();
        return future;
    });
    var candidate = new RoutingCandidate(agent, null);

    var model = new ExecutionModel<String>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(100),
            () -> List.of(candidate),
            FailurePolicy.defaults(),
            List.of(), "test");

    var driver = new ChoreographedDriver<>(
        AgentInvoker.defaultInvoker(),
        EventConcurrencyPolicy.serialize(),
        sink -> {
            sink.accept(DriverEvent.signal("go"));
            return EventSource.Cancellation.of(() -> {});
        });

    var future = CompletableFuture.supplyAsync(() ->
        driver.execute(model, "state").await().indefinitely());

    // Wait for dispatch, then cancel while agent is in-flight
    await().atMost(Duration.ofSeconds(2)).untilTrue(dispatchStarted);
    driver.cancel().await().indefinitely();

    var result = future.join();
    // Cancellation event queued — loop exits after current iteration completes
    assertThat(result).isInstanceOf(ExecutionResult.Cancelled.class);
}

@Test
void tickerDrivesPeriodicIterations() {
    var iterationCount = new AtomicInteger(0);
    var agent = AgentRef.external((Object input) -> {
        iterationCount.incrementAndGet();
        return CompletableFuture.completedFuture(AgentResult.success(null, "done"));
    });
    var candidate = new RoutingCandidate(agent, null);

    var model = new ExecutionModel<String>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(3),
            () -> List.of(candidate),
            FailurePolicy.defaults(),
            List.of(), "test");

    var executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    try {
        var driver = new ChoreographedDriver<>(
            AgentInvoker.defaultInvoker(),
            EventConcurrencyPolicy.serialize(),
            EventSource.ticker(Duration.ofMillis(50), executor));

        var result = driver.execute(model, "state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(iterationCount.get()).isEqualTo(3);
    } finally {
        executor.shutdownNow();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=ChoreographedDriverTest`
Expected: FAIL — new constructors and methods don't exist

- [ ] **Step 3: Modify ChoreographedDriver**

Replace the entire `ChoreographedDriver.java` with:

```java
package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentResult;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class ChoreographedDriver<T> extends AbstractExecutionDriver<T> {

    private final @Nullable EventSource eventSource;
    private final EventConcurrencyPolicy policy;
    private final LinkedBlockingQueue<DriverEvent> eventQueue = new LinkedBlockingQueue<>();

    public ChoreographedDriver() {
        super();
        this.eventSource = null;
        this.policy = EventConcurrencyPolicy.serialize();
    }

    public ChoreographedDriver(AgentInvoker<T> invoker) {
        super(invoker);
        this.eventSource = null;
        this.policy = EventConcurrencyPolicy.serialize();
    }

    public ChoreographedDriver(AgentInvoker<T> invoker,
                                EventConcurrencyPolicy policy,
                                EventSource... sources) {
        super(invoker);
        this.policy = policy;
        this.eventSource = sources.length == 0
            ? null
            : sources.length == 1 ? sources[0] : EventSource.merge(sources);
    }

    public void signal(DriverEvent event) {
        eventQueue.add(event);
    }

    public void signal(String source) {
        eventQueue.add(DriverEvent.signal(source));
    }

    @Override
    public Uni<Void> cancel() {
        eventQueue.add(DriverEvent.signal("cancelled"));
        return super.cancel();
    }

    @Override
    protected Uni<ExecutionResult> runLoop(ExecutionModel<T> model, T context) {
        return Uni.createFrom().item(() -> {
            EventSource.Cancellation subscription = null;
            try {
                if (eventSource != null) {
                    subscription = eventSource.subscribe(eventQueue::add);
                }

                var start = Instant.now();
                var allResults = new ArrayList<AgentResult>();
                int iteration = 0;

                while (!isCancelled()) {
                    transition(model, new ExecutionState.WaitingForEvent());

                    if (eventSource != null) {
                        policy.awaitEvents(eventQueue);
                    }

                    if (isCancelled()) break;

                    transition(model, new ExecutionState.Running(iteration));
                    var result = executeIteration(
                            model, context, iteration, start, allResults);
                    if (result != null) return result;

                    iteration++;
                }

                transition(model, new ExecutionState.Cancelled());
                return new ExecutionResult.Cancelled();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                transition(model, new ExecutionState.Cancelled());
                return new ExecutionResult.Cancelled();
            } finally {
                if (subscription != null) {
                    subscription.cancel();
                }
            }
        });
    }
}
```

- [ ] **Step 4: Run all ChoreographedDriver tests**

Run: `mvn --batch-mode test -pl blocks -Dtest=ChoreographedDriverTest`
Expected: PASS (9 tests — 1 existing + 8 new)

- [ ] **Step 5: Run the full test suite to verify no regressions**

Run: `mvn --batch-mode test -pl blocks`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add blocks/src/main/java/io/casehub/blocks/agentic/model/ChoreographedDriver.java blocks/src/test/java/io/casehub/blocks/agentic/model/ChoreographedDriverTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#19): add event-driven mode to ChoreographedDriver

Adds EventSource subscription, EventConcurrencyPolicy integration,
signal() for direct push, and cancel() override. Legacy no-arg
constructor preserves continuous-loop behavior.

Refs casehubio/blocks#19"
```

---

### Task 5: ExecutionBackend Factory Methods

**Files:**
- Modify: `blocks/blocks/src/main/java/io/casehub/blocks/agentic/model/ExecutionBackend.java`
- Modify: `blocks/blocks/src/test/java/io/casehub/blocks/agentic/model/ExecutionBackendTest.java`

**Interfaces:**
- Consumes: `ChoreographedDriver<T>` (Task 4), `EventSource` (Task 2), `EventConcurrencyPolicy` (Task 3), `AgentInvoker<T>` (existing), `CancellableBackend<T>` (existing)
- Produces: `ExecutionBackend.choreographed(EventConcurrencyPolicy, EventSource...)` and `ExecutionBackend.choreographed(AgentInvoker<T>, EventConcurrencyPolicy, EventSource...)`

- [ ] **Step 1: Write the failing test**

Add to `ExecutionBackendTest.java`:

```java
@Test
void choreographedBackendExecutesOnEvent() {
    var callCount = new AtomicInteger(0);
    var agent = AgentRef.external((Object s) -> {
        callCount.incrementAndGet();
        return CompletableFuture.completedFuture(AgentResult.success(null, "done"));
    });

    var model = new ExecutionModel<>(
        new FirstMatchRouting<>(c -> true),
        new IdentityDecomposition<>(),
        new OnExplicitDispatch<>(),
        new PassThrough<>(),
        new MaxIterationsTermination<>(1),
        () -> List.of(new RoutingCandidate(agent, null)),
        io.casehub.blocks.agentic.FailurePolicy.defaults(),
        List.of(), "test", null);

    EventSource trigger = sink -> {
        sink.accept(DriverEvent.signal("go"));
        return EventSource.Cancellation.of(() -> {});
    };

    ExecutionBackend<Object> backend = ExecutionBackend.choreographed(
        EventConcurrencyPolicy.serialize(), trigger);
    var result = backend.execute(model, "input").await().indefinitely();

    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    assertThat(callCount.get()).isEqualTo(1);
}

@Test
void choreographedBackendCancelStopsExecution() {
    var agent = AgentRef.external("slow", (Object ctx) -> {
        var future = new CompletableFuture<AgentResult>();
        new Thread(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            future.complete(AgentResult.success(null, "done"));
        }).start();
        return future;
    });

    var model = new ExecutionModel<>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(100),
            () -> List.of(new RoutingCandidate(agent, null)),
            io.casehub.blocks.agentic.FailurePolicy.defaults(),
            List.of(), "test", null);

    EventSource trigger = sink -> {
        sink.accept(DriverEvent.signal("go"));
        return EventSource.Cancellation.of(() -> {});
    };

    ExecutionBackend<Object> backend = ExecutionBackend.choreographed(
        EventConcurrencyPolicy.serialize(), trigger);

    var future = CompletableFuture.supplyAsync(() ->
        backend.execute(model, "input").await().indefinitely());

    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
    backend.cancel();

    var result = future.join();
    assertThat(result).isInstanceOf(ExecutionResult.Cancelled.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=ExecutionBackendTest`
Expected: FAIL — `ExecutionBackend.choreographed()` does not exist

- [ ] **Step 3: Add factory methods to ExecutionBackend**

Add after the existing `orchestrated()` methods:

```java
static <T> ExecutionBackend<T> choreographed(EventConcurrencyPolicy policy,
                                              EventSource... sources) {
    return new CancellableBackend<>(
        new ChoreographedDriver<>(AgentInvoker.defaultInvoker(), policy, sources));
}

static <T> ExecutionBackend<T> choreographed(AgentInvoker<T> invoker,
                                              EventConcurrencyPolicy policy,
                                              EventSource... sources) {
    return new CancellableBackend<>(new ChoreographedDriver<>(invoker, policy, sources));
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=ExecutionBackendTest`
Expected: PASS (all tests including 2 new ones)

- [ ] **Step 5: Run the full test suite**

Run: `mvn --batch-mode test -pl blocks`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add blocks/src/main/java/io/casehub/blocks/agentic/model/ExecutionBackend.java blocks/src/test/java/io/casehub/blocks/agentic/model/ExecutionBackendTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#19): add ExecutionBackend.choreographed() factory methods

Refs casehubio/blocks#19"
```

---

### Task 6: Documentation Sync

**Files:**
- Modify: `CLAUDE.md` (project repo)

**Interfaces:**
- Consumes: all types from Tasks 1-5
- Produces: updated CLAUDE.md with new types documented

- [ ] **Step 1: Update CLAUDE.md package table**

In the `## Package: io.casehub.blocks.agentic` section, add to the `agentic.model` sub-package row:

Add `DriverEvent`, `EventSource` (+ `Cancellation`), `EventConcurrencyPolicy` to the description. Update `ChoreographedDriver` description to mention event-driven mode. Add `choreographed()` to `ExecutionBackend` description.

- [ ] **Step 2: Verify CLAUDE.md is valid**

Read the updated section to ensure consistency with existing entries.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add CLAUDE.md
git -C /Users/mdproctor/claude/casehub/blocks commit -m "docs(#19): update CLAUDE.md with event-bus types

Refs casehubio/blocks#19"
```
