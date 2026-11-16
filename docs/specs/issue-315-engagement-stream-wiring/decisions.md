## D1: Injection point for EngagementRecorderCore

**Choice:** Inject into CognitionCore directly
**Alternatives:**
- Inject into StrategyLearningOrchestrator — wrong level; SLO buffers/batches but issue wants per-interaction persistence
- New EngagementBridge wrapper — premature abstraction for a one-line call (YAGNI)
**Rationale:** CognitionCore already orchestrates all cognitive recording (mood, user model, mental state, strategy). Adding EngagementRecorderCore follows the existing pattern.
**Trade-offs:** CognitionCore gains one more constructor dependency
**Sources:** CognitionCore.java:226 (recordInteraction), EngagementRecorderCore (neocortex memory-core)
**Exploration:** quick
**Status:** captured

## D2: Consume-side scope

**Choice:** Persistence only — wire the hook point, defer actual consumption to later issues
**Alternatives:**
- Lightweight observer updating relationship familiarity — adds immediate value but overlaps with #317 SocialComparison
- Full consumption pipeline with EngagementPromptSection — too much for S-scale issue
**Rationale:** StrategyLearningOrchestrator already handles strategy learning from engagement. The value of this issue is getting data into neocortex memory so #312 (consolidation), #316 (temporal focus), and other issues can query it.
**Trade-offs:** No immediate visible behavior change from the consume side
**Sources:** casehubio/blocks#315 issue body, handoff what's-next table
**Exploration:** quick
**Status:** captured

## D3: Mood congruence injection strategy (from #302)

