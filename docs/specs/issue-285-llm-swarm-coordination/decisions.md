## D1: Swarm topology placement

**Choice:** 9th PatternDefinition topology — full parity with existing topologies
**Alternatives:**
- Secondary definition (like NegotiationDefinition) — better if swarm shape diverges from 5-SPI model, but breaks Java/YAML parity expectation
- Hybrid topology + companion spec — two-level YAML adds complexity without clear benefit
**Rationale:** Java/YAML parity is a hard requirement. All 8 existing topologies follow the sealed record + PatternCompiler pattern. A 9th topology is the most natural addition.
**Trade-offs:** Swarm must map onto the 5-SPI ExecutionModel (routing/decomposition/activation/aggregation/termination), which constrains how coordination logic is expressed
**Sources:** PatternDefinition.java, PatternCompiler.java, PatternType enum, issue #285
**Exploration:** quick
**Status:** captured

## D2: LLM capability scope

**Choice:** All four capabilities in scope — team formation, experience replay, collective reasoning, adaptive coordination
**Alternatives:**
- Core two (team + replay) — ships faster but misses the adaptive loop that makes swarms valuable
- Core three (team + replay + adaptive) — drops collective reasoning, but that's the synthesis mechanism adaptive coordination depends on
**Rationale:** L/High issue warrants full set. The four capabilities compose naturally: experience replay informs team formation, collective reasoning feeds adaptive coordination. Shipping partial breaks the feedback loop.
**Trade-offs:** Larger implementation surface; risk of scope creep in each capability
**Sources:** Issue #285 body, SwarmExp paper (arXiv:2608.30661)
**Exploration:** quick
**Status:** captured

## D3: Engine integration model

**Choice:** Implement engine SPIs for provisioning veto + use ChoreographedDriver for event-driven execution. SwarmProvisioningAdvisor handles scaling decisions (should we add/remove agents?). Coordination intelligence (team formation, work distribution, collective reasoning) lives in the coordinator's own prompt/invocation pipeline, not in the provisioning SPI.
**Alternatives:**
- Self-contained with engine types — more independent but duplicates mechanical swarm logic
- Thin adapter + rich agency layer — adds an abstraction layer between SPI and real logic without clear benefit
**Rationale:** Clean dependency direction. Agency has provided dependency on engine-api. SwarmProvisioningAdvisor is one integration point for scaling decisions (`shouldProvision: boolean`). The real coordination intelligence is agency-owned and uses the coordinator's prompt assembly, not the advisor contract.
**Trade-offs:** Couples agency's provisioning advice to engine's lifecycle; coordination intelligence is agency-owned and independent
**Sources:** SwarmProvisioningAdvisor SPI (engine-api), ChoreographedDriver (agency-core), SwarmProvisioner (engine runtime)
**Exploration:** quick
**Status:** revised (R1-02: clarified provisioning veto vs coordination intelligence boundary)

## D4: Experience replay mechanism

**Choice:** PromptSection + ExperienceAnalyser — SwarmExperienceSection formats CBR team-level traces into coordinator's system prompt
**Alternatives:**
- Dedicated ExperienceReplayProvider SPI — over-engineers what's a prompt construction concern
- Inline in coordinator — simpler but not composable or YAML-configurable
**Rationale:** PromptSection is the established pattern for contributing context to LLM prompts. ExperienceAnalyser already provides worker success rates and plan trace data. A SwarmExperienceSection composes naturally with the prompt assembly pipeline.
**Trade-offs:** Limited to what fits in a system prompt; very large experience sets need pre-filtering heuristics
**Sources:** PromptSection SPI (speech-api), ExperienceAnalyser (engine-api), CbrAgentRoutingStrategy (agency routing.agent)
**Exploration:** quick
**Status:** captured

## D5: Coordination structure level

**Choice:** Light scaffolding — LLM drives coordination decisions
**Alternatives:**
- Structured phases (form → distribute → observe → adapt) — more predictable but constrains self-organisation
- Configurable structure level — flexible but adds complexity for uncertain benefit
**Rationale:** Aligns with "Drop the Hierarchy" paper finding that minimal scaffolding produces better self-organisation. The topology provides the event loop, team/role awareness, and experience context; the LLM decides how to coordinate.
**Trade-offs:** Less predictable behaviour; harder to debug than structured phases; LLM quality directly determines coordination quality
**Sources:** "Drop the Hierarchy and Roles" (arXiv:2603.28990), issue #285
**Exploration:** quick
**Status:** captured

