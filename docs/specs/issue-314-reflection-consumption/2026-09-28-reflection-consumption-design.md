# Reflection Consumption — Heuristic Retrieval in Agent Reasoning

**Issue:** casehubio/blocks#314
**Epic:** casehubio/blocks#311 (Neocortex cognitive integration)
**Depends on:** casehubio/neocortex#338 (ReflectionSynthesizer — closed)

## Context

Neocortex #338 implemented LLM-backed `ReflectionSynthesizer` that extracts actionable heuristics from experience trajectories. Reflections are stored as `domain="reflection"` memories with salience scoring. The retrieval API is ready — `ReflectionQuery` factory helpers and `CaseMemoryStore.query()`.

Blocks needs to retrieve relevant reflections during agent reasoning and present them as cognitive context in the LLM prompt. This follows the same wiring pattern as #315 (engagement stream) and #316 (TemporalFocus integration).

## Design

### Retrieval Strategy

Use `ReflectionQuery.salient(agentId, tenantId)` — salience-ordered, limit 15. No query context needed at tick time. Salience scoring ranks by importance, which is appropriate for general reasoning context. A future issue could add situation-specific `search()` retrieval if the eval shows salient alone is insufficient.

### Dependency Isolation

Extend the existing `ReflectionQueryStore` interface in blocks-core with a `findSalient()` method rather than introducing a new functional interface or adding a direct dependency on neocortex memory modules. `ReflectionQueryStore` already exists for event streaming (`findSince`); adding prompt-time retrieval is a natural extension of the same abstraction.

### Components

#### 1. ReflectionQueryStore (extend existing — blocks-core)

Add method:
```java
List<ReflectionEntry> findSalient(String agentId, String tenantId);
```

`NoOpReflectionQueryStore` returns `Collections.emptyList()`.

#### 2. ReflectionRetrievalOrchestrator (new — blocks-core)

Follows `TemporalFocusOrchestrator` pattern:
- Constructor takes `ReflectionQueryStore`
- `tick(String agentId, String tenantId)` — calls `findSalient()`, caches result in `volatile List<ReflectionEntry> lastReflections`
- `getLastReflections()` — returns cached value (read by prompt section)

Placed in the cognition package alongside other orchestrators.

#### 3. ReflectionPromptSection (new — blocks-core)

Follows `TemporalFocusPromptSection` pattern:
- Constructor takes `List<ReflectionEntry>`
- Renders as a "Learned heuristics:" prompt block
- Each entry renders its `insight` text
- Display limit: top 5 entries (salience already orders them; keep prompt concise)
- Returns empty/no section when list is empty

#### 4. CognitionConfig (extend — blocks-core)

Add `reflectionEnabled` boolean field. Update `all()`, `none()`, `with()`, `withDirectives()`, `without()`.

#### 5. CognitionCore (extend — blocks-core)

- Add `@Nullable ReflectionRetrievalOrchestrator reflectionOrchestrator` field
- Add to full constructor (becomes 17th param)
- Short constructors pass `null`
- `tick()`: in SOURCE phase — `if (config.reflectionEnabled() && reflectionOrchestrator != null) { safeRun(() -> reflectionOrchestrator.tick(agentId, tenantId)); }`
- `promptSections()`: conditionally add `new ReflectionPromptSection(reflectionOrchestrator.getLastReflections())`

#### 6. SocialAvatarCognition (extend — blocks-core)

Add `Optional<ReflectionRetrievalOrchestrator>` constructor parameter. Pass `.orElse(null)` to CognitionCore's full constructor.

#### 7. CDI/Spring Wiring

**BlocksBeans (blocks/ module):**
- Inject `Instance<ReflectionQueryStore>` field
- Produce `ReflectionRetrievalOrchestrator` as `@ApplicationScoped` from the query store
- Pass to `SocialAvatarCognition` via `optionalFrom()`

**BlocksAutoConfiguration (blocks-spring/ module):**
- `Optional<ReflectionQueryStore>` injection
- Conditional `ReflectionRetrievalOrchestrator` bean
- Wire to `SocialAvatarCognition`

#### 8. CDI Implementation of findSalient

In the existing `ReflectionQueryStore` CDI implementation (or a new `@Alternative` if none exists beyond NoOp):
- Inject `CaseMemoryStore`
- `findSalient()` calls `caseMemoryStore.query(ReflectionQuery.salient(agentId, tenantId))`
- Maps `Memory` → `ReflectionEntry` (extract insight from text, generatedAt from timestamp, source case IDs from attributes)

### Phase Placement

SOURCE phase in `CognitionCore.tick()`. Reflections are retrieved knowledge that informs reasoning — same tier as temporal focus. They don't depend on other cognitive outputs (not DERIVED) and aren't foundational config (not FOUNDATION).

## Testing

- **ReflectionRetrievalOrchestratorTest** — tick caches salient reflections; returns empty when store is empty; subsequent ticks refresh cache
- **ReflectionPromptSectionTest** — renders insight text; limits to 5 entries; handles empty list; omits section when no reflections
- **CognitionCoreTest** — promptSections includes reflection section after tick when enabled; excluded when disabled

## References

- ReflectionQuery.java (neocortex memory-api) — factory helpers for reflection retrieval
- ReflectionQueryStore.java (blocks-core) — existing blocks-side abstraction
- ReflectionEntry.java (blocks-core) — existing blocks-side record
- TemporalFocusOrchestrator.java (blocks-core) — orchestrator pattern reference
- TemporalFocusPromptSection.java (blocks-core) — prompt section pattern reference
- casehubio/blocks#315 spec — engagement stream wiring (dependency isolation pattern)
- casehubio/blocks#316 spec — TemporalFocus integration (orchestrator + prompt section pattern)
- casehubio/neocortex#338 — ReflectionSynthesizer implementation (closed)