**Choice:** GoalAppraisal decorator in blocks (MoodCongruentGoalAppraisal)
**Alternatives:**
- Add currentMood to AppraisalContext in neocortex-mindmap-api — cleaner data model but crosses repo boundaries, forces all callers to provide mood
**Rationale:** All changes stay within blocks. GoalAppraisal SPI remains mood-agnostic. Composes naturally via CognitiveGoalOrchestrator's existing GoalAppraisal dependency. No cross-repo coordination needed.
**Trade-offs:** Modulation happens outside the appraisal — if a new GoalAppraisal implementation is added, it won't get mood congruence automatically unless wrapped
**Sources:** HeuristicGoalAppraisal.java (fear threshold logic), MoodOrchestrator.java (currentMood API), CognitiveGoalOrchestrator.java (GoalAppraisal wiring), GoalEmotionMoodBridge.java (#301 reverse direction)
**Exploration:** quick
**Status:** captured

## D4: Consolidation signal consumption — data channel architecture

**Choice:** Typed artifact sealed interface alongside existing attention signals
**Alternatives:**
- Fix attention signals + poll MindMapStore — signals are lossy (null sourceNodeId on EXPERIENCE_GRADUATED, null principalId on MERGE_CANDIDATE), can't carry CuriositySignal's question/description fields through AttentionSignal's fixed schema, warps attention system's purpose from "what needs attention" to "what to render in prompt"
- Dual observer (ConsolidationCompleted + CognitiveAttentionRequired) — two trigger mechanisms, complex cache coordination, curiosity data is discarded before any event fires
**Rationale:** Consolidation produces knowledge, not just signals. AttentionSignals are low-fidelity ("something happened"), artifacts carry the actual knowledge content. CuriositySignalGenerator already computes rich data (question, description, targetNodeId) and discards it — artifacts capture what exists. Sealed interface gives compile-time exhaustiveness. Clean separation: signals drive threshold-based attention gating, artifacts carry knowledge for prompt rendering.
**Trade-offs:** Requires neocortex changes (new types + phase modifications). More types to maintain. But all data already exists in each phase — we're just exposing it.
**Sources:** ConsolidationScheduler.java (runPhases flow), ExperienceConsolidationPhase.java (null sourceNodeId on signals), MergeDetectionPhase.java (null principalId), CuriosityRefreshPhase.java (discards CuriositySignal list), CommunitySummaryPhase.java (discards summary node IDs), CognitiveAttentionAccumulator.java (__broadcast__ for null principalId)
**Exploration:** deep-analysis
**Status:** captured

## D5: ConsolidationArtifact module placement

**Choice:** mindmap-api (alongside CognitiveAttentionRequired, AttentionSignal, AttentionBriefing)
**Alternatives:**
- mindmap-intelligence — keeps all consolidation types together but places contracts in implementation module
- New consolidation-api module — clean but premature for ~5 types
**Rationale:** Platform convention: cross-boundary contract types consumed by blocks go in mindmap-api; implementation types stay in mindmap-intelligence. CognitiveAttentionRequired, AttentionSignal, and AttentionBriefing all follow this pattern. ConsolidationArtifact is consumed by blocks-core's ConsolidationMediator and ConsolidationPromptSection — it is a cross-boundary contract. Placing it in mindmap-intelligence would compound the existing misplacement of ConsolidationCompleted (which should also move to mindmap-api in a follow-up).
**Trade-offs:** Consolidation contracts split across mindmap-api (ConsolidationArtifact) and mindmap-intelligence (ConsolidationPhase, ConsolidationCompleted). This is the same split the attention system already uses successfully.
**Sources:** mindmap-api: CognitiveAttentionRequired.java, AttentionSignal.java, AttentionBriefing.java (cross-boundary contracts). mindmap-intelligence: CognitiveAttentionAccumulator.java, ConsolidationPhase.java (implementations).
**Exploration:** quick → revised after review
**Status:** revised — moved from mindmap-intelligence to mindmap-api per R1-03. Original rationale ("dependency already exists") was a sunk-cost argument; platform convention takes precedence.

## D6: Multi-agent artifact distribution — snapshot + per-agent drain

**Choice:** Tenant-scoped snapshot with per-agent seen-tracking
**Alternatives:**
- Pre-distribute to all agents at observer time — requires knowing all active agents, complex registration
- Drain-once (first agent takes all) — other agents miss consolidation results
- Per-principal cache (like CognitiveAttentionMediator) — can't work because ConsolidationCompleted is tenant-scoped and most artifacts are tenant-wide
**Rationale:** ConsolidationCompleted fires per tenant. Artifacts are a mix of agent-specific (GraduatedExperience has agentId) and tenant-wide (merges, curiosity, community). Snapshot stays until next consolidation. Per-agent timestamp tracking ensures each agent sees each consolidation exactly once. Agent-relevance filtering happens on drain: GraduatedExperience filtered by agentId, tenant-wide artifacts visible to all.
**Trade-offs:** Tenant-wide artifacts rendered for all agents — some may not be relevant to a specific agent's subgraph. Acceptable because consolidation runs infrequently and the prompt section is bounded.
**Sources:** CognitiveAttentionMediator.java (drain pattern), ConsolidationScheduler.tick() (tenant iteration), CuriositySignalGenerator.java (tenant-scoped queries)
**Exploration:** deep-analysis
**Status:** captured

## D7: GraduatedExperience agent-scoped filtering

**Choice:** Filter GraduatedExperience by agentId — only the originating agent sees the graduation artifact
**Alternatives:**
- Tenant-wide visibility — all agents see all graduated experiences. Benefits: explains mindmap changes visible to other agents, surfaces knowledge maturation across the multi-agent system
- Hybrid — tenant-wide with the originating agentId as metadata for context, no filtering
**Rationale:** GraduatedExperience notifications describe an agent's episodic memories graduating to the mindmap. The graduated KNOWLEDGE (new mindmap node) is already tenant-wide — all agents see it through normal mindmap queries. The artifact is a notification about the graduation EVENT, which is only relevant to the agent whose memory was processed. Broadcasting "Agent A's experience about X graduated" to Agent B adds noise without actionable context — B cannot reason about A's private episodic memories.
**Trade-offs:** Agent B won't know WHY the mindmap changed (new nodes appearing). Acceptable because the mindmap's state is the ground truth, not the graduation events.
**Sources:** ExperienceConsolidationPhase.java (agent-specific memory scan), CognitiveAttentionAccumulator.java (principalId routing for signals)
**Exploration:** surfaced by review (R1-09)
**Status:** captured

## D8: Artifact persistence — in-memory only

**Choice:** Transient in-memory snapshots, no persistence
**Alternatives:**
- Persist to database — survives restart, enables historical queries
- Write-ahead log — lightweight persistence, replay on startup
**Rationale:** Artifacts are notifications about events, not the events themselves. The underlying knowledge (graduated nodes, merged nodes, curiosity signals, community summaries) is persisted in MindMapStore. On server restart, the next consolidation tick (≤5 min default interval) recomputes all artifacts. The maximum loss window equals the consolidation interval. CognitiveAttentionMediator follows the same transient pattern for the same reason — signals are derived, the source data persists.
**Trade-offs:** Artifacts lost on restart. Acceptable because the knowledge persists and artifacts are recomputed quickly.
**Sources:** CognitiveAttentionMediator.java (same transient pattern), ConsolidationScheduler.java (5-min default interval)
**Exploration:** surfaced by review (R1-16)
**Status:** captured

## D9: Artifact consumption scope — prompt rendering only

**Choice:** Artifacts wired into promptSections() only, not available to CognitionCore.tick() cognitive phases
**Alternatives:**
- Deep wiring — make artifacts available to Strategy, GoalAppraisal, Narrative phases during tick()
- CognitionTickParticipant — expose artifacts through the extensible participant API
**Rationale:** This spec establishes the data channel and basic prompt rendering. Deep cognitive integration (strategy adjustment based on knowledge maturation, goal reprioritization based on community summaries) is a distinct design concern with its own decision space. The CognitionTickParticipant API already exists for this kind of extension — a follow-up issue can wire ConsolidationMediator as a participant without modifying CognitionCore's constructor.
**Trade-offs:** Cognitive phases can't yet reason about consolidation events programmatically. Acceptable for initial wiring.
**Sources:** CognitionCore.java (tick phases, CognitionTickParticipant API)
**Exploration:** surfaced by review (R1-18)
**Status:** captured
