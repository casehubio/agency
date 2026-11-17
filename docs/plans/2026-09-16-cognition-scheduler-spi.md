# Cognition Scheduler SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #161 — InnerLife scheduler SPI — shared tick coordination for orchestrators
**Issue group:** #161

**Goal:** Formalise CognitionCore's tick ordering as a phase model, fix drive data staleness, add consumer extension SPI, and clean up InnerLife's redundant drive re-tick.

**Architecture:** CognitionPhase enum defines five ordered phases (FOUNDATION → SOURCE → SOURCE_PER_SUBJECT → DERIVED → TERMINAL). CognitionCore.tick() dispatches built-in orchestrators by phase then runs custom CognitionTickParticipant instances registered at each phase. InnerLife stays on-demand (not a phase participant) with a runtime precondition check replacing its redundant drive re-tick.

**Tech Stack:** Java 17, JUnit 5, Mockito, blocks-core module

## Global Constraints

- No new dependencies — all types are plain Java in blocks-core
- Maintain backward compatibility for existing CognitionCore constructors (add new parameters as @Nullable, preserve existing overloads)
- All orchestrator tick signatures remain unchanged — the SPI is for custom participants only
- Package: `io.casehub.blocks.agentic.social`

---

## Batch 1: Foundation types — CognitionPhase, SPI, CognitionConfig

### Task 1: CognitionPhase enum, CognitionTickParticipant SPI, CognitionTickContext record

