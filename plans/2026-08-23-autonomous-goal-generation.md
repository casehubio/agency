# Autonomous Goal Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #136 — Autonomous Goal Generation
**Issue group:** #136

**Goal:** Bridge drive signals into the engine's existing goal lifecycle
via a GoalFormationService SPI and a blocks-level GoalProposalOrchestrator.

**Architecture:** Extract case-independent GoalFormationService and
GoalRemovalService SPIs from engine evaluators. Add goal provenance
(`attributes`) to AgentGoal (eidos-api). Build GoalProposalOrchestrator
(compositor pattern) in blocks that reads DriveProfile, maps to goal
proposals via per-axis DriveGoalMappers, and caches them for scheduler
registration.

**Tech Stack:** Java 21+, JUnit 5, Mockito, plain POJOs (no Quarkus
runtime in blocks tests)

## Global Constraints

- blocks tests: plain JUnit 5 + Mockito, no CDI container
- engine tests: JUnit 5 + Mockito for unit tests, `@QuarkusTest` only
  where existing test infrastructure requires it
- eidos-api: pure-Java, zero external dependencies
- All new types use `@Nullable` from `org.jspecify.annotations`
- Goal names use normalised keys: lowercase, trimmed, hyphen-separated
- Drive-sourced goals always `GoalPriority.SECONDARY`,
  `Visibility.PUBLIC`
- Compositor pattern: `tick()` caches results, no side effects

---

## Batch 1: Foundation types — eidos-api AgentGoal attributes

### Task 1: Add `@Nullable Map<String, String> attributes` to AgentGoal

**Files:**
- Modify: `/Users/mdproctor/claude/casehub/eidos/api/src/main/java/io/casehub/eidos/api/AgentGoal.java`
- Modify: `/Users/mdproctor/claude/casehub/eidos/runtime/src/main/java/io/casehub/eidos/runtime/registry/jpa/AgentGoalEntity.java`
- Modify: `/Users/mdproctor/claude/casehub/eidos/runtime/src/main/java/io/casehub/eidos/runtime/registry/jpa/AgentDescriptorMapper.java`
- Create: `/Users/mdproctor/claude/casehub/eidos/runtime/src/main/resources/db/eidos/migration/V2__add_goal_attributes.sql`
- Test: `/Users/mdproctor/claude/casehub/eidos/api/src/test/java/io/casehub/eidos/api/AgentGoalTest.java`

**Interfaces:**
- Produces: `AgentGoal(name, description, priority, visibility, capabilities, attributes)` — 6-arg record. `attributes` is `@Nullable Map<String, String>`. Builder gains `attributes(Map<String, String>)`.

- [ ] **Step 1: Write test for AgentGoal with attributes**

