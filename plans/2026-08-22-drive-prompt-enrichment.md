# DriveProfile Prompt Enrichment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #149 — DriveProfile prompt enrichment
**Issue group:** #149

**Goal:** Render DriveProfile as a cognitive observation section in agent
prompts and wire DriveOrchestrator.tick() into the InnerLifeOrchestrator
tick lifecycle.

**Architecture:** Add `motivationalStateSection()` to CognitiveObservationSections
(follows existing factory pattern). Make DriveOrchestrator CDI-managed with
an `@Inject` constructor that builds drive sources internally using
`Instance<MemoryHygieneOrchestrator>` for optional injection. Extend
DriveConfig with drive-source params. Wire drive tick into
InnerLifeOrchestrator.doTick().

**Tech Stack:** Java 21+, JUnit 5, Mockito, AssertJ, CDI (jakarta.inject)

## Global Constraints

- Blocks has no Quarkus runtime — tests are plain JUnit 5 with Mockito
- No Jandex index — CDI beans not auto-discovered by consumers
- All edits via IntelliJ MCP tools (`ide_edit_member`, `ide_insert_member`,
  `ide_replace_member`, `ide_create_file`)
- Commit after every task with `Refs casehubio/blocks#149`
- Project path: `/Users/mdproctor/claude/casehub/slots/146/blocks`
- Source root: `blocks/src/main/java/io/casehub/blocks/`
- Test root: `blocks/src/test/java/io/casehub/blocks/`

---

## Batch 1: DriveConfig extension + observation rendering

### Task 1: Extend DriveConfig with drive-source parameters

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/drive/DriveConfig.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/drive/DriveTypesTest.java` (if config tests exist there)
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/drive/DriveOrchestratorTest.java` (update `DriveConfig.defaults()` calls)
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/drive/DriveComposerTest.java` (update `DriveConfig.defaults()` calls)

**Interfaces:**
- Produces: `DriveConfig` with new fields `affiliationDecayThreshold()`,
  `affiliationStaleDuration()`, `autonomyConfidenceFloor()` and updated
  `defaults()` factory

- [ ] **Step 1: Write the failing test**

Add a test verifying the new fields exist and have correct defaults:

```java
@Test
void defaults_includes_drive_source_params() {
    var config = DriveConfig.defaults();
    assertThat(config.affiliationDecayThreshold()).isEqualTo(0.5);
    assertThat(config.affiliationStaleDuration()).isEqualTo(Duration.ofHours(24));
    assertThat(config.autonomyConfidenceFloor()).isEqualTo(0.6);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest="DriveTypesTest#defaults_includes_drive_source_params" -DfailIfNoTests=false`
Expected: compilation failure — fields don't exist

- [ ] **Step 3: Add new fields to DriveConfig**

Replace the `DriveConfig` record body to add three new fields:

```java
package io.casehub.blocks.agentic.social.drive;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record DriveConfig(
        Map<DriveAxis, Double> axisWeights,
        double changeThreshold,
        double moodPleasureModulation,
        double moodArousalModulation,
        double personalityModulationStrength,
        double maxIntensity,
        double minIntensity,
        double affiliationDecayThreshold,
        Duration affiliationStaleDuration,
        double autonomyConfidenceFloor) {
    public DriveConfig {
        Objects.requireNonNull(axisWeights, "axisWeights required");
        Objects.requireNonNull(affiliationStaleDuration, "affiliationStaleDuration required");
        axisWeights = Map.copyOf(axisWeights);
        if (changeThreshold < 0.0 || changeThreshold > 1.0)
            throw new IllegalArgumentException("changeThreshold must be in [0.0, 1.0]");
        if (maxIntensity < minIntensity)
            throw new IllegalArgumentException("maxIntensity must be >= minIntensity");
        if (minIntensity < 0.0) throw new IllegalArgumentException("minIntensity must be >= 0.0");
        if (maxIntensity > 1.0) throw new IllegalArgumentException("maxIntensity must be <= 1.0");
    }

    public static DriveConfig defaults() {
        return new DriveConfig(
                Map.of(DriveAxis.CURIOSITY, 1.0, DriveAxis.COMPETENCE, 1.0,
                       DriveAxis.AFFILIATION, 1.0, DriveAxis.AUTONOMY, 1.0),
                0.05, 0.3, 0.2, 0.25, 1.0, 0.0,
                0.5, Duration.ofHours(24), 0.6);
    }
}
```

- [ ] **Step 4: Fix existing tests**

All existing callers of `DriveConfig.defaults()` continue to work — the
`defaults()` factory includes the new fields. However, any tests that
construct `DriveConfig` directly (not via `defaults()`) need the three
new args added. Search for `new DriveConfig(` in test files and update.

- [ ] **Step 5: Run all drive tests to verify everything passes**

Run: `mvn --batch-mode test -pl blocks -Dtest="io.casehub.blocks.agentic.social.drive.*"`
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/146/blocks add blocks/src/
git -C /Users/mdproctor/claude/casehub/slots/146/blocks commit -m "feat(#149): extend DriveConfig with drive-source parameters

Refs casehubio/blocks#149"
```

### Task 2: Add motivationalStateSection to CognitiveObservationSections

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSectionsTest.java`

**Interfaces:**
- Consumes: `DriveProfile`, `DriveAxis`, `DriveIntensity` (from drive package)
- Produces: `CognitiveObservationSections.motivationalStateSection(DriveProfile) → ObservationSection`

- [ ] **Step 1: Write the failing tests**

Add five tests to `CognitiveObservationSectionsTest`:

```java
private static DriveProfile profile(Map<DriveAxis, DriveIntensity> drives) {
    double composite = drives.values().stream()
            .mapToDouble(DriveIntensity::intensity).average().orElse(0.0);
    DriveAxis dominant = drives.entrySet().stream()
            .max(Map.Entry.comparingByValue(
                    Comparator.comparingDouble(DriveIntensity::intensity)))
            .map(Map.Entry::getKey).orElse(DriveAxis.CURIOSITY);
    return new DriveProfile("agent-1", "tenant-1", drives,
            composite, dominant, Instant.now());
}

@Test
void motivationalStateSection_renders_non_zero_axes() {
    var drives = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.6, "5 low-retention memories across 3 groups"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.2, "1 declining of 3 dimensions"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.8, "2 of 3 relationships neglected"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.3, "2 high-confidence intentions across 1 subject"));
    var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).hasSize(4);
    assertThat(items.get(0)).isEqualTo("Curiosity: 0.6 — 5 low-retention memories across 3 groups");
    assertThat(items.get(1)).isEqualTo("Competence: 0.2 — 1 declining of 3 dimensions");
    assertThat(items.get(2)).isEqualTo("Affiliation: 0.8 — 2 of 3 relationships neglected");
    assertThat(items.get(3)).isEqualTo("Autonomy: 0.3 — 2 high-confidence intentions across 1 subject");
    assertThat(((ObservationSection.ItemList) section).header()).isEqualTo("Motivational State");
}

