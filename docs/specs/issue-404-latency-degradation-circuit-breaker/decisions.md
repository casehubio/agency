# Decisions — #404 LatencyDegradationTrigger Circuit Breaker Integration

## D1: Proper three-state circuit breaker model

**Choice:** Rewrite LatencyDegradationTrigger as a CLOSED/OPEN/HALF_OPEN state machine with controlled recovery probing
**Alternatives:**
- Keep boolean-only with external supplier bolted on — doesn't fix the oscillation problem during recovery
- Delegate entirely to an external CB library (Resilience4j) — adds a dependency for something the trigger already needs to own; no Resilience4j anywhere in the platform
**Rationale:** The trigger already behaves like a circuit breaker but with degenerate recovery (instant flood on window expiry). A proper state machine fixes oscillation, makes state observable, and aligns with engine-api's CircuitBreakerState. The HALF_OPEN→probe→close path works naturally with InFlightGate's at-most-one guarantee.
**Trade-offs:** More complex state management inside the trigger; all 6 topologies need constructor updates
**Sources:** DegradationAwareSummariser.java, HierarchicalTopology.java:129, engine-api CircuitBreakerState, ImprovementCircuitBreaker.java
**Exploration:** deep-analysis
**Status:** captured

## D2: StatefulDegradationTrigger interface layer

**Choice:** New interface `StatefulDegradationTrigger extends DegradationTrigger` adding `CircuitBreakerState state()`
**Alternatives:**
- Add state() directly to DegradationTrigger — forces state semantics on BudgetDegradationTrigger and CascadeDegradationTrigger which are inherently boolean
- Expose state only on the concrete class — consumers can't program against the abstraction; CompositeDegradationTrigger can't derive state generically
**Rationale:** Only LatencyDegradationTrigger and CompositeDegradationTrigger naturally have CB state. BudgetDegradationTrigger (stateless view query) and CascadeDegradationTrigger (boolean flag) don't need it. The extension preserves DegradationTrigger as @FunctionalInterface.
**Trade-offs:** Two interfaces instead of one; instanceof checks in CompositeDegradationTrigger to derive child state
**Sources:** DegradationTrigger.java, BudgetDegradationTrigger.java, CascadeDegradationTrigger.java
**Exploration:** deep-analysis
**Status:** captured

## D3: Reuse CircuitBreakerState from engine-api

**Choice:** Use existing `io.casehub.api.model.stigmergy.CircuitBreakerState` (CLOSED/OPEN/HALF_OPEN) from engine-api
**Alternatives:**
- Define a new enum in agency's degradation package — duplicates the exact same concept; can't interoperate with engine's CB infrastructure
**Rationale:** engine-api is already a compile dependency. The enum is exactly what we need. Using it means the degradation subsystem speaks the same language as ImprovementCircuitBreaker, EvolutionTicker, and CircuitBreakerStateChangedEvent.
**Trade-offs:** Couples agency's degradation model to engine-api's enum; if engine-api ever changes the enum, agency is affected
**Sources:** engine/api CircuitBreakerState.java, agency pom.xml dependency on engine-api
**Exploration:** quick
**Status:** captured

## D4: External state supplier for coordination

**Choice:** Optional `Supplier<CircuitBreakerState>` input on LatencyDegradationTrigger; most-restrictive-wins composition of local and external state
**Alternatives:**
- No external input — leaves coordination impossible; each trigger island is permanently isolated
- Bidirectional bridge (trigger both reads and writes external state) — writing external state is the transition listener's job, not the trigger's; mixing input and output muddies the contract
**Rationale:** Separates concerns: the trigger reads external state via supplier, emits transitions via listener. A coordinator component (follow-up) can aggregate transitions from multiple triggers and provide combined state. The supplier is the primitive that makes coordination possible.
**Trade-offs:** Coordination requires a separate registry/coordinator (not in this issue's scope); supplier adds a null-check per shouldDegrade() call
**Sources:** ImprovementCircuitBreaker.java (platform CB pattern), capability-ownership.md (AgentStateStore degradation tracking)
**Exploration:** deep-analysis
**Status:** captured

## D5: Remove unused latencyBreachWindow and use builder API

**Choice:** Remove dead `windowSize` constructor param and `latencyBreachWindow` from DegradationPolicy; switch to builder pattern for LatencyDegradationTrigger construction
**Alternatives:**
- Keep windowSize and implement count-based windowing — adds complexity for no benefit; time-based windowing is the proven model and what the implementation actually does
- Keep expanded constructor with new CB params — 6+ params, no optional params support (external supplier, transition listener)
**Rationale:** The windowSize parameter was never stored or used — dead code since inception. Time-based windowing is simpler and sufficient. Builder supports required (policy, config) + optional (external supplier, transition listener) cleanly.
**Trade-offs:** Breaking change to DegradationPolicy record (removing a field); all 6 topologies need constructor→builder migration
**Depends on:** D1 (new CB config params justify builder)
**Sources:** LatencyDegradationTrigger.java:19-26 (windowSize never stored), DegradationPolicy.java
**Exploration:** quick
**Status:** captured