## D6: Overall architecture

**Choice:** LLM Coordinator as unified SPI composition — LlmSwarmCoordinator composes SwarmRoutingStrategy, CollectiveReasoningAggregation, ConvergenceTermination, SwarmExperienceSection behind a single composition root
**Alternatives:**
- Specialised SwarmDriver subclass — cleaner swarm semantics but breaks the pattern where all topologies share the same driver → SPI composition; PatternCompiler needs special case
- Decomposition-centric swarm — elegant decomposition SPI mapping but forces swarm into task-breakdown mental model; doesn't support adaptive redistribution
**Rationale:** Stays within existing 5-SPI ExecutionModel so PatternCompiler works naturally. Concentrates LLM intelligence in coordinator with full context for reasoning. YAML surface is clean and consistent with existing topologies.
**Trade-offs:** Coordinator holds significant responsibility; single point of LLM reasoning rather than distributed intelligence. This is a deliberate v1 trade-off — full distributed per-agent LLM coordination is XL/Very High complexity. Engine's mechanical RoleTracker/TeamDetector handle emergent role/team formation; the LLM adds reasoning about what the mechanical layer detects, not replacing distributed emergence.
**Sources:** OrchestratedDriver/ChoreographedDriver (agency-core), PatternCompiler (agentic-yaml), ConvergenceTermination (agency-core)
**Exploration:** quick
**Status:** revised (R1-04: acknowledged centralisation as deliberate v1 scope boundary)

## D7: Team formation reasoning model

**Choice:** Batch reasoning with pre-filter fallback — one LLM call with full agent roster when roster ≤ configurable threshold (default 12). When roster exceeds threshold, CoalitionEvaluator heuristic pre-filters to top candidates by capability coverage, then LLM reasons about the shortlist.
**Alternatives:**
- Iterative selection — evaluate one candidate at a time against growing team; lower per-call cost but loses holistic composition reasoning
- Pure batch (no fallback) — simpler but context window overflow on large rosters is a hard failure
**Rationale:** Batch reasoning lets the LLM consider complementary capabilities simultaneously. Pre-filter fallback prevents context window overflow for large agent pools without sacrificing reasoning quality for typical swarm sizes (≤12 agents).
**Trade-offs:** Pre-filter may exclude agents the LLM would have selected; threshold tuning is domain-dependent
**Depends on:** D6 (coordinator architecture)
**Sources:** CoalitionEvaluator (agency deliberation/coalition), SwarmBootstrapContext (engine-api)
**Exploration:** quick
**Status:** revised (R1-06: added pre-filter fallback for large rosters)

## D8: Collective reasoning mechanism

**Choice:** AggregationStrategy with LLM summariser — CollectiveReasoningAggregation collects AgentResults from members, feeds through ContentSummariser to produce synthesised view
**Alternatives:**
- ObservationDispatcher pipeline — reuses topological sort and handler precedence; more structured but adds handler registration complexity for what's fundamentally a synthesis task
- Dedicated SwarmSynthesiser SPI — clean abstraction but duplicates what ContentSummariser already handles
**Rationale:** AggregationStrategy is the existing SPI for combining agent outputs. ContentSummariser is the proven LLM synthesis pattern. Composing both reuses established abstractions without new SPIs.
**Trade-offs:** Aggregation happens at fixed points in the driver loop, not continuously; fine for iteration-based synthesis
**Depends on:** D6 (coordinator architecture)
**Sources:** AggregationStrategy SPI (agency orchestration/aggregation), ContentSummariser (summarisation-api), LlmContentSummariser (agency perception/llm)
**Exploration:** quick
**Status:** captured

## D9: Adaptive coordination triggers

**Choice:** Engine swarm events via ChoreographedDriver — events arrive as DriverEvents through a SwarmEventSource bridge (EventSource impl). Agency cannot depend on engine-runtime-core where SwarmEvent lives, so the bridge consumes events via an engine-api SPI (SwarmCoordinationListener or equivalent) that engine-runtime implements.
**Alternatives:**
- Tick-based polling — fixed interval, simpler but wastes cycles and misses fast changes
- Threshold-triggered — only re-evaluate on metric threshold crossings; efficient but requires domain-specific threshold tuning
**Rationale:** Natural fit with D3 (ChoreographedDriver) and D5 (light scaffolding). The event bridge translates engine's swarm lifecycle events into DriverEvents without crossing the engine-api → engine-runtime dependency boundary. Coalescing via EventConcurrencyPolicy prevents LLM call storms.
**Trade-offs:** Requires a new SPI in engine-api for event delivery (SwarmCoordinationListener); engine-runtime must implement the bridge adapter
**Depends on:** D3 (engine integration), D5 (light scaffolding)
**Sources:** ChoreographedDriver (agency-core), EventSource (agency-core), SwarmEvent (engine-runtime — not directly consumed)
**Exploration:** quick
**Status:** revised (R1-05: explicit event bridge design with dependency-safe SPI)

