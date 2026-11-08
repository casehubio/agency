## D1: Topology architecture — composable builder, not class hierarchy

**Choice:** Composable `TopologyBuilder` with factory methods. HiveTopology as a class disappears — becomes `TopologyBuilder.hive(agentRoles, patternType)`. Extract `DegradationCascade` and `OrchestrationPatternExecutor` as reusable components.
**Alternatives:**
- Thin classes (Approach A) — keep HiveTopology as a class that composes with extracted infrastructure. More incremental but preserves the misleading class hierarchy.
- Hive-only fix — wire HivePatternAdapter to pattern builders without refactoring. Fastest but leaves 4x copy-pasted degradation cascade and stub execution across all topologies.
**Rationale:** The design spec explicitly says "Hive is not a separate construct." Analysis showed all 6 topology implementations share copy-pasted degradation cascade code and stub execution methods. The root cause is tangled orthogonal concerns (loop planning, outcome execution, degradation) — fixing Hive alone would leave the structural problem. CentralisedTopology<C> stays as-is for now (swarm-specific reasoner entanglement).
**Trade-offs:** Bigger change with more touch points. All HiveTopology consumers need updating. CentralisedTopology remains as an exception to the builder pattern until its reasoner wiring can be disentangled.
**Sources:** HiveTopology.java, CentralisedTopology.java, HierarchicalTopology.java, HybridTopology.java — shared cascade threshold-checking pattern in 4 files; GraduatedTopology shares the threshold-checking but diverges in cascade response (forces scope reduction via GraduationState rather than activating CascadeDegradationTrigger instances). DistributedTopology has no cascade. Design spec §2.1: "Hive is not a separate construct."
**Exploration:** deep-analysis
**Status:** revised — R1-02: DegradationCascade extraction refined to require CascadeBreachHandler strategy for polymorphic cascade responses

## D2: Bottom-up formation orchestrator lives in agency

