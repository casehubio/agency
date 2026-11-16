# blocks-social-jpa — JPA Persistence for Social Cognition Stores

**Issue:** casehubio/blocks#294
**Date:** 2026-09-23
**Scope:** 3 new Maven modules, 5 JPA entities, 4 store implementations (Quarkus + Spring)

## Problem

The social cognition store SPIs (`NarrativeStore`, `UserProfileStore`, `MentalModelStore`, `StrategyStore`) are backed by CBR wrapper implementations that serialize structured domain objects as CBR feature vectors. This works but:

- Serializes structured records into flat feature maps (lossy for nested types)
- Uses similarity-based retrieval (`retrieveSimilar`) for exact-match lookups (wasteful)
- Post-filters results in Java (e.g., matching `producerAgentId` after retrieval)
- No direct SQL queryability for analytics or debugging

## Solution

Three new Maven modules following neocortex's three-tier JPA pattern, providing direct relational persistence with SQL queryability on key fields and JSON TEXT columns for nested structures.

## Module Structure

### social-jpa-common (`casehub-blocks-social-jpa-common`)

Framework-neutral: JPA entities, Flyway migrations, JSON conversion utilities.

**Dependencies:**
- `jakarta.persistence-api` (provided)
- `com.fasterxml.jackson.core:jackson-databind` (provided)
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` (provided)
- `casehub-blocks-core` (provided — entity ↔ domain record conversion)

**Build plugins:** `jandex-maven-plugin`

### social-jpa (`casehub-blocks-social-jpa`)

Quarkus CDI store implementations.

**Dependencies:**
- `casehub-blocks-social-jpa-common`
- `casehub-blocks-core`
- `io.quarkus:quarkus-arc` (provided)
- `jakarta.persistence-api` (provided)

**Build plugins:** `jandex-maven-plugin`, `quarkus-maven-plugin`

### social-spring-jpa (`casehub-blocks-social-spring-jpa`)

Spring Boot auto-configuration with Spring Data repositories.

**Dependencies:**
- `casehub-blocks-social-jpa-common`
- `casehub-blocks-core`
- `org.springframework.boot:spring-boot-autoconfigure`
- `org.springframework:spring-context`
- `org.springframework.data:spring-data-jpa`
- `jakarta.persistence-api` (provided)

## Entity Design

All entities follow the neocortex pattern:
- Public fields, no getters/setters
- `@Id String id` — UUID assigned in application code (`UUID.randomUUID().toString()`)
- `@Column(name = "snake_case")` on every field, with `nullable = false` on required columns
- `@Table(name = "social_*")` — explicit table naming with `social_` prefix
- Unique constraints on natural key columns
- JSON TEXT columns for nested structures via `AttributeConverter`

### Package

All entities in `io.casehub.blocks.social.jpa`.

### UserProfileEntity

Maps to `social_user_profile`. Composite natural key: `(agent_id, subject_id, tenant_id)`.

| Column | Type | Source field | Notes |
|--------|------|-------------|-------|
| `id` | `VARCHAR(36)` PK | synthetic | UUID |
| `agent_id` | `VARCHAR(255)` NOT NULL | `agentId` | |
| `subject_id` | `VARCHAR(255)` NOT NULL | `subjectId` | |
| `tenant_id` | `VARCHAR(255)` NOT NULL | `tenantId` | |
| `relationship_stage` | `VARCHAR(50)` NOT NULL | `relationshipStage` | |
| `familiarity_score` | `DOUBLE PRECISION` NOT NULL | `familiarityScore` | |
| `total_interactions` | `INTEGER` NOT NULL | `totalInteractions` | |
| `positive_signals` | `INTEGER` NOT NULL | `positiveSignals` | |
| `negative_signals` | `INTEGER` NOT NULL | `negativeSignals` | |
| `neutral_signals` | `INTEGER` NOT NULL | `neutralSignals` | |
| `last_interaction` | `TIMESTAMP WITH TIME ZONE` NOT NULL | `lastInteraction` | |
| `profile_created` | `TIMESTAMP WITH TIME ZONE` NOT NULL | `profileCreated` | |
| `last_synthesised` | `TIMESTAMP WITH TIME ZONE` | `lastSynthesised` | nullable |
| `communication_style` | `TEXT` | `communicationStyle` | nullable |
| `topics_of_interest` | `TEXT` | `topicsOfInterest` | nullable |
| `preferences` | `TEXT` | `preferences` | nullable |
| `synthesis_notes` | `TEXT` | `synthesisNotes` | nullable |
| `metadata` | `TEXT` NOT NULL DEFAULT `'{}'` | `metadata` | JSON `Map<String,String>` |

**Unique constraint:** `(agent_id, subject_id, tenant_id)`
**Index:** `social_user_profile_agent_tenant_idx` on `(agent_id, tenant_id)` — for `findByAgent()`

### MentalModelEntity

Maps to `social_mental_model`. Composite natural key: `(agent_id, subject_id, tenant_id)`.

| Column | Type | Source field | Notes |
|--------|------|-------------|-------|
| `id` | `VARCHAR(36)` PK | synthetic | UUID |
| `agent_id` | `VARCHAR(255)` NOT NULL | `agentId` | |
| `subject_id` | `VARCHAR(255)` NOT NULL | `subjectId` | |
| `tenant_id` | `VARCHAR(255)` NOT NULL | `tenantId` | |
| `beliefs` | `TEXT` NOT NULL | `beliefs` | JSON `List<AttributedState>` |
| `desires` | `TEXT` NOT NULL | `desires` | JSON `List<AttributedState>` |
| `intentions` | `TEXT` NOT NULL | `intentions` | JSON `List<AttributedState>` |
| `last_signal` | `TIMESTAMP WITH TIME ZONE` NOT NULL | `lastSignal` | |
| `last_inference` | `TIMESTAMP WITH TIME ZONE` | `lastInference` | nullable |
| `snapshot_created` | `TIMESTAMP WITH TIME ZONE` NOT NULL | `snapshotCreated` | |

**Unique constraint:** `(agent_id, subject_id, tenant_id)`
**Index:** `social_mental_model_agent_tenant_idx` on `(agent_id, tenant_id)` — for `findByAgent()`

### NarrativeEntity

Maps to `social_narrative`. Composite natural key: `(scope_id, tenant_id)`.

| Column | Type | Source field | Notes |
|--------|------|-------------|-------|
| `id` | `VARCHAR(36)` PK | synthetic | UUID |
| `scope_id` | `VARCHAR(255)` NOT NULL | `scopeId` | |
| `tenant_id` | `VARCHAR(255)` NOT NULL | `tenantId` | |
| `scope` | `VARCHAR(20)` NOT NULL | `scope` | enum `NarrativeScope` as string |
| `fragments` | `TEXT` NOT NULL | `fragments` | JSON polymorphic `List<NarrativeFragment>` |
| `synthesised_at` | `TIMESTAMP WITH TIME ZONE` NOT NULL | `synthesisedAt` | |
| `reflection_count_at_synthesis` | `INTEGER` NOT NULL | `reflectionCountAtSynthesis` | |

**Unique constraint:** `(scope_id, tenant_id)`

### StrategyProfileEntity

Maps to `social_strategy_profile`. Composite natural key: `(agent_id, tenant_id)`.

| Column | Type | Source field | Notes |
|--------|------|-------------|-------|
| `id` | `VARCHAR(36)` PK | synthetic | UUID |
| `agent_id` | `VARCHAR(255)` NOT NULL | `agentId` | |
| `tenant_id` | `VARCHAR(255)` NOT NULL | `tenantId` | |
| `dimensions` | `TEXT` NOT NULL | `dimensions` | JSON `Map<String,Double>` |
| `guidelines` | `TEXT` NOT NULL | `guidelines` | JSON `List<String>` |
| `last_reflection` | `TIMESTAMP WITH TIME ZONE` NOT NULL | `lastReflection` | |
| `evidence_count` | `INTEGER` NOT NULL | `evidenceCount` | |

**Unique constraint:** `(agent_id, tenant_id)`

### EngagementEvidenceEntity

Maps to `social_engagement_evidence`. No unique constraint — multiple evidence records per agent+subject pair (one per conversation).

| Column | Type | Source field | Notes |
|--------|------|-------------|-------|
| `id` | `VARCHAR(36)` PK | synthetic | UUID |
| `agent_id` | `VARCHAR(255)` NOT NULL | agentId | |
| `subject_id` | `VARCHAR(255)` NOT NULL | subjectId | |
| `tenant_id` | `VARCHAR(255)` NOT NULL | tenantId | |
| `conversation_id` | `VARCHAR(255)` | conversationId | nullable, correlates to specific conversation |
| `conversation_summary` | `TEXT` | conversationSummary | nullable, human-readable summary |
| `turn_count` | `INTEGER` NOT NULL | turnCount | |
| `continuation_rate` | `DOUBLE PRECISION` NOT NULL | continuationRate | |
| `avg_response_length` | `DOUBLE PRECISION` NOT NULL | avgResponseLength | |
| `mean_affect_shift` | `DOUBLE PRECISION` NOT NULL | meanAffectShift | |
| `dimension_snapshots` | `TEXT` NOT NULL DEFAULT `'{}'` | dimensionSnapshots | JSON `Map<String,Double>` — per-dimension averages (avgSnapshot_verbosity, etc.) |
| `recorded_at` | `TIMESTAMP WITH TIME ZONE` NOT NULL | recordedAt | |

**Index:** `social_engagement_agent_tenant_idx` on `(agent_id, tenant_id)` — for `eraseAgent()`
**Index:** `social_engagement_subject_tenant_idx` on `(subject_id, tenant_id)` — for `subjectInsights()` and `eraseSubject()`

## Engagement Evidence Write Path

### Problem

`StrategyLearningOrchestrator` currently writes engagement evidence directly to `CbrRecordStore`, bypassing `StrategyStore`. The `StrategyStore` SPI has methods that *read* engagement data (`subjectInsights()`) and *delete* it (`eraseAgent()`, `eraseSubject()`), but no method to *write* it. Without a write path, the JPA engagement table would never be populated.

### Solution

Add a domain record and a write method to `StrategyStore`:

```java
// New record in blocks-core: io.casehub.blocks.agentic.social
public record EngagementEvidence(
    String agentId,
    String subjectId,
    String tenantId,
    @Nullable String conversationId,
    @Nullable String conversationSummary,
    int turnCount,
    double continuationRate,
    double avgResponseLength,
    double meanAffectShift,
    Map<String, Double> dimensionSnapshots,
    Instant recordedAt
) {}
```

```java
// Added to StrategyStore interface
void storeEvidence(EngagementEvidence evidence);
int evidenceCount(String agentId, String tenantId);
List<EngagementEvidence> recentEvidence(String agentId, String tenantId, int limit);
```

The read methods (`evidenceCount`, `recentEvidence`) are required because `StrategyLearningOrchestrator` has 3 read paths currently hard-coupled to `CbrRecordStore`:
- `countAgentCases()` — counts engagement cases to decide whether to trigger reflection
- `doReflect()` / `doReflectAsync()` — fetches cases for trend analysis and LLM-based strategy synthesis

Without these read methods on the SPI, the reflection pipeline silently breaks when JPA is active: engagement data writes to JPA but the orchestrator reads from CBR (which has nothing).

**Implementation plan:**
1. Add `EngagementEvidence` record to `blocks-core`
2. Add `storeEvidence()`, `evidenceCount()`, `recentEvidence()` to `StrategyStore` interface
3. Implement in `CbrStrategyStore` — `storeEvidence()` converts to `CbrFeatureRecord` and delegates to `CbrRecordStore`; read methods query CBR and convert back to `EngagementEvidence`
4. Implement in `JpaStrategyStore` — persists/queries `EngagementEvidenceEntity`
5. Implement in `SpringStrategyStore` — persists/queries via Spring Data repository
6. Implement in `InMemoryStrategyStore` (in `CognitionStack.java` test fixture) — `ArrayList`-backed
7. Update `StrategyLearningOrchestrator`:
   - `doTick()` — construct `EngagementEvidence` from `extractFeatures()` output, call `strategyStore.storeEvidence()`
   - `countAgentCases()` → `strategyStore.evidenceCount(agentId, tenantId)`
   - `doReflect()` / `doReflectAsync()` — call `strategyStore.recentEvidence()`, convert to feature maps for `TrendAnalyzer` (bridge method: `EngagementEvidence → Map<String, FeatureValue>`)
8. Remove `CbrRecordStore` field from `StrategyLearningOrchestrator` constructor (engagement writes and reads now go through `StrategyStore`)

The `TrendAnalyzer` bridge in step 7 converts `EngagementEvidence` fields to `FeatureValue` maps — this preserves the existing trend analysis without refactoring `TrendAnalyzer` itself.

## JSON Serialization

### SocialJsonMapper

Static utility class in `social-jpa-common` providing a pre-configured `ObjectMapper`:

```java
public final class SocialJsonMapper {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .addMixIn(NarrativeFragment.class, NarrativeFragmentMixin.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static String toJson(Object value) { ... }
    public static <T> T fromJson(String json, TypeReference<T> type) { ... }
}
```

### NarrativeFragment Polymorphic Serialization

A Jackson mixin in the common module registers type info without modifying blocks-core:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = IndividualEpisode.class, name = "individual"),
    @JsonSubTypes.Type(value = GroupEpisode.class, name = "group"),
    @JsonSubTypes.Type(value = DerivedTheme.class, name = "theme")
})
abstract class NarrativeFragmentMixin {}
```

This keeps blocks-core free of Jackson annotations. The mixin handles the sealed hierarchy polymorphism (`IndividualEpisode`, `GroupEpisode`, `DerivedTheme`) via a `@type` discriminator property in the JSON.

### JPA AttributeConverters

Generic converters for each JSON column type, all backed by `SocialJsonMapper`:

| Converter | JSON ↔ Java |
|-----------|-------------|
| `NarrativeFragmentListConverter` | `TEXT` ↔ `List<NarrativeFragment>` |
| `AttributedStateListConverter` | `TEXT` ↔ `List<AttributedState>` |
| `StringDoubleMapConverter` | `TEXT` ↔ `Map<String, Double>` |
| `StringListConverter` | `TEXT` ↔ `List<String>` |
| `StringStringMapConverter` | `TEXT` ↔ `Map<String, String>` |

Each converter implements `jakarta.persistence.AttributeConverter` and is applied via `@Convert(converter = ...)` on the entity field.

## Flyway Migration

Single initial migration at:
```
social-jpa-common/src/main/resources/db/social/migration/V1__social_cognition_stores.sql
```

Creates all 5 tables in one migration. PostgreSQL-compatible DDL with:
- Named PK constraints: `social_{table}_pk`
- Named unique constraints: `social_{table}_natural_key_uq`
- Named indexes: `social_{table}_{purpose}_idx`
- `TIMESTAMP WITH TIME ZONE` for all temporal columns
- `TEXT` for JSON columns with `DEFAULT '{}'` or `DEFAULT '[]'` where appropriate

Consumers wire the migration location:
```properties
quarkus.flyway.locations=classpath:db/social/migration
```

## Quarkus Store Implementations

Package: `io.casehub.blocks.social.jpa.quarkus`

All four stores annotated `@Alternative @Priority(3) @ApplicationScoped @Transactional`:
- Priority 3 displaces `@DefaultBean` CBR stores (no priority) when the JPA module is on the classpath
- No configuration required — classpath presence is the activation signal

### JpaUserProfileStore

```java
@Alternative @Priority(3) @ApplicationScoped @Transactional
public class JpaUserProfileStore implements UserProfileStore {
    @Inject EntityManager em;

    void store(UserProfile profile) {
        // find by natural key, merge or persist
    }
    Optional<UserProfile> lookup(String agentId, String subjectId, String tenantId) {
        // JPQL: WHERE agent_id = :a AND subject_id = :s AND tenant_id = :t
    }
    List<UserProfile> findByAgent(String agentId, String tenantId) {
        // JPQL: WHERE agent_id = :a AND tenant_id = :t
    }
    void eraseSubject(String subjectId, String tenantId) {
        // JPQL DELETE: WHERE subject_id = :s AND tenant_id = :t
    }
}
```

### JpaMentalModelStore

Same structure as `JpaUserProfileStore`. JPQL queries on `(agent_id, subject_id, tenant_id)`.

### JpaNarrativeStore

```java
@Alternative @Priority(3) @ApplicationScoped @Transactional
public class JpaNarrativeStore implements NarrativeStore {
    @Inject EntityManager em;

    void store(NarrativeState state) {
        // find by (scope_id, tenant_id), merge or persist
    }
    @Nullable NarrativeState load(String scopeId, String tenantId) {
        // JPQL: WHERE scope_id = :s AND tenant_id = :t
    }
}
```

### JpaStrategyStore

```java
@Alternative @Priority(3) @ApplicationScoped @Transactional
public class JpaStrategyStore implements StrategyStore {
    @Inject EntityManager em;

    void store(StrategyProfile profile) {
        // find by (agent_id, tenant_id), merge or persist
    }
    void storeEvidence(EngagementEvidence evidence) {
        // persist new EngagementEvidenceEntity
    }
    int evidenceCount(String agentId, String tenantId) {
        // JPQL COUNT: WHERE agent_id = :a AND tenant_id = :t
    }
    List<EngagementEvidence> recentEvidence(String agentId, String tenantId, int limit) {
        // JPQL: WHERE agent_id = :a AND tenant_id = :t ORDER BY recorded_at DESC LIMIT :limit
    }
    Optional<StrategyProfile> lookup(String agentId, String tenantId) {
        // JPQL: WHERE agent_id = :a AND tenant_id = :t
    }
    List<String> subjectInsights(String agentId, String subjectId, String tenantId) {
        // JPQL on engagement_evidence: WHERE agent_id = :a AND subject_id = :s AND tenant_id = :t
        // format each row into insight string (same format as CbrStrategyStore)
    }
    void eraseAgent(String agentId, String tenantId) {
        // DELETE from strategy_profile WHERE agent_id = :a AND tenant_id = :t
        // DELETE from engagement_evidence WHERE agent_id = :a AND tenant_id = :t
    }
    void eraseSubject(String subjectId, String tenantId) {
        // DELETE from engagement_evidence WHERE subject_id = :s AND tenant_id = :t
    }
}
```

### Entity ↔ Domain Conversion

Each store implementation contains private helper methods:
- `toEntity(DomainRecord) → Entity` — maps domain record fields to entity fields, handles JSON serialization via `SocialJsonMapper`
- `toDomain(Entity) → DomainRecord` — reverse mapping, handles JSON deserialization

These are private methods in the store class, not separate mapper classes — the mapping is simple field-by-field assignment.

## Spring Store Implementations

Package: `io.casehub.blocks.social.jpa.spring`

### Repositories

Four Spring Data `JpaRepository` interfaces with custom query methods:

```java
public interface UserProfileEntityRepository extends JpaRepository<UserProfileEntity, String> {
    Optional<UserProfileEntity> findByAgentIdAndSubjectIdAndTenantId(String a, String s, String t);
    List<UserProfileEntity> findByAgentIdAndTenantId(String a, String t);
    void deleteBySubjectIdAndTenantId(String s, String t);
}
```

Similar patterns for the other three stores + engagement evidence repository.

### Store Implementations

Four Spring store classes (plain classes, constructed by auto-configuration):
- `SpringUserProfileStore`
- `SpringMentalModelStore`
- `SpringNarrativeStore`
- `SpringStrategyStore`

Each delegates to the appropriate Spring Data repository. Same entity ↔ domain conversion as the Quarkus variants.

### Auto-Configuration

```java
@AutoConfiguration(before = BlocksAutoConfiguration.class)
@ConditionalOnClass(SpringUserProfileStore.class)
@EntityScan(basePackageClasses = UserProfileEntity.class)
@EnableJpaRepositories(basePackageClasses = UserProfileEntityRepository.class)
public class SocialJpaAutoConfiguration {

    @Bean @ConditionalOnMissingBean(UserProfileStore.class)
    public SpringUserProfileStore springUserProfileStore(UserProfileEntityRepository repo) { ... }

    @Bean @ConditionalOnMissingBean(MentalModelStore.class)
    public SpringMentalModelStore springMentalModelStore(MentalModelEntityRepository repo) { ... }

    @Bean @ConditionalOnMissingBean(NarrativeStore.class)
    public SpringNarrativeStore springNarrativeStore(NarrativeEntityRepository repo) { ... }

    @Bean @ConditionalOnMissingBean(StrategyStore.class)
    public SpringStrategyStore springStrategyStore(
            StrategyProfileEntityRepository profileRepo,
            EngagementEvidenceEntityRepository evidenceRepo) { ... }
}
```

Registration: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Ordering requirement:** `SocialJpaAutoConfiguration` must run before `BlocksAutoConfiguration` (or any auto-config that registers CBR store beans). If `BlocksAutoConfiguration` registers CBR stores with `@ConditionalOnMissingBean` typed to the concrete class (not the interface), it will also need updating to use `@ConditionalOnMissingBean(UserProfileStore.class)` etc. — otherwise both beans register and injection fails with `NoUniqueBeanDefinitionException`. Verify during implementation whether `BlocksAutoConfiguration` registers social store beans at all.

## Testing

### Unit Tests (in social-jpa-common)

- `SocialJsonMapperTest` — round-trip serialization for all JSON column types
- `NarrativeFragmentSerializationTest` — polymorphic round-trip for sealed hierarchy
- Each `AttributeConverter` tested for null handling, empty collections, round-trip fidelity

### Integration Tests (in social-jpa)

- One test class per store: `JpaUserProfileStoreTest`, `JpaMentalModelStoreTest`, `JpaNarrativeStoreTest`, `JpaStrategyStoreTest`
- Each test uses an in-memory H2 database with Flyway auto-migration
- Tests cover: store + lookup round-trip, findByAgent, eraseSubject, merge-on-duplicate-key, null handling for optional fields
- `JpaStrategyStoreTest` additionally tests `subjectInsights()` with multiple evidence rows and `eraseAgent()` cascading to engagement evidence

### Spring Tests (in social-spring-jpa)

- `SocialJpaAutoConfigurationTest` — verifies beans are registered, `@ConditionalOnMissingBean` override works
- One integration test per store using `@DataJpaTest`

## Consumer Activation

### Quarkus

Add dependency + Flyway location. No other configuration:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-social-jpa</artifactId>
</dependency>
```

```properties
# Add to existing locations (comma-separated, do not replace existing entries)
quarkus.flyway.locations=classpath:db/migration,classpath:db/social/migration
```

The `@Alternative @Priority(3)` stores automatically displace the `@DefaultBean` CBR stores.

### Spring Boot

Add dependency. Auto-configuration handles the rest:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-social-spring-jpa</artifactId>
</dependency>
```

## What This Does NOT Include

- Migration tooling from CBR → JPA (data migration is a separate concern)
- Removal of CBR store implementations from blocks-core (they remain as the default fallback)
- Database-specific optimizations (JSONB, partial indexes) — plain SQL TEXT + standard indexes only

## References

- neocortex `memory-cbr-jpa-common` / `memory-cbr-jpa` / `memory-cbr-spring-jpa` — three-tier JPA pattern
- `CbrUserProfileStore.java`, `CbrMentalModelStore.java`, `CbrNarrativeStore.java`, `CbrStrategyStore.java` — existing CBR implementations being replaced
- `UserProfile.java`, `MentalModelSnapshot.java`, `NarrativeState.java`, `StrategyProfile.java` — domain records
- `NarrativeFragment.java` (sealed: `IndividualEpisode`, `GroupEpisode`, `DerivedTheme`) — polymorphic JSON target
- `AttributedState.java` — BDI state record serialized to JSON
- `engine-adapter-core` / `engine-adapter` / `engine-adapter-spring` — blocks' existing three-tier module pattern
- casehubio/blocks#294 — issue specification
