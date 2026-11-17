# LatencyDegradationTrigger Circuit Breaker Integration

**Issue:** casehubio/agency#404
**Branch:** `issue-404-latency-degradation-circuit-breaker`
**Date:** 2026-11-17

## Problem

`LatencyDegradationTrigger` tracks LLM call latency independently via a
sliding window of `BudgetTelemetryEvent.Outcome` events. When the breach
count in the window exceeds a threshold, `shouldDegrade()` returns `true`
and `DegradationAwareSummariser` routes to the heuristic fallback.

Four problems:

1. **Uncontrolled recovery.** When all breach entries expire from the
   time-based window, `shouldDegrade()` instantly returns `false`. The
   next `summarise()` call goes through the LLM delegate at full rate. If
   the latency issue is intermittent, this causes rapid
   degrade→recover→flood→degrade oscillation.

2. **No observable state.** `DegradationAwareSummariser` only sees a
   boolean. It maintains its own `previouslyDegraded` AtomicBoolean to
   synthesize transition events — duplicating state the trigger should
   own. Topologies and monitoring have no way to query trigger health.

3. **No external input.** The trigger is hermetically sealed. If the
   platform knows the LLM provider is degraded (via eidos
   `AgentStateStore`, engine's `ImprovementCircuitBreaker`, or any
   external system), it cannot communicate that to the degradation
   subsystem.

4. **No coordination.** Each topology creates independent
   `LatencyDegradationTrigger` instances. If agent A's loop discovers
   latency issues with the same LLM provider, agent B's loop
   independently discovers the same through its own failed calls.

Additionally, the constructor accepts an `int windowSize` parameter
that is never stored or used — dead code since inception.

## Design

### StatefulDegradationTrigger Interface

New interface extending `DegradationTrigger`:

```java
public interface StatefulDegradationTrigger extends DegradationTrigger {
    CircuitBreakerState state();
}
```

Uses `io.casehub.api.model.stigmergy.CircuitBreakerState` from engine-api
(already a compile dependency). `DegradationTrigger` remains
`@FunctionalInterface` with `boolean shouldDegrade()` — no change.

Only triggers with natural circuit breaker semantics implement
`StatefulDegradationTrigger`:
- `LatencyDegradationTrigger` — primary implementor
- `CompositeDegradationTrigger` — derives state from children

Triggers without CB semantics keep plain `DegradationTrigger`:
- `BudgetDegradationTrigger` — stateless view query
- `CascadeDegradationTrigger` — boolean flag

### CircuitBreakerTransition Record

```java
public record CircuitBreakerTransition(
    CircuitBreakerState from,
    CircuitBreakerState to,
    Instant timestamp
) {}
```

Emitted by `LatencyDegradationTrigger` on local state changes via a
`Consumer<CircuitBreakerTransition>` wired at construction. Downstream
uses: telemetry, logging, coordination aggregators.

### LatencyDegradationTrigger State Machine

Three-state machine replacing the implicit boolean model:

```
CLOSED ──[breaches >= threshold]──▶ OPEN
  ▲                                   │
  │                                   │ [recoveryTimeout elapsed]
  │                                   ▼
  └──[probes >= successThreshold]── HALF_OPEN
                                      │
                                      │ [any probe failure]
                                      └──────▶ OPEN (timer reset)
```

**CLOSED:** Normal operation. `accept()` tracks breaches in the sliding
window. `shouldDegrade()` returns `false`. When breach count >=
threshold, transitions to OPEN.

**OPEN:** Degraded. `shouldDegrade()` returns `true`. All calls route to
heuristic fallback. No new breach tracking needed (no LLM calls happen
while degraded). When `recoveryTimeout` elapses, transitions to
HALF_OPEN on the next `shouldDegrade()` check.

**HALF_OPEN:** Recovery probing. `shouldDegrade()` returns `false`,
allowing a probe call through the LLM delegate. `InFlightGate` ensures
at-most-one call per loop, so probes are naturally serialized.
- `accept()` with latencyMs <= errorAfterMs: probe success, increment
  counter. When counter >= `probeSuccessThreshold`, transition to CLOSED
  (clear breach window).