@Test
void motivationalStateSection_filters_low_intensity_axes() {
    var drives = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.6, "gaps"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "none"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.03, "near zero"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "none"));
    var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).hasSize(1);
    assertThat(items.get(0)).startsWith("Curiosity: 0.6");
}

@Test
void motivationalStateSection_all_below_threshold_shows_no_active_drives() {
    var drives = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "none"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "none"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.02, "trace"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "none"));
    var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var il = (ObservationSection.ItemList) section;
    assertThat(il.items()).isEmpty();
    assertThat(il.emptyMessage()).isEqualTo("No active drives.");
}

@Test
void motivationalStateSection_single_axis_non_zero() {
    var drives = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "none"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "none"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.0, "none"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.9, "high pressure"));
    var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).hasSize(1);
    assertThat(items.get(0)).isEqualTo("Autonomy: 0.9 — high pressure");
}

@Test
void motivationalStateSection_boundary_at_threshold() {
    var drives = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.05, "just enough"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.04, "not enough"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.0, "none"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "none"));
    var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).hasSize(1);
    assertThat(items.get(0)).startsWith("Curiosity: 0.1");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest="CognitiveObservationSectionsTest#motivationalStateSection*" -DfailIfNoTests=false`
Expected: compilation failure — method doesn't exist

- [ ] **Step 3: Implement motivationalStateSection**

Add to `CognitiveObservationSections.java`:

```java
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveProfile;

