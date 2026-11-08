# Decisions — #363 Multi-Swarm Coordination

## D1: Use case scope

**Choice:** All three patterns — same-case specialisation, cross-case collaboration, hierarchical composition — with a layered core abstraction. Same-case specialisation as the first concrete implementation.
**Alternatives:**
- Same-case only — simplest, but limits the abstraction. Cross-case and hierarchical would require redesign.
- Cross-case first — harder coordination problem, premature without same-case foundation.
- Hierarchical first — natural composition but assumes the base coordination layer exists.
**Rationale:** The core abstraction (registry, graduated protocol, BJT scoring) is the same across all three patterns. Designing for all three from the start avoids rework. Same-case is the simplest consumer to validate the design.
**Trade-offs:** Larger design surface. Mitigated by layering — each pattern composes the same primitives.
**Sources:** agency#363 (deferred from #285), existing topology infrastructure
**Exploration:** quick
**Status:** captured

## D2: Inter-swarm communication — graduated coordination protocol

**Choice:** Four-level graduated coordination protocol with rules + Bayesian BJT + LLM:
- L0: Shared observation space (EventStreamBus, rule-based predicate matching)
- L1: Bayesian BJT relevance scoring (neocortex incremental junction tree)
- L1→L2: Bayesian escalation gate (evidence accumulation, threshold crossing)
- L2: BeliefSet (AGM) conflict detection + direct notification
- L3: LLM-mediated conflict resolution via negotiation/conversation channels
**Alternatives:**
- Pure stigmergy — decoupled but no urgency, no targeting, no conflict resolution.
- Pure channels — explicit but creates coupling, doesn't scale for many-to-many.
- Binary hybrid (stigmergy + escalation channel) — treats escalation as binary; doesn't recognize the gradient of coordination intensity.
**Rationale:** First-principles analysis of coordination concerns (awareness, observation sharing, conflict detection, resource negotiation, handoff, joint decision) revealed six concerns spanning a gradient from fully decoupled to tightly coupled. Each level uses the cheapest mechanism that can handle its concerns. Maps cleanly to existing infrastructure.
**Trade-offs:** More complex protocol than binary hybrid. Mitigated by each level being independently testable and the Bayesian layer being microsecond-cheap.
**Depends on:** D5 (neocortex BJT), D6 (BeliefSet)
**Sources:** EventStreamBus, StigmergyWriter, NegotiationProjection, ConversationProjection, EmissionPolicy (threshold gating pattern)
**Exploration:** deep-analysis
**Status:** captured

## D3: Swarm discovery — registry-based

**Choice:** A SwarmRegistry tracks active swarm instances with declared interests/capabilities. Swarms register on creation, deregister on completion. Supports dynamic interest updates (interests evolve as swarms work).
**Alternatives:**
- Stigmergy-based discovery — swarms discover each other through signal observation. Emergent but slow.
- Case-scoped implicit — swarms in same case automatically know each other. Simplest but limits cross-case.
**Rationale:** Registry gives explicit knowledge of active swarms, feeding the BJT relevance classifier with declared interests for prior computation. Dynamic interest updates handle the case where swarms discover new concerns during work.
**Trade-offs:** Central registry is a coordination point. Mitigated by being lightweight (in-memory, per-case), and the registry itself is mechanical — no LLM.
**Sources:** TopologyDescriptor (engine-api), SwarmCoordinationListener lifecycle
**Exploration:** quick
**Status:** captured

## D4: Meta-coordinator — rules + Bayesian BJT with LLM escalation

**Choice:** Deterministic rules for L0 signal routing. Neocortex BJT for L1 relevance scoring and L1→L2 escalation gating. LLM only for L3 judgment calls (conflict resolution, joint decisions).
**Alternatives:**
- Full LLM meta-coordinator — LLM reasons about all cross-swarm state. Expensive for high-frequency L0-L1 operations.
- No meta-coordinator — each swarm handles cross-swarm signals independently. Simpler but no coordinated conflict detection.
**Rationale:** L0-L1 operations are high-frequency, low-stakes probability estimates — exactly what BJT handles efficiently (microseconds vs seconds for LLM). LLM reserved for rare L3 judgment calls where the cost is justified.
**Trade-offs:** BJT model needs to be designed per multi-swarm deployment. Mitigated by neocortex's LLM network elicitation capability for bootstrapping.
**Depends on:** D5 (neocortex BJT)
**Sources:** neocortex inference-bayes (BayesInstance, MultiAgentComposition, OnlineParameterUpdater)
**Exploration:** deep-analysis
**Status:** captured

