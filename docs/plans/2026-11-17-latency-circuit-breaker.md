# LatencyDegradationTrigger Circuit Breaker Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #404 — LatencyDegradationTrigger external circuit breaker integration

**Goal:** Give LatencyDegradationTrigger proper CLOSED/OPEN/HALF_OPEN
circuit breaker semantics with controlled recovery probing, observable
state, and an external state supplier for platform-wide coordination.

**Architecture:** New `StatefulDegradationTrigger` interface extends
`DegradationTrigger` with `state()`. `LatencyDegradationTrigger` becomes
a three-state machine with builder API, optional external state supplier
(`Supplier<CircuitBreakerState>`), and transition listener
(`Consumer<CircuitBreakerTransition>`). `CompositeDegradationTrigger`
derives state from children. `DegradationPolicy` gains recovery config
and drops the dead `latencyBreachWindow` field.

**Tech Stack:** Java 17, JUnit 5, AssertJ, engine-api `CircuitBreakerState`

## Global Constraints

- Uses `io.casehub.api.model.stigmergy.CircuitBreakerState` from engine-api (already a compile dependency)
- No backward-compatibility shims — pre-release platform, fix all callers
- No external library dependencies (no Resilience4j)
- All new types in `io.casehub.agency.orchestration.degradation` package
- Thread safety via AtomicReference/CAS — lock-free on hot path

---

## Batch 1: Foundation Types

### Task 1: CircuitBreakerTransition, LatencyCircuitBreakerConfig, StatefulDegradationTrigger

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/degradation/CircuitBreakerTransition.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/degradation/LatencyCircuitBreakerConfig.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/degradation/StatefulDegradationTrigger.java`
- Test: `agency-core/src/test/java/io/casehub/agency/orchestration/degradation/LatencyCircuitBreakerConfigTest.java`

**Interfaces:**
- Produces: `CircuitBreakerTransition(CircuitBreakerState from, CircuitBreakerState to, Instant timestamp)` — used by Task 3's transition listener
- Produces: `LatencyCircuitBreakerConfig(int breachCount, Duration windowDuration, Duration recoveryTimeout, int probeSuccessThreshold)` — used by Task 2's `DegradationPolicy.toLatencyConfig()` and Task 3's builder
- Produces: `StatefulDegradationTrigger extends DegradationTrigger` with `CircuitBreakerState state()` — implemented by Task 3 and Task 4

- [ ] **Step 1: Write CircuitBreakerTransition record**

Use `ide_create_file` to create:
`agency-core/src/main/java/io/casehub/agency/orchestration/degradation/CircuitBreakerTransition.java`

```java
package io.casehub.agency.orchestration.degradation;

import io.casehub.api.model.stigmergy.CircuitBreakerState;
import java.time.Instant;
import java.util.Objects;

public record CircuitBreakerTransition(
    CircuitBreakerState from,
    CircuitBreakerState to,
    Instant timestamp
) {
    public CircuitBreakerTransition {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (from == to) throw new IllegalArgumentException("from and to must differ");
    }
}
```

- [ ] **Step 2: Write StatefulDegradationTrigger interface**

Use `ide_create_file` to create:
`agency-core/src/main/java/io/casehub/agency/orchestration/degradation/StatefulDegradationTrigger.java`

```java
package io.casehub.agency.orchestration.degradation;

import io.casehub.api.model.stigmergy.CircuitBreakerState;

public interface StatefulDegradationTrigger extends DegradationTrigger {
    CircuitBreakerState state();
}
```

- [ ] **Step 3: Write failing test for LatencyCircuitBreakerConfig**

Use `ide_create_file` to create:
`agency-core/src/test/java/io/casehub/agency/orchestration/degradation/LatencyCircuitBreakerConfigTest.java`

```java
package io.casehub.agency.orchestration.degradation;