**Files:**
- Create: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionPhase.java`
- Create: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionTickParticipant.java`
- Create: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionTickContext.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionPhaseTest.java`

**Interfaces:**
- Consumes: `SubjectResolver` (existing `@FunctionalInterface` in same package), `AgentDescriptor` (eidos-api)
- Produces: `CognitionPhase` enum (5 constants: FOUNDATION, SOURCE, SOURCE_PER_SUBJECT, DERIVED, TERMINAL), `CognitionTickParticipant` (`@FunctionalInterface`, `void tick(CognitionTickContext)`), `CognitionTickContext` record (`String agentId, String tenantId, @Nullable AgentDescriptor descriptor, SubjectResolver resolver`)

- [ ] **Step 1: Write failing test for CognitionPhase ordering**

```java
package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitionPhaseTest {

    @Test
    void phasesAreOrderedByDeclaration() {
        var phases = CognitionPhase.values();
        assertThat(phases).containsExactly(
                CognitionPhase.FOUNDATION,
                CognitionPhase.SOURCE,
                CognitionPhase.SOURCE_PER_SUBJECT,
                CognitionPhase.DERIVED,
                CognitionPhase.TERMINAL);
    }

    @Test
    void tickContextCarriesAllFields() {
        var resolver = (SubjectResolver) (a, t) -> java.util.Set.of();
        var ctx = new CognitionTickContext("agent-1", "tenant-1", null, resolver);
        assertThat(ctx.agentId()).isEqualTo("agent-1");
        assertThat(ctx.tenantId()).isEqualTo("tenant-1");
        assertThat(ctx.descriptor()).isNull();
        assertThat(ctx.resolver()).isSameAs(resolver);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitionPhaseTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `CognitionPhase` does not exist

- [ ] **Step 3: Create CognitionPhase enum**

Create `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionPhase.java`:

```java
package io.casehub.blocks.agentic.social;

public enum CognitionPhase {
    FOUNDATION,
    SOURCE,
    SOURCE_PER_SUBJECT,
    DERIVED,
    TERMINAL
}
```

- [ ] **Step 4: Create CognitionTickContext record**

Create `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionTickContext.java`:

```java
package io.casehub.blocks.agentic.social;

import io.casehub.eidos.api.AgentDescriptor;
import org.jspecify.annotations.Nullable;

public record CognitionTickContext(
        String agentId,
        String tenantId,
        @Nullable AgentDescriptor descriptor,
        SubjectResolver resolver
) {}
```

- [ ] **Step 5: Create CognitionTickParticipant SPI**

Create `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionTickParticipant.java`:

```java
package io.casehub.blocks.agentic.social;

@FunctionalInterface
public interface CognitionTickParticipant {
    void tick(CognitionTickContext context);
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitionPhaseTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionPhase.java blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionTickContext.java blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionTickParticipant.java blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionPhaseTest.java
git commit -m "feat(#161): CognitionPhase enum, CognitionTickParticipant SPI, CognitionTickContext record"
```

### Task 2: Add innerLifeEnabled to CognitionConfig

**Files:**
- Modify: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionConfig.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionConfigTest.java`

**Interfaces:**
- Consumes: existing `CognitionConfig` record
- Produces: `CognitionConfig` with new `boolean innerLifeEnabled` field, updated `all()`, `none()`, `with()`, `without()`, `withDirectives()`

- [ ] **Step 1: Write failing test for innerLifeEnabled**

Add to `CognitionConfigTest.java`:

```java
@Test
void innerLifeEnabledDefaultsToTrueInAll() {
    assertThat(CognitionConfig.all().innerLifeEnabled()).isTrue();
}

@Test
void innerLifeEnabledDefaultsToFalseInNone() {
    assertThat(CognitionConfig.none().innerLifeEnabled()).isFalse();
}

@Test
void withTogglesInnerLife() {
    var config = CognitionConfig.all().with("innerLife", false);
    assertThat(config.innerLifeEnabled()).isFalse();
    assertThat(config.moodEnabled()).isTrue();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitionConfigTest`
Expected: FAIL — `innerLifeEnabled()` does not exist

- [ ] **Step 3: Add innerLifeEnabled field to CognitionConfig**

Modify `CognitionConfig.java`:
- Add `boolean innerLifeEnabled` between `memoryHygieneEnabled` and `directivePrompts` in the record components
- Update `all()`: pass `true` for innerLifeEnabled
- Update `none()`: pass `false` for innerLifeEnabled
- Update `withDirectives()`: propagate innerLifeEnabled
- Add `"innerLife"` case to `with()` switch

- [ ] **Step 4: Fix all existing CognitionConfig construction sites**

Existing call sites that construct CognitionConfig directly need the new parameter. Search with `ide_find_references` for CognitionConfig constructor calls. Key sites:
- `CognitionConfig.all()` and `CognitionConfig.none()` — already updated in Step 3
- `CognitionConfig.withDirectives()` — already updated in Step 3
- `CognitionConfig.with()` — each switch case needs the new field propagated
- Test files that construct CognitionConfig directly (CognitionConfigTest) — update to include new field

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitionConfigTest`
Expected: PASS

- [ ] **Step 6: Run full test suite to verify no regressions**

Run: `mvn --batch-mode test -pl blocks`
Expected: PASS (all existing tests still compile and pass with the new field)

- [ ] **Step 7: Commit**

```bash
git add blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionConfig.java blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionConfigTest.java
git commit -m "feat(#161): add innerLifeEnabled flag to CognitionConfig"
```

---

## Batch 2: CognitionCore phase model + ordering fix

### Task 3: Refactor CognitionCore — phase ordering, custom participants, InnerLife field

**Files:**
- Modify: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionSnapshotTest.java`
- Modify: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognitionTest.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStack.java`

**Interfaces:**
- Consumes: `CognitionPhase`, `CognitionTickParticipant`, `CognitionTickContext` (from Task 1), `CognitionConfig` with `innerLifeEnabled` (from Task 2)
- Produces: `CognitionCore.addParticipant(CognitionPhase, CognitionTickParticipant)`, `CognitionCore.innerLife()` accessor, refactored `tick()` with correct phase ordering (FOUNDATION → SOURCE → SOURCE_PER_SUBJECT → DERIVED → TERMINAL)

- [ ] **Step 1: Write failing test for phase ordering**

Add to `CognitionCoreTest.java`:

```java
@Test
void tickExecutesInPhaseOrder() {
    var mood = mock(MoodOrchestrator.class);
    when(mood.currentMood(any(), any())).thenReturn(Optional.of(new MoodState(0, 0, 0)));
    var drives = mock(DriveOrchestrator.class);
    var strategy = mock(StrategyLearningOrchestrator.class);
    var userModel = mock(UserModelOrchestrator.class);
    var mentalModel = mock(MentalModelOrchestrator.class);
    var narrative = mock(NarrativeOrchestrator.class);
    var goals = mock(GoalProposalOrchestrator.class);
    var memoryHygiene = mock(MemoryHygieneOrchestrator.class);

    var core = new CognitionCore(mood, drives, userModel, mentalModel,
            strategy, narrative, goals, memoryHygiene);

    var descriptor = stubDescriptor();
    core.tick("a1", "t1", descriptor, (a, t) -> Set.of("subject-1"));

    var inOrder = inOrder(mood, memoryHygiene, narrative, strategy,
            userModel, mentalModel, drives, goals);
    // FOUNDATION
    inOrder.verify(mood).tick("a1", "t1");
    inOrder.verify(memoryHygiene).tick("a1", "t1");
    // SOURCE
    inOrder.verify(narrative).tick("a1", "t1");
    inOrder.verify(strategy).tick("a1", "t1");
    // SOURCE_PER_SUBJECT
    inOrder.verify(userModel).tick("a1", "subject-1", "t1");
    inOrder.verify(mentalModel).tick("a1", "subject-1", "t1");
    // DERIVED
    inOrder.verify(drives).tick("a1", "t1", descriptor);
    // TERMINAL
    inOrder.verify(goals).tick("a1", "t1", descriptor);
}
```

- [ ] **Step 2: Write failing test for custom participant execution**

```java
@Test
void customParticipantExecutesAtRegisteredPhase() {
    var core = minimalCore();
    var executed = new java.util.concurrent.atomic.AtomicBoolean(false);
    core.addParticipant(CognitionPhase.TERMINAL, ctx -> executed.set(true));

    core.tick("a1", "t1", stubDescriptor(), (a, t) -> Set.of());
    assertThat(executed.get()).isTrue();
}

@Test
void addParticipantRejectsSourcePerSubject() {
    var core = minimalCore();
    assertThatThrownBy(() ->
            core.addParticipant(CognitionPhase.SOURCE_PER_SUBJECT, ctx -> {}))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void customParticipantErrorDoesNotCrashTick() {
    var core = minimalCore();
    core.addParticipant(CognitionPhase.FOUNDATION, ctx -> {
        throw new RuntimeException("boom");
    });
    var secondExecuted = new java.util.concurrent.atomic.AtomicBoolean(false);
    core.addParticipant(CognitionPhase.TERMINAL, ctx -> secondExecuted.set(true));

    core.tick("a1", "t1", stubDescriptor(), (a, t) -> Set.of());
    assertThat(secondExecuted.get()).isTrue();
}

@Test
void disabledBuiltinDoesNotPreventCustomParticipant() {
    var mood = new MoodOrchestrator(MoodConfig.defaults());
    DriveSource baseline = (a, t) ->
            new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
    var drives = new DriveOrchestrator(
            baseline, baseline, baseline, baseline,
            mood, new DriveComposer(), DriveConfig.defaults());
    var config = CognitionConfig.all().with("drives", false);
    var core = new CognitionCore(mood, drives,
            null, null, null, null, null, null, null, config);

    var executed = new java.util.concurrent.atomic.AtomicBoolean(false);
    core.addParticipant(CognitionPhase.DERIVED, ctx -> executed.set(true));
    core.tick("a1", "t1", stubDescriptor(), (a, t) -> Set.of());
    assertThat(executed.get()).isTrue();
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitionCoreTest`
Expected: FAIL — new tests reference methods/constructors that don't exist yet

- [ ] **Step 4: Modify CognitionCore**

Changes to `CognitionCore.java`:
1. Add `@Nullable InnerLifeOrchestrator innerLife` field
2. Add `Map<CognitionPhase, List<CognitionTickParticipant>> customParticipants` field (initialized as `EnumMap`)
3. Update all three constructor overloads — add `@Nullable InnerLifeOrchestrator innerLife` parameter. The 2-arg and 3-arg constructors pass `null` for innerLife to the full constructor.
4. Add `addParticipant(CognitionPhase, CognitionTickParticipant)` method — throws `IllegalArgumentException` for `SOURCE_PER_SUBJECT`
5. Add `runCustomParticipants(CognitionPhase, CognitionTickContext)` private method — iterates and calls each participant wrapped in `safeRun()`
6. Add `innerLife()` accessor returning `@Nullable InnerLifeOrchestrator`
7. Refactor `tick()` method — reorder to: FOUNDATION (mood, memoryHygiene), SOURCE (narrative, strategy), SOURCE_PER_SUBJECT (per-subject userModel, mentalModel), DERIVED (drives), TERMINAL (goals). Add `runCustomParticipants()` call after each phase's built-ins. Wrap mood init+tick in `tickMood()` helper with `safeRun()`.

- [ ] **Step 5: Update all constructor call sites**

Update every CognitionCore construction to include the new `innerLife` parameter (null in most cases):
- `CognitionCoreTest.minimalCore()` — add `null` for innerLife
- `CognitionSnapshotTest.minimalCore()` — add `null` for innerLife  
- `CognitionStack.from()` (agentic-yaml test) — add `null` for innerLife
- `SocialAvatarCognition` constructor — pass `innerLife.orElse(null)` to CognitionCore, keep own field for now (Task 5 reorganises this)

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks`
Expected: PASS

- [ ] **Step 7: Run full build to verify cross-module compilation**

Run: `mvn --batch-mode test`
Expected: PASS (agentic-yaml tests compile with updated CognitionStack)

- [ ] **Step 8: Commit**

```bash
git add blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionSnapshotTest.java blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognitionTest.java agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStack.java
git commit -m "feat(#161): CognitionCore phase model with custom participant SPI and drive ordering fix"
```

---

## Batch 3: InnerLife cleanup — remove drive re-tick, add precondition

### Task 4: Remove drive re-tick from InnerLifeOrchestrator, add precondition check

**Files:**
- Modify: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/InnerLifeOrchestrator.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/InnerLifeOrchestratorTest.java`

**Interfaces:**
- Consumes: `DriveOrchestrator.currentDrives(String, String)` — returns `Optional<DriveProfile>`
- Produces: InnerLifeOrchestrator with no drive re-tick, precondition check returning `InnerLifeTick.Silent` with warning when drives not current

- [ ] **Step 1: Modify existing test — drive tick is no longer called**

Change `tick_calls_drive_orchestrator_tick` test in `InnerLifeOrchestratorTest.java` to verify the **opposite** — that `driveOrchestrator.tick()` is NOT called:

```java
@Test
void tickDoesNotCallDriveOrchestratorTick() {
    when(driveOrchestrator.currentDrives("agent-1", "tenant-1"))
            .thenReturn(Optional.of(mock(DriveProfile.class)));
    var orch = makeOrchestrator();
    orch.tick(descriptor, "context");
    verify(driveOrchestrator, never()).tick(any(), any(), any());
}
```

- [ ] **Step 2: Write failing test for precondition check**

```java
@Test
void tickReturnsSilentWhenDrivesNotCurrent() {
    when(driveOrchestrator.currentDrives("agent-1", "tenant-1"))
            .thenReturn(Optional.empty());
    var orch = makeOrchestrator();
    orch.observe(event("observation"), descriptor);
    var result = orch.tick(descriptor, "context");
    assertThat(result).isInstanceOf(InnerLifeTick.Silent.class);
    assertThat(((InnerLifeTick.Silent) result).reason())
            .isEqualTo("drives not current");
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=InnerLifeOrchestratorTest`
Expected: FAIL — drive tick is still called, no precondition check exists

- [ ] **Step 4: Modify InnerLifeOrchestrator.doTick()**

In `InnerLifeOrchestrator.java`, method `doTick()`:
1. Remove line 97: `driveOrchestrator.tick(descriptor.agentId(), descriptor.tenancyId(), descriptor);`
2. Add precondition check at the start of `doTick()`, before the existing code:
```java
if (driveOrchestrator.currentDrives(
        descriptor.agentId(), descriptor.tenancyId()).isEmpty()) {
    LOG.log(Level.WARNING,
            "InnerLife tick called without current drive profile for "
                    + descriptor.agentId()
                    + " — ensure CognitionCore.tick() has run first");
    return new InnerLifeTick.Silent("drives not current");
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=InnerLifeOrchestratorTest`
Expected: PASS

- [ ] **Step 6: Fix remaining InnerLife tests that mock drive tick**

Some existing tests may need updating: they previously expected `driveOrchestrator.tick()` to be called (or didn't stub `currentDrives()`). Ensure all existing tests stub `currentDrives()` to return a non-empty Optional so the precondition passes:

In `setUp()`:
```java
when(driveOrchestrator.currentDrives("agent-1", "tenant-1"))
        .thenReturn(Optional.of(mock(DriveProfile.class)));
```

- [ ] **Step 7: Run full test suite**

Run: `mvn --batch-mode test -pl blocks`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add blocks-core/src/main/java/io/casehub/blocks/agentic/social/InnerLifeOrchestrator.java blocks/src/test/java/io/casehub/blocks/agentic/social/InnerLifeOrchestratorTest.java
git commit -m "feat(#161): remove drive re-tick from InnerLife, add precondition check"
```

### Task 5: Reorganise SocialAvatarCognition — delegate InnerLife through CognitionCore

**Files:**
- Modify: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognitionTest.java`

**Interfaces:**
- Consumes: `CognitionCore.innerLife()` accessor (from Task 3)
- Produces: SocialAvatarCognition without its own `innerLife` field — accesses InnerLife through `core.innerLife()`

- [ ] **Step 1: Write failing test — construction without separate innerLife**

Update the existing `evaluateProactiveReturnsContentOnInitiated` test to construct `SocialAvatarCognition` without the `innerLife` parameter. InnerLife is now accessed via `core.innerLife()` — the test verifies the plumbing works through CognitionCore:

```java
@Test
void evaluateProactiveReturnsContentOnInitiatedViaCore() {
    // SocialAvatarCognition constructed without innerLife param
    // innerLife is accessed via core.innerLife()
    var cognition = new SocialAvatarCognition(
            mood, drives, mentalModel, userModel, strategy,
            Optional.of(narrative), Optional.of(goals),
            Optional.of(agentRegistry));
    // innerLife was passed to CognitionCore in the constructor
    // evaluateProactive should still work
    when(agentRegistry.findById("a1", "t1")).thenReturn(Optional.of(descriptor));
    when(innerLife.tick(descriptor, "ctx"))
            .thenReturn(new InnerLifeTick.Initiated("hello", null, 0.9));
    var result = cognition.evaluateProactive("a1", "t1", "ctx");
    assertThat(result).isEqualTo("hello");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=SocialAvatarCognitionTest`
Expected: FAIL — constructor signature mismatch

- [ ] **Step 3: Modify SocialAvatarCognition**

In `SocialAvatarCognition.java`:
1. Remove `private final Optional<InnerLifeOrchestrator> innerLife` field
2. Update constructor — remove `Optional<InnerLifeOrchestrator> innerLife` parameter. InnerLife is already passed to CognitionCore in Task 3.
3. Update `evaluateProactive()` — use `core.innerLife()` instead of `innerLife.get()`
4. Update `recordInteraction()` — use `core.innerLife()` instead of `innerLife.isPresent()`
5. Guard `evaluateProactive()` with `config.innerLifeEnabled()` check

- [ ] **Step 4: Update SocialAvatarCognitionTest**

Update all test construction sites to match the new constructor signature (no innerLife parameter). Mock setup for InnerLife goes through the CognitionCore mock or test CognitionCore construction.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=SocialAvatarCognitionTest`
Expected: PASS

- [ ] **Step 6: Run full build**

Run: `mvn --batch-mode test`
Expected: PASS — all modules compile and pass

- [ ] **Step 7: Commit**

```bash
git add blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognitionTest.java
git commit -m "feat(#161): delegate InnerLife through CognitionCore in SocialAvatarCognition"
```

---

## References

- [2026-09-16-cognition-scheduler-spi-design.md] — design spec this plan implements
- [CognitionCore.java:97-137] — current tick ordering
- [InnerLifeOrchestrator.java:97] — redundant drive re-tick
- [CognitionConfig.java:3-50] — existing config record
- [SocialAvatarCognition.java:40-60] — CognitionCore construction + InnerLife wiring
- [CognitionCoreTest.java:244-253] — minimalCore() helper pattern
- [InnerLifeOrchestratorTest.java:50-54] — makeOrchestrator() helper pattern
- [CognitionStack.java:184] — agentic-yaml test CognitionCore construction
- [GitHub #161] — focal issue
- [GitHub #136] — ordering constraint source
