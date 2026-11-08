# Decisions — #417 FORMING → ACTIVE transition

## D1: Separate activator vs inline in orchestrator

**Choice:** Separate `FormationActivator` class in `deliberation.coalition.formation`
**Alternatives:**
- Extend `BottomUpFormationOrchestrator` to call `activateGroup()` inline — simpler but conflates formation and activation concerns, breaks existing contract
**Rationale:** Formation (negotiation + group creation) and activation (commitment validation + state transition) are distinct concerns. Callers may want deferred activation. Follows the `VouchService` / `ConversationOrchestrator` composition root pattern.
**Trade-offs:** Two-step API for callers who always want immediate activation (minor — a convenience method can compose both)
**Sources:** `BottomUpFormationOrchestrator.java`, `FormationResult.java`, predecessor spec #410 §Out of scope
**Exploration:** quick
**Status:** captured

## D2: Result type — new sealed type vs extend FormationResult

**Depends on:** D1 (separate activator needs its own result type)
**Choice:** New `ActivationResult` sealed type with `Activated(groupInstanceId, intention)`, `MonitorRejected(signal)`, `ActivationFailed(reason)`
**Alternatives:**
- Extend `FormationResult` with activation variants — simpler for composed callers but muddies the sealed contract, mixing two lifecycle phases in one type
**Rationale:** Formation and activation are distinct lifecycle phases. Separate sealed types keep each narrowly scoped to its phase's outcomes.
**Trade-offs:** Callers composing both steps must handle two result types (minor ergonomic cost)
**Sources:** `FormationResult.java`, `JointIntention.java`, `ReconsiderationSignal.java`
**Exploration:** quick
**Status:** captured

## D6: Bean type — CompiledHive vs HiveTopology only

**Depends on:** D5 (recorder produces the bean)
**Choice:** Register `CompiledHive` as a `@Singleton` synthetic bean
**Alternatives:**
- Register `HiveTopology` only — simpler but discards `roleSpecs` and `agentRoles` that downstream consumers (#419, #420) need
**Rationale:** `CompiledHive` is the compiler's output type carrying all compiled artifacts. Downstream issues need role specs for society formation wiring.
**Trade-offs:** Consumers inject `CompiledHive` instead of `HiveTopology` directly (minor — `compiledHive.topology()` is trivial)
**Sources:** `HiveTopologyCompiler.CompiledHive`, issue #419, issue #420
**Exploration:** quick
**Status:** captured

## D5: Recorder execution time — RUNTIME_INIT vs STATIC_INIT

**Depends on:** D4 (build step feeds recorder)
**Choice:** `RUNTIME_INIT` — recorder takes `TopologySpec.Hive` spec + `AgentProvider` (runtime CDI bean)
**Alternatives:**
- `STATIC_INIT` with deferred factory — more complex, no benefit since `AgentProvider` is only available at runtime
**Rationale:** `HiveTopologyCompiler.compile()` needs `AgentProvider`. `RUNTIME_INIT` is the established pattern in `AgenticRecorder` (see `createPromptOptimisationCompiler`).
**Trade-offs:** None significant — `RUNTIME_INIT` is standard for beans needing runtime dependencies
**Sources:** `AgenticRecorder.java:23-29`, `HiveTopologyCompiler.compile()`
**Exploration:** quick
**Status:** captured

---

# Decisions — #418 HiveTopologyCompiler deployment wiring

## D4: Topology discovery — extract from patterns vs standalone YAML

**Choice:** Extract `TopologySpec.Hive` from already-parsed `AgenticPatternsBuildItem` in a new `@BuildStep`
**Alternatives:**
- Standalone `META-INF/topology.yaml` discovery — more flexible but no current consumer needs it, adds redundant discovery path
**Rationale:** Patterns already carry topology specs. YAGNI — no consumer needs standalone topology YAML. Follows existing build-item composition pattern.
**Trade-offs:** Topologies can only be declared within pattern YAML, not standalone (easily added later if needed)
**Sources:** `AgenticYamlProcessor.java`, `PatternDefinition.Swarm.topology`, `AgenticPatternsBuildItem.java`
**Exploration:** quick
**Status:** captured

## D3: IntentionMonitor — optional vs required

**Depends on:** D1 (activator uses monitor for validation)
**Choice:** Optional `@Nullable IntentionMonitor` in constructor
**Alternatives:**
- Required monitor, callers pass `intention -> null` for unconditional activation — adds ceremony for the common case
**Rationale:** Nullable optional SPIs are the established pattern in this codebase (e.g., nullable `DegradationCascadeFactory`, nullable `FeedbackHandler`). Unconditional activation is legitimate — negotiation already validated consensus.
**Trade-offs:** Null check in activation path (trivial)
**Sources:** `IntentionMonitor.java`, `ComposableTopology` (nullable pattern precedent)
**Exploration:** quick
**Status:** captured