public static ObservationSection motivationalStateSection(DriveProfile profile) {
    var items = new ArrayList<String>();
    for (var axis : DriveAxis.values()) {
        var intensity = profile.drives().get(axis);
        if (intensity != null && intensity.intensity() >= 0.05) {
            String name = axis.name().charAt(0) + axis.name().substring(1).toLowerCase();
            items.add(String.format("%s: %.1f — %s", name, intensity.intensity(), intensity.trigger()));
        }
    }
    if (items.isEmpty()) {
        return ObservationSection.items("Motivational State", "No active drives.", List.of());
    }
    return ObservationSection.items("Motivational State", null, items);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest="CognitiveObservationSectionsTest" -DfailIfNoTests=false`
Expected: all pass

- [ ] **Step 5: Run end-to-end rendering test**

Add one more test verifying the full rendering pipeline:

```java
@Test
void motivationalStateSection_renders_via_affordance_renderer() {
    var drives = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.6, "gaps"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "none"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.0, "none"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "none"));
    var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
    var rendered = new AffordanceRenderer().renderObservation(List.of(section));
    assertThat(rendered).contains("== Motivational State ==");
    assertThat(rendered).contains("- Curiosity: 0.6");
}
```

Run: `mvn --batch-mode test -pl blocks -Dtest="CognitiveObservationSectionsTest" -DfailIfNoTests=false`
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/146/blocks add blocks/src/
git -C /Users/mdproctor/claude/casehub/slots/146/blocks commit -m "feat(#149): add motivationalStateSection to CognitiveObservationSections

Refs casehubio/blocks#149"
```

---

## Batch 2: CDI enablement + lifecycle wiring

### Task 3: Make DriveOrchestrator and DriveComposer CDI-managed

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/drive/DriveOrchestrator.java`
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/drive/DriveComposer.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/drive/DriveOrchestratorTest.java`

**Interfaces:**
- Consumes: `DriveConfig` (from Task 1), all source orchestrators (existing),
  `Instance<MemoryHygieneOrchestrator>` (CDI optional injection)
- Produces: CDI-managed `DriveOrchestrator` with `@Inject` constructor

- [ ] **Step 1: Write the failing test for CDI constructor**

Add to `DriveOrchestratorTest.java`:

```java
@Test
void cdiConstructor_with_hygiene_available() {
    var hygiene = mock(MemoryHygieneOrchestrator.class);
    when(hygiene.knowledgeGaps("agent-1", "tenant-1"))
            .thenReturn(new KnowledgeGapSummary(5, 10, 3));

    @SuppressWarnings("unchecked")
    Instance<MemoryHygieneOrchestrator> hygieneInstance = mock(Instance.class);
    when(hygieneInstance.isResolvable()).thenReturn(true);
    when(hygieneInstance.get()).thenReturn(hygiene);

    var strategy = mock(StrategyLearningOrchestrator.class);
    when(strategy.engagementTrend("agent-1", "tenant-1")).thenReturn(Optional.empty());
    var userModel = mock(UserModelOrchestrator.class);
    when(userModel.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of());
    var mentalModel = mock(MentalModelOrchestrator.class);
    when(mentalModel.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of());

    var orch = new DriveOrchestrator(hygieneInstance, strategy, userModel,
            mentalModel, moodOrchestrator, new DriveComposer(), DriveConfig.defaults());

    var tick = orch.tick("agent-1", "tenant-1", descriptor);
    assertThat(tick).isInstanceOf(DriveTick.Updated.class);
    var profile = ((DriveTick.Updated) tick).current();
    assertThat(profile.drives().get(DriveAxis.CURIOSITY).intensity()).isGreaterThan(0.0);
}

@Test
void cdiConstructor_without_hygiene_returns_zero_curiosity() {
    @SuppressWarnings("unchecked")
    Instance<MemoryHygieneOrchestrator> hygieneInstance = mock(Instance.class);
    when(hygieneInstance.isResolvable()).thenReturn(false);

    var strategy = mock(StrategyLearningOrchestrator.class);
    when(strategy.engagementTrend("agent-1", "tenant-1")).thenReturn(Optional.empty());
    var userModel = mock(UserModelOrchestrator.class);
    when(userModel.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of());
    var mentalModel = mock(MentalModelOrchestrator.class);
    when(mentalModel.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of());

    var orch = new DriveOrchestrator(hygieneInstance, strategy, userModel,
            mentalModel, moodOrchestrator, new DriveComposer(), DriveConfig.defaults());

    var tick = orch.tick("agent-1", "tenant-1", descriptor);
    assertThat(tick).isInstanceOf(DriveTick.Updated.class);
    var profile = ((DriveTick.Updated) tick).current();
    assertThat(profile.drives().get(DriveAxis.CURIOSITY).intensity()).isEqualTo(0.0);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest="DriveOrchestratorTest#cdiConstructor*" -DfailIfNoTests=false`
