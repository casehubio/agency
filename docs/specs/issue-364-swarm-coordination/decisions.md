# Decisions — #361 Distributed Per-Agent LLM Coordination

## D1: Cross-repo scope

**Choice:** Agency + engine (dual repo)
**Alternatives:**
- Agency-only — use existing engine SPIs without changes. Limited: no per-agent observation feed, no write-back path.
- Spike first — minimal proof-of-concept before full design. Delays delivery without adding clarity given the existing skeleton.
**Rationale:** The per-agent LLM reasoner needs to read observations from and write signals back to the engine's stigmergy environment. Existing SPIs expose swarm-level events but not per-agent observation/signal state. Engine#1314 filed for the complement.
**Trade-offs:** Cross-repo coordination cost. Mitigated by filing engine#1314 as an independent issue.
**Sources:** `SwarmCoordinationListener.java` (engine SPI), `AgentWorkLifecycleListener.java` (engine SPI), `DistributedTopology.java` (agency skeleton), engine#1314
**Exploration:** quick
**Status:** captured

## D2: Decision distribution model — signal-based full autonomy

**Choice:** All three decision types distributed (routing, aggregation, provisioning) via stigmergy signals. Agents never directly execute shared-state mutations — they emit signals into the environment.
**Alternatives:**
- Local routing + aggregation only — provisioning stays centralised. Simpler but artificially limits the distributed model.
- Routing only — minimal starting point. Leaves centralised bottlenecks in aggregation and provisioning.
**Rationale:** Stigmergy signal reinforcement naturally de-duplicates conflicting decisions. Engine's consensus detection (`detectSignalConsensus()`) triggers provisioning only when threshold is met. Work state FSM serialises task assignment. Aggregation achieves eventual consistency via gossip-style observation propagation.
**Trade-offs:** More complex than centralised model. Emergent behaviour harder to debug. Mitigated by: signal-based approach makes conflicts impossible (idempotent reinforcement), engine handles serialisation, budget system governs cost.
**Sources:** `StigmergyCoordinator.detectSignalConsensus()`, `SwarmProvisioner` (already checks `swarm:need-capacity:*` signals), `DecisionOutcome.WriteBack` (carries `List<SignalDeposit>`), `SwarmConvergenceEvaluator`
**Exploration:** deep-analysis
**Status:** captured

## D3: Per-agent observation sources

**Choice:** All four sources — agent's role + team context, visible stigmergy signals, peer work states, and swarm-level progress.
**Alternatives:**
- Signals + peer states only — skip swarm-level progress for pure locality. Limits reasoning.
- Minimal (signals only) — simplest prompt but insufficient context for meaningful decisions.
**Rationale:** Fullest observation context produces best LLM reasoning. Budget system governs cost, not prompt size. All four sources already exist in the platform.
**Trade-offs:** Larger prompts per agent = higher token cost. Mitigated by budget awareness and configurable per-agent model.
**Sources:** `AgentObservationProvider` (engine#1314), `AgentWorkLifecycleListener`, `SwarmCoordinationListener`, `SwarmContext`
**Exploration:** quick
**Status:** captured

## D4: Autonomy level

**Choice:** Uniform full autonomy (`DecisionScope.FULL_AUTONOMY`) for all agents in a distributed topology.
**Alternatives:**
- Per-role autonomy — vary by agent role (senior=FULL, junior=SUPERVISED). Adds prompt and validation complexity without clear benefit in the stigmergy model.
**Rationale:** Mixed autonomy is better served by `HybridTopology` or `GraduatedTopology`. `DistributedTopology` is the pure stigmergy model — all agents are equal peers.
**Trade-offs:** No safety net for inexperienced agents. Mitigated by budget limits and degradation triggers per loop.
**Sources:** `DistributedTopology.java` (already hardcodes FULL_AUTONOMY), `DecisionScope` enum
**Exploration:** quick
**Status:** captured

## D5: LLM output format

**Choice:** Structured JSON for all decision types (signals, task claims, synthesis).
**Alternatives:**
- Hybrid — structured signals, free-text synthesis. More natural for the LLM but harder to parse into `DecisionOutcome.WriteBack`.
**Rationale:** Consistent with centralised model pattern. Reliable parsing via `StructuredAgentInvoker.invokeRaw()`. All three centralised LLM sites use this pattern.
**Trade-offs:** Constrains LLM output format. Mitigated by well-designed schema.
**Sources:** `StructuredAgentInvoker`, `SwarmRoutingStrategy` (JSON parsing), `CollectiveReasoningAggregation` (JSON parsing)
**Exploration:** quick
**Status:** captured

## D6: API surface — dual parity

**Choice:** Both fluent Java builder DSL and YAML surface from the start. `DistributedTopologyBuilder` for Java, `TopologySpec.Distributed` for YAML, compiled via `DistributedTopologyCompiler`.
**Alternatives:**
- Code-only — add YAML later once API stabilises. Breaks parity with other topologies.
**Rationale:** All other topologies (Centralised via `SwarmCoordinatorBuilder`, Hive via `HiveTopologyCompiler`) maintain dual surface. Consistency is a platform convention.
**Trade-offs:** More upfront work. Mitigated by established patterns to follow.
**Sources:** `SwarmCoordinatorBuilder`, `HiveTopologyCompiler`, `TopologySpec.Hive`
**Exploration:** quick
**Status:** captured

## D7: Model configuration

**Choice:** Same `AgentProvider`, configurable per-agent override via builder/YAML (e.g., lighter model for routine observations).
**Alternatives:**
- Always same provider — simpler but no cost optimisation lever beyond budget system.
**Rationale:** N per-agent LLM calls vs 1 centralised call means cost multiplies by swarm size. Per-agent model override lets consumers use smaller models for routine ticks and full models for novel situations.
**Trade-offs:** Configuration complexity. Mitigated by sensible default (use topology's provider unless overridden).
**Sources:** `AgentRefSpec` (already has optional `model` field), `DecisionLoopDescriptor`
**Exploration:** quick
**Status:** captured