**Choice:** `BottomUpFormationOrchestrator` in agency-core. Takes `GroupLifecycleService` (eidos org-api) as a constructor param (provided dependency). Composes CoalitionProposal → CoalitionEvaluator → NegotiationProtocol (via Qhorus channel) → GroupLifecycleService.proposeGroup() → approveProposal().
**Alternatives:**
- Engine/desiredstate — orchestrator in engine where full lifecycle integration happens. Splits formation logic across repos.
- Event-driven — agency emits a CoalitionApproved CDI event after deliberation; engine/desiredstate observes and calls GroupLifecycleService. Preserves dependency direction but splits the formation flow across repos, creating invisible coupling that requires reading both codebases to trace the full path from proposal to group creation.
- SPI in agency, impl in engine — adds an interface that only has one implementation. Premature abstraction.
**Rationale:** Agency already owns CoalitionProposal, CoalitionEvaluator, and NegotiationProtocol. The orchestrator is a composition of these building blocks. GroupLifecycleService is taken as a constructor parameter — eidos org-api must be added as a NEW provided dependency (corrected: not currently on agency-core's classpath). Direct composition in a single `BottomUpFormationOrchestrator` class keeps the full formation flow readable top-to-bottom: proposal → evaluation → negotiation → group lifecycle. An event-driven approach splits this across repos (deliberation in agency, lifecycle mutation in engine) — while technically feasible (agency-core does use CDI: 7 `@ApplicationScoped` beans, 5 `@Inject` sites in production code), splitting the orchestration creates invisible coupling and makes the formation flow untraceable without reading both codebases. The translation from CoalitionProposal's model (taskId, requiredCapabilities, proposedMembers) to GroupLifecycleService's model (specId, tenancyId, proposerAgentId) must be handled by the orchestrator — specId derived from the coalition's task context, tenancyId from TopologyContext, proposerAgentId from the coalition initiator. NegotiationProtocol operates via Qhorus channel semantics (audit trail, ACL, ledger entry) — this is intentional for governance traceability of formation negotiations.
**Trade-offs:** Agency gains a NEW provided dependency on eidos org-api for the GroupLifecycleService parameter type (one-line POM addition). Formation negotiation carries full Qhorus channel overhead — justified by the governance/audit requirements for group formation. The event-driven alternative would avoid this dependency but at the cost of split orchestration.
**Sources:** Design spec §3.3 "Bottom-Up Proposal". GroupLifecycleService.java (eidos org-api — not currently on agency-core classpath). CoalitionProposal.java, NegotiationProtocol (agency-core). Agency-core CDI usage: SwarmCoordinationListenerBridge, SwarmPromptOutcomeProvider, NegotiationProtocol, ConversationProtocol, LlmTriageHandler, ModelPreferenceSignalProvider, JudgmentProtocol.
**Exploration:** quick
**Status:** revised — R1-05: corrected dependency claim (new, not existing). R1-06: translation layer addressed. R1-07: event-driven alternative explicitly listed and rejected on traceability grounds (corrected: agency-core is NOT zero CDI). R1-08: Qhorus channel semantics clarified as intentional.

## D3: RolePolicySpec compiles to eidos RoleSpec

**Choice:** Agency's `TopologySpec.RolePolicySpec` (proposed greenfield type in agentic-yaml) becomes a YAML-layer convenience that compiles to eidos `RoleSpec`. Compiler in a dedicated `RolePolicyCompiler` (also greenfield). Mapping: simple `obligations: [name1, name2]` → `RoleSpec.obligations(List.of("name1", "name2"))`, simple `permissions: [name1]` → `RoleSpec.permissions(List.of("name1"))`. Structured capability-level policies with expectations → `RoleSpec.policies(List.of(new RolePolicy(cap, kind, expectations)))`. YAML gains optional `min_agents`, `max_agents`, `incompatible_with` fields mapping to `RoleSpec.minCardinality/maxCardinality/incompatibleWith`. PolicyKind.ELIGIBILITY is available via the structured `policies:` block when capability-level eligibility gates are needed — not surfaced as a simple shorthand because eligibility typically requires expectations metadata.
**Alternatives:**
- Agency defines its own canonical role model — creates a parallel type system alongside eidos. Wrong: eidos owns the org model.
- Remove RolePolicySpec entirely, use RoleSpec in YAML — YAML becomes verbose (PolicyKind, expectations map for simple cases). Wrong: YAML should be concise.
**Rationale:** Design spec §2.2 establishes eidos RoleSpec as canonical. RolePolicySpec is syntactic sugar for the common case (named obligations/permissions). Full RoleSpec (including all three PolicyKind values: ELIGIBILITY, OBLIGATION, PERMISSION) can be expressed in YAML via an explicit `policies:` block. Note: TopologySpec, TopologySpecRegistry, and RolePolicyCompiler are all greenfield types to be created in the agentic-yaml module — they do not exist in the codebase today.
**Trade-offs:** Two representations of the same concept (YAML convenience + canonical type). Compiler must be kept in sync with eidos RoleSpec changes.
**Sources:** Design spec §2.2. RoleSpec.java (separate `obligations`, `permissions`, and `policies` fields), RolePolicy.java, PolicyKind.java (ELIGIBILITY, OBLIGATION, PERMISSION) — all in eidos org-api. TopologySpec.RolePolicySpec: proposed for agentic-yaml.
**Exploration:** quick
**Depends on:** D1 (TopologyBuilder consumes compiled RoleSpecs)
**Status:** revised — R1-10: corrected mapping (simple obligations/permissions use dedicated RoleSpec fields, not RolePolicy wrapper). R1-11: PolicyKind.ELIGIBILITY addressed. R1-12: greenfield status clarified.

## D4: TopologyContext gains groupId and tenancyId

**Choice:** Add `@Nullable String groupId` and `@Nullable String tenancyId` to the `TopologyContext` record. Nullable because case-scoped topologies (pre-society, no group context) legitimately have no group. Populated from the society model when a topology is activated for a group.
**Alternatives:**
- Non-nullable with sentinel values — violates null-safety conventions, forces callers to check sentinel strings.
- Separate GroupTopologyContext subclass — Java records can't extend. A wrapper adds unnecessary indirection.
**Rationale:** Design spec mandates it. TopologyContext is the shared context for all topology operations. Without groupId/tenancyId, HiveTopology can't query the org model for group policy. @Nullable is the standard pattern for optional context in CaseHub records.
**Trade-offs:** Existing callers must update to pass the new fields (null for non-group topologies). All existing tests need a minor update.
**Sources:** Design spec §2.1. TopologyContext.java. Issue #412.
**Exploration:** quick
**Status:** captured

## D5: TopologyFactory bridges YAML spec to TopologyBuilder

**Choice:** `TopologyFactory` (proposed) in agentic-yaml maps `TopologySpec` (proposed) variants to `TopologyBuilder` (proposed, D1) factory calls. For `TopologySpec.Hive`: reads `orchestration` field → `OrchestrationPatternType` (hive-scoped, see D8), reads `roles` + `policy` → compiled `RoleSpec` list, calls `TopologyBuilder.hive(roles, patternType)`. Registered as a step in a new `PatternCompiler` (proposed — not the Guava class of the same name).
**Alternatives:**
- Inline compilation in the new PatternCompiler — mixes YAML parsing with topology construction. Even as a greenfield type, separation of concerns matters.
- Per-variant factory classes (HiveFactory, SwarmFactory, etc.) — class explosion for what are configuration variations.
**Rationale:** Single factory with exhaustive switch on the sealed TopologySpec hierarchy. Each variant maps to a TopologyBuilder factory method. Clean separation: YAML parsing (Jackson) → TopologyFactory → TopologyBuilder → CoordinationTopology. All four types (TopologyFactory, TopologySpec, TopologySpecRegistry, PatternCompiler) are greenfield — this decision designs a new YAML-to-runtime compilation pipeline, not wiring of existing components.
**Trade-offs:** TopologyFactory depends on both agentic-yaml types and agency-core TopologyBuilder. This is the natural dependency direction (YAML surface depends on runtime).
**Sources:** Issue #413. All types proposed for agentic-yaml module. TopologyBuilder proposed in D1.
**Exploration:** quick
**Depends on:** D1 (TopologyBuilder), D3 (RolePolicySpec compiler), D8 (OrchestrationPatternType scoping)
**Status:** revised — R1-17: greenfield status clarified. R1-18: PatternCompiler clarified as proposed type.

## D6: Migrate all non-CentralisedTopology implementations to TopologyBuilder

**Choice:** Migrate Distributed, Hierarchical, Hybrid, Graduated, and Hive topologies to `TopologyBuilder` factory methods. CentralisedTopology<C> stays as-is (swarm-specific reasoner entanglement). Extract `DegradationCascade` for reuse across all topologies that have cascade logic.
**Alternatives:**
- Hive only — migrate only HiveTopology, leave others. Creates inconsistency: one topology uses the new pattern, four don't.
**Rationale:** The degradation cascade threshold-checking pattern is copy-pasted across 4 topology classes (Hive, Hierarchical, Hybrid, Graduated). The cascade *response* diverges: Hive/Hierarchical/Hybrid activate CascadeDegradationTrigger instances; GraduatedTopology forces scope reduction. DistributedTopology has no cascade at all. Extracting the shared threshold-checking with a CascadeBreachHandler callback (per D1 revision) eliminates the 4-way duplication while accommodating GraduatedTopology's divergent response. Migrating all topologies to TopologyBuilder factory methods in one pass creates the clean structure needed for the execution model implementation (see D9).
**Trade-offs:** Larger change surface. All topology tests must be updated. But the alternative (doing it incrementally across 4 follow-up issues) is more total work.
**Sources:** HiveTopology.java, HierarchicalTopology.java, HybridTopology.java, GraduatedTopology.java — shared cascade threshold-checking. DistributedTopology.java — no cascade. GraduatedTopology.java — divergent cascade response (forceScope vs trigger activation).
**Exploration:** quick
**Depends on:** D1 (TopologyBuilder design), D9 (refactor-first sequencing)
**Status:** revised — R1-21: cascade scope refined to acknowledge GraduatedTopology divergence and DistributedTopology absence.

## D7: TopologyBuilder eliminates type-based dispatch — configuration-driven model

**Choice:** TopologyBuilder factory methods return `CoordinationTopology` (the interface), not concrete types like `HiveTopology`. Consumers interact through the CoordinationTopology interface. Type-specific accessors (`HiveTopology.patternType()`, `GraduatedTopology.graduationState()`) are not part of the public API.
**Alternatives:**
- Preserve concrete types alongside builder — consumers can still use instanceof, type-specific accessors. Retains type-safe dispatch but contradicts the "Hive is not a separate construct" design principle.
- Sealed interface with permits clause — compile-time exhaustive switch over topology variants. More ceremony for a distinction the design explicitly wants to collapse.
**Rationale:** `patternType()` has zero production references (only tests). `graduationState()` has zero references outside GraduatedTopology. No external consumer currently relies on type-based dispatch. The design spec's "Hive is not a separate construct" directly supports collapsing the type hierarchy. If a consumer genuinely needs topology-specific behavior, it should be exposed through the CoordinationTopology interface (e.g., a method that returns topology-specific capabilities).
**Trade-offs:** If future features need type-specific dispatch, the information must be accessed through the interface rather than downcasting. This forces explicit API design for any new topology-specific behavior.
**Sources:** HiveTopology.patternType() — zero production references. GraduatedTopology.graduationState() — zero external references.
**Surfaced by:** R1-03 (implicit decision made explicit)
**Exploration:** quick
**Status:** captured

## D8: OrchestrationPatternType scoped to hive topology in YAML

**Choice:** `OrchestrationPatternType` (SUPERVISOR, DEBATE, VOTING) is scoped to hive-type topologies only. In the YAML schema, the `orchestration` field is nested under a hive-specific block, not available as a top-level concept for all topologies.
**Alternatives:**
- First-class YAML concept for all topologies — makes `orchestration` available to DistributedTopology, HierarchicalTopology, etc. But DEBATE/VOTING have no defined semantics for non-hive topologies.
- Remove OrchestrationPatternType from YAML entirely — force programmatic configuration. Loses the YAML ergonomics for the hive case.
**Rationale:** `OrchestrationPatternType` is only referenced by `HiveTopology` and `HivePatternAdapter` — zero references from other topologies. SUPERVISOR/DEBATE/VOTING are hive-specific orchestration patterns. Making them available to DistributedTopology (where DEBATE is meaningless) would create invalid configurations with no compile-time or runtime validation.
**Trade-offs:** If a future topology needs orchestration pattern selection, a new mechanism must be designed for that topology's YAML block rather than reusing the hive concept.
**Sources:** OrchestrationPatternType.java — only referenced by HiveTopology, HivePatternAdapter, and their tests.
**Surfaced by:** R1-23 (implicit decision made explicit)
**Exploration:** quick
**Status:** captured

## D9: Refactor-first sequencing — structural refactoring precedes execution model

**Choice:** Refactor topology structure (extract DegradationCascade, migrate to TopologyBuilder) before implementing real execution methods. The execution methods remain stubs during structural refactoring.
**Alternatives:**
- Implement first, refactor later — implement real execution in the current copy-pasted structure, then consolidate. Risk: implementing the same infrastructure differently in 5 places, then reworking all of it.
- Interleave — refactor one topology at a time, implementing its execution in the new structure before moving to the next. Risk: the first topology's structure may not generalize.
**Rationale:** The structural problem (4x copy-pasted degradation cascade, tangled concerns) is independent of whether execution methods are stubs or real. Extracting shared infrastructure first means real execution implementations write against a clean surface without duplicating infrastructure. The TopologyBuilder API captures topology *configuration* (agent roles, communication modes, budget policies, degradation thresholds) — with one exception: `TopologyBuilder.hive(agentRoles, patternType)` accepts `OrchestrationPatternType`, which is an execution-model discriminator (SUPERVISOR/DEBATE/VOTING select different execution paths via HivePatternAdapter). This parameter selects WHICH pattern, not HOW it executes — pattern-specific configuration (quorum rules for debate, vote thresholds for voting, delegation policies for supervisor) belongs with the `DecisionOutcomeExecutor` or `HivePatternAdapter`, not the builder. The execution model plugs in via `DecisionOutcomeExecutor`, which is already a functional interface that the builder can accept.
**Trade-offs:** Risk that TopologyBuilder's API needs revision once execution is real. `patternType` is the one builder parameter that touches execution semantics — if the execution model requires pattern-specific companion configuration, the hive factory method may need a `patternConfig` parameter alongside `patternType`. Mitigated by keeping pattern-specific execution configuration in the executor/adapter layer, not the builder.
**Sources:** All topology implementations: execution methods are stubs returning FeedbackSignal.SUCCESS. DecisionOutcomeExecutor is already a functional interface accepting (agentId, outcome, ctx) → CompletionStage<CoordinationFeedback>. OrchestrationPatternType is referenced only by HiveTopology and HivePatternAdapter.
**Surfaced by:** R1-24 (implicit sequencing decision made explicit)
**Exploration:** quick
**Status:** revised — R2-04: acknowledged that patternType touches execution semantics; refined stability claim.

## D10: describeTopology declared on CoordinationTopology

**Choice:** `describeTopology(UUID caseId)` should be declared as a method on the `CoordinationTopology` interface. Currently all 6 topology implementations define this method but it appears on neither `CoordinationTopology` nor `TopologyProvider`.
**Alternatives:**
- Leave undeclared — the method works via convention. Risk: no compile-time enforcement, new implementations could omit it.
- Merge with topologyFor — `TopologyProvider.topologyFor(agentId, tenancyId)` already returns `TopologyDescriptor`. Risk: these serve different audiences and lifecycles (topologyFor is the engine SPI query; describeTopology is agency-internal per-case description).
**Rationale:** `topologyFor(agentId, tenancyId)` is the engine SPI entry point — called by the coordination infrastructure to query which topology an agent belongs to. `describeTopology(caseId)` is the agency-internal method for describing a topology's structure within a case context. These are semantically distinct operations for different audiences. Making `describeTopology` a declared method on `CoordinationTopology` formalizes the existing convention without conflating it with the engine SPI.
**Trade-offs:** CoordinationTopology's interface surface grows. The relationship between the engine SPI (`topologyFor`) and agency-internal methods (`configure`, `describeTopology`) should be documented at the interface level.
**Sources:** CoordinationTopology.java (no describeTopology declaration). All 6 topology implementations define describeTopology(UUID). TopologyProvider.topologyFor(String, String) is the engine SPI.
**Surfaced by:** R1-25 (implicit decision made explicit)
**Exploration:** quick
**Status:** captured