Expected: compilation failure — constructor doesn't exist

- [ ] **Step 3: Add CDI annotations to DriveComposer**

Add `@ApplicationScoped` to `DriveComposer.java`:

```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DriveComposer {
    // ... existing body unchanged
}
```

- [ ] **Step 4: Add CDI constructor to DriveOrchestrator**

Add imports and new `@Inject` constructor to `DriveOrchestrator.java`.
Add `@ApplicationScoped`. Keep existing constructors unannotated:

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;

@ApplicationScoped
public class DriveOrchestrator {

    // ... existing fields ...

    @Inject
    public DriveOrchestrator(
            Instance<MemoryHygieneOrchestrator> hygieneInstance,
            StrategyLearningOrchestrator strategy,
            UserModelOrchestrator userModel,
            MentalModelOrchestrator mentalModel,
            MoodOrchestrator mood,
            DriveComposer composer,
            DriveConfig config) {
        this(
            hygieneInstance.isResolvable()
                ? new CuriosityDrive(hygieneInstance.get())
                : (agentId, tenantId) -> new DriveIntensity(
                        DriveAxis.CURIOSITY, 0.0, "no hygiene orchestrator"),
            new CompetenceDrive(strategy),
            new AffiliationDrive(userModel,
                    config.affiliationDecayThreshold(),
                    config.affiliationStaleDuration()),
            new AutonomyDrive(mentalModel, config.autonomyConfidenceFloor()),
            mood, composer, config
        );
    }

    // existing public constructor — remove @Inject if accidentally present
    public DriveOrchestrator(CuriosityDrive curiosity, CompetenceDrive competence,
                             AffiliationDrive affiliation, AutonomyDrive autonomy,
                             MoodOrchestrator moodOrchestrator, DriveComposer composer,
                             DriveConfig config) {
        this(curiosity, competence, affiliation, autonomy, moodOrchestrator,
                composer, config, Clock.systemUTC());
    }

