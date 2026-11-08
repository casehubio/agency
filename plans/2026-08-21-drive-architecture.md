# Drive Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #129 — Drive Architecture — intrinsic motivation from social cognition outputs
**Issue group:** #129

**Goal:** Add an intrinsic motivation system that reads existing social cognition orchestrator outputs and synthesises motivational signals along four axes (curiosity, competence, affiliation, autonomy).

**Architecture:** DriveOrchestrator pulls from four DriveSource implementations (one per axis), each reading a specific source orchestrator's public API. DriveComposer applies personality and mood modulation, then weighted composition into a DriveProfile. The orchestrator follows a compositor pattern — tick() without record(), since drives consume derived state, not raw signals.

**Tech Stack:** Java 21, JUnit 5, Mockito, blocks' existing orchestrator infrastructure. Dependencies: eidos-api (AgentDescriptor, AgentDisposition, DispositionAxis), neocortex-memory-api (MoodState).

## Global Constraints

- Package: `io.casehub.blocks.agentic.social.drive`
- All records use compact constructor validation
- All doubles use `[0.0, 1.0]` range unless stated otherwise
- All new types are `public` (part of blocks' public API)
- Tests: plain JUnit 5 + Mockito, no Quarkus runtime
- Commits reference `Refs #129`

---

## Batch 1: Foundation — types + composer

### Task 1: Foundation types

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/DriveAxis.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/DriveIntensity.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/DriveSource.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/DriveProfile.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/DriveTick.java`
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/DriveConfig.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/drive/DriveTypesTest.java`

**Interfaces:**
- Produces: `DriveAxis`, `DriveIntensity`, `DriveSource`, `DriveProfile`, `DriveTick`, `DriveConfig` — used by all subsequent tasks

- [ ] **Step 1: Write validation tests for DriveIntensity**

```java
package io.casehub.blocks.agentic.social.drive;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DriveTypesTest {

    @Test
    void driveIntensity_validRange() {
        var intensity = new DriveIntensity(DriveAxis.CURIOSITY, 0.75, "3 knowledge gaps");
        assertThat(intensity.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(intensity.intensity()).isEqualTo(0.75);
        assertThat(intensity.trigger()).isEqualTo("3 knowledge gaps");
    }

    @Test
    void driveIntensity_rejectsNegative() {
        assertThatThrownBy(() -> new DriveIntensity(DriveAxis.CURIOSITY, -0.1, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void driveIntensity_rejectsAboveOne() {
        assertThatThrownBy(() -> new DriveIntensity(DriveAxis.CURIOSITY, 1.1, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void driveIntensity_rejectsNullAxis() {
        assertThatThrownBy(() -> new DriveIntensity(null, 0.5, "x"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void driveIntensity_rejectsNullTrigger() {
        assertThatThrownBy(() -> new DriveIntensity(DriveAxis.CURIOSITY, 0.5, null))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=DriveTypesTest -DfailIfNoTests=false`
Expected: FAIL — DriveAxis and DriveIntensity do not exist

- [ ] **Step 3: Create DriveAxis enum**

```java
package io.casehub.blocks.agentic.social.drive;

public enum DriveAxis {
    CURIOSITY,
    COMPETENCE,
    AFFILIATION,
    AUTONOMY
}
```

- [ ] **Step 4: Create DriveIntensity record**

```java
package io.casehub.blocks.agentic.social.drive;

import java.util.Objects;

public record DriveIntensity(DriveAxis axis, double intensity, String trigger) {
    public DriveIntensity {
        Objects.requireNonNull(axis, "axis required");
        Objects.requireNonNull(trigger, "trigger required");
        if (intensity < 0.0 || intensity > 1.0) {
            throw new IllegalArgumentException("intensity must be in [0.0, 1.0], got " + intensity);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=DriveTypesTest`
Expected: PASS (5 tests)

- [ ] **Step 6: Add DriveProfile validation tests**

Append to `DriveTypesTest`:

```java
@Test
void driveProfile_validConstruction() {
    var drives = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.8, "gaps"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.3, "ok"));
    var profile = new DriveProfile("agent-1", "tenant-1", drives, 0.55,
            DriveAxis.CURIOSITY, Instant.now());
    assertThat(profile.drives()).hasSize(2);
    assertThat(profile.dominantDrive()).isEqualTo(DriveAxis.CURIOSITY);
}

@Test
void driveProfile_defensiveCopy() {
    var mutable = new java.util.HashMap<DriveAxis, DriveIntensity>();
    mutable.put(DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "x"));
    var profile = new DriveProfile("a", "t", mutable, 0.5, DriveAxis.CURIOSITY, Instant.now());
    mutable.put(DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.1, "y"));
    assertThat(profile.drives()).hasSize(1);
}

@Test
void driveProfile_rejectsInvalidComposite() {
    assertThatThrownBy(() -> new DriveProfile("a", "t", Map.of(), 1.5,
            DriveAxis.CURIOSITY, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 7: Create DriveProfile record**

```java
package io.casehub.blocks.agentic.social.drive;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record DriveProfile(
        String agentId,
        String tenantId,
        Map<DriveAxis, DriveIntensity> drives,
        double compositeMotivation,
        DriveAxis dominantDrive,
        Instant evaluatedAt) {
    public DriveProfile {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(drives, "drives required");
        Objects.requireNonNull(dominantDrive, "dominantDrive required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt required");
        if (compositeMotivation < 0.0 || compositeMotivation > 1.0) {
            throw new IllegalArgumentException(
                    "compositeMotivation must be in [0.0, 1.0], got " + compositeMotivation);
        }
        drives = Map.copyOf(drives);
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=DriveTypesTest`
Expected: PASS (8 tests)

- [ ] **Step 9: Create remaining types — DriveSource, DriveTick, DriveConfig**

DriveSource:
```java
package io.casehub.blocks.agentic.social.drive;

@FunctionalInterface
public interface DriveSource {
    DriveIntensity evaluate(String agentId, String tenantId);
}
```

DriveTick:
```java
package io.casehub.blocks.agentic.social.drive;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Objects;

public sealed interface DriveTick {
    record NoChange(@Nullable String reason) implements DriveTick {}
    record Updated(DriveProfile previous, DriveProfile current,
                   List<DriveAxis> changed) implements DriveTick {
        public Updated {
            Objects.requireNonNull(previous, "previous required");
            Objects.requireNonNull(current, "current required");
            Objects.requireNonNull(changed, "changed required");
            changed = List.copyOf(changed);
        }
    }
}
```

DriveConfig:
```java
package io.casehub.blocks.agentic.social.drive;

import java.util.Map;
import java.util.Objects;

public record DriveConfig(
        Map<DriveAxis, Double> axisWeights,
        double changeThreshold,
        double moodPleasureModulation,
        double moodArousalModulation,
        double personalityModulationStrength,
        double maxIntensity,
        double minIntensity) {
    public DriveConfig {
        Objects.requireNonNull(axisWeights, "axisWeights required");
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
                0.05, 0.3, 0.2, 0.25, 1.0, 0.0);
    }
}
```

- [ ] **Step 10: Add DriveTick and DriveConfig tests, run all**

Append to `DriveTypesTest`:

```java
@Test
void driveTick_noChange() {
    var tick = new DriveTick.NoChange("no signals");
    assertThat(tick.reason()).isEqualTo("no signals");
}

@Test
void driveTick_updated() {
    var now = Instant.now();
    var prev = new DriveProfile("a", "t", Map.of(), 0.0, DriveAxis.CURIOSITY, now);
    var curr = new DriveProfile("a", "t", Map.of(), 0.5, DriveAxis.CURIOSITY, now);
    var tick = new DriveTick.Updated(prev, curr, List.of(DriveAxis.CURIOSITY));
    assertThat(tick.changed()).containsExactly(DriveAxis.CURIOSITY);
}

@Test
void driveConfig_defaults() {
    var config = DriveConfig.defaults();
    assertThat(config.axisWeights()).hasSize(4);
    assertThat(config.changeThreshold()).isEqualTo(0.05);
}

@Test
void driveConfig_rejectsInvalidRange() {
    assertThatThrownBy(() -> new DriveConfig(Map.of(), 0.05, 0.3, 0.2, 0.25, 0.5, 0.8))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxIntensity must be >= minIntensity");
}
```

Run: `mvn --batch-mode test -pl . -Dtest=DriveTypesTest`
Expected: PASS (12 tests)

- [ ] **Step 11: Commit**

```bash
git add src/main/java/io/casehub/blocks/agentic/social/drive/ src/test/java/io/casehub/blocks/agentic/social/drive/
git commit -m "feat(#129): add drive architecture foundation types — DriveAxis, DriveIntensity, DriveSource, DriveProfile, DriveTick, DriveConfig Refs #129"
```

### Task 2: DriveComposer — modulation and composition

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/DriveComposer.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/drive/DriveComposerTest.java`

**Interfaces:**
- Consumes: `DriveAxis`, `DriveIntensity`, `DriveProfile`, `DriveConfig` (from Task 1)
- Consumes: `AgentDisposition` (`io.casehub.eidos.api`), `DispositionAxis` (`io.casehub.eidos.api`), `DispositionValue` (`io.casehub.eidos.api`), `MoodState` (`io.casehub.neocortex.memory.mood`)
- Produces: `DriveComposer.compose(Map<DriveAxis, DriveIntensity>, AgentDisposition, MoodState, DriveConfig, String, String, Instant) → DriveProfile`

- [ ] **Step 1: Write test for composition without modulation**

```java
package io.casehub.blocks.agentic.social.drive;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DriveComposerTest {

    private final DriveComposer composer = new DriveComposer();
    private final Instant now = Instant.parse("2026-08-21T12:00:00Z");

    @Test
    void compose_noModulation_equalWeights() {
        var raw = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.8, "gaps"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.4, "ok"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.2, "stable"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.6, "pressure"));

        var profile = composer.compose(raw, null, null, DriveConfig.defaults(),
                "agent-1", "tenant-1", now);

        assertThat(profile.agentId()).isEqualTo("agent-1");
        assertThat(profile.dominantDrive()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(profile.compositeMotivation()).isCloseTo(0.5, within(0.01));
        assertThat(profile.drives()).hasSize(4);
        assertThat(profile.evaluatedAt()).isEqualTo(now);
    }

    @Test
    void compose_noModulation_unequalWeights() {
        var config = new DriveConfig(
                Map.of(DriveAxis.CURIOSITY, 2.0, DriveAxis.COMPETENCE, 1.0,
                       DriveAxis.AFFILIATION, 1.0, DriveAxis.AUTONOMY, 1.0),
                0.05, 0.3, 0.2, 0.25, 1.0, 0.0);
        var raw = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 1.0, "x"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "x"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.0, "x"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "x"));

        var profile = composer.compose(raw, null, null, config,
                "a", "t", now);

        // (2.0*1.0 + 1.0*0.0 + 1.0*0.0 + 1.0*0.0) / (2.0+1.0+1.0+1.0) = 2.0/5.0 = 0.4
        assertThat(profile.compositeMotivation()).isCloseTo(0.4, within(0.01));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=DriveComposerTest`
Expected: FAIL — DriveComposer does not exist

- [ ] **Step 3: Implement DriveComposer — compose without modulation**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.eidos.api.AgentDisposition;
import io.casehub.neocortex.memory.mood.MoodState;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

public class DriveComposer {

    public DriveProfile compose(Map<DriveAxis, DriveIntensity> rawDrives,
                                @Nullable AgentDisposition disposition,
                                @Nullable MoodState mood,
                                DriveConfig config,
                                String agentId, String tenantId, Instant now) {
        var modulated = new EnumMap<DriveAxis, DriveIntensity>(DriveAxis.class);

        for (var entry : rawDrives.entrySet()) {
            var axis = entry.getKey();
            var raw = entry.getValue();
            double intensity = raw.intensity();

            if (mood != null) {
                intensity = applyMoodModulation(intensity, axis, mood, config);
            }
            if (disposition != null) {
                intensity = applyPersonalityModulation(intensity, axis, disposition, config);
            }

            intensity = Math.clamp(intensity, config.minIntensity(), config.maxIntensity());
            modulated.put(axis, new DriveIntensity(axis, intensity, raw.trigger()));
        }

        double composite = computeComposite(modulated, config);
        DriveAxis dominant = findDominant(modulated);

        return new DriveProfile(agentId, tenantId, modulated, composite, dominant, now);
    }

    private double applyMoodModulation(double intensity, DriveAxis axis,
                                       MoodState mood, DriveConfig config) {
        double pleasureMod = mood.pleasure() * config.moodPleasureModulation();
        double arousalMod = mood.arousal() * config.moodArousalModulation();

        intensity += arousalMod;

        if (axis == DriveAxis.AFFILIATION || axis == DriveAxis.COMPETENCE) {
            intensity += pleasureMod;
        } else {
            intensity += pleasureMod * 0.5;
        }

        if (axis == DriveAxis.AUTONOMY && mood.dominance() < 0) {
            intensity += Math.abs(mood.dominance()) * config.moodPleasureModulation();
        }

        return intensity;
    }

    private double applyPersonalityModulation(double intensity, DriveAxis axis,
                                              AgentDisposition disposition,
                                              DriveConfig config) {
        var matchedAxis = switch (axis) {
            case CURIOSITY -> io.casehub.eidos.api.DispositionAxis.RISK_APPETITE;
            case COMPETENCE -> io.casehub.eidos.api.DispositionAxis.RULE_FOLLOWING;
            case AFFILIATION -> io.casehub.eidos.api.DispositionAxis.SOCIAL_ORIENTATION;
            case AUTONOMY -> io.casehub.eidos.api.DispositionAxis.AUTONOMY;
        };

        var values = disposition.get(matchedAxis);
        if (values.isEmpty()) return intensity;

        double weight = values.getFirst().weight();
        return intensity + (weight * config.personalityModulationStrength());
    }

    private double computeComposite(Map<DriveAxis, DriveIntensity> drives, DriveConfig config) {
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        for (var entry : drives.entrySet()) {
            double w = config.axisWeights().getOrDefault(entry.getKey(), 1.0);
            weightedSum += w * entry.getValue().intensity();
            totalWeight += w;
        }
        return totalWeight > 0 ? Math.clamp(weightedSum / totalWeight, 0.0, 1.0) : 0.0;
    }

    private DriveAxis findDominant(Map<DriveAxis, DriveIntensity> drives) {
        return drives.entrySet().stream()
                .max(Map.Entry.comparingByValue(
                        (a, b) -> Double.compare(a.intensity(), b.intensity())))
                .map(Map.Entry::getKey)
                .orElse(DriveAxis.CURIOSITY);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=DriveComposerTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Add mood modulation tests**

Append to `DriveComposerTest`:

```java
@Test
void compose_moodModulation_highArousalAmplifies() {
    var raw = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "x"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "x"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "x"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "x"));
    var mood = new MoodState("a", "t", 0.0, 0.8, 0.0, "excited", null, Map.of());

    var profile = composer.compose(raw, null, mood, DriveConfig.defaults(), "a", "t", now);

    // arousal 0.8 * arousalMod 0.2 = +0.16 to all drives
    for (var di : profile.drives().values()) {
        assertThat(di.intensity()).isGreaterThan(0.5);
    }
}

@Test
void compose_moodModulation_lowDominanceAmplifiesAutonomy() {
    var raw = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "x"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "x"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "x"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "x"));
    var mood = new MoodState("a", "t", 0.0, 0.0, -0.8, "controlled", null, Map.of());

    var profile = composer.compose(raw, null, mood, DriveConfig.defaults(), "a", "t", now);

    assertThat(profile.drives().get(DriveAxis.AUTONOMY).intensity())
            .isGreaterThan(profile.drives().get(DriveAxis.CURIOSITY).intensity());
}

@Test
void compose_moodModulation_negativePleasureDampens() {
    var raw = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "x"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "x"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "x"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "x"));
    var mood = new MoodState("a", "t", -0.8, 0.0, 0.0, "sad", null, Map.of());

    var profile = composer.compose(raw, null, mood, DriveConfig.defaults(), "a", "t", now);

    for (var di : profile.drives().values()) {
        assertThat(di.intensity()).isLessThan(0.5);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=DriveComposerTest`
Expected: PASS (5 tests)

- [ ] **Step 7: Add personality modulation tests**

Append to `DriveComposerTest`:

```java
@Test
void compose_personalityModulation_socialOrientAmplifiesAffiliation() {
    var raw = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "x"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "x"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "x"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "x"));
    var disposition = AgentDisposition.builder()
            .socialOrient(DispositionValue.of("collaborative"))
            .build();

    var profile = composer.compose(raw, disposition, null, DriveConfig.defaults(),
            "a", "t", now);

    assertThat(profile.drives().get(DriveAxis.AFFILIATION).intensity())
            .isGreaterThan(profile.drives().get(DriveAxis.COMPETENCE).intensity());
}

@Test
void compose_personalityModulation_noDispositionValues_noEffect() {
    var raw = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "x"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "x"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "x"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "x"));
    var disposition = AgentDisposition.builder().build();

    var profile = composer.compose(raw, disposition, null, DriveConfig.defaults(),
            "a", "t", now);

    for (var di : profile.drives().values()) {
        assertThat(di.intensity()).isEqualTo(0.5);
    }
}

@Test
void compose_intensityClamped() {
    var raw = Map.of(
            DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.95, "x"),
            DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "x"),
            DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "x"),
            DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "x"));
    var mood = new MoodState("a", "t", 0.9, 0.9, 0.0, "euphoric", null, Map.of());
    var disposition = AgentDisposition.builder()
            .riskAppetite(DispositionValue.of("aggressive"))
            .build();

    var profile = composer.compose(raw, disposition, mood, DriveConfig.defaults(),
            "a", "t", now);

    assertThat(profile.drives().get(DriveAxis.CURIOSITY).intensity()).isLessThanOrEqualTo(1.0);
}
```

- [ ] **Step 8: Run all tests**

Run: `mvn --batch-mode test -pl . -Dtest=DriveComposerTest`
Expected: PASS (8 tests)

- [ ] **Step 9: Commit**

```bash
git add src/main/java/io/casehub/blocks/agentic/social/drive/DriveComposer.java src/test/java/io/casehub/blocks/agentic/social/drive/DriveComposerTest.java
git commit -m "feat(#129): add DriveComposer — personality and mood modulation with weighted composition Refs #129"
```

## Batch 2: Drive sources + upstream API additions

### Task 3: CuriosityDrive + MemoryHygiene knowledge gap API

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/KnowledgeGapSummary.java`
- Modify: `src/main/java/io/casehub/blocks/memory/MemoryHygieneOrchestrator.java` — add `knowledgeGaps()` method and internal state tracking
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/CuriosityDrive.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/drive/CuriosityDriveTest.java`

**Interfaces:**
- Consumes: `DriveSource`, `DriveIntensity`, `DriveAxis` (from Task 1)
- Consumes: `MemoryHygieneOrchestrator.knowledgeGaps()` (added in this task)
- Produces: `KnowledgeGapSummary` record, `CuriosityDrive` implements `DriveSource`

- [ ] **Step 1: Write KnowledgeGapSummary record**

```java
package io.casehub.blocks.agentic.social.drive;

public record KnowledgeGapSummary(
        int lowRetentionCount,
        int consolidationGroups,
        int totalScored,
        long millisSinceLastTick) {
    public KnowledgeGapSummary {
        if (lowRetentionCount < 0) throw new IllegalArgumentException("lowRetentionCount must be >= 0");
        if (consolidationGroups < 0) throw new IllegalArgumentException("consolidationGroups must be >= 0");
        if (totalScored < 0) throw new IllegalArgumentException("totalScored must be >= 0");
    }

    public static KnowledgeGapSummary empty() {
        return new KnowledgeGapSummary(0, 0, 0, -1);
    }
}
```

- [ ] **Step 2: Write CuriosityDrive test**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CuriosityDriveTest {

    @Test
    void evaluate_noGaps_zeroIntensity() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        when(orchestrator.knowledgeGaps("agent-1", "tenant-1"))
                .thenReturn(KnowledgeGapSummary.empty());

        var drive = new CuriosityDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_lowRetentionMemories_positiveIntensity() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        when(orchestrator.knowledgeGaps("agent-1", "tenant-1"))
                .thenReturn(new KnowledgeGapSummary(5, 3, 20, 60000));

        var drive = new CuriosityDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(result.intensity()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void evaluate_manyGaps_highIntensity() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        when(orchestrator.knowledgeGaps("agent-1", "tenant-1"))
                .thenReturn(new KnowledgeGapSummary(15, 8, 30, 60000));

        var drive = new CuriosityDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isGreaterThan(0.5);
    }

    @Test
    void evaluate_intensityNeverExceedsOne() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        when(orchestrator.knowledgeGaps("agent-1", "tenant-1"))
                .thenReturn(new KnowledgeGapSummary(100, 50, 200, 60000));

        var drive = new CuriosityDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isLessThanOrEqualTo(1.0);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=CuriosityDriveTest -DfailIfNoTests=false`
Expected: FAIL — CuriosityDrive and knowledgeGaps() do not exist

- [ ] **Step 4: Add `knowledgeGaps()` to MemoryHygieneOrchestrator**

Use `ide_insert_member` to add:
- A `ConcurrentHashMap<String, GapState>` field for tracking per-agent gap data
- The `knowledgeGaps(String agentId, String tenantId)` public method
- Update the `doTick()` method to accumulate gap data from `HygieneTick.Completed` scores

The gap state tracks: count of `RetentionScore` entries below a retention threshold (0.3), number of consolidation groups (from `consolidated` count in `HygieneTick.Completed`), and total scored.

```java
// Field:
private final ConcurrentHashMap<String, GapState> gapStates = new ConcurrentHashMap<>();

// Inner class:
static final class GapState {
    int lowRetentionCount;
    int consolidationGroups;
    int totalScored;
    Instant lastTick;
    GapState() { this.lastTick = Instant.EPOCH; }
}

// Public method:
public KnowledgeGapSummary knowledgeGaps(String agentId, String tenantId) {
    var key = agentId + ":" + tenantId;
    var state = gapStates.get(key);
    if (state == null) return KnowledgeGapSummary.empty();
    long millis = state.lastTick == Instant.EPOCH ? -1
            : Duration.between(state.lastTick, Instant.now()).toMillis();
    return new KnowledgeGapSummary(state.lowRetentionCount,
            state.consolidationGroups, state.totalScored, millis);
}
```

Update the existing `doTick()` to populate `gapStates` when a `Completed` result is produced:

```java
// After HygieneTick.Completed is constructed, before return:
var gapState = gapStates.computeIfAbsent(agentId + ":" + tenantId, k -> new GapState());
gapState.lowRetentionCount = (int) scores.stream().filter(s -> s.composite() < 0.3).count();
gapState.consolidationGroups = consolidated;
gapState.totalScored = totalScored;
gapState.lastTick = Instant.now();
```

- [ ] **Step 5: Implement CuriosityDrive**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.memory.MemoryHygieneOrchestrator;

public class CuriosityDrive implements DriveSource {

    private final MemoryHygieneOrchestrator hygieneOrchestrator;

    public CuriosityDrive(MemoryHygieneOrchestrator hygieneOrchestrator) {
        this.hygieneOrchestrator = hygieneOrchestrator;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var gaps = hygieneOrchestrator.knowledgeGaps(agentId, tenantId);

        if (gaps.totalScored() == 0) {
            return new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "no hygiene data");
        }

        double retentionRatio = (double) gaps.lowRetentionCount() / gaps.totalScored();
        double groupDiversity = Math.min(gaps.consolidationGroups() / 10.0, 1.0);
        double intensity = Math.clamp((retentionRatio + groupDiversity) / 2.0, 0.0, 1.0);

        String trigger = gaps.lowRetentionCount() + " low-retention memories across "
                + gaps.consolidationGroups() + " groups";
        return new DriveIntensity(DriveAxis.CURIOSITY, intensity, trigger);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=CuriosityDriveTest`
Expected: PASS (4 tests)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/casehub/blocks/agentic/social/drive/KnowledgeGapSummary.java src/main/java/io/casehub/blocks/agentic/social/drive/CuriosityDrive.java src/main/java/io/casehub/blocks/memory/MemoryHygieneOrchestrator.java src/test/java/io/casehub/blocks/agentic/social/drive/CuriosityDriveTest.java
git commit -m "feat(#129): add CuriosityDrive — knowledge gap detection from MemoryHygiene Refs #129"
```

### Task 4: CompetenceDrive + StrategyLearning engagement trend API

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/EngagementTrend.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/social/StrategyLearningOrchestrator.java` — add `engagementTrend()` method
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/CompetenceDrive.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/drive/CompetenceDriveTest.java`

**Interfaces:**
- Consumes: `DriveSource`, `DriveIntensity`, `DriveAxis` (from Task 1)
- Consumes: `StrategyLearningOrchestrator.engagementTrend()` (added in this task)
- Produces: `EngagementTrend` record, `CompetenceDrive` implements `DriveSource`

- [ ] **Step 1: Write EngagementTrend record**

```java
package io.casehub.blocks.agentic.social.drive;

import java.util.Map;
import java.util.Objects;

public record EngagementTrend(
        Map<String, TrendDirection> dimensionTrends,
        double responseRateTrajectory,
        int evidenceWindow) {
    public EngagementTrend {
        Objects.requireNonNull(dimensionTrends, "dimensionTrends required");
        dimensionTrends = Map.copyOf(dimensionTrends);
        if (evidenceWindow < 0) throw new IllegalArgumentException("evidenceWindow must be >= 0");
    }

    public enum TrendDirection { IMPROVING, STABLE, DECLINING }
}
```

- [ ] **Step 2: Write CompetenceDrive test**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompetenceDriveTest {

    @Test
    void evaluate_noTrend_zeroIntensity() {
        var orchestrator = mock(StrategyLearningOrchestrator.class);
        when(orchestrator.engagementTrend("agent-1", "tenant-1"))
                .thenReturn(Optional.empty());

        var drive = new CompetenceDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.axis()).isEqualTo(DriveAxis.COMPETENCE);
        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_allDeclining_highIntensity() {
        var orchestrator = mock(StrategyLearningOrchestrator.class);
        when(orchestrator.engagementTrend("agent-1", "tenant-1"))
                .thenReturn(Optional.of(new EngagementTrend(
                        Map.of("clarity", EngagementTrend.TrendDirection.DECLINING,
                               "engagement", EngagementTrend.TrendDirection.DECLINING),
                        -0.3, 10)));

        var drive = new CompetenceDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isGreaterThan(0.5);
    }

    @Test
    void evaluate_allImproving_lowIntensity() {
        var orchestrator = mock(StrategyLearningOrchestrator.class);
        when(orchestrator.engagementTrend("agent-1", "tenant-1"))
                .thenReturn(Optional.of(new EngagementTrend(
                        Map.of("clarity", EngagementTrend.TrendDirection.IMPROVING,
                               "engagement", EngagementTrend.TrendDirection.IMPROVING),
                        0.5, 10)));

        var drive = new CompetenceDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isLessThan(0.3);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=CompetenceDriveTest -DfailIfNoTests=false`
Expected: FAIL — CompetenceDrive and engagementTrend() do not exist

- [ ] **Step 4: Add `engagementTrend()` to StrategyLearningOrchestrator**

Use `ide_insert_member` to add the public method. The trend is derived from the orchestrator's internal `AgentLearningState` — comparing current dimension values against recent history using the state's counters (totalSignals, totalResponded, sentimentSum).

```java
public Optional<EngagementTrend> engagementTrend(String agentId, String tenantId) {
    var key = stateKey(agentId, tenantId);
    var state = states.get(key);
    if (state == null || state.currentProfile == null) return Optional.empty();

    var profile = state.currentProfile;
    var trends = new java.util.HashMap<String, EngagementTrend.TrendDirection>();
    for (var entry : profile.dimensions().entrySet()) {
        double val = entry.getValue();
        trends.put(entry.getKey(), val > 0.6 ? EngagementTrend.TrendDirection.IMPROVING
                : val < 0.4 ? EngagementTrend.TrendDirection.DECLINING
                : EngagementTrend.TrendDirection.STABLE);
    }
    double responseRate = state.totalSignals > 0
            ? (double) state.totalResponded / state.totalSignals : 0.0;
    return Optional.of(new EngagementTrend(trends, responseRate, state.totalSignals));
}
```

- [ ] **Step 5: Implement CompetenceDrive**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;

public class CompetenceDrive implements DriveSource {

    private final StrategyLearningOrchestrator strategyOrchestrator;

    public CompetenceDrive(StrategyLearningOrchestrator strategyOrchestrator) {
        this.strategyOrchestrator = strategyOrchestrator;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var trendOpt = strategyOrchestrator.engagementTrend(agentId, tenantId);
        if (trendOpt.isEmpty()) {
            return new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "no engagement data");
        }

        var trend = trendOpt.get();
        if (trend.dimensionTrends().isEmpty()) {
            return new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "no dimensions");
        }

        long declining = trend.dimensionTrends().values().stream()
                .filter(d -> d == EngagementTrend.TrendDirection.DECLINING).count();
        long improving = trend.dimensionTrends().values().stream()
                .filter(d -> d == EngagementTrend.TrendDirection.IMPROVING).count();
        int total = trend.dimensionTrends().size();

        double declineRatio = (double) declining / total;
        double improvementDiscount = (double) improving / total * 0.5;
        double intensity = Math.clamp(declineRatio - improvementDiscount, 0.0, 1.0);

        String trigger = declining + " declining, " + improving + " improving of " + total + " dimensions";
        return new DriveIntensity(DriveAxis.COMPETENCE, intensity, trigger);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=CompetenceDriveTest`
Expected: PASS (3 tests)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/casehub/blocks/agentic/social/drive/EngagementTrend.java src/main/java/io/casehub/blocks/agentic/social/drive/CompetenceDrive.java src/main/java/io/casehub/blocks/agentic/social/StrategyLearningOrchestrator.java src/test/java/io/casehub/blocks/agentic/social/drive/CompetenceDriveTest.java
git commit -m "feat(#129): add CompetenceDrive — engagement trend detection from StrategyLearning Refs #129"
```

### Task 5: AffiliationDrive + UserModel cross-subject API

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/social/UserModelOrchestrator.java` — add `activeProfiles()`
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/AffiliationDrive.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/drive/AffiliationDriveTest.java`

**Interfaces:**
- Consumes: `DriveSource`, `DriveIntensity`, `DriveAxis` (from Task 1)
- Consumes: `UserModelOrchestrator.activeProfiles(agentId, tenantId) → List<UserProfile>` (added in this task)
- Produces: `AffiliationDrive` implements `DriveSource`

- [ ] **Step 1: Write AffiliationDrive test**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.UserProfile;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AffiliationDriveTest {

    private final Instant now = Instant.parse("2026-08-21T12:00:00Z");

    private UserProfile profile(String subjectId, double familiarity, Instant lastInteraction) {
        return new UserProfile("agent-1", subjectId, "tenant-1", "established",
                familiarity, 10, 5, 2, 3, lastInteraction, now.minus(Duration.ofDays(30)),
                null, null, null, null, null, Map.of());
    }

    @Test
    void evaluate_noProfiles_zeroIntensity() {
        var orchestrator = mock(UserModelOrchestrator.class);
        when(orchestrator.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of());

        var drive = new AffiliationDrive(orchestrator, 0.4, Duration.ofDays(7));
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.axis()).isEqualTo(DriveAxis.AFFILIATION);
        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_decayingRelationships_positiveIntensity() {
        var orchestrator = mock(UserModelOrchestrator.class);
        when(orchestrator.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of(
                profile("user-1", 0.3, now.minus(Duration.ofDays(10))),
                profile("user-2", 0.2, now.minus(Duration.ofDays(14)))));

        var drive = new AffiliationDrive(orchestrator, 0.4, Duration.ofDays(7));
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isGreaterThan(0.0);
    }

    @Test
    void evaluate_healthyRelationships_lowIntensity() {
        var orchestrator = mock(UserModelOrchestrator.class);
        when(orchestrator.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of(
                profile("user-1", 0.9, now.minus(Duration.ofHours(2))),
                profile("user-2", 0.8, now.minus(Duration.ofHours(6)))));

        var drive = new AffiliationDrive(orchestrator, 0.4, Duration.ofDays(7));
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isEqualTo(0.0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=AffiliationDriveTest -DfailIfNoTests=false`
Expected: FAIL — AffiliationDrive and activeProfiles() do not exist

- [ ] **Step 3: Add `activeProfiles()` to UserModelOrchestrator**

Use `ide_insert_member` to add after `currentProfile()`:

```java
public List<UserProfile> activeProfiles(String agentId, String tenantId) {
    var prefix = agentId + ":";
    var suffix = ":" + tenantId;
    return states.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix) && e.getKey().endsWith(suffix))
            .map(e -> e.getValue().currentProfile)
            .filter(java.util.Objects::nonNull)
            .toList();
}
```

- [ ] **Step 4: Implement AffiliationDrive**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.UserModelOrchestrator;

import java.time.Duration;
import java.time.Instant;

public class AffiliationDrive implements DriveSource {

    private final UserModelOrchestrator userModelOrchestrator;
    private final double decayThreshold;
    private final Duration staleDuration;

    public AffiliationDrive(UserModelOrchestrator userModelOrchestrator,
                            double decayThreshold, Duration staleDuration) {
        this.userModelOrchestrator = userModelOrchestrator;
        this.decayThreshold = decayThreshold;
        this.staleDuration = staleDuration;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var profiles = userModelOrchestrator.activeProfiles(agentId, tenantId);
        if (profiles.isEmpty()) {
            return new DriveIntensity(DriveAxis.AFFILIATION, 0.0, "no tracked subjects");
        }

        var now = Instant.now();
        int neglected = 0;
        for (var profile : profiles) {
            boolean lowFamiliarity = profile.familiarityScore() < decayThreshold;
            boolean stale = Duration.between(profile.lastInteraction(), now).compareTo(staleDuration) > 0;
            if (lowFamiliarity || stale) neglected++;
        }

        double intensity = Math.clamp((double) neglected / profiles.size(), 0.0, 1.0);
        String trigger = neglected + " of " + profiles.size() + " relationships neglected";
        return new DriveIntensity(DriveAxis.AFFILIATION, intensity, trigger);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=AffiliationDriveTest`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/agentic/social/drive/AffiliationDrive.java src/main/java/io/casehub/blocks/agentic/social/UserModelOrchestrator.java src/test/java/io/casehub/blocks/agentic/social/drive/AffiliationDriveTest.java
git commit -m "feat(#129): add AffiliationDrive — familiarity decay detection from UserModel Refs #129"
```

### Task 6: AutonomyDrive + MentalModel cross-subject API

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/social/MentalModelOrchestrator.java` — add `activeSnapshots()`
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/AutonomyDrive.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/drive/AutonomyDriveTest.java`

**Interfaces:**
- Consumes: `DriveSource`, `DriveIntensity`, `DriveAxis` (from Task 1)
- Consumes: `MentalModelOrchestrator.activeSnapshots(agentId, tenantId) → List<MentalModelSnapshot>` (added in this task)
- Produces: `AutonomyDrive` implements `DriveSource`

- [ ] **Step 1: Write AutonomyDrive test**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutonomyDriveTest {

    private final Instant now = Instant.parse("2026-08-21T12:00:00Z");

    private MentalModelSnapshot snapshot(String subjectId, List<AttributedState> intentions) {
        return new MentalModelSnapshot("agent-1", subjectId, "tenant-1",
                List.of(), List.of(), intentions, now, null, now);
    }

    private AttributedState intention(String key, double confidence) {
        return new AttributedState(key, key, confidence, BdiDimension.INTENTION, now);
    }

    @Test
    void evaluate_noSnapshots_zeroIntensity() {
        var orchestrator = mock(MentalModelOrchestrator.class);
        when(orchestrator.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of());

        var drive = new AutonomyDrive(orchestrator, 0.5);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.axis()).isEqualTo(DriveAxis.AUTONOMY);
        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_highConfidenceIntentions_positiveIntensity() {
        var orchestrator = mock(MentalModelOrchestrator.class);
        when(orchestrator.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of(
                snapshot("user-1", List.of(
                        intention("redirect-workflow", 0.8),
                        intention("change-approach", 0.7))),
                snapshot("user-2", List.of(
                        intention("override-decision", 0.9)))));

        var drive = new AutonomyDrive(orchestrator, 0.5);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isGreaterThan(0.0);
    }

    @Test
    void evaluate_lowConfidenceIntentions_filtered() {
        var orchestrator = mock(MentalModelOrchestrator.class);
        when(orchestrator.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of(
                snapshot("user-1", List.of(
                        intention("maybe-redirect", 0.3),
                        intention("vague-change", 0.2)))));

        var drive = new AutonomyDrive(orchestrator, 0.5);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isEqualTo(0.0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=AutonomyDriveTest -DfailIfNoTests=false`
Expected: FAIL — AutonomyDrive and activeSnapshots() do not exist

- [ ] **Step 3: Add `activeSnapshots()` to MentalModelOrchestrator**

Use `ide_insert_member` to add after `observeConversation()`:

```java
public List<MentalModelSnapshot> activeSnapshots(String agentId, String tenantId) {
    var prefix = agentId + ":";
    var suffix = ":" + tenantId;
    return states.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix) && e.getKey().endsWith(suffix))
            .map(e -> e.getValue().currentSnapshot)
            .filter(java.util.Objects::nonNull)
            .toList();
}
```

- [ ] **Step 4: Implement AutonomyDrive**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.MentalModelOrchestrator;

public class AutonomyDrive implements DriveSource {

    private final MentalModelOrchestrator mentalModelOrchestrator;
    private final double confidenceFloor;

    public AutonomyDrive(MentalModelOrchestrator mentalModelOrchestrator,
                         double confidenceFloor) {
        this.mentalModelOrchestrator = mentalModelOrchestrator;
        this.confidenceFloor = confidenceFloor;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var snapshots = mentalModelOrchestrator.activeSnapshots(agentId, tenantId);
        if (snapshots.isEmpty()) {
            return new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "no mental models");
        }

        double totalPressure = 0.0;
        int intentionCount = 0;
        for (var snapshot : snapshots) {
            for (var intention : snapshot.intentions()) {
                if (intention.confidence() >= confidenceFloor) {
                    totalPressure += intention.confidence();
                    intentionCount++;
                }
            }
        }

        if (intentionCount == 0) {
            return new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "no high-confidence intentions");
        }

        double intensity = Math.clamp(totalPressure / (intentionCount + 4.0), 0.0, 1.0);
        String trigger = intentionCount + " high-confidence intentions across "
                + snapshots.size() + " subjects";
        return new DriveIntensity(DriveAxis.AUTONOMY, intensity, trigger);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=AutonomyDriveTest`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/agentic/social/drive/AutonomyDrive.java src/main/java/io/casehub/blocks/agentic/social/MentalModelOrchestrator.java src/test/java/io/casehub/blocks/agentic/social/drive/AutonomyDriveTest.java
git commit -m "feat(#129): add AutonomyDrive — intention pressure detection from MentalModel Refs #129"
```

## Batch 3: DriveOrchestrator — lifecycle and integration

### Task 7: DriveOrchestrator

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/social/drive/DriveOrchestrator.java`
- Test: `src/test/java/io/casehub/blocks/agentic/social/drive/DriveOrchestratorTest.java`

**Interfaces:**
- Consumes: All types from Tasks 1-6
- Consumes: `AgentDescriptor` (`io.casehub.eidos.api`), `MoodOrchestrator` (blocks social)
- Produces: `DriveOrchestrator.tick(agentId, tenantId, AgentDescriptor) → DriveTick`, `DriveOrchestrator.currentDrives(agentId, tenantId) → Optional<DriveProfile>`

- [ ] **Step 1: Write DriveOrchestrator test — basic tick lifecycle**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriveOrchestratorTest {

    private final Instant fixedNow = Instant.parse("2026-08-21T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    private CuriosityDrive curiosity;
    private CompetenceDrive competence;
    private AffiliationDrive affiliation;
    private AutonomyDrive autonomy;
    private MoodOrchestrator moodOrchestrator;
    private DriveComposer composer;
    private AgentDescriptor descriptor;

    @BeforeEach
    void setUp() {
        curiosity = mock(CuriosityDrive.class);
        competence = mock(CompetenceDrive.class);
        affiliation = mock(AffiliationDrive.class);
        autonomy = mock(AutonomyDrive.class);
        moodOrchestrator = mock(MoodOrchestrator.class);
        composer = new DriveComposer();
        descriptor = mock(AgentDescriptor.class);

        when(descriptor.disposition()).thenReturn(AgentDisposition.builder().build());
        when(moodOrchestrator.currentMood("agent-1", "tenant-1")).thenReturn(Optional.empty());

        when(curiosity.evaluate("agent-1", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.CURIOSITY, 0.6, "gaps"));
        when(competence.evaluate("agent-1", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.COMPETENCE, 0.3, "ok"));
        when(affiliation.evaluate("agent-1", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.AFFILIATION, 0.1, "stable"));
        when(autonomy.evaluate("agent-1", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.AUTONOMY, 0.4, "moderate"));
    }

    private DriveOrchestrator createOrchestrator() {
        return new DriveOrchestrator(curiosity, competence, affiliation, autonomy,
                moodOrchestrator, composer, DriveConfig.defaults(), clock);
    }

    @Test
    void tick_firstCall_returnsUpdated() {
        var orchestrator = createOrchestrator();
        var tick = orchestrator.tick("agent-1", "tenant-1", descriptor);

        assertThat(tick).isInstanceOf(DriveTick.Updated.class);
        var updated = (DriveTick.Updated) tick;
        assertThat(updated.current().dominantDrive()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(updated.current().drives()).hasSize(4);
    }

    @Test
    void currentDrives_beforeTick_empty() {
        var orchestrator = createOrchestrator();
        assertThat(orchestrator.currentDrives("agent-1", "tenant-1")).isEmpty();
    }

    @Test
    void currentDrives_afterTick_returnsCachedProfile() {
        var orchestrator = createOrchestrator();
        orchestrator.tick("agent-1", "tenant-1", descriptor);

        var drives = orchestrator.currentDrives("agent-1", "tenant-1");
        assertThat(drives).isPresent();
        assertThat(drives.get().dominantDrive()).isEqualTo(DriveAxis.CURIOSITY);
    }

    @Test
    void tick_noChange_whenBelowThreshold() {
        var orchestrator = createOrchestrator();
        orchestrator.tick("agent-1", "tenant-1", descriptor);

        // Same values → NoChange
        var tick2 = orchestrator.tick("agent-1", "tenant-1", descriptor);
        assertThat(tick2).isInstanceOf(DriveTick.NoChange.class);
    }

    @Test
    void tick_separateAgents_independentState() {
        var orchestrator = createOrchestrator();
        when(curiosity.evaluate("agent-2", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.CURIOSITY, 0.9, "x"));
        when(competence.evaluate("agent-2", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.COMPETENCE, 0.9, "x"));
        when(affiliation.evaluate("agent-2", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.AFFILIATION, 0.9, "x"));
        when(autonomy.evaluate("agent-2", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.AUTONOMY, 0.9, "x"));
        when(moodOrchestrator.currentMood("agent-2", "tenant-1")).thenReturn(Optional.empty());

        orchestrator.tick("agent-1", "tenant-1", descriptor);
        orchestrator.tick("agent-2", "tenant-1", descriptor);

        var d1 = orchestrator.currentDrives("agent-1", "tenant-1").orElseThrow();
        var d2 = orchestrator.currentDrives("agent-2", "tenant-1").orElseThrow();

        assertThat(d1.compositeMotivation()).isNotEqualTo(d2.compositeMotivation());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=DriveOrchestratorTest -DfailIfNoTests=false`
Expected: FAIL — DriveOrchestrator does not exist

- [ ] **Step 3: Implement DriveOrchestrator**

```java
package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.eidos.api.AgentDescriptor;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class DriveOrchestrator {

    private final CuriosityDrive curiosity;
    private final CompetenceDrive competence;
    private final AffiliationDrive affiliation;
    private final AutonomyDrive autonomy;
    private final MoodOrchestrator moodOrchestrator;
    private final DriveComposer composer;
    private final DriveConfig config;
    private final Clock clock;

    private final ConcurrentHashMap<String, DriveProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    public DriveOrchestrator(CuriosityDrive curiosity, CompetenceDrive competence,
                             AffiliationDrive affiliation, AutonomyDrive autonomy,
                             MoodOrchestrator moodOrchestrator, DriveComposer composer,
                             DriveConfig config) {
        this(curiosity, competence, affiliation, autonomy, moodOrchestrator,
                composer, config, Clock.systemUTC());
    }

    DriveOrchestrator(CuriosityDrive curiosity, CompetenceDrive competence,
                      AffiliationDrive affiliation, AutonomyDrive autonomy,
                      MoodOrchestrator moodOrchestrator, DriveComposer composer,
                      DriveConfig config, Clock clock) {
        this.curiosity = curiosity;
        this.competence = competence;
        this.affiliation = affiliation;
        this.autonomy = autonomy;
        this.moodOrchestrator = moodOrchestrator;
        this.composer = composer;
        this.config = config;
        this.clock = clock;
    }

    public DriveTick tick(String agentId, String tenantId, AgentDescriptor descriptor) {
        var key = agentId + ":" + tenantId;
        var lock = tickLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            var raw = new EnumMap<DriveAxis, DriveIntensity>(DriveAxis.class);
            raw.put(DriveAxis.CURIOSITY, curiosity.evaluate(agentId, tenantId));
            raw.put(DriveAxis.COMPETENCE, competence.evaluate(agentId, tenantId));
            raw.put(DriveAxis.AFFILIATION, affiliation.evaluate(agentId, tenantId));
            raw.put(DriveAxis.AUTONOMY, autonomy.evaluate(agentId, tenantId));

            var mood = moodOrchestrator.currentMood(agentId, tenantId).orElse(null);
            var disposition = descriptor.disposition();
            var now = Instant.now(clock);

            var newProfile = composer.compose(raw, disposition, mood, config,
                    agentId, tenantId, now);

            var previous = profiles.get(key);
            profiles.put(key, newProfile);

            if (previous == null) {
                return new DriveTick.Updated(newProfile, newProfile,
                        List.of(DriveAxis.values()));
            }

            var changed = new ArrayList<DriveAxis>();
            for (var axis : DriveAxis.values()) {
                var prevI = previous.drives().get(axis);
                var newI = newProfile.drives().get(axis);
                if (prevI == null || newI == null) {
                    changed.add(axis);
                } else if (Math.abs(prevI.intensity() - newI.intensity()) > config.changeThreshold()) {
                    changed.add(axis);
                }
            }

            if (changed.isEmpty()) {
                return new DriveTick.NoChange("all axes within threshold");
            }
            return new DriveTick.Updated(previous, newProfile, changed);
        } finally {
            lock.unlock();
        }
    }

    public Optional<DriveProfile> currentDrives(String agentId, String tenantId) {
        return Optional.ofNullable(profiles.get(agentId + ":" + tenantId));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=DriveOrchestratorTest`
Expected: PASS (5 tests)

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test`
Expected: PASS — all existing tests + all new drive tests

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/agentic/social/drive/DriveOrchestrator.java src/test/java/io/casehub/blocks/agentic/social/drive/DriveOrchestratorTest.java
git commit -m "feat(#129): add DriveOrchestrator — compositor lifecycle with tick/cache/accessor Refs #129"
```

## References

- [2026-08-21-drive-architecture-design.md] — design spec this plan implements
- [MemoryHygieneOrchestrator.java] — upstream API addition for CuriosityDrive
- [StrategyLearningOrchestrator.java:138] — currentStrategy() accessor pattern
- [UserModelOrchestrator.java:97] — currentProfile() accessor pattern
- [MentalModelOrchestrator.java:110] — project() accessor pattern
- [MoodOrchestrator.java:60] — currentMood() accessor (modulation input)
- [AgentDisposition] (eidos-api) — disposition.get(axis), primaryTerm(axis)
- [DispositionAxis] (eidos-api) — SOCIAL_ORIENTATION, RULE_FOLLOWING, RISK_APPETITE, AUTONOMY
- [DispositionValue] (eidos-api) — term + weight [0,1]
- [MoodState] (neocortex-memory-api) — pleasure, arousal, dominance [-1,1]
- [HygieneTick] — Completed variant with RetentionScore list
- [RetentionScore] — composite score for memory retention
- [GE-20260811-e941cc] — AgentDisposition vs DispositionProfile type split
- [GE-20260729-172d18] — check-before-creating SPIs pattern
- [GitHub #129] — Drive Architecture epic