import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatencyCircuitBreakerConfigTest {

    @Test
    void defaults_returnsValidConfig() {
        var config = LatencyCircuitBreakerConfig.defaults();
        assertThat(config.breachCount()).isEqualTo(3);
        assertThat(config.windowDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.recoveryTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.probeSuccessThreshold()).isEqualTo(2);
    }

    @Test
    void rejects_zeroBreachCount() {
        assertThatThrownBy(() -> new LatencyCircuitBreakerConfig(
            0, Duration.ofMinutes(2), Duration.ofSeconds(30), 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negativeWindowDuration() {
        assertThatThrownBy(() -> new LatencyCircuitBreakerConfig(
            3, Duration.ofMinutes(-1), Duration.ofSeconds(30), 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zeroRecoveryTimeout() {
        assertThatThrownBy(() -> new LatencyCircuitBreakerConfig(
            3, Duration.ofMinutes(2), Duration.ZERO, 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zeroProbeThreshold() {
        assertThatThrownBy(() -> new LatencyCircuitBreakerConfig(
            3, Duration.ofMinutes(2), Duration.ofSeconds(30), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency-core -Dtest=LatencyCircuitBreakerConfigTest`
Expected: compilation failure — `LatencyCircuitBreakerConfig` does not exist

- [ ] **Step 5: Write LatencyCircuitBreakerConfig record**

Use `ide_create_file` to create:
`agency-core/src/main/java/io/casehub/agency/orchestration/degradation/LatencyCircuitBreakerConfig.java`

```java
package io.casehub.agency.orchestration.degradation;

import java.time.Duration;
import java.util.Objects;

public record LatencyCircuitBreakerConfig(
    int breachCount,
    Duration windowDuration,
    Duration recoveryTimeout,
    int probeSuccessThreshold
) {
    public LatencyCircuitBreakerConfig {
        if (breachCount < 1)
            throw new IllegalArgumentException("breachCount must be >= 1");
        Objects.requireNonNull(windowDuration, "windowDuration must not be null");
        if (windowDuration.isNegative() || windowDuration.isZero())
            throw new IllegalArgumentException("windowDuration must be positive");
        Objects.requireNonNull(recoveryTimeout, "recoveryTimeout must not be null");
        if (recoveryTimeout.isNegative() || recoveryTimeout.isZero())
            throw new IllegalArgumentException("recoveryTimeout must be positive");
        if (probeSuccessThreshold < 1)
            throw new IllegalArgumentException("probeSuccessThreshold must be >= 1");
    }

    public static LatencyCircuitBreakerConfig defaults() {
        return new LatencyCircuitBreakerConfig(
            3, Duration.ofMinutes(2), Duration.ofSeconds(30), 2);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency-core -Dtest=LatencyCircuitBreakerConfigTest`
Expected: 5 tests PASS

- [ ] **Step 7: Verify build with diagnostics**

Run: `ide_diagnostics` on all 3 new files to verify no compilation errors.

- [ ] **Step 8: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/degradation/CircuitBreakerTransition.java \
      agency-core/src/main/java/io/casehub/agency/orchestration/degradation/LatencyCircuitBreakerConfig.java \
      agency-core/src/main/java/io/casehub/agency/orchestration/degradation/StatefulDegradationTrigger.java \
      agency-core/src/test/java/io/casehub/agency/orchestration/degradation/LatencyCircuitBreakerConfigTest.java
git commit -m "feat(#404): add CircuitBreakerTransition, LatencyCircuitBreakerConfig, StatefulDegradationTrigger

Foundation types for circuit breaker integration. StatefulDegradationTrigger
extends DegradationTrigger with CircuitBreakerState state(). Config record
holds breachCount, windowDuration, recoveryTimeout, probeSuccessThreshold.

Refs casehubio/agency#404"
```

---

## Batch 2: State Machine + Full Migration

### Task 2: Rewrite DegradationPolicy

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/degradation/DegradationPolicy.java`
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/degradation/DegradationPolicyTest.java`

**Interfaces:**
- Consumes: `LatencyCircuitBreakerConfig` from Task 1
- Produces: `DegradationPolicy` without `latencyBreachWindow`, with `recoveryTimeout`, `probeSuccessThreshold`, `toLatencyConfig()` — used by Task 3's topology migrations

**Note:** This task intentionally breaks compilation of topologies and some
tests that call `DegradationPolicy.defaults()` or reference
`latencyBreachWindow()`. Task 3 fixes all callers. The batch compiles
after both tasks complete.

- [ ] **Step 1: Write failing test for new DegradationPolicy shape**

Use `ide_replace_member` to replace the entire `DegradationPolicyTest` class body with:

```java
class DegradationPolicyTest {

    @Test
    void defaults_returnsValidPolicy() {
        var policy = DegradationPolicy.defaults();
        assertThat(policy.thresholdType()).isEqualTo(CascadeThresholdType.SIMPLE_FRACTION);
        assertThat(policy.cascadeThreshold()).isEqualTo(0.5);
        assertThat(policy.latencyBreachCount()).isEqualTo(3);
        assertThat(policy.latencyBreachWindowDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.recoveryTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.probeSuccessThreshold()).isEqualTo(2);
        assertThat(policy.priorityWeights()).containsEntry(BudgetPriority.RESERVED, 2.0);
        assertThat(policy.priorityWeights()).containsEntry(BudgetPriority.NORMAL, 1.0);
    }

    @Test
    void toLatencyConfig_extractsCorrectValues() {
        var policy = DegradationPolicy.defaults();
        var config = policy.toLatencyConfig();
        assertThat(config.breachCount()).isEqualTo(3);
        assertThat(config.windowDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.recoveryTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.probeSuccessThreshold()).isEqualTo(2);
    }

    @Test
    void rejects_zeroCascadeThreshold() {
        assertThatThrownBy(() -> new DegradationPolicy(
            CascadeThresholdType.SIMPLE_FRACTION, 0.0,
            Map.of(), 3, Duration.ofMinutes(2), Duration.ofSeconds(30), 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negativeBreachWindowDuration() {
        assertThatThrownBy(() -> new DegradationPolicy(
            CascadeThresholdType.SIMPLE_FRACTION, 0.5,
            Map.of(), 3, Duration.ofMinutes(-1), Duration.ofSeconds(30), 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zeroBreachCount() {
        assertThatThrownBy(() -> new DegradationPolicy(
            CascadeThresholdType.SIMPLE_FRACTION, 0.5,
            Map.of(), 0, Duration.ofMinutes(2), Duration.ofSeconds(30), 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zeroRecoveryTimeout() {
        assertThatThrownBy(() -> new DegradationPolicy(
            CascadeThresholdType.SIMPLE_FRACTION, 0.5,
            Map.of(), 3, Duration.ofMinutes(2), Duration.ZERO, 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zeroProbeSuccessThreshold() {
        assertThatThrownBy(() -> new DegradationPolicy(
            CascadeThresholdType.SIMPLE_FRACTION, 0.5,
            Map.of(), 3, Duration.ofMinutes(2), Duration.ofSeconds(30), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Rewrite DegradationPolicy record**

Use `ide_replace_member` to replace the entire `DegradationPolicy` record body. New shape:

```java
public record DegradationPolicy(
    CascadeThresholdType thresholdType,
    double cascadeThreshold,
    Map<BudgetPriority, Double> priorityWeights,
    int latencyBreachCount,
    Duration latencyBreachWindowDuration,
    Duration recoveryTimeout,
    int probeSuccessThreshold
) {
    public DegradationPolicy {
        Objects.requireNonNull(thresholdType, "thresholdType must not be null");
        if (cascadeThreshold <= 0.0 || cascadeThreshold > 1.0)
            throw new IllegalArgumentException("cascadeThreshold must be in (0.0, 1.0]");
        Objects.requireNonNull(priorityWeights, "priorityWeights must not be null");
        priorityWeights = Map.copyOf(priorityWeights);
        if (latencyBreachCount < 1)
            throw new IllegalArgumentException("latencyBreachCount must be >= 1");
        Objects.requireNonNull(latencyBreachWindowDuration,
            "latencyBreachWindowDuration must not be null");
        if (latencyBreachWindowDuration.isNegative() || latencyBreachWindowDuration.isZero())
            throw new IllegalArgumentException(
                "latencyBreachWindowDuration must be positive");
        Objects.requireNonNull(recoveryTimeout, "recoveryTimeout must not be null");
        if (recoveryTimeout.isNegative() || recoveryTimeout.isZero())
            throw new IllegalArgumentException("recoveryTimeout must be positive");
        if (probeSuccessThreshold < 1)
            throw new IllegalArgumentException("probeSuccessThreshold must be >= 1");
    }

    public LatencyCircuitBreakerConfig toLatencyConfig() {
        return new LatencyCircuitBreakerConfig(
            latencyBreachCount, latencyBreachWindowDuration,
            recoveryTimeout, probeSuccessThreshold);
    }

    public static DegradationPolicy defaults() {
        return new DegradationPolicy(
            CascadeThresholdType.SIMPLE_FRACTION, 0.5,
            Map.of(BudgetPriority.RESERVED, 2.0, BudgetPriority.NORMAL, 1.0),
            3, Duration.ofMinutes(2), Duration.ofSeconds(30), 2);
    }
}
```

- [ ] **Step 3: Run DegradationPolicyTest to verify it passes**

Run: `mvn --batch-mode test -pl agency-core -Dtest=DegradationPolicyTest`
Expected: 7 tests PASS

- [ ] **Step 4: Commit (compilation intentionally broken for topologies)**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/degradation/DegradationPolicy.java \
      agency-core/src/test/java/io/casehub/agency/orchestration/degradation/DegradationPolicyTest.java
git commit -m "wip(#404): rewrite DegradationPolicy — remove latencyBreachWindow, add CB config

Remove dead latencyBreachWindow field (was never enforced by trigger).
Add recoveryTimeout and probeSuccessThreshold for circuit breaker control.
Add toLatencyConfig() convenience extractor.

Compilation intentionally broken — topology migrations in next commit.

Refs casehubio/agency#404"
```

### Task 3: Rewrite LatencyDegradationTrigger + Migrate Topologies + Fix Tests

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/degradation/LatencyDegradationTrigger.java`
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/degradation/LatencyDegradationTriggerTest.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HierarchicalTopology.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/DistributedTopology.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/GraduatedTopology.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HybridTopology.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HiveTopology.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/CentralisedTopology.java`

**Interfaces:**
- Consumes: `StatefulDegradationTrigger`, `CircuitBreakerTransition`, `LatencyCircuitBreakerConfig` from Task 1
- Consumes: `DegradationPolicy.toLatencyConfig()` from Task 2
- Produces: `LatencyDegradationTrigger` implementing `StatefulDegradationTrigger`, `Consumer<BudgetTelemetryEvent>` with builder API — used by topologies and Task 4

- [ ] **Step 1: Write failing tests for the state machine**

Use `ide_replace_member` to replace the entire `LatencyDegradationTriggerTest` class body. The new test suite covers all state transitions, the builder, the external supplier, and the transition listener:

```java
class LatencyDegradationTriggerTest {

    private LlmInvocationPolicy policy() {
        return LlmInvocationPolicy.of(
            Duration.ofSeconds(5), Duration.ofSeconds(8), Duration.ofSeconds(10));
    }

    private LatencyCircuitBreakerConfig config() {
        return new LatencyCircuitBreakerConfig(3, Duration.ofMinutes(2),
            Duration.ofMillis(100), 2);
    }

    private BudgetTelemetryEvent.Outcome outcome(long latencyMs) {
        return new BudgetTelemetryEvent.Outcome("corr-1", "model", 100, latencyMs, Instant.now());
    }

    // --- CLOSED state ---

    @Test
    void startsInClosedState() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config()).build();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void staysClosed_belowBreachCount() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config()).build();
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void staysClosed_whenLatencyBelowThreshold() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config()).build();
        trigger.accept(outcome(5_000));
        trigger.accept(outcome(5_000));
        trigger.accept(outcome(5_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    // --- CLOSED → OPEN ---

    @Test
    void opensOnBreachThreshold() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config()).build();
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(trigger.shouldDegrade()).isTrue();
    }

    // --- OPEN → HALF_OPEN ---

    @Test
    void transitionsToHalfOpen_afterRecoveryTimeout() throws Exception {
        var trigger = LatencyDegradationTrigger.builder(policy(), config()).build();
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.OPEN);

        Thread.sleep(150);

        assertThat(trigger.shouldDegrade()).isFalse();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.HALF_OPEN);
    }

    // --- HALF_OPEN → CLOSED ---

    @Test
    void closesAfterSuccessfulProbes() throws Exception {
        var config = new LatencyCircuitBreakerConfig(3, Duration.ofMinutes(2),
            Duration.ofMillis(50), 2);
        var trigger = LatencyDegradationTrigger.builder(policy(), config).build();

        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        Thread.sleep(80);
        trigger.shouldDegrade(); // triggers OPEN → HALF_OPEN

        trigger.accept(outcome(5_000)); // probe success 1
        trigger.accept(outcome(5_000)); // probe success 2
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    // --- HALF_OPEN → OPEN ---

    @Test
    void reopensOnProbeFailure() throws Exception {
        var config = new LatencyCircuitBreakerConfig(3, Duration.ofMinutes(2),
            Duration.ofMillis(50), 2);
        var trigger = LatencyDegradationTrigger.builder(policy(), config).build();

        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        Thread.sleep(80);
        trigger.shouldDegrade(); // triggers OPEN → HALF_OPEN

        trigger.accept(outcome(5_000));  // probe success 1
        trigger.accept(outcome(11_000)); // probe failure → reopen
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(trigger.shouldDegrade()).isTrue();
    }

    // --- Transition listener ---

    @Test
    void transitionListenerFiresOnStateChanges() throws Exception {
        var transitions = new java.util.ArrayList<CircuitBreakerTransition>();
        var config = new LatencyCircuitBreakerConfig(2, Duration.ofMinutes(2),
            Duration.ofMillis(50), 1);
        var trigger = LatencyDegradationTrigger.builder(policy(), config)
            .onTransition(transitions::add)
            .build();

        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000)); // → OPEN
        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).from()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(transitions.get(0).to()).isEqualTo(CircuitBreakerState.OPEN);

        Thread.sleep(80);
        trigger.shouldDegrade(); // → HALF_OPEN
        assertThat(transitions).hasSize(2);
        assertThat(transitions.get(1).from()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(transitions.get(1).to()).isEqualTo(CircuitBreakerState.HALF_OPEN);

        trigger.accept(outcome(5_000)); // → CLOSED
        assertThat(transitions).hasSize(3);
        assertThat(transitions.get(2).from()).isEqualTo(CircuitBreakerState.HALF_OPEN);
        assertThat(transitions.get(2).to()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    // --- External state supplier ---

    @Test
    void externalOpenForcesDegradation() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config())
            .externalState(() -> CircuitBreakerState.OPEN)
            .build();
        assertThat(trigger.shouldDegrade()).isTrue();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void externalClosedDoesNotAffectLocalState() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config())
            .externalState(() -> CircuitBreakerState.CLOSED)
            .build();
        assertThat(trigger.shouldDegrade()).isFalse();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void externalOpenBlocksLocalProbing() throws Exception {
        var externalState = new java.util.concurrent.atomic.AtomicReference<>(CircuitBreakerState.CLOSED);
        var config = new LatencyCircuitBreakerConfig(2, Duration.ofMinutes(2),
            Duration.ofMillis(50), 1);
        var trigger = LatencyDegradationTrigger.builder(policy(), config)
            .externalState(externalState::get)
            .build();

        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000)); // → OPEN
        Thread.sleep(80);

        externalState.set(CircuitBreakerState.OPEN);
        // Local transitions to HALF_OPEN but external is OPEN → effective OPEN
        assertThat(trigger.shouldDegrade()).isTrue();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.OPEN);

        externalState.set(CircuitBreakerState.CLOSED);
        // Now local HALF_OPEN takes effect
        assertThat(trigger.shouldDegrade()).isFalse();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.HALF_OPEN);
    }

    // --- Window expiry (preserved from original) ---

    @Test
    void breachesExpireFromWindow() throws Exception {
        var config = new LatencyCircuitBreakerConfig(3, Duration.ofMillis(100),
            Duration.ofMillis(50), 1);
        var trigger = LatencyDegradationTrigger.builder(policy(), config).build();
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);

        Thread.sleep(150);
        // Breaches expired — even adding one more shouldn't reach threshold
        trigger.accept(outcome(11_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agency-core -Dtest=LatencyDegradationTriggerTest`
Expected: compilation failure — `LatencyDegradationTrigger.builder()` does not exist

- [ ] **Step 3: Rewrite LatencyDegradationTrigger**

Use `ide_replace_member` to replace the entire class body of `LatencyDegradationTrigger`:

```java
public class LatencyDegradationTrigger implements StatefulDegradationTrigger, Consumer<BudgetTelemetryEvent> {

    private final long errorAfterMs;
    private final int breachCount;
    private final long windowDurationMs;
    private final long recoveryTimeoutMs;
    private final int probeSuccessThreshold;
    private final Supplier<CircuitBreakerState> externalState;
    private final Consumer<CircuitBreakerTransition> transitionListener;

    private final ConcurrentLinkedDeque<Instant> breaches = new ConcurrentLinkedDeque<>();
    private final AtomicReference<CircuitBreakerState> localState = new AtomicReference<>(CircuitBreakerState.CLOSED);
    private volatile Instant openedAt;
    private final AtomicInteger probeSuccessCount = new AtomicInteger(0);

    private LatencyDegradationTrigger(LlmInvocationPolicy policy,
                                       LatencyCircuitBreakerConfig config,
                                       Supplier<CircuitBreakerState> externalState,
                                       Consumer<CircuitBreakerTransition> transitionListener) {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(config, "config must not be null");
        this.errorAfterMs = policy.errorAfter().toMillis();
        this.breachCount = config.breachCount();
        this.windowDurationMs = config.windowDuration().toMillis();
        this.recoveryTimeoutMs = config.recoveryTimeout().toMillis();
        this.probeSuccessThreshold = config.probeSuccessThreshold();
        this.externalState = externalState;
        this.transitionListener = transitionListener;
    }

    public static Builder builder(LlmInvocationPolicy policy, LatencyCircuitBreakerConfig config) {
        return new Builder(policy, config);
    }

    @Override
    public void accept(BudgetTelemetryEvent event) {
        if (!(event instanceof BudgetTelemetryEvent.Outcome outcome)) return;

        boolean breach = outcome.latencyMs() > errorAfterMs;
        var current = localState.get();

        switch (current) {
            case CLOSED -> {
                if (breach) {
                    breaches.addLast(outcome.timestamp());
                    pruneExpired();
                    if (breaches.size() >= breachCount) {
                        if (localState.compareAndSet(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN)) {
                            openedAt = Instant.now();
                            emitTransition(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN);
                        }
                    }
                }
            }
            case OPEN -> {
                // No tracking while open — heuristic fallback is active, no LLM calls expected.
                // Stray events (from in-flight calls that completed after open) are ignored.
            }
            case HALF_OPEN -> {
                if (breach) {
                    if (localState.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN)) {
                        openedAt = Instant.now();
                        probeSuccessCount.set(0);
                        emitTransition(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN);
                    }
                } else {
                    int successes = probeSuccessCount.incrementAndGet();
                    if (successes >= probeSuccessThreshold) {
                        if (localState.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED)) {
                            breaches.clear();
                            probeSuccessCount.set(0);
                            emitTransition(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldDegrade() {
        checkRecoveryTimeout();
        return state() == CircuitBreakerState.OPEN;
    }

    @Override
    public CircuitBreakerState state() {
        var local = localState.get();
        if (local == CircuitBreakerState.CLOSED) {
            pruneExpired();
        }
        var external = externalState.get();
        return local.ordinal() >= external.ordinal() ? local : external;
    }

    private void checkRecoveryTimeout() {
        if (localState.get() == CircuitBreakerState.OPEN) {
            Instant opened = openedAt;
            if (opened != null && Instant.now().toEpochMilli() - opened.toEpochMilli() >= recoveryTimeoutMs) {
                if (localState.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                    probeSuccessCount.set(0);
                    emitTransition(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN);
                }
            }
        }
    }

    private void pruneExpired() {
        Instant cutoff = Instant.now().minusMillis(windowDurationMs);
        while (!breaches.isEmpty() && breaches.peekFirst().isBefore(cutoff)) {
            breaches.pollFirst();
        }
    }

    private void emitTransition(CircuitBreakerState from, CircuitBreakerState to) {
        transitionListener.accept(new CircuitBreakerTransition(from, to, Instant.now()));
    }

    public static class Builder {
        private final LlmInvocationPolicy policy;
        private final LatencyCircuitBreakerConfig config;
        private Supplier<CircuitBreakerState> externalState;
        private Consumer<CircuitBreakerTransition> transitionListener;

        private Builder(LlmInvocationPolicy policy, LatencyCircuitBreakerConfig config) {
            this.policy = policy;
            this.config = config;
        }

        public Builder externalState(Supplier<CircuitBreakerState> supplier) {
            this.externalState = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder onTransition(Consumer<CircuitBreakerTransition> listener) {
            this.transitionListener = Objects.requireNonNull(listener);
            return this;
        }

        public LatencyDegradationTrigger build() {
            return new LatencyDegradationTrigger(policy, config,
                externalState != null ? externalState : () -> CircuitBreakerState.CLOSED,
                transitionListener != null ? transitionListener : t -> {});
        }
    }
}
```

Imports needed:
```java
import io.casehub.api.model.stigmergy.CircuitBreakerState;
import io.casehub.platform.agent.budget.BudgetTelemetryEvent;
import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
```

- [ ] **Step 4: Run LatencyDegradationTriggerTest to verify it passes**

Run: `mvn --batch-mode test -pl agency-core -Dtest=LatencyDegradationTriggerTest`
Expected: all tests PASS

- [ ] **Step 5: Migrate all 6 topologies**

For each topology, replace every `new LatencyDegradationTrigger(policy, dp.latencyBreachWindow(), dp.latencyBreachCount(), dp.latencyBreachWindowDuration())` with `LatencyDegradationTrigger.builder(policy, dp.toLatencyConfig()).build()`.

The 11 construction sites:

| File | Line(s) | Old pattern | New pattern |
|------|---------|-------------|-------------|
| `HierarchicalTopology.java` | 129 | `new LatencyDegradationTrigger(defaultPolicy, dp.latencyBreachWindow(), dp.latencyBreachCount(), dp.latencyBreachWindowDuration())` | `LatencyDegradationTrigger.builder(defaultPolicy, dp.toLatencyConfig()).build()` |
| `DistributedTopology.java` | 93-95 | Same pattern with `defaultPolicy` | Same replacement |
| `GraduatedTopology.java` | 84 | Same pattern with `defaultPolicy` | Same replacement |
| `HybridTopology.java` | 102 | Same pattern with `defaultPolicy` (coordLatency) | Same replacement |
| `HybridTopology.java` | 110 | Same pattern with `defaultPolicy` (latency) | Same replacement |
| `HiveTopology.java` | 138 | Same pattern with `defaultPolicy` (coordLatency) | Same replacement |
| `HiveTopology.java` | 146 | Same pattern with `defaultPolicy` (latency) | Same replacement |
| `CentralisedTopology.java` | 154 | Inline `LlmInvocationPolicy.of(...)` (routingLatency) | `LatencyDegradationTrigger.builder(LlmInvocationPolicy.of(...), dp.toLatencyConfig()).build()` |
| `CentralisedTopology.java` | 155 | Inline `LlmInvocationPolicy.of(...)` (aggregationLatency) | Same pattern |
| `CentralisedTopology.java` | 156 | Inline `LlmInvocationPolicy.of(...)` (provisioningLatency) | Same pattern |

Use `ide_search_text` to find each site, then `ide_replace_text_in_file` or `ide_replace_member` to update. Remove unused `LatencyDegradationTrigger` import of `int windowSize` constructor if IntelliJ flags it.

Also remove the import of `LatencyDegradationTrigger` from topology files only if IntelliJ flags it as unused after the change — the import stays since the class is still referenced.

- [ ] **Step 6: Run full build**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS — all tests pass, all compilation errors resolved

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(#404): rewrite LatencyDegradationTrigger as three-state circuit breaker

CLOSED/OPEN/HALF_OPEN state machine with controlled recovery probing.
Builder API with optional external state supplier and transition listener.
Migrate all 6 topologies to builder pattern. Remove dead windowSize param.

Refs casehubio/agency#404"
```

### Task 4: CompositeDegradationTrigger State Derivation

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/degradation/CompositeDegradationTrigger.java`
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/degradation/DegradationTriggerTest.java`

**Interfaces:**
- Consumes: `StatefulDegradationTrigger` from Task 1, `LatencyDegradationTrigger` from Task 3

- [ ] **Step 1: Write failing tests for composite state derivation**

Add new tests to `DegradationTriggerTest`:

```java
@Test
void composite_state_derivesMostRestrictiveFromChildren() {
    var open = new CascadeDegradationTrigger();
    open.activate();
    var closed = new CascadeDegradationTrigger();
    var composite = new CompositeDegradationTrigger(List.of(open, closed));
    assertThat(composite.state()).isEqualTo(CircuitBreakerState.OPEN);
}

@Test
void composite_state_closedWhenAllChildrenClosed() {
    var composite = new CompositeDegradationTrigger(
        List.of(() -> false, () -> false));
    assertThat(composite.state()).isEqualTo(CircuitBreakerState.CLOSED);
}

@Test
void composite_state_delegatesToStatefulChildren() {
    var config = new LatencyCircuitBreakerConfig(1, Duration.ofMinutes(2),
        Duration.ofMillis(50), 1);
    var policy = LlmInvocationPolicy.of(
        Duration.ofSeconds(5), Duration.ofSeconds(8), Duration.ofSeconds(10));
    var latency = LatencyDegradationTrigger.builder(policy, config).build();
    latency.accept(new BudgetTelemetryEvent.Outcome("c", "m", 100, 11_000, Instant.now()));
    // breachCount=1 → OPEN

    var composite = new CompositeDegradationTrigger(List.of(latency, () -> false));
    assertThat(composite.state()).isEqualTo(CircuitBreakerState.OPEN);
}

@Test
void composite_state_emptyList_returnsClosed() {
    var composite = new CompositeDegradationTrigger(List.of());
    assertThat(composite.state()).isEqualTo(CircuitBreakerState.CLOSED);
}
```

Add required imports:
```java
import io.casehub.api.model.stigmergy.CircuitBreakerState;
import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;
import io.casehub.platform.agent.budget.BudgetTelemetryEvent;
import java.time.Duration;
import java.time.Instant;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency-core -Dtest=DegradationTriggerTest`
Expected: compilation failure — `CompositeDegradationTrigger` has no `state()` method

- [ ] **Step 3: Implement CompositeDegradationTrigger state derivation**

Use `ide_replace_member` on `CompositeDegradationTrigger`. Change `implements DegradationTrigger` to `implements StatefulDegradationTrigger`. Add imports and `state()` method:

```java
package io.casehub.agency.orchestration.degradation;

import io.casehub.api.model.stigmergy.CircuitBreakerState;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CompositeDegradationTrigger implements StatefulDegradationTrigger {

    private final List<DegradationTrigger> triggers;

    public CompositeDegradationTrigger(List<DegradationTrigger> triggers) {
        this.triggers = List.copyOf(Objects.requireNonNull(triggers));
    }

    @Override
    public boolean shouldDegrade() {
        return triggers.stream().anyMatch(DegradationTrigger::shouldDegrade);
    }

    @Override
    public CircuitBreakerState state() {
        return triggers.stream()
            .map(t -> t instanceof StatefulDegradationTrigger s
                      ? s.state()
                      : t.shouldDegrade() ? CircuitBreakerState.OPEN : CircuitBreakerState.CLOSED)
            .max(Comparator.comparingInt(CircuitBreakerState::ordinal))
            .orElse(CircuitBreakerState.CLOSED);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency-core -Dtest=DegradationTriggerTest`
Expected: all tests PASS (original 7 + 4 new)

- [ ] **Step 5: Run full build**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/degradation/CompositeDegradationTrigger.java \
      agency-core/src/test/java/io/casehub/agency/orchestration/degradation/DegradationTriggerTest.java
git commit -m "feat(#404): CompositeDegradationTrigger implements StatefulDegradationTrigger

Derives state from children: StatefulDegradationTrigger children expose
their state directly, plain DegradationTrigger children infer OPEN/CLOSED
from shouldDegrade(). Most-restrictive child wins.

Refs casehubio/agency#404"
```

---

## References

- [2026-11-17-latency-circuit-breaker-integration-design.md] — design spec this plan implements
- `agency-core/.../degradation/LatencyDegradationTrigger.java` — primary rewrite target
- `agency-core/.../degradation/DegradationPolicy.java` — config record changes
- `agency-core/.../degradation/CompositeDegradationTrigger.java` — state derivation
- `agency-core/.../degradation/DegradationTrigger.java` — preserved @FunctionalInterface
- `agency-core/.../topology/HierarchicalTopology.java:129` — topology wiring pattern (6 files, 11 sites)
- `engine/api/.../stigmergy/CircuitBreakerState.java` — platform CB enum reuse
- GitHub #404 — focal issue
