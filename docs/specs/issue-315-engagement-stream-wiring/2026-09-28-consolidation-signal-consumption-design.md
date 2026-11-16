# Consolidation Signal Consumption — Design Spec

**Issue:** casehubio/blocks#312
**Epic:** casehubio/blocks#311 (neocortex-cognitive-integration)
**Date:** 2026-09-28
**Decisions:** D4, D5, D6

## Problem

Neocortex consolidation phases produce rich knowledge during periodic sleep-cycle processing — graduated experiences, merge candidates, curiosity questions, community summaries. Today, only low-fidelity attention signals (`AttentionSignal`) cross the boundary into blocks via `CognitiveAttentionRequired`. These signals have critical limitations:

- `EXPERIENCE_GRADUATED` signals have `sourceNodeId = null` (node IDs from `addNodes()` are discarded)
- `MERGE_CANDIDATE` signals have `principalId = null` (tenant-wide operation, broadcasts via `__broadcast__`)
- `CuriosityRefreshPhase` computes rich `CuriositySignal` records (question, description, targetNodeId) and **discards the entire list**
- `CommunitySummaryPhase` creates Summary nodes and **discards the node IDs**

Agents see "attention required: [EXPERIENCE_GRADUATED] some-node (0.85): experience graduated" but cannot reason about what matured, what merged, or what questions emerged.

## Solution

A parallel data channel — **typed consolidation artifacts** — alongside the existing attention signal flow. Artifacts carry the actual knowledge content; signals continue to drive threshold-based attention gating. Clean separation of concerns.

## Architecture

### Neocortex Changes

#### 1. ConsolidationArtifact sealed interface (mindmap-api)

New file in `io.casehub.neocortex.mindmap` package (mindmap-api module):

```java
public sealed interface ConsolidationArtifact {
    String tenantId();
    Instant timestamp();

    record GraduatedExperience(String tenantId, String nodeId, String name,
            String cognitiveKind, double graduationScore, String agentId,
            String eventType, Instant timestamp) implements ConsolidationArtifact {}

    record MergePerformed(String tenantId, String keepNodeId,
            String removedNodeId, double score, String reason,
            Instant timestamp) implements ConsolidationArtifact {}

    record MergeFlagged(String tenantId, String nodeId1, String nodeId2,
            double score, String reason,
            Instant timestamp) implements ConsolidationArtifact {}

    record CuriosityQuestion(String tenantId, String targetNodeId,
            String targetSubgraphId, String question, String description,
            double score, Instant timestamp) implements ConsolidationArtifact {}

    record CommunitySummaryCreated(String tenantId, String summaryNodeId,
            String subgraphId, String title, int memberCount,
            Instant timestamp) implements ConsolidationArtifact {}
}
```

**Module placement:** ConsolidationArtifact goes in **mindmap-api**, following the established platform pattern: `CognitiveAttentionRequired`, `AttentionSignal`, and `AttentionBriefing` — all cross-boundary contract types consumed by blocks — live in mindmap-api, while implementation types (`CognitiveAttentionAccumulator`, consolidation phases) stay in mindmap-intelligence. ConsolidationArtifact is a contract type consumed by blocks-core's `ConsolidationMediator` and `ConsolidationPromptSection`, so it follows the same split.

**Self-describing types:** Every variant carries `tenantId`, matching the `AttentionSignal` pattern. Artifacts remain self-describing when extracted from their `ConsolidationCompleted` envelope — for logging, observability, and debugging.

