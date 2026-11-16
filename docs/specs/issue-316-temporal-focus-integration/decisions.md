## D1: New section vs extend AttentionPromptSection

**Choice:** New `TemporalFocusPromptSection`
**Alternatives:**
- Extend AttentionPromptSection to render both — mixes push (event-driven) and pull (query-time) concerns
- Replace AttentionPromptSection — destroys existing urgency signal functionality
**Rationale:** AttentionPromptSection renders push-based CognitiveAttentionRequired signals. TemporalFocus is a pull-based ranked view from TemporalIndex. Different data sources, different rendering needs. Follows one-section-per-subsystem pattern.
**Trade-offs:** One more prompt section in the prompt
**Sources:** AttentionPromptSection.java, CognitiveAttentionMediator.java, TemporalFocus.java
**Exploration:** quick
**Status:** captured

## D2: Query timing — tick() vs promptSections()

**Choice:** Query TemporalIndex during tick()
**Alternatives:**
- Query lazily during promptSections() — breaks established pattern, adds I/O during prompt assembly
**Rationale:** tick() refreshes all cognitive state; promptSections() reads cached state. Follows existing lastBriefing pattern. tick() has agentId, tenantId, and activeSubjects available for building the query.
**Trade-offs:** Data might be slightly stale if tick interval is long
**Sources:** CognitionCore.java:177 (tick method), CognitionCore.java:407 (promptSections)
**Exploration:** quick
**Status:** captured

## D3: AffectTrajectory computation

**Choice:** Wire real trajectory computation from affect memory data
**Alternatives:**
- Pass empty trajectories — leaves trajectory-modified scoring as dead code, defers to "later" indefinitely
**Rationale:** All infrastructure exists: AffectTrajectoryAnalyzer.analyze() in cognitive-index (on classpath), CaseMemoryStore queryable for domain="affect" memories, CognitiveProfile.computeTrajectory() demonstrates the exact pattern. No new API needed — compose existing utilities during tick(). Building it now means TemporalFocus scoring works fully from day one.
**Trade-offs:** Adds CaseMemoryStore as a dependency to the tick path (query cost per tick)
**Sources:** AffectTrajectoryAnalyzer.java, AffectTrajectoryDecorator.java, CognitiveProfile.java:213 (computeTrajectory pattern)
**Exploration:** quick
**Status:** captured