## D5: Bayesian implementation — neocortex BJT with BeliefSet for conflicts

**Choice:** Neocortex incremental BJT for L0-L1 relevance scoring and escalation gating. Agency's BeliefSet (AGM belief revision) for L2 conflict detection where formal contradiction identification adds value.
**Alternatives:**
- Standalone Bayesian model — lighter but misses conditional independence structure, no message passing.
- BeliefSet everywhere — heavier machinery than needed for probability tracking at L0-L1.
**Rationale:** BJT provides proper conditional independence structure and efficient incremental message passing. Three-tier propagation (no-op, dirty subtree, fast retraction) handles the high-frequency L1 updates efficiently. MultiAgentComposition gives per-swarm junction trees with common knowledge and belief divergence detection. BeliefSet adds formal contradiction identification (expand/contract/revise) which the BJT doesn't do.
**Trade-offs:** Two reasoning systems (BJT + BeliefSet). Mitigated by clear separation of concerns: BJT for probability, BeliefSet for logical consistency.
**Sources:** neocortex inference-bayes (BayesInstance, MultiAgentComposition, OnlineParameterUpdater, ExplanationEngine), agency BeliefSet<T> (deliberation/belief)
**Exploration:** quick
**Status:** captured

## D6: Topology structure — single topology with optional LLM enhancement

**Choice:** One MultiSwarmTopology with a mechanical core. LLM features activate when AgentProvider is available (L3 mediation, LLM-guided re-formation). Without it, topology works fully at L0-L2 using BJT + BeliefSet + rules.
**Alternatives:**
- Two topology types (MechanicalMultiSwarm + LlmMultiSwarm) — cleaner separation but more types to maintain.
**Rationale:** Matches single-swarm pattern where SwarmCoordinatorBuilder's heuristic fallback handles LLM unavailability. Optional enhancement is the established pattern.
**Trade-offs:** Single type handles both paths. Mitigated by clean internal separation (mechanical core + optional LLM layer).
**Sources:** SwarmCoordinatorBuilder (buildTopology with heuristic fallback), SwarmFallbackStrategy
**Exploration:** quick
**Status:** captured

## D7: Engine/agency boundary — mechanical base in engine

**Choice:** Push mechanical multi-swarm infrastructure to engine. Engine owns identity, lifecycle, raw events. Agency owns coordination logic (BJT, BeliefSet, LLM).

Engine-api additions:
- SwarmDescriptor (swarm identity record)
- MultiSwarmRegistry SPI (register/deregister/query)
- Extend SwarmCoordinationListener with swarmId parameter
- CrossSwarmConvergenceEvaluator SPI (structural convergence)
- Move SwarmCoordinationEvent sealed hierarchy (contains only engine-api types)

Agency keeps:
- CoordinationTopology interface and all implementations
- All LLM strategies, budget, degradation
- BJT relevance scoring, BeliefSet conflict detection
- Graduated coordination protocol, L3 mediation

Post-implementation audit to verify seam is clean.
**Alternatives:**
- Keep everything in agency — simpler delivery but violates engine=mechanical, agency=LLM principle.
- Defer engine push — build in agency first, extract later. Avoids cross-repo work but creates debt.
**Rationale:** Engine=mechanical, agency=LLM is a standing architectural principle. Swarms existed before LLMs. The registry, event routing, and structural convergence are mechanical concepts.
**Trade-offs:** Cross-repo implementation. Mitigated by using slot 220 with both repos.
**Sources:** engine-api SwarmCoordinationListener, SwarmProvisioningAdvisor, TopologyType, TopologyDescriptor
**Exploration:** deep-analysis
**Status:** captured

## D8: Execution approach — slot 220, single pass

