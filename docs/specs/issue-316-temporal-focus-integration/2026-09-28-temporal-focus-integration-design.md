# Design: TemporalFocus integration (#316)

## Problem

Blocks agents have no awareness of what's temporally salient — upcoming events, recent changes, or entities with worsening affect trajectories. Neocortex provides the full computation stack (`TemporalIndex` → `TemporalFocus.focus()` → ranked `AttentionItem` list), but nothing in blocks queries it or renders it in prompts.

## Scope

**In scope:**
- `TemporalFocusOrchestrator` — encapsulates TemporalIndex query, AffectTrajectory computation, and TemporalFocus ranking
- `TemporalFocusPromptSection` — renders ranked attention items in agent prompts
- Wire into CognitionCore tick/promptSections cycle
- Thread through SocialAvatarCognition/BlocksBeans/BlocksAutoConfiguration
- `temporalFocusEnabled` flag in CognitionConfig
- Unit tests with Mockito (no CDI container)

**Out of scope:**
- CBR store querying in TemporalIndex (injected but not yet implemented upstream)
- Modifying TemporalFocus scoring algorithm (neocortex concern)

## Design

### Change 1: TemporalFocusOrchestrator

New class in `io.casehub.blocks.agentic.social` that encapsulates the temporal focus computation cycle. Follows the existing orchestrator pattern (MoodOrchestrator, StrategyLearningOrchestrator, etc.).

**Dependencies:** `TemporalIndex`, `CaseMemoryStore`, `TemporalFocusConfig`

**Methods:**
- `tick(String agentId, String tenantId, Set<String> activeSubjects)` — queries TemporalIndex for recent/upcoming entries, computes per-entity AffectTrajectories from affect memories, runs `TemporalFocus.focus()`, caches the result
- `List<AttentionItem> lastFocus()` — returns the cached ranked list (empty if no tick has run)

**Query construction:** Build a `TemporalQuery.since()` with a 24-hour lookback window, limited to the agent's tenant, with entity IDs from active subjects. Also query `TemporalQuery.upcoming()` for future events. Merge results and pass to `TemporalFocus.focus()`.

**Trajectory computation:** For each unique entity ID in the temporal entries, query `CaseMemoryStore` for `domain="affect"` memories and run `AffectTrajectoryAnalyzer.analyze()`. Build `Map<String, AffectTrajectory>` keyed by entity ID. This follows the exact pattern from `CognitiveProfile.computeTrajectory()`.

**Thread safety:** The orchestrator holds a `volatile List<AttentionItem>` field for the cached result — same pattern as `lastBriefing` in CognitionCore.

### Change 2: TemporalFocusPromptSection

New `PromptSection` implementation in `io.casehub.blocks.agentic.social.prompt`.

**Constructor:** Takes `List<AttentionItem>` (the ranked list from the orchestrator).

**Rendering:** Returns null if the list is empty. Otherwise renders:
```
Temporal awareness:
- [reason] (salience: 0.85)
- [reason] (salience: 0.62)
- [reason] (salience: 0.41)
```

Limits output to the top 5 items to avoid prompt bloat. The list is already sorted by salience descending from `TemporalFocus.focus()`.

### Change 3: CognitionConfig — add temporalFocusEnabled

Add `boolean temporalFocusEnabled` as the 14th field in `CognitionConfig`. `all()` returns `true`, `none()` returns `false`. Update `with()` and `withDirectives()` to propagate the new field.

### Change 4: CognitionCore — wire TemporalFocusOrchestrator

Add `@Nullable TemporalFocusOrchestrator temporalFocus` as a field and constructor parameter (16th parameter).

**In tick():** After the FOUNDATION phase (after attention mediator drain), call:
```java
if (config.temporalFocusEnabled() && temporalFocus != null) {
    safeRun(() -> temporalFocus.tick(agentId, tenantId,
            resolver.relevantSubjects(agentId, tenantId)));
}
```

