# Decisions — #319 CognitiveProfile Deep Integration

## D1: Integration mode

**Choice:** New EntityKnowledgePromptSection alongside existing sections
**Alternatives:**
- Replace UserModel/MentalModel — subsumes their data but loses per-tick state granularity
- Enrichment layer — enriches orchestrators but doesn't render directly, misses the unified view
**Rationale:** Existing sections handle per-tick state (mood deltas, drive intensity, strategy signals). CognitiveProfile provides deep cross-store knowledge that complements rather than replaces those real-time signals.
**Trade-offs:** Some information overlap between UserModel/MentalModel sections and entity knowledge. Token cost increases.
**Sources:** CognitionCore.promptSections(), EntityKnowledge record, issue #283
**Exploration:** quick
**Status:** captured

## D2: Entity selection strategy

**Choice:** Attention-driven — use existing attention signals (AttentionBriefing, TemporalFocus, active subjects) as the seed set
**Alternatives:**
- LLM-judged relevance — more accurate but adds LLM call per tick
- Graph-walk heuristic — walks edges to depth N, needs tuning
- Active subjects only — too narrow, misses entities the agent should be thinking about
- All known entities — too expensive
- Hybrid (attention + high-edge-density supplement) — attention seeds supplemented by a lightweight MindMap query for entities with high edge density or recent memory activity, ensuring deep knowledge isn't gated by recency-biased attention
**Rationale:** The system already surfaces what's salient via CognitiveAttentionMediator and TemporalFocusOrchestrator. Using these as seeds avoids new heuristics. Requires identity mapping from AttentionSignal.sourceNodeId (MindMap node IDs) to CognitiveProfile entity references — this is a deterministic graph lookup, not a heuristic, but must be implemented explicitly.
**Trade-offs:** Entity selection is limited to what attention/temporal systems surface. If those systems miss something, the profile won't resolve it. Entities with deep knowledge but no recent attention signals may be omitted — the hybrid alternative addresses this at the cost of additional MindMap queries per tick.
**Sources:** CognitiveAttentionMediator, TemporalFocusOrchestrator, AttentionBriefing, AttentionSignal.sourceNodeId
**Exploration:** quick
**Status:** revised — R1-02 acknowledged identity mapping step and added hybrid alternative

## D3: Perspectival resolution

**Choice:** Always perspectival — every entity resolved through the agent's PrincipalId
**Alternatives:**
- Shared view only — simpler, cheaper, but all agents see the same thing
**Rationale:** Perspectival resolution is what makes agents genuinely different. An agent's subjective view of an entity (overlays, biased memories, subjective affect) is the core value proposition of CognitiveProfile.
**Trade-offs:** Higher cost per resolve() call due to overlay merging. Requires PrincipalId to be available during tick.
**Sources:** CognitiveProfile.resolve() asSeenBy parameter, PerspectivalResolver
**Exploration:** quick
**Status:** captured

## D4: Tick lifecycle phase

**Choice:** TERMINAL phase via CognitionTickParticipant
**Alternatives:**
- DERIVED phase — earlier but mood/attention may not be fully updated
- Lazy on promptSections() — avoids wasted work but adds latency at prompt time
**Rationale:** TERMINAL phase runs after all orchestrators have ticked, so attention signals, temporal focus, and active subjects are all up-to-date when entity selection runs. Follows the established participant pattern (CognitiveGoalOrchestrator, the only existing CognitionTickParticipant, registered at TERMINAL). Consistent with the pre-resolved section pattern used by AttentionPromptSection, ConsolidationPromptSection, TemporalFocusPromptSection, and ReflectionPromptSection — all of which use data captured during tick() rather than lazy resolution at contribute() time.
**Trade-offs:** Profile data is one tick behind for consumers that run in earlier phases. Acceptable since profile data is supplementary context, not decision input.
**Sources:** CognitionCore.tick() phase ordering, CognitionPhase enum, AttentionPromptSection/ConsolidationPromptSection pre-resolved pattern
**Exploration:** quick
**Status:** revised — R1-05/R2-02 removed stale ConsolidationMediator reference, strengthened rationale with pre-resolved section pattern evidence

## D5: Prompt rendering format

**Choice:** Structured natural language — prose sections per entity
**Alternatives:**
- Structured data block (YAML/JSON) — more precise but less natural for LLM
- Salience-filtered prose — most token-efficient but needs threshold tuning
**Rationale:** LLMs reason better from natural language than structured data. Structured NL is also more token-efficient than JSON while preserving key information.
**Trade-offs:** Loses some precision compared to structured data. May need formatting iteration to find the right balance.
**Sources:** MoodPromptSection (emotion labels pattern), ReflectionPromptSection
**Exploration:** quick
**Status:** captured

