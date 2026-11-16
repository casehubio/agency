# Decisions — blocks-social-jpa (#294)

## D1: Engagement evidence scope

**Choice:** Include both strategy profile and engagement evidence tables — full parity with CbrStrategyStore
**Alternatives:**
- Defer engagement — profile only, subjectInsights() returns empty. Simpler initial scope but partial parity.
**Rationale:** StrategyStore.subjectInsights() is the only method that returns derived analytics. Deferring it would break the contract and require consumers to check which store implementation they have.
**Trade-offs:** Larger initial scope (5 tables instead of 4), engagement schema may evolve as strategy learning matures.
**Sources:** CbrStrategyStore.java — dual case type pattern (profileCaseType + engagementCaseType)
**Exploration:** quick
**Status:** captured

## D2: JSON serialization library

**Choice:** Jackson with @JsonTypeInfo for polymorphic sealed hierarchy serialization
**Alternatives:**
- Gson — lighter but needs manual TypeAdapter for sealed types, less ecosystem support for polymorphism
- JSONB column + PostgreSQL JSON queries — richer querying but ties persistence to PostgreSQL
**Rationale:** Jackson is already on blocks' classpath via agentic-yaml. @JsonTypeInfo provides declarative polymorphic serialization for NarrativeFragment's sealed hierarchy (IndividualEpisode, GroupEpisode, DerivedTheme).
**Trade-offs:** Jackson is heavier than Gson, but the dependency already exists.
**Sources:** agentic-yaml/pom.xml (Jackson dependency), NarrativeFragment.java (sealed hierarchy)
**Exploration:** quick
**Status:** captured

## D3: Entity primary key strategy

**Choice:** Synthetic UUID @Id with unique constraints on natural key columns
**Alternatives:**
- Composite @IdClass — natural key columns form the PK directly. More purist but verbose JPA and harder to extend.
**Rationale:** Matches neocortex JPA pattern (CbrCaseEntity uses `@Id String id` with UUID). Simpler entity mapping, easier to reference across relationships.
**Trade-offs:** Extra synthetic column per table, natural key uniqueness enforced via constraint rather than PK.
**Sources:** neocortex memory-cbr-jpa-common CbrCaseEntity.java
**Exploration:** quick
**Status:** captured

## D4: Column strategy

**Choice:** Scalar fields as individual columns, nested structures (lists/maps/sealed hierarchies) as JSON TEXT columns
**Alternatives:**
- Keys + full JSON blob — only lookup keys as columns, entire domain object as one JSON TEXT. Simplest but least queryable.
- Full denormalization — every field as a column with child tables for lists. Most queryable but complex schema.
**Rationale:** Balanced approach: lookup keys and analytics-relevant scalars (familiarityScore, totalInteractions, evidenceCount) are individually queryable. Complex nested types (fragments, beliefs/desires/intentions, dimensions, guidelines, metadata) serialize naturally to JSON without child table overhead.
**Trade-offs:** JSON columns can't be indexed or used in WHERE clauses. Acceptable since all query patterns use the scalar key columns.
**Sources:** UserProfile.java, MentalModelSnapshot.java, StrategyProfile.java, NarrativeState.java
**Exploration:** quick
**Status:** captured

## D5: Store upsert semantics

**Choice:** Merge — find by natural key, update if exists, insert if new
**Alternatives:**
- Delete + insert — simpler code but two round-trips and loses any DB-level audit trail.
**Rationale:** All four store SPIs have upsert-like behavior (store() overwrites for the same natural key). Merge is the standard JPA pattern for this and avoids unnecessary deletes. Single round-trip for updates.
**Trade-offs:** Slightly more complex store code (find-then-set vs. just persist).
**Sources:** CbrUserProfileStore.store() — overwrites by storing with same producer agent ID
**Exploration:** quick
**Status:** captured

## D6: Engagement evidence write path

**Choice:** Add `storeEvidence(EngagementEvidence)` to `StrategyStore` SPI and refactor `StrategyLearningOrchestrator` to use it instead of raw `CbrRecordStore`
**Alternatives:**
- Separate `EngagementEvidenceStore` SPI — cleaner separation but adds another SPI for what is conceptually part of strategy learning
- Keep `CbrRecordStore` for evidence, JPA only for profiles — simpler but engagement table never populated, `subjectInsights()` always empty
**Rationale:** `StrategyStore` already reads and deletes engagement data. Adding the write method completes the abstraction. The orchestrator's direct `CbrRecordStore` dependency was an implementation leak. Self-review identified this gap: without a write path, the JPA engagement table would be permanently empty.
**Trade-offs:** Changes the `StrategyStore` SPI contract — all existing implementations must add `storeEvidence()`. The `CbrStrategyStore` implementation is trivial (delegate to `CbrRecordStore`).
**Depends on:** D1 (engagement evidence in scope)
**Sources:** StrategyLearningOrchestrator.java:218 — direct CbrRecordStore.store() call
**Exploration:** quick
**Status:** captured