**Choice:** Implement in slot 220 (~/claude/casehub/slots/220/) with both agency and engine clones. Single pass — engine extraction and multi-swarm implementation together. No phased delivery.
**Alternatives:**
- Phased — build in agency first, extract to engine later. Avoids blocking on engine but creates temporary architectural debt.
- Wait for engine — don't start until engine is free. Delays all work.
**Rationale:** Slot 220 already has both repos on main. Working in the slot avoids blocking either canonical repo. IntelliJ workspace refactoring available for cross-repo moves.
**Trade-offs:** Larger single deliverable. Mitigated by slot isolation — doesn't block other work in either repo.
**Sources:** ~/claude/casehub/slots/220/ (agency + engine clones on main)
**Exploration:** quick
**Status:** captured

## D9: YAML surface

**Choice:** Add TopologySpec.MultiSwarm to the sealed interface alongside Swarm, Hive, Mesh, Many, Distributed. Compiled via MultiSwarmTopologyCompiler.
**Alternatives:**
- API-only — add YAML later. Breaks parity with other topologies.
**Rationale:** Every other topology has a YAML surface. Consistency is a platform convention.
**Trade-offs:** More upfront work. Mitigated by established compiler patterns.
**Sources:** TopologySpec sealed interface, HiveTopologyCompiler, DistributedTopologyCompiler
**Exploration:** quick
**Status:** captured

## D10: Testing — TDD core paths, hardening in #423

**Choice:** Core graduated-path tests (L0→L1, L1→L2, L2→L3, full path) are written test-first during #363 implementation (TDD). agency#423 specifies additional hardening, edge cases, and stress scenarios beyond the core paths.
**Alternatives:**
- All tests in #423 — risks implementation shaping tests rather than tests driving implementation.
- All tests inline — risks missing the e2e cross-component scenarios that #423 specifies.
**Rationale:** Platform convention: "Tests are part of implementation, not a phase after it." Core graduated-path tests validate the design during implementation. #423 provides the comprehensive test matrix for hardening.
**Trade-offs:** Core tests duplicated across #363 TDD and #423 specification. Acceptable — TDD tests are the source of truth, #423 adds coverage.
**Sources:** agency#423, design-implementation.md (TDD convention)
**Exploration:** quick
**Status:** revised (from decision review R1-08)

## D11: Cross-case scope — designed-for, deferred from v1

**Choice:** v1 implementation targets same-case specialisation and hierarchical composition. Cross-case collaboration is designed-for (the SPI supports it via `allActive()` and cross-case `SwarmDescriptor` queries) but the concrete implementation and testing are deferred. D3's registry is per-case in v1.
**Alternatives:**
- Implement all three patterns in v1 — adds cross-case complexity (tenancy isolation, global registry, lifecycle management) without a validated use case.
- Same-case only, no cross-case SPI — simpler SPI but requires redesign when cross-case is needed.
**Rationale:** No concrete cross-case scenario has been articulated (R1-09). The SPI's `allActive()` method ensures cross-case can be added without breaking the API. Per-case registry avoids tenancy isolation and global lifecycle concerns in v1.
**Trade-offs:** Cross-case is deferred. Mitigated by SPI design that supports it without redesign.
**Depends on:** D1
**Sources:** D1, D3, R1-04, R1-09
**Exploration:** quick
**Status:** captured (from decision review)

## D12: BJT/BeliefSet integration boundary

**Choice:** BJT escalation gate produces a `CrossSwarmEscalation` record containing: source swarm ID, target swarm ID, escalation type (CONFLICT, DEPENDENCY, RESOURCE_CONTENTION), P(escalate) posterior, and the top-N evidence contributions (from `ExplanationEngine`). This record is the input to BeliefSet processing.

BeliefSet models each swarm's conclusions as `Belief<SwarmConclusion>` where `SwarmConclusion` is a record of (swarmId, domain, conclusion, confidence). The `ConsistencyChecker<SwarmConclusion>` compares conclusions within the same domain — two conclusions in the same domain with opposing valence are contradictory.