- `accept()` with latencyMs > errorAfterMs: probe failure, transition
  to OPEN (reset recovery timer).

**Thread safety:** `AtomicReference<CircuitBreakerState>` for state,
CAS for all transitions, volatile `Instant openedAt`, `AtomicInteger`
probe counter. Lock-free on the hot path (`shouldDegrade()` and
`accept()` are both called frequently).

**`shouldDegrade()` semantics with external state:**

```
effectiveState = max(localState, externalState)   // OPEN > HALF_OPEN > CLOSED
return effectiveState == OPEN
```

When external state is OPEN and local is CLOSED, the trigger degrades
without firing its own state machine. When external state clears, the
trigger's local state takes over. The external supplier is consulted on
every `shouldDegrade()` call — no caching.

`state()` returns the same effective state: `max(localState, externalState)`.

**Interaction: external OPEN blocks local probing.** If the local state
is HALF_OPEN but external state is OPEN, the effective state is OPEN and
`shouldDegrade()` returns `true`. Local recovery probing is suppressed
until external state clears. This is correct — external OPEN means the
platform has evidence the local trigger lacks.

### External State Supplier

Optional `Supplier<CircuitBreakerState>` on the trigger. When absent,
external state is CLOSED (no effect).

This is the coordination primitive. A follow-up can wire a shared
supplier backed by a provider-keyed registry that aggregates state from
multiple triggers. This issue provides the hook; the registry is not in
scope.

The supplier also enables integration with platform CB infrastructure:
an adapter from `ImprovementCircuitBreaker` state, from eidos
`AgentStateStore` degradation state, or from any external CB library.

### CompositeDegradationTrigger Enhancement

`CompositeDegradationTrigger` implements `StatefulDegradationTrigger`.
State derivation from children:

```java
public CircuitBreakerState state() {
    return triggers.stream()
        .map(t -> t instanceof StatefulDegradationTrigger s
                  ? s.state()
                  : t.shouldDegrade() ? OPEN : CLOSED)
        .max(Comparator.comparingInt(CircuitBreakerState::ordinal))
        .orElse(CLOSED);
}
```

Most-restrictive child wins. For plain `DegradationTrigger` children,
infer OPEN if `shouldDegrade()=true`, CLOSED otherwise.

### LatencyCircuitBreakerConfig Record

New configuration record replacing the spread of constructor params:

```java
public record LatencyCircuitBreakerConfig(
    int breachCount,
    Duration windowDuration,
    Duration recoveryTimeout,
    int probeSuccessThreshold
) {
    public LatencyCircuitBreakerConfig {
        if (breachCount < 1)
            throw new IllegalArgumentException("breachCount must be >= 1");
        Objects.requireNonNull(windowDuration);
        if (windowDuration.isNegative() || windowDuration.isZero())
            throw new IllegalArgumentException("windowDuration must be positive");
        Objects.requireNonNull(recoveryTimeout);
        if (recoveryTimeout.isNegative() || recoveryTimeout.isZero())
            throw new IllegalArgumentException("recoveryTimeout must be positive");
        if (probeSuccessThreshold < 1)
            throw new IllegalArgumentException("probeSuccessThreshold must be >= 1");
    }

    public static LatencyCircuitBreakerConfig defaults() {
        return new LatencyCircuitBreakerConfig(3, Duration.ofMinutes(2),
            Duration.ofSeconds(30), 2);
    }
}
```

### DegradationPolicy Changes

Remove `latencyBreachWindow` (dead field — never enforced by the
trigger). Add `recoveryTimeout` and `probeSuccessThreshold`.

```java
public record DegradationPolicy(
    CascadeThresholdType thresholdType,
    double cascadeThreshold,
    Map<BudgetPriority, Double> priorityWeights,
    int latencyBreachCount,
    Duration latencyBreachWindowDuration,
    Duration recoveryTimeout,
    int probeSuccessThreshold
) { ... }
```

`defaults()` gains: `recoveryTimeout = Duration.ofSeconds(30)`,
`probeSuccessThreshold = 2`.