`GraduatedExperience` is agent-specific (`agentId` from source memory's `subject().id()`). All other variants are tenant-wide.

**Note:** `ConsolidationCompleted` (the CDI event) currently lives in mindmap-intelligence. The same cross-boundary pattern argues it should also move to mindmap-api. That is a separate refactor — this spec places the new types correctly from the start.

#### 2. ConsolidationPhase.artifacts() default method (mindmap-intelligence)

```java
default List<ConsolidationArtifact> artifacts() { return List.of(); }
```

Parallel to existing `signals()`. Each phase overrides to return typed artifacts after `run()`.

**beginTick() contract:** Phases that accumulate artifacts in a field MUST clear them in `beginTick()`, matching the existing signals pattern:
```java
@Override public void beginTick() {
    pendingSignals.clear();
    pendingArtifacts.clear();  // NEW — same lifecycle as signals
}
```
Failure to clear in `beginTick()` would leak stale artifacts from a previous tick into the next consolidation.

#### 3. Phase modifications

**ExperienceConsolidationPhase:** Capture the `List<String>` return from `mindMapStore.addNodes(graduatedInputs, tenantId)`. Pair each returned nodeId with the corresponding graduation data (name, cognitiveKind, graduationScore, agentId, eventType) to build `GraduatedExperience` artifacts. Store in a field, return from `artifacts()`.

**MergeDetectionPhase:** In the merge scoring loop, when `score >= autoMergeThreshold` (0.9), build `MergePerformed` after `store.mergeNodes()`. When `score >= 0.7` (flagged only), build `MergeFlagged`. Data is already in local variables (`keepId`, `removedId`, `score`, `reason`).

**CuriosityRefreshPhase:** `run()` currently calls `curiosityGenerator.computeSignals(tenantId, Set.of())` and discards the return. Instead, capture the `List<CuriositySignal>` and map each to a `CuriosityQuestion` artifact. The `CuriositySignal` record already has `question`, `description`, `targetNodeId`, `targetSubgraphId`, `score`.

**CommunitySummaryPhase:** Capture the `addNodes()` return for newly created Summary nodes. Pair with the generated title (from top-3 member names) and memberCount already in scope.

#### 4. ConsolidationCompleted enrichment

```java
public record ConsolidationCompleted(
    String tenantId,
    List<PhaseResult> phaseResults,
    List<ConsolidationArtifact> artifacts    // NEW
) {}
```

#### 5. ConsolidationScheduler.runPhases() change

After each `phase.run()`, call `phase.artifacts()` and collect into an aggregate list. Pass to `ConsolidationCompleted` alongside existing `phaseResults`. Existing signal flow (`signals()` → `CognitiveAttentionAccumulator` → `CognitiveAttentionRequired`) is untouched.

**Backward compatibility:** Adding `artifacts` to the `ConsolidationCompleted` record changes its canonical constructor. The only producer is `ConsolidationScheduler` (updated in this issue). Test constructors must also be updated. No external consumers exist in blocks today (`find_references` returned zero hits).

### Blocks Changes

#### 6. ConsolidationMediator

`@ApplicationScoped` CDI observer. Snapshot-based caching with per-agent seen-tracking.

```java
@ApplicationScoped
public class ConsolidationMediator {

    private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> agentLastSeen = new ConcurrentHashMap<>();

    void onConsolidation(@Observes ConsolidationCompleted event) {
        if (event.artifacts() == null || event.artifacts().isEmpty()) return;
        snapshots.put(event.tenantId(), new Snapshot(event.artifacts(), Instant.now()));
    }

    public List<ConsolidationArtifact> drainForAgent(String agentId, String tenantId) {
        var snapshot = snapshots.get(tenantId);
        if (snapshot == null) return List.of();
        var seenKey = tenantId + ":" + agentId;
        var lastSeen = agentLastSeen.get(seenKey);
        if (lastSeen != null && !snapshot.timestamp.isAfter(lastSeen)) return List.of();
        agentLastSeen.put(seenKey, snapshot.timestamp);
        return snapshot.artifacts.stream()
            .filter(a -> isRelevant(a, agentId))
            .toList();
    }

    private static boolean isRelevant(ConsolidationArtifact a, String agentId) {
        return switch (a) {
            case GraduatedExperience e -> e.agentId().equals(agentId);
            case MergePerformed m -> true;
            case MergeFlagged m -> true;
            case CuriosityQuestion q -> true;
            case CommunitySummaryCreated c -> true;
        };
    }

    record Snapshot(List<ConsolidationArtifact> artifacts, Instant timestamp) {}
}
```

**Snapshot-not-drain:** Unlike `CognitiveAttentionMediator` which drains on read, the snapshot stays until the next consolidation replaces it. This allows all agents in a multi-agent system (e.g. 17 wacky-manor characters) to see each consolidation's artifacts.

**Per-agent seen-tracking:** `agentLastSeen` uses a composite key (`tenantId + ":" + agentId`) to prevent cross-tenant timestamp collisions. Without the composite key, Agent X draining Tenant A at T5 would set a timestamp that blocks Agent X from seeing Tenant B's consolidation at T3. The `snapshots` map correctly keys by tenantId; `agentLastSeen` must do the same.

**Agent-relevance filter — explicit design choice:** `GraduatedExperience` is filtered by `agentId`. Graduated experiences originate from an agent's episodic memories — Agent A's experiences are contextually specific to A's interactions. The graduated knowledge itself (the new mindmap node) is already tenant-wide and visible to all agents through normal mindmap queries. The artifact is a notification that the graduation EVENT occurred, which is relevant only to the originating agent. All other artifact types (merges, curiosity, community summaries) are tenant-wide operations — visible to all agents.

**Signal/artifact overlap:** Some consolidation events produce BOTH an `AttentionSignal` (via `CognitiveAttentionAccumulator`) and a `ConsolidationArtifact`. This is intentional. Signals are threshold-gated — they may not fire if cumulative significance is below the attention threshold. Artifacts always persist in the snapshot. When both channels fire for the same event, the agent sees two complementary renderings: an attention alert (`[MERGE_CANDIDATE] keepNode (0.92): auto-merged`) and a knowledge summary (`Unified concepts: keepNode absorbed removedNode — name+neighbors`). The different framing serves different cognitive purposes, and the prompt cost is bounded by `MAX_ITEMS_PER_TYPE`.

**Lifecycle:** Both maps are bounded in practice — `snapshots` grows to at most one entry per active tenant (each consolidation replaces the previous snapshot), and `agentLastSeen` grows to `tenants × agents`. Neither is a growth-over-time leak. If tenant deactivation is introduced, a cleanup hook should remove the corresponding snapshot and agentLastSeen entries.

#### 7. ConsolidationPromptSection

Single `PromptSection` implementation rendering all artifact types as subsections:

```java
public class ConsolidationPromptSection implements PromptSection {

    private static final int MAX_ITEMS_PER_TYPE = 5;
    private final List<ConsolidationArtifact> artifacts;

    @Override
    public @Nullable String contribute(PromptContext context) {
        if (artifacts.isEmpty()) return null;
        var sb = new StringBuilder("Recent consolidation insights:");
        renderGraduated(sb);
        renderMerges(sb);
        renderCuriosity(sb);
        renderCommunity(sb);
        var result = sb.toString();
        return result.equals("Recent consolidation insights:") ? null : result;
    }
}
```

Rendering per type:
- **Graduated:** `"\nConsolidated knowledge: [name] (kind: [cognitiveKind], strength: [score])"`
- **MergePerformed:** `"\nUnified concepts: [keepNode] absorbed [removedNode] — [reason]"`
- **MergeFlagged:** `"\nPossible connection: [node1] and [node2] seem related ([score]) — [reason]"`
- **CuriosityQuestion:** `"\nQuestion to explore: [question] — [description]"`
- **CommunitySummary:** `"\nCommunity theme: [title] ([memberCount] concepts)"`

Each type sorted by descending significance score before capping at MAX_ITEMS_PER_TYPE = 5. Significance scores: `graduationScore` (GraduatedExperience), `score` (MergePerformed, MergeFlagged, CuriosityQuestion), `memberCount` (CommunitySummaryCreated). This ensures the most significant artifacts survive truncation rather than depending on iteration order.

#### 8. CognitionConfig

Add `consolidationEnabled` flag:

```java
public record CognitionConfig(
    // ... existing 15 flags ...
    boolean consolidationEnabled    // NEW — 16th flag
) { ... }
```

Update `all()` (default true), `none()` (false), `with()` switch.

#### 9. CognitionCore wiring

- Add nullable `ConsolidationMediator consolidationMediator` field + constructor parameter
- In `tick()`: `lastConsolidationArtifacts = consolidationMediator.drainForAgent(agentId, tenantId)`
- In `promptSections()`: add `ConsolidationPromptSection` when `consolidationEnabled && lastConsolidationArtifacts != null && !lastConsolidationArtifacts.isEmpty()`

#### 10. SocialAvatarCognition

Add `Optional<ConsolidationMediator>` to constructor. Pass `.orElse(null)` to CognitionCore. No `@DefaultBean` needed — `ConsolidationMediator` is `@ApplicationScoped` with `@Observes`, CDI discovers it automatically.

## Testing Strategy

**Neocortex:**
- Unit test per phase: verify `artifacts()` returns correct typed artifacts after `run()`
- ConsolidationScheduler integration test: verify `ConsolidationCompleted` carries artifacts
- Backward compat: existing `signals()` behavior unchanged

**Blocks:**
- ConsolidationMediator: snapshot caching, per-agent drain, seen-tracking, agent-relevance filtering
- ConsolidationPromptSection: rendering per artifact type, MAX_ITEMS cap, empty/null handling
- CognitionCore: consolidation artifacts drained at tick, rendered in promptSections()
- CognitionConfig: consolidationEnabled flag gating

## Data Flow Summary

```
ConsolidationScheduler.runPhases(tenantId)
  → each phase.run(tenantId, subgraphs)
  → each phase.artifacts() → collected into allArtifacts     // NEW
  → each phase.signals()   → CognitiveAttentionAccumulator   // UNCHANGED
  → ConsolidationCompleted(tenantId, phaseResults, allArtifacts) fired as CDI event

ConsolidationMediator (@Observes ConsolidationCompleted)
  → snapshots.put(tenantId, Snapshot(artifacts, now))

CognitionCore.tick(agentId, tenantId)
  → consolidationMediator.drainForAgent(agentId, tenantId)
  → filters by agent relevance
  → stores as lastConsolidationArtifacts

CognitionCore.promptSections()
  → ConsolidationPromptSection(lastConsolidationArtifacts)
  → renders graduated, merges, curiosity, community subsections
```

## References

- ConsolidationScheduler.java — orchestrator flow, phase iteration, event firing
- ExperienceConsolidationPhase.java — graduation pipeline, addNodes() return discarded
- MergeDetectionPhase.java — auto-merge/flag logic, null principalId on signals
- CuriosityRefreshPhase.java — computeSignals() return discarded
- CuriositySignalGenerator.java — rich CuriositySignal computation
- CommunitySummaryPhase.java — k-core decomposition, summary node creation
- CognitiveAttentionMediator.java — existing CDI observer pattern (drain model)
- CognitiveAttentionAccumulator.java — __broadcast__ for null principalId
- CognitionCore.java — tick/promptSections lifecycle, attention gating
- AttentionPromptSection.java — existing prompt section pattern
- ReflectionRetrievalOrchestrator.java — tick-driven orchestrator pattern (not chosen)
- CognitionConfig.java — subsystem enable flags
- SocialAvatarCognition.java — Optional dependency wiring
- casehubio/blocks#306 — runtime attention override policy (related)
- casehubio/blocks#307 — CognitionSnapshot attention observability (closed, related)