## D6: compare() API scope

**Choice:** Wire both resolve() and compare()
**Alternatives:**
- resolve() only — simpler but misses multi-agent perspective reasoning
**Rationale:** Multi-agent perspective comparison ("Dick Dastardly thinks Penelope trusts him but she doesn't") is a core capability for wacky-manor style scenarios. Wiring it now while integrating the profile is cheaper than retrofitting later.
**Trade-offs:** compare() adds complexity and is expensive (resolves N overlays). Needs to be gated by config flag.
**Sources:** CognitiveProfile.compare(), PerspectivalResolver
**Exploration:** quick
**Depends on:** D3 (perspectival resolution)
**Status:** captured

## D7: Wiring pattern

**Choice:** CognitionTickParticipant — create CognitiveProfileParticipant registered at TERMINAL
**Alternatives:**
- Direct in CognitionCore.tick() — simpler wiring but grows CognitionCore further
**Rationale:** Follows the established participant pattern. Keeps CognitionCore stable. Same pattern used by CognitiveGoalOrchestrator, which implements CognitionTickParticipant and is registered at TERMINAL via `core.addParticipant(CognitionPhase.TERMINAL, this.cognitiveGoals)` in SocialAvatarCognition.Builder. The participant receives its dependencies (CognitiveProfile handle, etc.) at construction time, not from CognitionTickContext — same approach as CognitiveGoalOrchestrator which injects MindMapStore, GoalAppraisal, CaseMemoryStore directly.
**Trade-offs:** Slightly more indirection than direct wiring. Participant must be registered externally (via SocialAvatarCognition Builder or BlocksBeans). CognitionCore constructor already has 18 parameters — adding CognitiveProfile as a core dependency would exacerbate this; participant pattern avoids further constructor growth.
**Sources:** CognitionCore.addParticipant(), CognitionPhase.TERMINAL, CognitiveGoalOrchestrator (the only existing CognitionTickParticipant)
**Exploration:** quick
**Status:** revised — R1-05 corrected ConsolidationMediator citation (it is an @ApplicationScoped CDI bean, not a participant)

## D8: API ownership and requirements flow

**Choice:** Decisions express blocks' requirements toward neocortex — CognitiveProfile API shape is a requirement flowing from blocks to neocortex, not a consumed stable API
**Alternatives:**
- Wait for neocortex API stabilisation before finalising integration decisions — delays integration design, but ensures decisions fit the actual API
- Design the CognitiveProfile API jointly as a shared concern — requires cross-repo coordination
**Rationale:** CognitiveProfile, EntityKnowledge, and PerspectivalResolver do not yet exist in neocortex/cognitive-api or anywhere else. These decisions document what blocks needs from the CognitiveProfile capability: perspectival entity resolution, entity comparison, structured entity knowledge records. When the neocortex API is designed, these decisions will be revised to match. Until then they serve as a requirements specification for the API.
**Trade-offs:** If the eventual API has a different shape (batched resolution, async Uni returns, different entity model), integration decisions will need revisiting. The current decisions may lock in assumptions about API shape that prove incorrect.
**Sources:** neocortex/cognitive-api (no CognitiveProfile types found), casehub-platform-api PrincipalId
**Exploration:** quick (surfaced by R1-07)
**Status:** captured

## D9: Entity identity mapping strategy

**Choice:** Deterministic graph lookup — map AttentionSignal.sourceNodeId to CognitiveProfile entity references via MindMap node metadata
**Alternatives:**
- Unified identity type — introduce a cross-system EntityRef type that all systems use, eliminating mapping entirely
- SPI-based mapping — let applications define the mapping via a SourceNodeToEntityMapper interface
**Rationale:** The codebase has three identity spaces relevant to entity resolution: AttentionSignal.sourceNodeId (MindMap node IDs), SubjectResolver subject IDs, and PrincipalId (ActorType:id). PromptContext.subjectId is the same space as SubjectResolver. The mapping from sourceNodeId to entity reference is deterministic via MindMap graph structure (nodes have types and attributes that identify the entity they represent). AttentionBriefing already carries principalId, and CognitionCore.tick() passes agentId as the principalId to CognitiveAttentionMediator.drainAttention(), so PrincipalId.agent(agentId) provides the perspectival identity.
**Trade-offs:** Relies on MindMap node structure being consistent enough for deterministic entity identification. If MindMap nodes don't carry sufficient entity metadata, the mapping becomes ambiguous.
**Sources:** AttentionSignal (principalId, sourceNodeId, sourceName), SubjectResolver, PromptContext, PrincipalId, CognitiveAttentionMediator.drainAttention()
**Exploration:** quick (surfaced by R1-08)
**Status:** captured