This goes in the FOUNDATION phase because temporal focus is a perceptual input (what's salient right now), not derived from other cognitive state.

**In promptSections():** After AttentionPromptSection, add:
```java
if (config.temporalFocusEnabled() && temporalFocus != null) {
    var items = temporalFocus.lastFocus();
    if (!items.isEmpty()) {
        sections.add(new TemporalFocusPromptSection(items));
    }
}
```

### Change 5: Thread through SocialAvatarCognition and CDI/Spring producers

Add `Optional<TemporalFocusOrchestrator>` to SocialAvatarCognition constructor. Pass `.orElse(null)` to CognitionCore.

In BlocksBeans: produce `TemporalFocusOrchestrator` as an `@ApplicationScoped` bean from `TemporalIndex` + `CaseMemoryStore` + `TemporalFocusConfig.defaults()`. Inject via `Instance<TemporalFocusOrchestrator>` and pass to SocialAvatarCognition.

In BlocksAutoConfiguration: same pattern with Spring `Optional<>`.

### Testing

Plain JUnit 5 + Mockito:

**TemporalFocusOrchestrator:**
1. tick with entries and trajectories — verify lastFocus() returns ranked items
2. tick with no entries — verify lastFocus() returns empty list
3. tick with entries but no affect memories — verify items scored by recency only (empty trajectories)

**TemporalFocusPromptSection:**
4. renders top items with salience scores
5. returns null for empty list
6. limits to 5 items

**CognitionCore integration:**
7. tick with temporalFocus orchestrator — verify tick() is called on orchestrator
8. promptSections includes TemporalFocusPromptSection when focus items exist
9. promptSections skips when temporalFocusEnabled is false
10. null orchestrator doesn't error

### Data flow (after)

```
CognitionCore.tick()
  ├── attentionMediator.drainAttention()      [existing — push-based]
  ├── temporalFocus.tick()                     [NEW — pull-based]
  │     ├── TemporalIndex.query(recent)
  │     ├── TemporalIndex.query(upcoming)
  │     ├── CaseMemoryStore.query(affect)      per entity
  │     ├── AffectTrajectoryAnalyzer.analyze()  per entity
  │     └── TemporalFocus.focus(entries, trajectories)
  ├── mood, drives, narrative, ...             [existing]
  └── ...

CognitionCore.promptSections()
  ├── AttentionPromptSection(lastBriefing)     [existing — event signals]
  └── TemporalFocusPromptSection(lastFocus)    [NEW — ranked temporal items]
```

## Trade-offs

- CognitionCore constructor gains one more parameter (16th). Telescoping chain gets longer. Accepted: follows established pattern, builder refactoring is a separate concern.
- Trajectory computation adds CaseMemoryStore queries during tick(). Cost is proportional to active subjects × affect memory depth. Mitigated by TemporalQuery limit and recency window.
- TemporalFocusPromptSection caps at 5 items. This is a rendering choice — the orchestrator caches the full ranked list for other consumers.

## References

- `TemporalFocus.java` (neocortex cognitive-index) — `focus()` static utility
- `TemporalIndex.java` (neocortex cognitive-index) — CDI bean, `query()` method
- `AttentionItem.java` (neocortex cognitive-index) — record with entry, salience, reason
- `TemporalFocusConfig.java` (neocortex cognitive-index) — configurable thresholds
- `AffectTrajectoryAnalyzer.java` (neocortex cognitive-index) — `analyze(List<Memory>)` static utility
- `CognitiveProfile.java:213` (neocortex cognitive-index) — existing trajectory computation pattern
- `AttentionPromptSection.java` (blocks-core) — existing push-based attention rendering
- `CognitionCore.java:177` — tick() method with phase ordering
- `CognitionCore.java:407` — promptSections() method
- `CognitionConfig.java` — subsystem enable flags
- casehubio/blocks#311 — parent epic
