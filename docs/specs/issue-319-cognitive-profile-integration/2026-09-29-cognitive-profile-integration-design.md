# Design: CognitiveProfile Deep Integration (#319)

**Epic:** #311 (Neocortex cognitive integration)
**Scale:** M | **Complexity:** Med
**Date:** 2026-09-29

## Problem

CognitionCore renders cognitive state through ~10 isolated PromptSections (mood, drives, user model, mental model, strategy, goals, etc.). Each section queries its own orchestrator and renders its own slice. There is no unified view of entities — what the agent knows about a person, their relationships, memories across domains, or emotional trajectory.

Neocortex's `CognitiveProfile` provides cross-store entity resolution: MindMap node + edges + memories across 6 domains + affect trajectory + perspectival overlay, unified into an `EntityKnowledge` record. It supports perspectival resolution via `asSeenBy` PrincipalId and `compare()` for batched multi-agent perspective comparison.

## Goal

Wire CognitiveProfile into blocks so agents render rich, subjective, per-entity knowledge in their prompts. The integration adds a new prompt section alongside existing ones (D1), uses attention-driven entity selection (D2), always resolves perspectivally (D3), runs at TERMINAL tick phase (D4), renders as structured natural language (D5), and wires both resolve() and compare() APIs (D6).

## Architecture

Three new classes in `blocks-core`:

### CognitiveProfileParticipant

Implements `CognitionTickParticipant`, registered at `TERMINAL` phase (D4, D7). During `tick()`:

1. **Collect entity seed set** from three sources (D2):
   - Active subjects from `SubjectResolver` — map subject ID to `CognitiveProfileQuery.byName(subjectId, tenantId)`
   - Attention signals from `AttentionBriefing` — map `AttentionSignal.sourceNodeId` to `CognitiveProfileQuery.byId(sourceNodeId, tenantId)` (D9)
   - Temporal focus items from `TemporalFocusOrchestrator.lastFocus()` — extract entity identity from `TemporalSource` (e.g., `FromMindMap` carries the node)
   - Deduplicate across sources by node ID

2. **Resolve profiles** — call `CognitiveProfile.resolve(query)` for each entity in the seed set, with `asSeenBy` set to `PrincipalId.agent(agentId)` (D3). The agentId is available from the tick context.

3. **Compare perspectives** (optional, gated by `CognitionConfig.perspectiveComparisonEnabled`) — call `CognitiveProfile.compare(query, agentSet)` for entities where multi-agent context is available (D6). The agent set comes from the active subjects.

4. **Cache results** — store `List<EntityKnowledge>` (resolve results) and `Map<String, Map<PrincipalId, EntityKnowledge>>` (compare results, keyed by entity node ID) for prompt rendering.

Constructor takes: `CognitiveProfile`, `CognitiveAttentionMediator` (to read last briefing), `TemporalFocusOrchestrator` (to read last focus), `CognitionConfig`.

### EntityKnowledgePromptSection

Reads cached entity knowledge from `CognitiveProfileParticipant`. Renders structured natural language per entity (D5):

```
About [entity name]:
  [node properties — type, key attributes]
  Relationships: [edge summaries — "connected to X (type: Y)"]
  You remember: [top memories per domain, most salient first]
  Emotional trajectory: [trending up/down/stable, rate of change]
  [Unresolved references: "You've heard of X but don't know them yet"]
```

When `perspectiveComparisonEnabled` and compare() results exist, append:

```
How others see [entity]:
  [Agent A] sees: [key differences from your view]
  [Agent B] sees: [key differences from your view]
```

**Rendering limits** (configurable via constructor defaults):
- Max 3 memories per domain per entity
- Max 5 edges per entity
- Max 3 comparison agents per entity

### CognitionConfig additions

Two new boolean flags:
- `entityKnowledgeEnabled` (default: true when CognitiveProfile is available)
- `perspectiveComparisonEnabled` (default: false — opt-in, expensive)

Follow existing pattern: add to the `CognitionConfig` record, update `all()`, `none()`, `with()`.

## Entity Identity Mapping (D9)

Three identity spaces are relevant:

| Space | Source | Example |
|-------|--------|---------|
| MindMap node ID | `AttentionSignal.sourceNodeId` | `"node-abc123"` |
| Subject ID | `SubjectResolver` | `"penelope-pitstop"` |
| PrincipalId | `AttentionBriefing.principalId` | `PrincipalId.agent("hooded-claw")` |