Convenience method to extract the config:

```java
public LatencyCircuitBreakerConfig toLatencyConfig() {
    return new LatencyCircuitBreakerConfig(
        latencyBreachCount, latencyBreachWindowDuration,
        recoveryTimeout, probeSuccessThreshold);
}
```

### Builder API

```java
public static Builder builder(LlmInvocationPolicy policy,
                               LatencyCircuitBreakerConfig config) {
    return new Builder(policy, config);
}

public static class Builder {
    private Supplier<CircuitBreakerState> externalState;
    private Consumer<CircuitBreakerTransition> transitionListener;

    public Builder externalState(Supplier<CircuitBreakerState> supplier) { ... }
    public Builder onTransition(Consumer<CircuitBreakerTransition> listener) { ... }
    public LatencyDegradationTrigger build() { ... }
}
```

### Topology Updates

All 6 topologies (`Hierarchical`, `Distributed`, `Graduated`, `Hybrid`,
`Hive`, `Centralised`) update from:

```java
new LatencyDegradationTrigger(policy, dp.latencyBreachWindow(),
    dp.latencyBreachCount(), dp.latencyBreachWindowDuration())
```

to:

```java
LatencyDegradationTrigger.builder(policy, dp.toLatencyConfig()).build()
```

No external supplier or transition listener wired in this issue — that
is follow-up work. The topologies get the proper state machine and
controlled recovery immediately.

## Testing

**New tests for LatencyDegradationTrigger:**
- State transitions: CLOSED→OPEN on breach threshold, OPEN→HALF_OPEN
  after recovery timeout, HALF_OPEN→CLOSED after N successful probes,
  HALF_OPEN→OPEN on probe failure
- Transition listener fires on each state change with correct from/to
- External state supplier: local CLOSED + external OPEN → shouldDegrade
  true; external clears → local state takes over
- External HALF_OPEN + local OPEN → effective OPEN (most restrictive)
- Probe counter resets on HALF_OPEN→OPEN→HALF_OPEN cycle
- Thread safety: concurrent accept() and shouldDegrade() (stress test)

**Updated CompositeDegradationTrigger tests:**
- state() with mixed StatefulDegradationTrigger and plain children
- Most-restrictive derivation across children

**Existing topology tests** should continue to pass — behavioral
contract unchanged (`shouldDegrade()` semantics are preserved).

**DegradationPolicy tests:**
- Validation of new fields
- defaults() includes recovery config
- toLatencyConfig() extraction

## Scope

**In scope:**
- Three-state machine in LatencyDegradationTrigger
- StatefulDegradationTrigger interface
- CircuitBreakerTransition record
- LatencyCircuitBreakerConfig record
- External state supplier SPI (Supplier<CircuitBreakerState>)
- Transition listener (Consumer<CircuitBreakerTransition>)
- CompositeDegradationTrigger state derivation
- DegradationPolicy field changes
- 6 topology constructor migrations
- Remove dead windowSize parameter

**Not in scope:**
- Provider-keyed circuit breaker registry (follow-up: coordination)
- DegradationAwareSummariser changes (still uses shouldDegrade() boolean)
- eidos/engine adapter implementations for the external supplier
- Resilience4j or any external library integration

## References

- `agency-core/.../degradation/LatencyDegradationTrigger.java` — current implementation
- `agency-core/.../degradation/DegradationTrigger.java` — @FunctionalInterface SPI
- `agency-core/.../degradation/DegradationAwareSummariser.java` — primary consumer
- `agency-core/.../degradation/DegradationPolicy.java` — config record
- `agency-core/.../topology/HierarchicalTopology.java:129` — typical topology wiring
- `engine/api/.../stigmergy/CircuitBreakerState.java` — platform CB enum
- `engine/runtime-core/.../improvement/ImprovementCircuitBreaker.java` — platform CB pattern
- `engine/docs/specs/issue-383-per-agent-degradation-cascade-policy/` — original design spec
- `parent/docs/platform/capability-ownership.md` — AgentStateStore, DegradationReason ownership