Feedback: BeliefSet contraction outcomes (which beliefs were contracted) feed back into the BJT via `OnlineParameterUpdater` — if L2 confirms a conflict, the L1 escalation gate's prior for that swarm pair is updated to escalate faster next time. If L2 finds no contradiction (false alarm), the prior is updated to be more conservative.
**Alternatives:**
- No explicit boundary — let implementation figure it out. Risks inconsistent integration.
- BJT only, no BeliefSet — loses formal contradiction identification.
**Rationale:** The boundary must be explicit because BJT (probability) and BeliefSet (logic) speak different languages. The `CrossSwarmEscalation` record is the translation layer. `ConsistencyChecker` provides domain-specific contradiction logic without coupling BeliefSet to any particular domain.
**Trade-offs:** Additional record types and the consistency checker SPI. Necessary complexity.
**Depends on:** D2, D5
**Sources:** BayesInstance.marginalize(), ExplanationEngine.explain(), BeliefSet.revise(), ConsistencyChecker<T>, OnlineParameterUpdater
**Exploration:** deep-analysis
**Status:** captured (from decision review R1-05)

## D13: Signal schema and shared vocabulary

**Choice:** `MultiSwarmSignal` payload is a sealed interface with typed variants:
- `FindingSignal(String domain, String conclusion, double confidence, Map<String, Object> evidence)`
- `ProgressSignal(SwarmProgress progress)`
- `ResourceClaimSignal(Set<String> agentIds, Set<String> capabilities)`
- `CompletionSignal(String domain, Object result)`
- `HandoffSignal(String domain, Object payload, Set<String> requiredCapabilities)`

Domain tags use eidos `VocabularyTerm` references for cross-swarm compatibility. Capability tags use the existing engine-api capability vocabulary.

L1 BJT `CapabilityOverlap` and `DomainMatch` nodes use set intersection over these vocabulary-backed tags.

L2 `ConsistencyChecker<SwarmConclusion>` compares `FindingSignal` conclusions within the same domain using vocabulary-backed term equivalence.
**Alternatives:**
- Untyped Object payload — no schema enforcement, BJT and BeliefSet can't reason about content.
- String-based ad hoc tags — works for L0 predicate matching but insufficient for L1/L2 reasoning.
**Rationale:** The graduated protocol requires structured signal content for L1 relevance scoring and L2 conflict detection. Sealed interface enforces exhaustive handling. Eidos vocabulary provides cross-swarm term equivalence without swarms needing to agree on string conventions.
**Trade-offs:** Sealed interface constrains signal extensibility. Mitigated by `HandoffSignal` with generic payload for domain-specific content.
**Sources:** eidos VocabularyRegistry, VocabularyTerm, TopologySpec.RolePolicySpec.vocabulary
**Exploration:** quick
**Status:** captured (from decision review R1-07)

## D14: L3 conflict resolution heuristic fallback

**Choice:** When L3 LLM mediation fails (budget exhaustion, timeout, invocation error), the fallback strategy is:
1. **Conflict:** Both conclusions retained with a `DISPUTED` tag. Neither overwrites the other. Escalation to human via existing oversight gate mechanism (`OversightGateService`).
2. **Resource contention:** First-claimer wins. Contending swarm is notified with alternative capabilities.
3. **Joint decision:** Defer to the swarm with higher convergence score (`SwarmProgress.consensusScore()`). Lower-scoring swarm receives the decision as an external constraint.
4. **Handoff:** Retry with exponential backoff. After 3 failures, mark handoff as blocked and notify both swarms.
**Alternatives:**
- Last-writer-wins for all failure types — simple but loses minority conclusions.
- Always escalate to human — safe but blocks autonomy.
**Rationale:** Each failure type has different consequences. Conflict resolution must preserve both positions (information loss is worse than ambiguity). Resource contention needs a deterministic tiebreaker. Joint decisions need a quality-based fallback.
**Trade-offs:** Multiple fallback strategies increase implementation complexity. Mitigated by each being simple and independently testable.
**Sources:** SwarmFallbackStrategy (existing fallback patterns), OversightGateService (human escalation), SwarmProgress.consensusScore()
**Exploration:** quick
**Status:** captured (from decision review R1-10)