Mapping strategy:
- Subject IDs → `CognitiveProfileQuery.byName(subjectId, tenantId)` — CognitiveProfile resolves by entity name
- AttentionSignal sourceNodeId → `CognitiveProfileQuery.byId(nodeId, tenantId)` — direct node lookup
- PrincipalId → used for `asSeenBy`, not for entity resolution
- TemporalFocus items → extract node ID from `TemporalSource.FromMindMap` node

Deduplication: collect into `LinkedHashSet<CognitiveProfileQuery>` keyed by nodeId/entityName to avoid resolving the same entity twice.

## CDI/Spring Wiring

Same pattern as `CognitiveGoalOrchestrator` in the Builder (D7):

**SocialAvatarCognition.Builder:**
- New field: `Optional<CognitiveProfile> cognitiveProfile = Optional.empty()`
- New setter: `cognitiveProfile(Optional<CognitiveProfile>)`
- In `SocialAvatarCognition(Builder)` constructor: when `cognitiveProfile` is present, create `CognitiveProfileParticipant` and register at TERMINAL via `core.addParticipant(CognitionPhase.TERMINAL, participant)`

**BlocksBeans (Quarkus):**
- New injection: `@Inject Instance<CognitiveProfile> cognitiveProfileInstance`
- Pass `optionalFrom(cognitiveProfileInstance)` to builder

**BlocksAutoConfiguration (Spring):**
- New parameter: `Optional<CognitiveProfile> cognitiveProfile`
- Pass to builder

**SocialCognitionDefaultBeans:** No default needed — `CognitiveProfile` is `@ApplicationScoped` in neocortex `cognitive-index`, present only when that module is on the classpath. Graceful degradation: when absent, no participant is created, no section rendered.

## API Ownership (D8)

These decisions express blocks' requirements toward neocortex. `CognitiveProfile`, `EntityKnowledge`, and `CognitiveProfileQuery` already exist in `casehub-neocortex-cognitive-index`. If the neocortex API changes, these integration decisions will be revised to match.

## Testing

### CognitiveProfileParticipantTest
- Verify seed collection from attention signals, temporal focus items, and active subjects
- Verify `resolve()` called with correct `asSeenBy` PrincipalId
- Verify `compare()` gated by `perspectiveComparisonEnabled` config flag
- Verify deduplication across sources
- Verify graceful handling when CognitiveProfile returns `Optional.empty()`

### EntityKnowledgePromptSectionTest
- Verify NL rendering for a complete EntityKnowledge (node + edges + memories + trajectory)
- Verify rendering with empty memories, no edges, no trajectory
- Verify unresolved refs rendering
- Verify comparison rendering when perspectiveComparisonEnabled
- Verify rendering limits (memory cap, edge cap, agent cap)

### CognitionCoreTest additions
- Register `CognitiveProfileParticipant` at TERMINAL, tick, verify entity knowledge section appears in `promptSections()`
- Verify section absent when `entityKnowledgeEnabled` is false

## Files Changed

| Module | File | Change |
|--------|------|--------|
| blocks-core | `CognitiveProfileParticipant.java` | New — tick participant |
| blocks-core | `prompt/EntityKnowledgePromptSection.java` | New — prompt section |
| blocks-core | `CognitionConfig.java` | Add 2 flags |
| blocks-core | `CognitionCore.java` | Add entityKnowledge to promptSections() |
| blocks-core | `prompt/SocialAvatarCognition.java` | Builder: cognitiveProfile field + wiring |
| blocks | `BlocksBeans.java` | Inject CognitiveProfile, pass to builder |
| blocks-spring | `BlocksAutoConfiguration.java` | Inject CognitiveProfile, pass to builder |
| blocks-core | `CognitiveProfileParticipantTest.java` | New — unit tests |
| blocks-core | `prompt/EntityKnowledgePromptSectionTest.java` | New — unit tests |
| blocks | `CognitionCoreTest.java` | Integration test additions |

## References

- `CognitiveProfile` — `casehub-neocortex-cognitive-index` (resolve, compare, PerspectivalResolver)
- `EntityKnowledge` — record: node, edges, memories, trajectory, unresolvedRefs, perceiver
- `CognitiveProfileQuery` — byId, byName, withAsSeenBy, withDomains
- `AffectTrajectory` — pleasureSlope, arousalVolatility, trend, rateOfChange
- `CognitionCore.addParticipant()` — TERMINAL phase participant registration
- `CognitiveGoalOrchestrator` — existing TERMINAL participant pattern (SocialAvatarCognition.Builder)
- `AttentionSignal` — sourceNodeId, sourceName for entity identity mapping
- `TemporalFocusOrchestrator.lastFocus()` — AttentionItem with TemporalSource
- Issue #283 — directive-minimal architecture (current partial cognitive data)
- Issue #311 — neocortex cognitive integration epic