```java
@Test
void goalWithAttributes() {
    var attrs = Map.of("source", "drive", "driveAxis", "CURIOSITY");
    var goal = new AgentGoal("explore-gaps", "Explore knowledge gaps",
            GoalPriority.SECONDARY, Visibility.PUBLIC, List.of(), attrs);
    assertThat(goal.attributes()).isEqualTo(attrs);
}

@Test
void goalWithNullAttributes() {
    var goal = new AgentGoal("assigned-goal", "Do the work",
            GoalPriority.PRIMARY, Visibility.PUBLIC, List.of(), null);
    assertThat(goal.attributes()).isNull();
}

@Test
void goalAttributesDefensivelyCopied() {
    var attrs = new HashMap<>(Map.of("source", "drive"));
    var goal = new AgentGoal("g", "d", GoalPriority.SECONDARY,
            Visibility.PUBLIC, List.of(), attrs);
    attrs.put("extra", "injected");
    assertThat(goal.attributes()).doesNotContainKey("extra");
}

@Test
void builderPreservesAttributes() {
    var attrs = Map.of("source", "drive");
    var goal = new AgentGoal("g", "d", GoalPriority.SECONDARY,
            Visibility.PUBLIC, List.of(), attrs);
    var rebuilt = goal.toBuilder().description("updated").build();
    assertThat(rebuilt.attributes()).isEqualTo(attrs);
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `mvn --batch-mode test -pl api -f /Users/mdproctor/claude/casehub/eidos/pom.xml -Dtest=AgentGoalTest`
Expected: compilation failure — 6-arg constructor doesn't exist yet

- [ ] **Step 3: Add attributes field to AgentGoal record**

Add `@Nullable Map<String, String> attributes` as the 6th record component.
Update compact constructor to defensively copy:
```java
attributes = attributes != null ? Map.copyOf(attributes) : null;
```
Add to Builder class: field `private Map<String, String> attributes;`,
builder method `attributes(Map<String, String>)`, pass to `build()`.
Update `toBuilder()` to include `this.attributes`.

- [ ] **Step 4: Fix all existing 5-arg call sites in eidos**

Search for `new AgentGoal(` in eidos repo and add `null` as 6th argument.
Update `AgentDescriptorMapper.toGoal()` to pass `null` for attributes (will
be updated in step 7).

- [ ] **Step 5: Run tests — verify they pass**

Run: `mvn --batch-mode test -pl api -f /Users/mdproctor/claude/casehub/eidos/pom.xml`
Expected: all tests pass

- [ ] **Step 6: Add attributes column to AgentGoalEntity + mapper**

In `AgentGoalEntity.java`, add:
```java
@Column(name = "attributes") String attributes;
```

In `AgentDescriptorMapper.toGoal()`, change to:
```java
private AgentGoal toGoal(AgentGoalEntity g) {
    return new AgentGoal(g.name, g.description,
                         GoalPriority.valueOf(g.priority),
                         Visibility.valueOf(g.visibility),
                         readJson(g.capabilities, new TypeReference<List<String>>() {}),
                         readJson(g.attributes, new TypeReference<Map<String, String>>() {}));
}
```

In `AgentDescriptorMapper.toGoalEntity()`, add:
```java
e.attributes = writeJson(g.attributes());
```

- [ ] **Step 7: Create Flyway migration**

File: `V2__add_goal_attributes.sql`
```sql
ALTER TABLE agent_goal ADD COLUMN attributes TEXT;
```

- [ ] **Step 8: Run eidos tests**

Run: `mvn --batch-mode test -f /Users/mdproctor/claude/casehub/eidos/pom.xml`
Expected: all tests pass

- [ ] **Step 9: Fix call sites in engine + blocks**

Search for `new AgentGoal(` in engine and blocks repos. Add `null` as 6th
argument to all existing call sites. Run `mvn --batch-mode compile` in
each to verify.

- [ ] **Step 10: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/eidos commit -am "feat(#136): add @Nullable attributes to AgentGoal for goal provenance Refs casehubio/blocks#136"
```

---

## Batch 2: Engine SPI types + service implementations

### Task 2: GoalFormationService + GoalRemovalService SPIs and implementations

**Files:**
- Create: `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/GoalFormationService.java`
- Create: `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/GoalFormationResult.java`
- Create: `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/GoalRemovalService.java`
- Create: `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/GoalRemovalResult.java`
- Modify: `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/GoalFormationProposal.java` (add attributes to ProposedGoal)
- Modify: `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/model/event/CaseHubEventType.java` (add GOAL_REMOVED)
- Create: `/Users/mdproctor/claude/casehub/engine/runtime/src/main/java/io/casehub/engine/internal/routing/DefaultGoalFormationService.java`
- Create: `/Users/mdproctor/claude/casehub/engine/runtime/src/main/java/io/casehub/engine/internal/routing/DefaultGoalRemovalService.java`
- Modify: `/Users/mdproctor/claude/casehub/engine/runtime/src/main/java/io/casehub/engine/internal/routing/GoalFormationEvaluator.java` (refactor to delegate)
- Modify: `/Users/mdproctor/claude/casehub/engine/runtime/src/main/java/io/casehub/engine/internal/routing/GoalRevisionEvaluator.java` (refactor to delegate)
- Test: `/Users/mdproctor/claude/casehub/engine/runtime/src/test/java/io/casehub/engine/internal/routing/DefaultGoalFormationServiceTest.java`
- Test: `/Users/mdproctor/claude/casehub/engine/runtime/src/test/java/io/casehub/engine/internal/routing/DefaultGoalRemovalServiceTest.java`

**Interfaces:**
- Consumes: `AgentGoal` with attributes (Task 1)
- Produces: `GoalFormationService.propose(agentId, tenancyId, proposal) → GoalFormationResult`; `GoalRemovalService.removeGoals(agentId, tenancyId, goalNames, reason) → GoalRemovalResult`

- [ ] **Step 1: Create engine-api SPI types**

`GoalFormationService.java`:
```java
package io.casehub.api.spi.routing;

public interface GoalFormationService {
    GoalFormationResult propose(String agentId, String tenancyId,
                                GoalFormationProposal proposal);
}
```

`GoalFormationResult.java`:
```java
package io.casehub.api.spi.routing;

import io.casehub.eidos.api.AgentGoal;
import java.util.List;
import java.util.Objects;

public record GoalFormationResult(
        List<AgentGoal> registered,
        List<RejectedGoal> rejected,
        int totalGoalCount) {
    public GoalFormationResult {
        Objects.requireNonNull(registered);
        Objects.requireNonNull(rejected);
        registered = List.copyOf(registered);
        rejected = List.copyOf(rejected);
    }
    public record RejectedGoal(String name, String reason) {
        public RejectedGoal {
            Objects.requireNonNull(name);
            Objects.requireNonNull(reason);
        }
    }
}
```

`GoalRemovalService.java`:
```java
package io.casehub.api.spi.routing;

import java.util.List;

public interface GoalRemovalService {
    GoalRemovalResult removeGoals(String agentId, String tenancyId,
                                  List<String> goalNames, String reason);
}
```

`GoalRemovalResult.java`:
```java
package io.casehub.api.spi.routing;

import java.util.List;
import java.util.Objects;

public record GoalRemovalResult(
        List<String> removedGoals,
        int remainingGoalCount) {
    public GoalRemovalResult {
        Objects.requireNonNull(removedGoals);
        removedGoals = List.copyOf(removedGoals);
    }
}
```

- [ ] **Step 2: Extend ProposedGoal with attributes**

In `GoalFormationProposal.java`, add `@Nullable Map<String, String> attributes`
as 5th field to `ProposedGoal` record. Add defensive copy in compact constructor.
Fix existing call sites (in `LlmGoalFormationStrategy` etc.) to pass `null`.

- [ ] **Step 3: Add GOAL_REMOVED to CaseHubEventType**

```java
GOAL_REMOVED, // agent goal removed via GoalRemovalService
```

- [ ] **Step 4: Write DefaultGoalFormationService test**

```java
@Test
void propose_registersValidGoals() {
    // given: agent with 2 existing goals, MAX_GOALS=10
    // when: propose 2 new goals
    // then: both registered, result.registered().size() == 2, totalGoalCount == 4
}

@Test
void propose_rejectsDuplicateNames() {
    // given: agent with goal "explore-gaps"
    // when: propose goal named "explore-gaps"
    // then: rejected with reason "duplicate name"
}

@Test
void propose_rejectsWhenCapacityExceeded() {
    // given: agent with 10 goals (MAX_GOALS)
    // when: propose 1 more
    // then: rejected with reason "capacity exceeded"
}

@Test
void propose_propagatesAttributes() {
    // given: proposal with attributes {"source": "drive", "driveAxis": "CURIOSITY"}
    // when: propose
    // then: registered goal has same attributes
}
```

- [ ] **Step 5: Write DefaultGoalFormationService implementation**

Extract validation/registration/audit logic from `GoalFormationEvaluator.formGoals()`:
- Validate name length (MAX_NAME_LENGTH=100), description length (MAX_DESCRIPTION_LENGTH=500)
- Deduplicate against existing goal names
- Check capacity (MAX_GOALS=10)
- Build `AgentGoal` with attributes from `ProposedGoal`
- Merge onto descriptor via `AgentRegistry.register()`
- Write EventLog (GOAL_FORMED)
- Return `GoalFormationResult`

- [ ] **Step 6: Write DefaultGoalRemovalService test and implementation**

Test: removal of named goals, audit logging, remaining count.
Implementation: load descriptor, filter out named goals, re-register, write EventLog (GOAL_REMOVED).

- [ ] **Step 7: Refactor GoalFormationEvaluator to delegate**

Replace inline validation/registration in `formGoals()` with:
```java
GoalFormationResult result = goalFormationService.propose(agentId, tenancyId, proposal);
```
Keep the autoApprove check: if `autoApprove=false`, write `GOAL_PROPOSED` and skip service call.
Keep the cooldown mechanism in the evaluator.
Keep memory retrieval in the evaluator.

- [ ] **Step 8: Refactor GoalRevisionEvaluator — delegate removal to GoalRemovalService**

In `handleEvolved()`, replace inline goal removal with:
```java
goalRemovalService.removeGoals(agentId, tenancyId, abandonedGoals, "goal revised");
```

- [ ] **Step 9: Run engine tests**

Run: `mvn --batch-mode test -f /Users/mdproctor/claude/casehub/engine/pom.xml`
Expected: all existing + new tests pass

- [ ] **Step 10: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/engine commit -am "feat(#136): extract GoalFormationService + GoalRemovalService SPIs, refactor evaluators Refs casehubio/blocks#136"
```

---

## Batch 3: Blocks goal proposal types + orchestrator

### Task 3: DriveGoalMapper SPI + proposal types + GoalProposalOrchestrator

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/social/goal/DriveGoalMapper.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/goal/DriveGoalProposal.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/goal/GoalProposalTick.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/goal/GoalProposalConfig.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/goal/GoalProposalOrchestrator.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/goal/GoalProposalOrchestratorTest.java`

**Interfaces:**
- Consumes: `DriveOrchestrator.currentDrives()` (blocks #129), `DriveProfile`, `DriveIntensity`, `DriveAxis`, `AgentDescriptor` (eidos-api)
- Produces: `GoalProposalOrchestrator.tick() → GoalProposalTick`; `currentProposals() → Optional<List<DriveGoalProposal>>`

- [ ] **Step 1: Create DriveGoalMapper, DriveGoalProposal, GoalProposalTick, GoalProposalConfig**

Create all 4 type files as specified in the design spec (§Type System → Blocks).
`DriveGoalMapper`: `@FunctionalInterface`, single `evaluate` method.
`DriveGoalProposal`: record with axis, goalName, goalDescription, formationReason, driveIntensity.
`GoalProposalTick`: sealed interface with NoChange and Proposed variants.
`GoalProposalConfig`: record with defaults() factory.

- [ ] **Step 2: Write GoalProposalOrchestrator failing tests**

```java
@Test
void tick_returnsNoChange_whenNoDrives() {
    when(driveOrchestrator.currentDrives("a1", "t1")).thenReturn(Optional.empty());
    var tick = orchestrator.tick("a1", "t1", descriptor);
    assertThat(tick).isInstanceOf(GoalProposalTick.NoChange.class);
}

@Test
void tick_proposesGoal_whenDriveAboveThreshold() {
    // given: curiosity drive at 0.7, mapper returns a proposal
    // when: tick
    // then: Proposed with 1 new proposal
}

@Test
void tick_respectsMaxDriveGoals() {
    // given: 3 existing drive-sourced goals (maxDriveGoals=3)
    // when: tick with high drives
    // then: NoChange (capacity full)
}

@Test
void tick_ranksProposalsByIntensity() {
    // given: 4 axes produce proposals, maxDriveGoals allows 2
    // when: tick
    // then: top 2 by intensity selected
}

@Test
void tick_abandonsGoalWhenDriveWeakens() {
    // given: existing drive-sourced goal for CURIOSITY,
    //        curiosity drive now 0.1 (below relevanceThreshold=0.2),
    //        staleAfter elapsed
    // when: tick
    // then: Proposed with abandonedGoalNames containing the goal
}

@Test
void tick_respectsCooldown() {
    // given: recent tick within cooldown window
    // when: tick again
    // then: NoChange
}

@Test
void tick_isCompositor_noSideEffects() {
    // given: drives above threshold, mapper returns proposal
    // when: tick twice without registration
    // then: same result both times (no cumulative state change)
}

@Test
void currentProposals_returnsCachedState() {
    // given: tick produced proposals
    // when: currentProposals()
    // then: returns the same proposals
}
```

- [ ] **Step 3: Run tests — verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalProposalOrchestratorTest`
Expected: compilation failure

- [ ] **Step 4: Implement GoalProposalOrchestrator**

Follow the tick() flow from the design spec:
1. Read DriveProfile
2. Check cooldown
3. Count existing drive-sourced goals (attributes filter)
4. Evaluate mappers for axes above threshold
5. Rank by intensity, take top N
6. Relevance re-evaluation for existing goals
7. Cache and return GoalProposalTick

Internal `GoalProposalState`:
```java
private static final class GoalProposalState {
    Instant lastProposalTimestamp = Instant.EPOCH;
    List<DriveGoalProposal> cachedProposals = List.of();
    List<String> cachedAbandonments = List.of();
    Map<String, Instant> driveGoalBelowThresholdSince = new HashMap<>();
    Set<String> failureSuppressedGoalNames = new HashSet<>();
}
```

- [ ] **Step 5: Run tests — verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalProposalOrchestratorTest`
Expected: all tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/agentic/social/goal/ src/test/java/io/casehub/blocks/agentic/social/goal/
git commit -m "feat(#136): add GoalProposalOrchestrator — compositor for drive-to-goal proposals Refs #136"
```

---

## Batch 4: Drive goal mapper implementations

### Task 4: CuriosityGoalMapper + CompetenceGoalMapper

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/social/goal/CuriosityGoalMapper.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/goal/CompetenceGoalMapper.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/goal/CuriosityGoalMapperTest.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/goal/CompetenceGoalMapperTest.java`

**Interfaces:**
- Consumes: `MemoryHygieneOrchestrator.knowledgeGaps()` → `KnowledgeGapSummary`, `StrategyLearningOrchestrator.engagementTrend()` → `EngagementTrend`
- Produces: `DriveGoalProposal` with normalised goal names

- [ ] **Step 1: Write CuriosityGoalMapper tests**

```java
@Test
void evaluate_returnsProposal_whenKnowledgeGapsExist() {
    var gaps = new KnowledgeGapSummary(5, 3, 0);
    when(hygieneOrch.knowledgeGaps("a1", "t1")).thenReturn(gaps);
    var proposal = mapper.evaluate("a1", "t1", intensity(0.7));
    assertThat(proposal).isNotNull();
    assertThat(proposal.axis()).isEqualTo(DriveAxis.CURIOSITY);
    assertThat(proposal.goalName()).isEqualTo("explore-knowledge-gaps");
}

@Test
void evaluate_returnsNull_whenNoGaps() {
    var gaps = new KnowledgeGapSummary(0, 0, 0);
    when(hygieneOrch.knowledgeGaps("a1", "t1")).thenReturn(gaps);
    assertThat(mapper.evaluate("a1", "t1", intensity(0.7))).isNull();
}

@Test
void evaluate_returnsNull_whenGapsNull() {
    when(hygieneOrch.knowledgeGaps("a1", "t1")).thenReturn(null);
    assertThat(mapper.evaluate("a1", "t1", intensity(0.7))).isNull();
}
```

- [ ] **Step 2: Implement CuriosityGoalMapper**

Read `knowledgeGaps()`, return null when no gaps. Build proposal with
normalised name "explore-knowledge-gaps" and descriptive text from gap counts.

- [ ] **Step 3: Write CompetenceGoalMapper tests**

```java
@Test
void evaluate_returnsProposal_whenDecliningDimensions() {
    var trend = new EngagementTrend(
        Map.of("technical", TrendDirection.DECLINING), 0.3, 50);
    when(strategyOrch.engagementTrend("a1", "t1")).thenReturn(Optional.of(trend));
    var proposal = mapper.evaluate("a1", "t1", intensity(0.6));
    assertThat(proposal).isNotNull();
    assertThat(proposal.goalName()).startsWith("improve-");
}

@Test
void evaluate_returnsNull_whenNoDecliningDimensions() {
    var trend = new EngagementTrend(
        Map.of("technical", TrendDirection.IMPROVING), 0.8, 100);
    when(strategyOrch.engagementTrend("a1", "t1")).thenReturn(Optional.of(trend));
    assertThat(mapper.evaluate("a1", "t1", intensity(0.6))).isNull();
}
```

- [ ] **Step 4: Implement CompetenceGoalMapper**

Read `engagementTrend()`, find most declining dimension, build proposal.

- [ ] **Step 5: Run tests — verify pass**

Run: `mvn --batch-mode test -pl blocks -Dtest="CuriosityGoalMapperTest,CompetenceGoalMapperTest"`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/agentic/social/goal/Curiosity* src/main/java/io/casehub/blocks/agentic/social/goal/Competence* src/test/java/io/casehub/blocks/agentic/social/goal/Curiosity* src/test/java/io/casehub/blocks/agentic/social/goal/Competence*
git commit -m "feat(#136): add CuriosityGoalMapper + CompetenceGoalMapper Refs #136"
```

### Task 5: AffiliationGoalMapper + AutonomyGoalMapper

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/social/goal/AffiliationGoalMapper.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/goal/AutonomyGoalMapper.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/goal/AffiliationGoalMapperTest.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/goal/AutonomyGoalMapperTest.java`

**Interfaces:**
- Consumes: `UserModelOrchestrator.activeProfiles()` → `List<UserProfile>`, `MentalModelOrchestrator.activeSnapshots()` → `List<MentalModelSnapshot>`
- Produces: `DriveGoalProposal` with normalised goal names

- [ ] **Step 1: Write AffiliationGoalMapper tests**

```java
@Test
void evaluate_returnsProposal_whenNeglectedRelationship() {
    var profile = new UserProfile("user-abc", /* ... low familiarity, stale ... */);
    when(userModelOrch.activeProfiles("a1", "t1")).thenReturn(List.of(profile));
    var proposal = mapper.evaluate("a1", "t1", intensity(0.5));
    assertThat(proposal).isNotNull();
    assertThat(proposal.goalName()).isEqualTo("reconnect-user-abc");
}

@Test
void evaluate_returnsNull_whenAllRelationshipsHealthy() {
    var profile = new UserProfile("user-abc", /* ... high familiarity, recent ... */);
    when(userModelOrch.activeProfiles("a1", "t1")).thenReturn(List.of(profile));
    assertThat(mapper.evaluate("a1", "t1", intensity(0.5))).isNull();
}
```

- [ ] **Step 2: Implement AffiliationGoalMapper**

Scan profiles for decay: low `familiarityScore`, stale `lastInteraction`.
Select most neglected subject. Build proposal "reconnect-{subjectId}".

- [ ] **Step 3: Write AutonomyGoalMapper tests**

```java
@Test
void evaluate_returnsProposal_whenHighConfidenceIntentions() {
    // given: snapshot with high-confidence intention projections
    // when: evaluate
    // then: proposal with "reassess-{subject}" goal name
}

@Test
void evaluate_returnsNull_whenNoHighConfidenceIntentions() {
    // given: snapshot with low-confidence projections only
    // when: evaluate
    // then: null
}
```

- [ ] **Step 4: Implement AutonomyGoalMapper**

Read `activeSnapshots()`, scan for high-confidence intention projections.
Select subject with most misaligned intentions. Build proposal "reassess-{subject}".

- [ ] **Step 5: Run all mapper tests**

Run: `mvn --batch-mode test -pl blocks -Dtest="AffiliationGoalMapperTest,AutonomyGoalMapperTest"`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/agentic/social/goal/Affiliation* src/main/java/io/casehub/blocks/agentic/social/goal/Autonomy* src/test/java/io/casehub/blocks/agentic/social/goal/Affiliation* src/test/java/io/casehub/blocks/agentic/social/goal/Autonomy*
git commit -m "feat(#136): add AffiliationGoalMapper + AutonomyGoalMapper Refs #136"
```

---

## Batch 5: Integration test + documentation

### Task 6: Integration test + CLAUDE.md + contributor guide

**Files:**
- Create: `src/test/java/io/casehub/blocks/agentic/social/goal/GoalProposalIntegrationTest.java`
- Modify: `CLAUDE.md` (add goal package to key directories)
- Modify: `docs/guides/contributor-guide.md` (add goal proposal section)

**Interfaces:**
- Consumes: All types from Tasks 1-5

- [ ] **Step 1: Write integration test**

Full tick cycle: set up mocked source orchestrator state → create
DriveOrchestrator with real drive sources → tick DriveOrchestrator → tick
GoalProposalOrchestrator → verify proposals reflect drive state.

```java
@Test
void fullTickCycle_driveToGoalProposal() {
    // given: MemoryHygieneOrchestrator has knowledge gaps,
    //        UserModelOrchestrator has a neglected relationship
    // when: driveOrchestrator.tick() → goalProposalOrchestrator.tick()
    // then: proposals include explore-knowledge-gaps AND reconnect-{subject}
}

@Test
void fullTickCycle_noProposalsWhenDrivesWeak() {
    // given: all source orchestrators return healthy state
    // when: tick
    // then: all drives below threshold, no proposals
}
```

- [ ] **Step 2: Run integration test**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalProposalIntegrationTest`

- [ ] **Step 3: Update CLAUDE.md**

Add to Key Directories table:
```
| `src/main/java/io/casehub/blocks/agentic/social/goal/` | Autonomous goal proposal — `GoalProposalOrchestrator` (compositor), `DriveGoalMapper` SPI, four mapper implementations |
| `src/test/java/io/casehub/blocks/agentic/social/goal/` | Tests for goal proposal |
```

Add to package description section for `io.casehub.blocks.agentic.social.goal`.

- [ ] **Step 4: Update contributor guide**

Add goal proposal section to `docs/guides/contributor-guide.md` describing the
compositor lifecycle, DriveGoalMapper SPI, and scheduler integration pattern.

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test`
Expected: all tests pass

- [ ] **Step 6: Commit**

```bash
git add src/test/java/io/casehub/blocks/agentic/social/goal/GoalProposalIntegrationTest.java CLAUDE.md docs/guides/contributor-guide.md
git commit -m "docs(#136): add goal proposal integration test, update CLAUDE.md and contributor guide Refs #136"
```

---

## References

- [2026-08-23-autonomous-goal-generation-design.md] — design spec this plan implements
- [decisions.md] — 9 validated decisions (D1-D9)
- [AgentGoal.java] (eidos-api) — target record for attributes extension
- [AgentGoalEntity.java] (eidos runtime) — JPA entity to extend
- [AgentDescriptorMapper.java] (eidos runtime) — mapper to update
- [GoalFormationEvaluator.java] (engine runtime) — source for service extraction
- [GoalRevisionEvaluator.java] (engine runtime) — source for removal extraction
- [GoalFormationProposal.java] (engine-api) — ProposedGoal to extend
- [CaseHubEventType.java] (engine-api) — enum to extend
- [DriveOrchestrator.java] (blocks) — compositor pattern reference
- [DriveSource.java] (blocks) — mapper dependency pattern reference
- [GitHub #136] — focal issue
