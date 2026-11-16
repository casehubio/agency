## D1: Retrieval strategy

**Choice:** Salient-only — use `ReflectionQuery.salient(agentId, tenantId)` for prompt-time retrieval
**Alternatives:**
- Search with context — `ReflectionQuery.search(question)` using current reasoning state as query. More targeted but requires assembling a query string from cognitive state.
- Both with fallback — try search() first, fall back to salient(). Flexible but adds complexity beyond S-scale scope.
**Rationale:** Salient retrieval matches the TemporalFocus pattern — cached at tick time, rendered in prompt section. No query context needed. Salience scoring already ranks by importance, which is what the agent needs for general reasoning context.
**Trade-offs:** Misses opportunity for situation-specific retrieval. A future issue could add search-based retrieval if the eval shows salient alone is insufficient.
**Sources:** ReflectionQuery.java (memory-api), TemporalFocusOrchestrator (#316 pattern)
**Exploration:** quick
**Status:** captured

## D2: Dependency isolation mechanism

**Choice:** Extend existing `ReflectionQueryStore` with `findSalient(agentId, tenantId)` method
**Alternatives:**
- New functional interface — `Function<(agentId, tenantId), List<ReflectionEntry>>` passed to CognitionCore. Same pattern as #315's `Consumer<EngagementEvent>`. More isolated but another single-method interface when one already exists.
- Direct CaseMemoryStore — add memory-api as blocks-core dependency, use CaseMemoryStore + ReflectionQuery directly. Simplest but couples blocks-core to neocortex memory module.
**Rationale:** `ReflectionQueryStore` already exists in blocks-core with `findSince()` for event streaming. Adding `findSalient()` is a natural extension. `NoOpReflectionQueryStore` handles the absent case. The CDI layer implements via `CaseMemoryStore.query(ReflectionQuery.salient(...))`.
**Trade-offs:** Widens an existing SPI rather than keeping it narrowly focused on event streaming. Acceptable because both methods serve reflection data retrieval.
**Sources:** ReflectionQueryStore.java (blocks-core), Consumer<EngagementEvent> pattern (#315)
**Exploration:** quick
**Status:** captured
