# Decisions — #367 Memory Hygiene Evolution Loop

## D1: Conflict mutex granularity

**Choice:** Per-agent — one active improvement per agent at a time
**Alternatives:**
- Per-dimension — more parallel, but retention/consolidation/confidence dimensions interact and would produce inconsistent results
- Per-agent-per-dimension — maximum parallelism but highest complexity, dimension interactions still problematic
**Rationale:** RetentionConfig is a single record per agent. Concurrent tuning proposals for the same agent's memory hygiene would conflict. Matches prompt optimisation's per-signature pattern.
**Trade-offs:** Cannot tune two agents' memory hygiene simultaneously if they share a tenancy. Unlikely bottleneck — evolution ticks are infrequent.
**Sources:** PromptConflictStrategy.java (per-signatureId pattern), RetentionConfig.java
**Exploration:** quick
**Status:** captured

## D2: SPI scope

**Choice:** Core four SPIs + CapabilityArea (five total)
**Alternatives:**
- Core four only — matches prompt optimisation exactly, but misses opportunity to be first CapabilityArea implementor
- Core four + DenyPatternProvider — too niche for initial implementation
**Rationale:** CapabilityArea is simple (one method returning a record) and provides health scoring that the evolution loop can use for prioritisation. Memory hygiene is a natural fit — health score maps to retention quality metrics.
**Trade-offs:** Slightly more scope than prompt optimisation. CapabilityArea has no existing implementor to reference.
**Sources:** CapabilityArea.java, CapabilityAreaAssessment.java (engine-api)
**Exploration:** quick
**Status:** captured

## D3: Quality data source

**Choice:** Both HygieneTick and MaintenanceTick via a MemoryHygieneOutcomeProvider @FunctionalInterface
**Alternatives:**
- HygieneTick only — simpler but misses reflection quality and integrity violation signals
- Direct HealthScoreSnapshot reads — tighter coupling, less testable, breaks the outcome provider abstraction
**Rationale:** Full quality picture. HygieneTick provides retention/eviction/consolidation metrics. MaintenanceTick adds reflection count, cross-links, integrity violations. The @FunctionalInterface pattern decouples data retrieval from proposal logic, matching PromptOutcomeProvider.
**Trade-offs:** Consumer must aggregate both tick types into a single quality summary. Slightly more complex wiring.
**Sources:** MemoryHygieneOrchestrator.java (HygieneTick sealed type), MemoryHygieneScheduler.java (MaintenanceTick sealed type), PromptOutcomeProvider.java (pattern)
**Exploration:** quick
**Status:** captured