    // existing package-private test constructor — unchanged
    DriveOrchestrator(..., Clock clock) { ... }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest="DriveOrchestratorTest" -DfailIfNoTests=false`
Expected: all pass (existing + new)

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/146/blocks add blocks/src/
git -C /Users/mdproctor/claude/casehub/slots/146/blocks commit -m "feat(#149): make DriveOrchestrator and DriveComposer CDI-managed

Refs casehubio/blocks#149"
```

### Task 4: Wire DriveOrchestrator.tick() into InnerLifeOrchestrator

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/InnerLifeOrchestrator.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/InnerLifeOrchestratorTest.java`

**Interfaces:**
- Consumes: `DriveOrchestrator` (CDI-managed, from Task 3)
- Produces: `InnerLifeOrchestrator.doTick()` calls `DriveOrchestrator.tick()`
  before existing logic

- [ ] **Step 1: Write the failing test**

Add to `InnerLifeOrchestratorTest.java`:

```java
private DriveOrchestrator driveOrchestrator;

// Update setUp to add:
driveOrchestrator = mock(DriveOrchestrator.class);

// Update makeOrchestrator to pass driveOrchestrator:
@SuppressWarnings("unchecked")
private InnerLifeOrchestrator makeOrchestrator(CivilityConstraint... constraints) {
    Instance<CivilityConstraint> cdi = mock(Instance.class);
    when(cdi.stream()).thenReturn(java.util.stream.Stream.of(constraints));
    return new InnerLifeOrchestrator(
            reflectionOrchestrator, agentProvider, cdi,
            InnerLifeConfig.defaults(), driveOrchestrator);
}

@Test
void tick_calls_drive_orchestrator_tick() {
    var orch = makeOrchestrator(ctx -> new CivilityCheck.Permitted());
    orch.observe(event("something happened"), descriptor);

    orch.tick(descriptor, "context");

    verify(driveOrchestrator).tick("agent-1", "tenant-1", descriptor);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest="InnerLifeOrchestratorTest#tick_calls_drive_orchestrator_tick" -DfailIfNoTests=false`
Expected: compilation failure — constructor doesn't accept DriveOrchestrator

- [ ] **Step 3: Add DriveOrchestrator to InnerLifeOrchestrator**

Modify `InnerLifeOrchestrator.java`:

1. Add field: `private final DriveOrchestrator driveOrchestrator;`

2. Update the `@Inject` constructor to accept `DriveOrchestrator`:

```java
@Inject
public InnerLifeOrchestrator(
        final ReflectionOrchestrator reflectionOrchestrator,
        final AgentProvider agentProvider,
        final Instance<CivilityConstraint> civilityConstraints,
        final InnerLifeConfig config,
        final DriveOrchestrator driveOrchestrator) {
    this.reflectionOrchestrator = reflectionOrchestrator;
    this.agentProvider = agentProvider;
    this.civilityConstraints = civilityConstraints.stream().toList();
    this.config = config;
    this.driveOrchestrator = driveOrchestrator;
}
```

3. Add drive tick call at the start of `doTick()` (line ~95, before
   Step 0):

```java
private InnerLifeTick doTick(final AgentDescriptor descriptor,
                              final String channelContext,
                              final String agentKey) {
    // Drive tick — compute motivational state before inner life logic
    driveOrchestrator.tick(descriptor.agentId(), descriptor.tenancyId(), descriptor);

    var state = agentStates.computeIfAbsent(agentKey, k -> new AgentState());
    // ... rest of existing doTick unchanged ...
}
```

- [ ] **Step 4: Fix existing tests**

Update `makeOrchestrator()` helper in `InnerLifeOrchestratorTest.java`
to pass the mocked `driveOrchestrator`. Add `driveOrchestrator` field
initialization in `setUp()`.

- [ ] **Step 5: Run all InnerLifeOrchestrator tests**

Run: `mvn --batch-mode test -pl blocks -Dtest="io.casehub.blocks.agentic.social.InnerLifeOrchestratorTest" -DfailIfNoTests=false`
Expected: all pass

- [ ] **Step 6: Run full test suite to verify no regressions**

Run: `mvn --batch-mode test -pl blocks`
Expected: all pass

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/146/blocks add blocks/src/
git -C /Users/mdproctor/claude/casehub/slots/146/blocks commit -m "feat(#149): wire DriveOrchestrator.tick() into InnerLifeOrchestrator lifecycle

Refs casehubio/blocks#149"
```

---

## Batch 3: Documentation update

### Task 5: Update contributor guide and CLAUDE.md

**Files:**
- Modify: `docs/guides/contributor-guide.md`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: All changes from Tasks 1-4

- [ ] **Step 1: Update contributor guide**

Add to the "Drive Architecture" section in `docs/guides/contributor-guide.md`:

- Document the CDI constructor with `Instance<MemoryHygieneOrchestrator>`
- Document the lifecycle wiring (InnerLifeOrchestrator calls DriveOrchestrator.tick())
- Note the intentional short-term coupling and Layer 2 extraction plan

- [ ] **Step 2: Update CLAUDE.md key directories**

Update the `DriveOrchestrator` entry to reflect CDI management. Update
the `CognitiveObservationSections` entry if it exists, or add mention of
`motivationalStateSection()`.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/146/blocks add docs/ CLAUDE.md
git -C /Users/mdproctor/claude/casehub/slots/146/blocks commit -m "docs(#149): update contributor guide and CLAUDE.md for drive prompt enrichment

Refs casehubio/blocks#149"
```

---

## References

- [2026-08-22-drive-prompt-enrichment-design.md] — design spec this plan implements
- [CognitiveObservationSections.java] — existing factory method pattern
- [DriveOrchestrator.java] — tick lifecycle, currentDrives() accessor
- [DriveConfig.java] — record to extend with drive-source params
- [InnerLifeOrchestrator.java:84] — tick() entry point for lifecycle wiring
- [InnerLifeOrchestratorTest.java:48] — makeOrchestrator() helper pattern
- [DriveOrchestratorTest.java:55] — createOrchestrator() helper pattern
- [GE-20260529-ab148d] — multiple CDI constructors require exactly one @Inject
- [GitHub #149] — focal issue
- [GitHub #129] — drive architecture foundation