## D10: YAML surface shape

**Choice:** Agency-specific fields only — no duplication of engine's SwarmConfig. YAML fields: `experience` (replay config: maxTraces, minSimilarity), `coordinationModel` (LLM model id), `invocationPolicy` (cost budget per execution), `preFilterThreshold` (roster size above which CoalitionEvaluator pre-filters). Mechanical swarm tuning (maxSwarmSize, teamAffinityThreshold, roleSimilarityThreshold, detectionInterval) lives in engine's SwarmConfig, not duplicated here.
**Alternatives:**
- Rich surface with engine-duplicating fields — creates two configuration authorities for the same swarm, guarantees drift
- Extensible with properties map — flexible but untyped and hard to validate
**Rationale:** Single source of truth: engine owns mechanical swarm tuning, agency owns LLM coordination tuning. Domain teams configure engine's SwarmConfig for mechanical behaviour and agency's YAML for LLM coordination. No conflicting values.
**Trade-offs:** Domain teams must configure two surfaces (engine SwarmConfig + agency YAML); but each surface is authoritative for its concern
**Depends on:** D1 (topology placement), D5 (coordination structure)
**Sources:** PatternDefinition existing topology fields, SwarmConfig (engine-api)
**Exploration:** quick
**Status:** revised (R1-03/R1-11: removed engine-duplicating fields, added invocationPolicy)

## D11: LLM cost controls

**Choice:** LlmInvocationPolicy per swarm execution — reuse existing LlmInvocationPolicy (agency stigmergy/llm) to gate coordination calls with configurable cost budgets. Coalescing EventConcurrencyPolicy (D9) provides mechanical rate limiting; LlmInvocationPolicy provides cost-aware gating. When budget is exhausted, coordinator falls back to heuristic decisions (CoalitionEvaluator for team formation, CollectAll for aggregation).
**Alternatives:**
- No cost controls — all four LLM capabilities fire without limit; unsustainable at production scale with concurrent swarms
- Per-capability budgets — separate budgets for team formation, collective reasoning, adaptive coordination; more granular but harder to configure and reason about
**Rationale:** A single execution-level budget is simplest to configure and reason about. The existing LlmInvocationPolicy already provides latency thresholds; extending it with token/cost budgets is natural. Heuristic fallbacks are production-safe defaults.
**Trade-offs:** Coarse-grained budget may exhaust on team formation, leaving nothing for adaptive coordination later in the execution
**Depends on:** D2 (all four capabilities), D7 (team formation), D8 (collective reasoning), D9 (adaptive coordination)
**Sources:** LlmInvocationPolicy (agency stigmergy/llm), CoalitionEvaluator (agency deliberation/coalition), CollectAll (agency orchestration/aggregation)
**Exploration:** quick (surfaced by R1-09)
**Status:** captured

## D12: Failure degradation

**Choice:** Graceful heuristic fallback — when coordinator LLM call fails (network, rate limit, context overflow), fall back to mechanical coordination: CoalitionEvaluator for team formation, CollectAll for aggregation, no adaptive re-coordination. Swarm continues operating with reduced intelligence rather than stopping.
**Alternatives:**
- Hard failure — coordinator failure stops the swarm; simpler but production-fragile
- Retry with exponential backoff — delays but doesn't solve persistent failures; blocks the driver loop
**Rationale:** LLM provider outages and rate limits are production realities. The mechanical fallbacks already exist in agency and are production-tested. Degraded operation is better than no operation.
**Trade-offs:** Degraded mode may produce suboptimal team compositions; consumers may not be aware coordination is running in heuristic mode without observability signals
**Depends on:** D6 (coordinator architecture), D11 (cost controls)
**Sources:** CoalitionEvaluator (agency deliberation/coalition), CollectAll (agency orchestration/aggregation), AbstractExecutionDriver (agency-core)
**Exploration:** quick (surfaced by R1-10)
**Status:** captured
