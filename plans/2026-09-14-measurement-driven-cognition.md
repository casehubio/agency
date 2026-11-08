# Measurement-Driven Cognition Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #261 — Historical encounter — measurement-driven cognition integration
**Issue group:** #261

**Goal:** Iteratively wire neurocortex cognition into the historical
encounter showcase, measuring the delta each stage adds over a pure-LLM
baseline with CognitionSnapshot dumps and CognitionMetrics per-turn
tables.

**Architecture:** Extract CognitionCore (shared orchestrator composition)
into blocks main scope. Add CognitionSnapshot/Delta/Metrics for
observability. Rewrite ConversationRunner to use single-turn queries
with cognition sections in system prompt (matching production's
SocialPromptAssembler pattern). Stage-gate CognitionStack from baseline
through full orchestration. Produce per-stage results files.

**Tech Stack:** Java 17, JUnit 5, Mockito, blocks social cognition
orchestrators, eidos-runtime, Claude SDK (via ClaudeAgentClient),
Gson (JSON serialization)

## Global Constraints

- All new main-scope code in `blocks/src/main/java/io/casehub/blocks/agentic/social/`
- All new test-scope code in `agentic-yaml/src/test/java/.../yaml/llm/`
- Results output to `agentic-yaml/src/test/resources/results/`
- Tests gated by `@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY")`
- No CDI in test scope — all orchestrators manually constructed
- Existing tests must continue to pass (`mvn test` without `-Pllm`)
- Commit after every task with `Refs #261`

---

## Batch 1: CognitionCore + Observability Infrastructure

### Task 1: CognitionCore — shared orchestrator composition

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java`

**Interfaces:**
- Consumes: `MoodOrchestrator`, `DriveOrchestrator`, `UserModelOrchestrator`, `MentalModelOrchestrator`, `StrategyLearningOrchestrator`, `NarrativeOrchestrator`, `GoalProposalOrchestrator`, `MemoryHygieneOrchestrator` — all existing orchestrators
- Produces: `CognitionCore` — `tick(agentId, tenantId, descriptor, activeSubjects)`, `recordInteraction(agentId, tenantId, subjectId, userMessage, response)`, `promptSections()`, orchestrator accessors (`mood()`, `drives()`, etc.)

- [ ] **Step 1: Write failing test — tick ordering**

```java
package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveComposer;
import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveSource;
import io.casehub.blocks.speech.PromptContext;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class CognitionCoreTest {

    @Test
    void tickPopulatesMoodAndDrives() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        DriveSource baseline = (a, t) ->
                new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
        var drives = new DriveOrchestrator(
                baseline, baseline, baseline, baseline,
                mood, new DriveComposer(), DriveConfig.defaults());
        var core = new CognitionCore(mood, drives,
                null, null, null, null, null, null);

        core.tick("agent1", "tenant1", null, Set.of());

        assertThat(core.mood().currentMood("agent1", "tenant1")).isPresent();
        assertThat(core.drives().currentDrives("agent1", "tenant1")).isPresent();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl blocks -Dtest=CognitionCoreTest#tickPopulatesMoodAndDrives -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `CognitionCore` does not exist

- [ ] **Step 3: Implement CognitionCore**

```java
package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.blocks.agentic.social.prompt.MoodPromptSection;
import io.casehub.blocks.agentic.social.prompt.DrivePromptSection;
import io.casehub.blocks.agentic.social.prompt.NarrativePromptSection;
import io.casehub.blocks.agentic.social.prompt.UserModelPromptSection;
import io.casehub.blocks.agentic.social.prompt.MentalModelPromptSection;
import io.casehub.blocks.agentic.social.prompt.StrategyPromptSection;
import io.casehub.blocks.agentic.social.prompt.GoalPromptSection;
import io.casehub.eidos.api.AgentDescriptor;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CognitionCore {

    private static final System.Logger LOG =
            System.getLogger(CognitionCore.class.getName());

    private final MoodOrchestrator mood;
    private final DriveOrchestrator drives;
    private final @Nullable UserModelOrchestrator userModel;
    private final @Nullable MentalModelOrchestrator mentalModel;
    private final @Nullable StrategyLearningOrchestrator strategy;
    private final @Nullable NarrativeOrchestrator narrative;
    private final @Nullable GoalProposalOrchestrator goals;
    private final @Nullable MemoryHygieneOrchestrator memoryHygiene;

    public CognitionCore(MoodOrchestrator mood,
                          DriveOrchestrator drives,
                          @Nullable UserModelOrchestrator userModel,
                          @Nullable MentalModelOrchestrator mentalModel,
                          @Nullable StrategyLearningOrchestrator strategy,
                          @Nullable NarrativeOrchestrator narrative,
                          @Nullable GoalProposalOrchestrator goals,
                          @Nullable MemoryHygieneOrchestrator memoryHygiene) {
        this.mood = mood;
        this.drives = drives;
        this.userModel = userModel;
        this.mentalModel = mentalModel;
        this.strategy = strategy;
        this.narrative = narrative;
        this.goals = goals;
        this.memoryHygiene = memoryHygiene;
    }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor,
                     Set<String> activeSubjects) {
        // 1. Mood (standalone)
        if (mood.currentMood(agentId, tenantId).isEmpty()) {
            mood.record(new MoodSignal.InteractionAppraisal(0, 0, 0, "init"),
                    agentId, tenantId);
        }
        mood.tick(agentId, tenantId);

        // 2. Memory hygiene (standalone)
        if (memoryHygiene != null) {
            safeRun(() -> memoryHygiene.tick(agentId, tenantId));
        }

        // 3. Narrative (standalone)
        if (narrative != null) {
            safeRun(() -> narrative.tick(agentId, tenantId));
        }

        // 4. Drives (depends on mood, memoryHygiene, narrative for modulation)
        if (descriptor != null) {
            safeRun(() -> drives.tick(agentId, tenantId, descriptor));
        }

        // 5. Strategy (standalone tick)
        if (strategy != null) {
            safeRun(() -> strategy.tick(agentId, tenantId));
        }

        // 6-7. Subject-scoped orchestrators
        for (String subjectId : activeSubjects) {
            if (userModel != null) {
                safeRun(() -> userModel.tick(agentId, subjectId, tenantId));
            }
            if (mentalModel != null) {
                safeRun(() -> mentalModel.tick(agentId, subjectId, tenantId));
            }
        }

        // 8. Goals (depends on drives)
        if (goals != null && descriptor != null) {
            safeRun(() -> goals.tick(agentId, tenantId, descriptor));
        }
    }

    public void recordInteraction(String agentId, String tenantId,
                                   @Nullable String subjectId,
                                   String userMessage, String response) {
        if (subjectId != null) {
            if (userModel != null) {
                safeRun(() -> userModel.record(
                        new InteractionSignal.CustomSignal(
                                userMessage, QualitySignal.NEUTRAL),
                        agentId, subjectId, tenantId));
            }
            if (mentalModel != null) {
                safeRun(() -> mentalModel.record(
                        new MentalStateSignal.VerbalCue(
                                userMessage, CueType.BELIEF_STATEMENT),
                        agentId, subjectId, tenantId));
            }
            if (strategy != null) {
                safeRun(() -> strategy.record(
                        new EngagementSignal.TurnOutcome(
                                new EngagementEvent(agentId, subjectId,
                                        tenantId, null,
                                        UUID.randomUUID().toString(),
                                        Instant.now(),
                                        userMessage.isBlank()
                                                ? "[interaction]"
                                                : userMessage,
                                        null, Map.of(), true, null,
                                        (int) response.length(),
                                        null, null, null),
                                Map.of(), response),
                        agentId, subjectId, tenantId));
            }
        }
    }

    public List<PromptSection> promptSections() {
        var sections = new ArrayList<PromptSection>();
        sections.add(new MoodPromptSection(mood));
        sections.add(new DrivePromptSection(drives));
        if (narrative != null)
            sections.add(new NarrativePromptSection(narrative));
        if (userModel != null)
            sections.add(new UserModelPromptSection(userModel));
        if (mentalModel != null)
            sections.add(new MentalModelPromptSection(mentalModel));
        if (strategy != null)
            sections.add(new StrategyPromptSection(strategy));
        if (goals != null)
            sections.add(new GoalPromptSection(goals));
        return sections;
    }

    public MoodOrchestrator mood() { return mood; }
    public DriveOrchestrator drives() { return drives; }
    public @Nullable UserModelOrchestrator userModel() { return userModel; }
    public @Nullable MentalModelOrchestrator mentalModel() { return mentalModel; }
    public @Nullable StrategyLearningOrchestrator strategy() { return strategy; }
    public @Nullable NarrativeOrchestrator narrative() { return narrative; }
    public @Nullable GoalProposalOrchestrator goals() { return goals; }
    public @Nullable MemoryHygieneOrchestrator memoryHygiene() {
        return memoryHygiene;
    }

    private void safeRun(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Orchestrator operation failed", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl blocks -Dtest=CognitionCoreTest#tickPopulatesMoodAndDrives`
Expected: PASS

- [ ] **Step 5: Add tests for recordInteraction and promptSections**

```java
@Test
void recordInteractionDispatchesSignals() {
    var mood = new MoodOrchestrator(MoodConfig.defaults());
    DriveSource baseline = (a, t) ->
            new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
    var drives = new DriveOrchestrator(
            baseline, baseline, baseline, baseline,
            mood, new DriveComposer(), DriveConfig.defaults());
    var core = new CognitionCore(mood, drives,
            null, null, null, null, null, null);

    // Should not throw even with null orchestrators
    core.recordInteraction("a", "t", "subject", "hello", "hi there");
}

@Test
void promptSectionsSkipsNullOrchestrators() {
    var mood = new MoodOrchestrator(MoodConfig.defaults());
    DriveSource baseline = (a, t) ->
            new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
    var drives = new DriveOrchestrator(
            baseline, baseline, baseline, baseline,
            mood, new DriveComposer(), DriveConfig.defaults());
    var core = new CognitionCore(mood, drives,
            null, null, null, null, null, null);

    var sections = core.promptSections();
    // Mood + Drives only (others are null)
    assertThat(sections).hasSize(2);
}
```

- [ ] **Step 6: Run all tests**

Run: `mvn test -pl blocks -Dtest=CognitionCoreTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java
git commit -m "feat(#261): CognitionCore — shared orchestrator composition

Extracts tick ordering, signal recording, and prompt section building
into a reusable composition class. Nullable orchestrators for staged
wiring. Matches SocialAvatarCognition's production patterns.

Refs #261"
```

### Task 2: CognitionSnapshot + CognitionDelta + CognitionMetrics

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionSnapshot.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionDelta.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionMetrics.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionSnapshotTest.java`

**Interfaces:**
- Consumes: `MoodState`, `DriveProfile`, `MentalModelSnapshot`, `UserProfile`, `StrategyProfile`, `NarrativeState`, `DriveGoalProposal` — existing orchestrator output types; `CognitionCore` (from Task 1) for orchestrator accessors
- Produces: `CognitionSnapshot` — `snapshot(agentId, tenantId, turnNumber, subjectIds)` factory, `diffFrom(previous)` → `CognitionDelta`, `toJson()` serialization; `CognitionMetrics` — `CognitionMetrics(turnNumber, agentId, sectionCount, sectionContent, delta, snapshot)`, `summary()`, `toMarkdownRow()`

- [ ] **Step 1: Write failing test — snapshot creation and diff**

```java
package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class CognitionSnapshotTest {

    @Test
    void emptySnapshotHasNullFields() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        DriveSource baseline = (a, t) ->
                new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
        var drives = new DriveOrchestrator(
                baseline, baseline, baseline, baseline,
                mood, new DriveComposer(), DriveConfig.defaults());
        var core = new CognitionCore(mood, drives,
                null, null, null, null, null, null);

        var snap = CognitionSnapshot.capture(core, "agent", "tenant",
                0, Set.of());
        assertThat(snap.agentId()).isEqualTo("agent");
        assertThat(snap.turnNumber()).isEqualTo(0);
        assertThat(snap.mentalModels()).isEmpty();
        assertThat(snap.userProfiles()).isEmpty();
    }

    @Test
    void diffDetectsMoodChange() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        DriveSource baseline = (a, t) ->
                new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
        var drives = new DriveOrchestrator(
                baseline, baseline, baseline, baseline,
                mood, new DriveComposer(), DriveConfig.defaults());
        var core = new CognitionCore(mood, drives,
                null, null, null, null, null, null);

        var before = CognitionSnapshot.capture(core, "agent", "tenant",
                0, Set.of());
        // Tick to populate mood
        core.tick("agent", "tenant", null, Set.of());
        var after = CognitionSnapshot.capture(core, "agent", "tenant",
                1, Set.of());

        var delta = after.diffFrom(before);
        // Before had no mood, after has mood — delta should be non-null
        assertThat(delta.mood()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl blocks -Dtest=CognitionSnapshotTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — classes do not exist

- [ ] **Step 3: Implement CognitionSnapshot**

```java
package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.agentic.social.goal.DriveGoalProposal;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CognitionSnapshot(
        String agentId,
        String tenantId,
        int turnNumber,
        Instant capturedAt,
        @Nullable MoodState mood,
        @Nullable DriveProfile drives,
        Map<String, MentalModelSnapshot> mentalModels,
        Map<String, UserProfile> userProfiles,
        @Nullable StrategyProfile strategy,
        @Nullable NarrativeState narrative,
        List<DriveGoalProposal> goalProposals
) {

    public static CognitionSnapshot capture(CognitionCore core,
                                             String agentId,
                                             String tenantId,
                                             int turnNumber,
                                             Set<String> subjectIds) {
        var moodState = core.mood().currentMood(agentId, tenantId)
                .orElse(null);
        var driveProfile = core.drives().currentDrives(agentId, tenantId)
                .orElse(null);

        var mentalModels = new LinkedHashMap<String, MentalModelSnapshot>();
        var userProfiles = new LinkedHashMap<String, UserProfile>();
        for (String subjectId : subjectIds) {
            if (core.mentalModel() != null) {
                core.mentalModel().lookup(agentId, subjectId, tenantId)
                        .ifPresent(s -> mentalModels.put(subjectId, s));
            }
            if (core.userModel() != null) {
                var profile = core.userModel()
                        .currentProfile(agentId, subjectId, tenantId);
                if (profile != null) userProfiles.put(subjectId, profile);
            }
        }

        StrategyProfile strategyProfile = null;
        if (core.strategy() != null) {
            strategyProfile = core.strategy()
                    .currentStrategy(agentId, tenantId).orElse(null);
        }

        NarrativeState narrativeState = null;
        if (core.narrative() != null) {
            narrativeState = core.narrative()
                    .currentNarrative(agentId, tenantId).orElse(null);
        }

        List<DriveGoalProposal> goals = List.of();
        if (core.goals() != null) {
            goals = core.goals().currentProposals(agentId, tenantId)
                    .orElse(List.of());
        }

        return new CognitionSnapshot(agentId, tenantId, turnNumber,
                Instant.now(), moodState, driveProfile, mentalModels,
                userProfiles, strategyProfile, narrativeState, goals);
    }

    public CognitionDelta diffFrom(@Nullable CognitionSnapshot previous) {
        return CognitionDelta.compute(previous, this);
    }

    public String toJson() {
        return CognitionJsonSerializer.serialize(this);
    }
}
```

- [ ] **Step 4: Implement CognitionDelta**

```java
package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.agentic.social.goal.DriveGoalProposal;
import io.casehub.blocks.agentic.social.narrative.NarrativeFragment;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CognitionDelta(
        @Nullable MoodDelta mood,
        @Nullable DriveDelta drives,
        Map<String, BdiDelta> mentalModelDeltas,
        Map<String, ProfileDelta> userProfileDeltas,
        int newEpisodeCount,
        int newThemeCount,
        List<DriveGoalProposal> newGoals
) {
    public record MoodDelta(double pleasureDelta, double arousalDelta,
                            double dominanceDelta) {}

    public record DriveDelta(Map<DriveAxis, Double> intensityDeltas,
                             double compositeDelta) {}

    public record BdiDelta(int newBeliefs, int newDesires,
                           int newIntentions) {}

    public record ProfileDelta(double familiarityDelta,
                               int interactionCountDelta) {}

    static CognitionDelta compute(@Nullable CognitionSnapshot before,
                                   CognitionSnapshot after) {
        MoodDelta moodDelta = null;
        if (after.mood() != null) {
            if (before == null || before.mood() == null) {
                moodDelta = new MoodDelta(
                        after.mood().pleasure(),
                        after.mood().arousal(),
                        after.mood().dominance());
            } else {
                moodDelta = new MoodDelta(
                        after.mood().pleasure() - before.mood().pleasure(),
                        after.mood().arousal() - before.mood().arousal(),
                        after.mood().dominance() - before.mood().dominance());
            }
        }

        DriveDelta driveDelta = null;
        if (after.drives() != null) {
            var deltas = new LinkedHashMap<DriveAxis, Double>();
            double prevComposite = 0;
            for (var entry : after.drives().drives().entrySet()) {
                double prev = 0;
                if (before != null && before.drives() != null) {
                    var prevIntensity = before.drives().drives()
                            .get(entry.getKey());
                    if (prevIntensity != null)
                        prev = prevIntensity.intensity();
                }
                deltas.put(entry.getKey(),
                        entry.getValue().intensity() - prev);
            }
            if (before != null && before.drives() != null) {
                prevComposite = before.drives().compositeMotivation();
            }
            driveDelta = new DriveDelta(deltas,
                    after.drives().compositeMotivation() - prevComposite);
        }

        var mentalDeltas = new LinkedHashMap<String, BdiDelta>();
        for (var entry : after.mentalModels().entrySet()) {
            var prev = before != null
                    ? before.mentalModels().get(entry.getKey()) : null;
            int prevBeliefs = prev != null ? prev.beliefs().size() : 0;
            int prevDesires = prev != null ? prev.desires().size() : 0;
            int prevIntentions = prev != null
                    ? prev.intentions().size() : 0;
            mentalDeltas.put(entry.getKey(), new BdiDelta(
                    entry.getValue().beliefs().size() - prevBeliefs,
                    entry.getValue().desires().size() - prevDesires,
                    entry.getValue().intentions().size() - prevIntentions));
        }

        var profileDeltas = new LinkedHashMap<String, ProfileDelta>();
        for (var entry : after.userProfiles().entrySet()) {
            var prev = before != null
                    ? before.userProfiles().get(entry.getKey()) : null;
            double prevFam = prev != null
                    ? prev.familiarityScore() : 0;
            int prevCount = prev != null
                    ? prev.totalInteractions() : 0;
            profileDeltas.put(entry.getKey(), new ProfileDelta(
                    entry.getValue().familiarityScore() - prevFam,
                    entry.getValue().totalInteractions() - prevCount));
        }

        int prevEpisodes = 0;
        int prevThemes = 0;
        if (before != null && before.narrative() != null) {
            prevEpisodes = before.narrative().episodes().size();
            prevThemes = before.narrative().themes().size();
        }
        int afterEpisodes = after.narrative() != null
                ? after.narrative().episodes().size() : 0;
        int afterThemes = after.narrative() != null
                ? after.narrative().themes().size() : 0;

        var prevGoalNames = before != null
                ? before.goalProposals().stream()
                        .map(DriveGoalProposal::goalName)
                        .toList()
                : List.<String>of();
        var newGoals = after.goalProposals().stream()
                .filter(g -> !prevGoalNames.contains(g.goalName()))
                .toList();

        return new CognitionDelta(moodDelta, driveDelta, mentalDeltas,
                profileDeltas, afterEpisodes - prevEpisodes,
                afterThemes - prevThemes, newGoals);
    }
}
```

- [ ] **Step 5: Implement CognitionMetrics**

```java
package io.casehub.blocks.agentic.social;

import java.util.Map;
import java.util.stream.Collectors;

public record CognitionMetrics(
        int turnNumber,
        String agentId,
        int promptSectionsContributed,
        Map<String, String> promptSectionContent,
        CognitionDelta delta,
        CognitionSnapshot snapshotAfter
) {
    public String summary() {
        var sb = new StringBuilder();
        sb.append(String.format("[sections] %d contributed%n",
                promptSectionsContributed));
        if (delta.mood() != null) {
            sb.append(String.format("[mood] P%+.2f A%+.2f D%+.2f%n",
                    delta.mood().pleasureDelta(),
                    delta.mood().arousalDelta(),
                    delta.mood().dominanceDelta()));
        }
        if (delta.drives() != null) {
            var driveStr = delta.drives().intensityDeltas().entrySet()
                    .stream()
                    .map(e -> String.format("%s:%+.2f",
                            e.getKey(), e.getValue()))
                    .collect(Collectors.joining(" "));
            sb.append(String.format("[drives] %s%n", driveStr));
        }
        for (var entry : delta.mentalModelDeltas().entrySet()) {
            var bdi = entry.getValue();
            sb.append(String.format("[mental-model] %s: +%dB +%dD +%dI%n",
                    entry.getKey(), bdi.newBeliefs(),
                    bdi.newDesires(), bdi.newIntentions()));
        }
        sb.append(String.format("[narrative] +%d episodes +%d themes%n",
                delta.newEpisodeCount(), delta.newThemeCount()));
        sb.append(String.format("[goals] %d new%n",
                delta.newGoals().size()));
        return sb.toString();
    }

    public String toMarkdownRow() {
        var moodStr = delta.mood() != null
                ? String.format("P%+.2f A%+.2f D%+.2f",
                        delta.mood().pleasureDelta(),
                        delta.mood().arousalDelta(),
                        delta.mood().dominanceDelta())
                : "—";
        var driveStr = delta.drives() != null
                ? String.format("%+.2f", delta.drives().compositeDelta())
                : "—";
        int totalBdi = delta.mentalModelDeltas().values().stream()
                .mapToInt(d -> d.newBeliefs() + d.newDesires()
                        + d.newIntentions())
                .sum();
        return String.format("| %d | %s | %d | %s | %s | %d | %d | %d |",
                turnNumber, agentId, promptSectionsContributed,
                moodStr, driveStr, totalBdi,
                delta.newEpisodeCount(), delta.newGoals().size());
    }
}
```

- [ ] **Step 6: Implement CognitionJsonSerializer (package-private utility)**

```java
package io.casehub.blocks.agentic.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

final class CognitionJsonSerializer {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    static String serialize(CognitionSnapshot snapshot) {
        return GSON.toJson(snapshot);
    }

    private CognitionJsonSerializer() {}
}
```

Note: Gson is already a compile dependency of blocks (via speech-sherpa
transitive). If not on the classpath for blocks module directly, use a
simple StringBuilder-based JSON builder instead.

- [ ] **Step 7: Run all tests**

Run: `mvn test -pl blocks -Dtest=CognitionSnapshotTest`
Expected: PASS

- [ ] **Step 8: Verify full build**

Run: `mvn test -pl blocks`
Expected: All existing tests pass + new tests pass

- [ ] **Step 9: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionSnapshot.java blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionDelta.java blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionMetrics.java blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionJsonSerializer.java blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionSnapshotTest.java
git commit -m "feat(#261): CognitionSnapshot + CognitionDelta + CognitionMetrics

Observable cognitive state with structured diff and per-turn metrics.
Designed for neocortex #333 compatibility (cognition_inspect/diff).

Refs #261"
```

---

## Batch 2: ConversationRunner Rewrite + Stage 0 Baseline

### Task 3: Rewrite ConversationRunner + ResultsWriter + CognitionStack delegation

**Files:**
- Modify: `agentic-yaml/src/test/java/.../llm/ConversationRunner.java`
- Modify: `agentic-yaml/src/test/java/.../llm/CognitionStack.java`
- Create: `agentic-yaml/src/test/java/.../llm/ResultsWriter.java`
- Test: `agentic-yaml/src/test/java/.../llm/ConversationRunnerTest.java` (existing — update)

**Interfaces:**
- Consumes: `CognitionCore` (Task 1), `CognitionSnapshot.capture()` (Task 2), `CognitionMetrics` (Task 2), `AgentProvider.invoke()`, `PromptSection.contribute()`
- Produces: `ConversationRunner` — single-turn query loop with per-turn metrics; `ConversationRunner.ConversationResult` with `List<CognitionMetrics>`; `ResultsWriter` — `writeConversation(result, path)`, `writeDump(snapshot, path)`, `writeMetrics(metrics, path)`; `CognitionStack` — delegates to `CognitionCore`, `Stage` enum

- [ ] **Step 1: Update CognitionStack to delegate to CognitionCore**

Rewrite `CognitionStack.java` to:
- Add `Stage` enum (`BASELINE`, `SIGNALS`, `REAL_DRIVES`, `NARRATIVE`, `FULL`)
- `from(CompiledCognition, AgentProvider, Stage)` factory
- Delegate `tick()`, `recordInteraction()`, `promptSections()`, `snapshot()` to `CognitionCore`
- Keep existing in-memory store inner classes

```java
public class CognitionStack {

    private final CognitionCore core;
    private final Stage stage;

    public enum Stage {
        BASELINE, SIGNALS, REAL_DRIVES, NARRATIVE, FULL
    }

    CognitionStack(CognitionCore core, Stage stage) {
        this.core = core;
        this.stage = stage;
    }

    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider agentProvider,
                                       Stage stage) {
        var mood = new MoodOrchestrator(config.mood());
        var narrative = new NarrativeOrchestrator(
                new InMemoryNarrativeStore());

        DriveSource baseline = (agentId, tenantId) ->
                new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
        var composer = new DriveComposer();
        var drives = new DriveOrchestrator(
                baseline, baseline, baseline, baseline,
                mood, composer, config.drive());

        UserModelOrchestrator userModel = null;
        MentalModelOrchestrator mentalModel = null;
        StrategyLearningOrchestrator strategy = null;

        if (agentProvider != null
                && stage.ordinal() >= Stage.SIGNALS.ordinal()) {
            userModel = new UserModelOrchestrator(
                    new InMemoryUserProfileStore(), agentProvider,
                    config.userModel());
            mentalModel = new MentalModelOrchestrator(
                    new InMemoryMentalModelStore(), agentProvider,
                    config.mentalModel());
        }

        var core = new CognitionCore(mood, drives, userModel,
                mentalModel, strategy, narrative, null, null);
        return new CognitionStack(core, stage);
    }

    // Backward-compatible factory (defaults to SIGNALS stage)
    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider ap) {
        return from(config, ap, Stage.SIGNALS);
    }

    public CognitionCore core() { return core; }
    public Stage stage() { return stage; }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor) {
        core.tick(agentId, tenantId, descriptor, Set.of());
    }

    public List<PromptSection> promptSections() {
        return core.promptSections();
    }

    public CognitionSnapshot snapshot(String agentId, String tenantId,
                                       int turnNumber,
                                       Set<String> subjectIds) {
        return CognitionSnapshot.capture(core, agentId, tenantId,
                turnNumber, subjectIds);
    }

    // Orchestrator accessors (kept for backward compat with existing tests)
    public MoodOrchestrator mood() { return core.mood(); }
    public DriveOrchestrator drives() { return core.drives(); }
    public NarrativeOrchestrator narrative() { return core.narrative(); }
    public @Nullable UserModelOrchestrator userModel() {
        return core.userModel();
    }
    public @Nullable MentalModelOrchestrator mentalModel() {
        return core.mentalModel();
    }

    // Inner store classes remain unchanged...
}
```

- [ ] **Step 2: Rewrite ConversationRunner to single-turn queries**

Replace `AgentSession`-based loop with `AgentProvider.invoke()`:
- Build fresh system prompt per turn (briefing + cognition sections + world)
- Build user prompt with full conversation history
- Capture `CognitionSnapshot` before/after each turn
- Compute `CognitionMetrics` per turn
- Support `includeComgnition` flag (false for Stage 0 baseline)

Key changes:
- Remove `Map<String, AgentSession> sessions` and `openSession()` calls
- Add `Set<String> subjectIds` tracking (other speakers)
- Add `List<CognitionMetrics> metrics` accumulator
- Use `cognitionStack.core().recordInteraction()` after each turn
- Use `AgentSessionConfig.of(systemPrompt, userPrompt)` for single-turn queries

- [ ] **Step 3: Implement ResultsWriter**

```java
package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.CognitionMetrics;
import io.casehub.blocks.agentic.social.CognitionSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ResultsWriter {

    static void writeConversation(
            ConversationRunner.ConversationResult result,
            String stageName, Path dir) throws IOException {
        Files.createDirectories(dir);
        var sb = new StringBuilder();
        sb.append("# ").append(stageName).append("\n\n");
        for (var turn : result.turns()) {
            sb.append(String.format("## Turn %d — %s%n",
                    turn.number(), turn.speakerName()));
            sb.append(String.format("[dialogue] %s%n%n",
                    turn.dialogue()));
            // Find matching metrics
            result.metrics().stream()
                    .filter(m -> m.turnNumber() == turn.number())
                    .findFirst()
                    .ifPresent(m -> sb.append(m.summary()).append("\n"));
            sb.append("---\n\n");
        }
        // Summary table
        sb.append("## Summary\n\n");
        sb.append("| Turn | Agent | Sections | Mood Δ | Drive Δ | BDI | Episodes | Goals |\n");
        sb.append("|------|-------|----------|--------|---------|-----|----------|-------|\n");
        for (var m : result.metrics()) {
            sb.append(m.toMarkdownRow()).append("\n");
        }
        Files.writeString(dir.resolve(stageName + "-conversation.md"),
                sb.toString());
    }

    static void writeDump(CognitionSnapshot snapshot, String stageName,
                           Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(stageName + "-dump.json"),
                snapshot.toJson());
    }

    static void writeMetrics(List<CognitionMetrics> metrics,
                              String stageName, Path dir)
            throws IOException {
        Files.createDirectories(dir);
        var sb = new StringBuilder();
        sb.append("# ").append(stageName).append(" — Metrics\n\n");
        sb.append("| Turn | Agent | Sections | Mood Δ | Drive Δ | BDI | Episodes | Goals |\n");
        sb.append("|------|-------|----------|--------|---------|-----|----------|-------|\n");
        for (var m : metrics) {
            sb.append(m.toMarkdownRow()).append("\n");
        }
        Files.writeString(dir.resolve(stageName + "-metrics.md"),
                sb.toString());
    }
}
```

- [ ] **Step 4: Update existing ConversationRunnerTest**

Ensure existing tests still compile with the new constructor/builder API.
The backward-compatible `CognitionStack.from(config, provider)` factory
keeps existing tests working.

- [ ] **Step 5: Run all tests**

Run: `mvn test -pl agentic-yaml`
Expected: All existing tests pass (compilation tests don't use LLM)

- [ ] **Step 6: Commit**

```bash
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/
git commit -m "feat(#261): rewrite ConversationRunner to single-turn queries

Switches from multi-turn AgentSession to AgentProvider.invoke() per turn.
Cognition sections injected into system prompt (matching production
SocialPromptAssembler pattern). Per-turn CognitionMetrics capture via
snapshot-diff. Stage-gated CognitionStack. ResultsWriter for markdown
and JSON output.

Refs #261"
```

### Task 4: Stage 0 baseline capture test

**Files:**
- Modify: `agentic-yaml/src/test/java/.../llm/HistoricalEncounterLlmTest.java`
- Create: `agentic-yaml/src/test/resources/results/` (output directory)

**Interfaces:**
- Consumes: `CognitionStack.from(config, provider, Stage.BASELINE)`, `ConversationRunner`, `ResultsWriter`
- Produces: `stage0_baselineCapture()` test method, `results/stage-0-*` output files

- [ ] **Step 1: Add Stage 0 test method**

```java
@Test
void stage0_baselineCapture() throws IOException {
    var compiled = loadCognition();
    var world = loadWorld();
    var descriptors = DescriptorLoader.load(SCENARIO);
    var stack = CognitionStack.from(compiled, agentProvider,
            CognitionStack.Stage.BASELINE);

    var result = ConversationRunner.builder()
            .agentProvider(agentProvider)
            .cognitionStack(stack)
            .world(world)
            .descriptors(descriptors)
            .maxTurns(4)
            .includeCognition(false)  // Stage 0: no cognition in prompt
            .build()
            .run();

    assertThat(result.turnCount()).isEqualTo(4);

    // Metrics should all be zero (no cognition sections)
    for (var m : result.metrics()) {
        assertThat(m.promptSectionsContributed()).isZero();
    }

    // Write results
    var resultsDir = Path.of("agentic-yaml/src/test/resources/results");
    ResultsWriter.writeConversation(result, "stage-0", resultsDir);
    ResultsWriter.writeDump(result.finalSnapshot(), "stage-0",
            resultsDir);
    ResultsWriter.writeMetrics(result.metrics(), "stage-0", resultsDir);

    System.out.println("\n=== Stage 0 complete ===");
    System.out.printf("Turns: %d  Elapsed: %s%n",
            result.turnCount(), result.elapsed());
}
```

- [ ] **Step 2: Run the test (requires LLM credentials)**

Run: `mvn test -pl agentic-yaml -Pllm -Dtest=HistoricalEncounterLlmTest#stage0_baselineCapture`
Expected: PASS — 4-turn conversation, zero cognition metrics, results written

- [ ] **Step 3: Verify results files**

Check `agentic-yaml/src/test/resources/results/`:
- `stage-0-conversation.md` — full dialogue with zero metrics
- `stage-0-dump.json` — empty/minimal cognitive graph
- `stage-0-metrics.md` — all-zero summary table

- [ ] **Step 4: Commit**

```bash
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/HistoricalEncounterLlmTest.java agentic-yaml/src/test/resources/results/
git commit -m "feat(#261): Stage 0 baseline capture — control conversation

4-turn Leonardo/Tesla conversation with no cognition sections.
All CognitionMetrics show zero deltas — proves instrumentation.
Results saved to resources/results/stage-0-*.

Refs #261"
```

---

## Batch 3: Stage 1-2 — Signal Extraction + Real Drives

### Task 5: Stage 1 — signal recording + cognition prompt injection

**Files:**
- Modify: `agentic-yaml/src/test/java/.../llm/HistoricalEncounterLlmTest.java`

**Interfaces:**
- Consumes: `CognitionStack.from(config, provider, Stage.SIGNALS)`, `ConversationRunner` (with `includeCognition(true)`)
- Produces: `stage1_signalExtraction()` test, `results/stage-1-*` files

- [ ] **Step 1: Add Stage 1 test method**

```java
@Test
void stage1_signalExtraction() throws IOException {
    var compiled = loadCognition();
    var world = loadWorld();
    var descriptors = DescriptorLoader.load(SCENARIO);
    var stack = CognitionStack.from(compiled, agentProvider,
            CognitionStack.Stage.SIGNALS);

    var result = ConversationRunner.builder()
            .agentProvider(agentProvider)
            .cognitionStack(stack)
            .world(world)
            .descriptors(descriptors)
            .maxTurns(4)
            .includeCognition(true)  // Stage 1: cognition in prompt
            .build()
            .run();

    assertThat(result.turnCount()).isEqualTo(4);

    // At least some sections should now contribute
    var totalSections = result.metrics().stream()
            .mapToInt(CognitionMetrics::promptSectionsContributed)
            .sum();
    assertThat(totalSections).isGreaterThan(0);

    var resultsDir = Path.of("agentic-yaml/src/test/resources/results");
    ResultsWriter.writeConversation(result, "stage-1", resultsDir);
    ResultsWriter.writeDump(result.finalSnapshot(), "stage-1",
            resultsDir);
    ResultsWriter.writeMetrics(result.metrics(), "stage-1", resultsDir);

    System.out.println("\n=== Stage 1 complete ===");
    System.out.printf("Total sections contributed: %d%n", totalSections);
}
```

- [ ] **Step 2: Run test**

Run: `mvn test -pl agentic-yaml -Pllm -Dtest=HistoricalEncounterLlmTest#stage1_signalExtraction`
Expected: PASS — sections > 0, mood deltas visible, mental model beliefs forming

- [ ] **Step 3: Commit with results**

```bash
git add agentic-yaml/
git commit -m "feat(#261): Stage 1 — signal extraction + prompt injection

Signal recording wired: UserModel, MentalModel, Strategy signals
dispatched after each turn. Cognition PromptSections in system prompt.
Measures section contribution, mood deltas, belief formation.

Refs #261"
```

### Task 6: Stage 2 — real drive sources

**Files:**
- Modify: `agentic-yaml/src/test/java/.../llm/CognitionStack.java` (add REAL_DRIVES wiring)
- Modify: `agentic-yaml/src/test/java/.../llm/HistoricalEncounterLlmTest.java`

**Interfaces:**
- Consumes: `CuriosityDrive(MemoryHygieneOrchestrator)`, `CompetenceDrive(StrategyLearningOrchestrator)`, `AffiliationDrive(UserModelOrchestrator)`, `AutonomyDrive(MentalModelOrchestrator)`, `DriveOrchestrator` explicit DriveSource constructor
- Produces: `stage2_realDrives()` test, `results/stage-2-*` files

- [ ] **Step 1: Wire Stage.REAL_DRIVES in CognitionStack.from()**

Add to `CognitionStack.from()`:

```java
if (stage.ordinal() >= Stage.REAL_DRIVES.ordinal()) {
    // Strategy needs stores — use in-memory
    strategy = new StrategyLearningOrchestrator(
            new InMemoryStrategyStore(),
            new InMemoryCbrCaseMemoryStore(),
            null, // ReflectionOrchestrator — null safe, skips reflection
            agentProvider, config.strategyLearning());

    // Real drive sources from orchestrators
    var curiosity = new CuriosityDrive(/* memoryHygiene — null for now */);
    var competence = new CompetenceDrive(strategy);
    var affiliation = new AffiliationDrive(userModel, 0.3,
            java.time.Duration.ofHours(1));
    var autonomy = new AutonomyDrive(mentalModel, 0.5);
    drives = new DriveOrchestrator(curiosity, competence,
            affiliation, autonomy, mood, composer, config.drive());
}
```

Note: `CuriosityDrive` needs `MemoryHygieneOrchestrator` which has 11
constructor params. For Stage 2, use a null/no-op curiosity source
instead:
```java
DriveSource curiosity = (a, t) ->
        new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "no-memory-hygiene");
```

Full `MemoryHygieneOrchestrator` wiring can be added in Stage 4 if
needed — CuriosityDrive with real memory hygiene is a Stage 4+ concern.

- [ ] **Step 2: Add Stage 2 test**

```java
@Test
void stage2_realDrives() throws IOException {
    var compiled = loadCognition();
    var world = loadWorld();
    var descriptors = DescriptorLoader.load(SCENARIO);
    var stack = CognitionStack.from(compiled, agentProvider,
            CognitionStack.Stage.REAL_DRIVES);

    var result = ConversationRunner.builder()
            .agentProvider(agentProvider)
            .cognitionStack(stack)
            .world(world)
            .descriptors(descriptors)
            .maxTurns(4)
            .includeCognition(true)
            .build()
            .run();

    // Drive shifts should be non-zero (real sources)
    var anyDriveShift = result.metrics().stream()
            .anyMatch(m -> m.delta().drives() != null
                    && m.delta().drives().compositeDelta() != 0);
    System.out.printf("Any drive shift: %s%n", anyDriveShift);

    var resultsDir = Path.of("agentic-yaml/src/test/resources/results");
    ResultsWriter.writeConversation(result, "stage-2", resultsDir);
    ResultsWriter.writeDump(result.finalSnapshot(), "stage-2",
            resultsDir);
    ResultsWriter.writeMetrics(result.metrics(), "stage-2", resultsDir);
}
```

- [ ] **Step 3: Run test**

Run: `mvn test -pl agentic-yaml -Pllm -Dtest=HistoricalEncounterLlmTest#stage2_realDrives`

- [ ] **Step 4: Commit**

```bash
git add agentic-yaml/
git commit -m "feat(#261): Stage 2 — real drive sources from orchestrators

CompetenceDrive from StrategyLearning trends, AffiliationDrive from
UserModel neglected profiles, AutonomyDrive from MentalModel intentions.
Drive shifts now reflect actual orchestrator state changes.

Refs #261"
```

---

## Batch 4: Stage 3-4 — Narrative Synthesis + Goal Proposals

### Task 7: Stage 3 — narrative synthesis with LLM

**Files:**
- Modify: `agentic-yaml/src/test/java/.../llm/CognitionStack.java` (NARRATIVE stage wiring)
- Modify: `agentic-yaml/src/test/java/.../llm/HistoricalEncounterLlmTest.java`

**Interfaces:**
- Consumes: `NarrativeContentSummariser(AgentProvider)`, `NarrativeOrchestrator(NarrativeStore)`, `NarrativeModulation`
- Produces: `stage3_narrativeSynthesis()` test, `results/stage-3-*` files

- [ ] **Step 1: Wire NARRATIVE stage in CognitionStack.from()**

For `Stage.NARRATIVE`, the NarrativeOrchestrator needs
`NarrativeContentSummariser` (LLM-backed). The issue is that
`NarrativeOrchestrator` receives episodes via its store — episodes
must be created and stored during the conversation.

The actual wiring depends on whether `NarrativeOrchestrator.tick()`
produces episodes from accumulated signals or whether episodes need
to be explicitly created. Read the `NarrativeOrchestrator` source to
determine the exact wiring.

If episodes are created via `NarrativePipeline` (summarisation runner),
the pipeline needs to be wired with a `ReflectionEventAdapter` as the
event source. For the test scope, a simpler approach may be to create
episodes directly in the `InMemoryNarrativeStore` based on conversation
content.

Implementation details will be determined during execution based on
the actual `NarrativeOrchestrator` API.

- [ ] **Step 2: Add Stage 3 test**

```java
@Test
void stage3_narrativeSynthesis() throws IOException {
    var compiled = loadCognition();
    var world = loadWorld();
    var descriptors = DescriptorLoader.load(SCENARIO);
    var stack = CognitionStack.from(compiled, agentProvider,
            CognitionStack.Stage.NARRATIVE);

    var result = ConversationRunner.builder()
            .agentProvider(agentProvider)
            .cognitionStack(stack)
            .world(world)
            .descriptors(descriptors)
            .maxTurns(4)
            .includeCognition(true)
            .build()
            .run();

    // Check for narrative episodes
    var finalSnap = result.finalSnapshot();
    System.out.printf("Narrative episodes: %d, themes: %d%n",
            finalSnap.narrative() != null
                    ? finalSnap.narrative().episodes().size() : 0,
            finalSnap.narrative() != null
                    ? finalSnap.narrative().themes().size() : 0);

    var resultsDir = Path.of("agentic-yaml/src/test/resources/results");
    ResultsWriter.writeConversation(result, "stage-3", resultsDir);
    ResultsWriter.writeDump(result.finalSnapshot(), "stage-3",
            resultsDir);
    ResultsWriter.writeMetrics(result.metrics(), "stage-3", resultsDir);
}
```

- [ ] **Step 3: Run test + commit**

Run: `mvn test -pl agentic-yaml -Pllm -Dtest=HistoricalEncounterLlmTest#stage3_narrativeSynthesis`

```bash
git add agentic-yaml/
git commit -m "feat(#261): Stage 3 — narrative synthesis with LLM

Episodes form from conversation content, themes derive from episodes.
Narrative modulates drive intensities. Narrative PromptSection shows
identity narrative in system prompt.

Refs #261"
```

### Task 8: Stage 4 — full orchestration + goal proposals

**Files:**
- Modify: `agentic-yaml/src/test/java/.../llm/CognitionStack.java` (FULL stage wiring)
- Modify: `agentic-yaml/src/test/java/.../llm/HistoricalEncounterLlmTest.java`

**Interfaces:**
- Consumes: `GoalProposalOrchestrator(DriveOrchestrator, List<DriveGoalMapper>, GoalProposalConfig)` minimal test constructor, `CuriosityGoalMapper`, `CompetenceGoalMapper`, `AffiliationGoalMapper`, `AutonomyGoalMapper`
- Produces: `stage4_fullOrchestration()` test, `results/stage-4-*` files

- [ ] **Step 1: Wire FULL stage in CognitionStack.from()**

```java
if (stage.ordinal() >= Stage.FULL.ordinal()) {
    var mappers = List.<DriveGoalMapper>of(
            new CuriosityGoalMapper(curiosityDrive),
            new CompetenceGoalMapper(competenceDrive),
            new AffiliationGoalMapper(affiliationDrive),
            new AutonomyGoalMapper(autonomyDrive));
    goals = new GoalProposalOrchestrator(drives, mappers,
            noOpGoalSignalStore(), config.goalProposal(), Clock.systemUTC());
}
```

Where `noOpGoalSignalStore()` returns a no-op `Instance<GoalSignalStore>`.

- [ ] **Step 2: Add Stage 4 test**

```java
@Test
void stage4_fullOrchestration() throws IOException {
    var compiled = loadCognition();
    var world = loadWorld();
    var descriptors = DescriptorLoader.load(SCENARIO);
    var stack = CognitionStack.from(compiled, agentProvider,
            CognitionStack.Stage.FULL);

    var result = ConversationRunner.builder()
            .agentProvider(agentProvider)
            .cognitionStack(stack)
            .world(world)
            .descriptors(descriptors)
            .maxTurns(4)
            .includeCognition(true)
            .build()
            .run();

    // Check for goal proposals
    var finalSnap = result.finalSnapshot();
    System.out.printf("Goal proposals: %d%n",
            finalSnap.goalProposals().size());

    var resultsDir = Path.of("agentic-yaml/src/test/resources/results");
    ResultsWriter.writeConversation(result, "stage-4", resultsDir);
    ResultsWriter.writeDump(result.finalSnapshot(), "stage-4",
            resultsDir);
    ResultsWriter.writeMetrics(result.metrics(), "stage-4", resultsDir);
}
```

- [ ] **Step 3: Run test + commit**

Run: `mvn test -pl agentic-yaml -Pllm -Dtest=HistoricalEncounterLlmTest#stage4_fullOrchestration`

```bash
git add agentic-yaml/
git commit -m "feat(#261): Stage 4 — full orchestration with goal proposals

GoalProposalOrchestrator with 4 axis-specific mappers. Goals emerge
from drive intensities and appear in system prompt. Goal escalation
from narrative themes.

Refs #261"
```

---

## Batch 5: Stage 5-6 — Visualization + A/B Comparison

### Task 9: Stage 5 — Mermaid visualization + Stage 6 — A/B evaluation

**Files:**
- Create: `agentic-yaml/src/test/java/.../llm/MermaidGenerator.java`
- Create: `agentic-yaml/src/test/java/.../llm/ConversationEvaluator.java`
- Modify: `agentic-yaml/src/test/java/.../llm/HistoricalEncounterLlmTest.java`

**Interfaces:**
- Consumes: `CognitionSnapshot`, `ConversationRunner.ConversationResult`, `CognitionMetrics`, `AgentProvider`
- Produces: `MermaidGenerator.generate(snapshot)` → Mermaid diagram string; `ConversationEvaluator.compare(baseline, withCognition)` → `ComparisonResult`; `stage5_visualization()` + `stage6_abComparison()` tests

- [ ] **Step 1: Implement MermaidGenerator**

```java
package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.CognitionSnapshot;

class MermaidGenerator {

    static String generate(CognitionSnapshot snapshot) {
        var sb = new StringBuilder();
        sb.append("graph TD\n");
        sb.append(String.format("    %s[%s]%n",
                snapshot.agentId(), snapshot.agentId()));

        // Mental model beliefs
        for (var entry : snapshot.mentalModels().entrySet()) {
            var subjectId = entry.getKey();
            var model = entry.getValue();
            sb.append(String.format("    %s[%s]%n",
                    subjectId, subjectId));
            for (var belief : model.beliefs()) {
                var beliefId = belief.key().replaceAll("[^a-zA-Z0-9]", "");
                sb.append(String.format(
                        "    %s -->|believes| %s[\"%s (%.1f)\"]%n",
                        snapshot.agentId(), beliefId,
                        belief.description(), belief.confidence()));
            }
        }

        // Narrative themes
        if (snapshot.narrative() != null) {
            for (var theme : snapshot.narrative().themes()) {
                var themeId = "theme_" + theme.label()
                        .replaceAll("[^a-zA-Z0-9]", "");
                sb.append(String.format(
                        "    %s -.->|theme| %s[\"%s\"]%n",
                        snapshot.agentId(), themeId, theme.label()));
            }
        }

        // Goals
        for (var goal : snapshot.goalProposals()) {
            var goalId = "goal_" + goal.goalName()
                    .replaceAll("[^a-zA-Z0-9]", "");
            sb.append(String.format(
                    "    %s ==>|goal| %s[\"%s (%.1f)\"]%n",
                    snapshot.agentId(), goalId,
                    goal.goalName(), goal.driveIntensity()));
        }

        return sb.toString();
    }
}
```

- [ ] **Step 2: Implement ConversationEvaluator**

```java
package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.CognitionMetrics;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class ConversationEvaluator {

    private final AgentProvider judge;

    ConversationEvaluator(AgentProvider judge) {
        this.judge = judge;
    }

    record ComparisonResult(
            Map<String, Integer> baselineScores,
            Map<String, Integer> cognitionScores,
            String winner,
            String assessment
    ) {}

    ComparisonResult compare(
            ConversationRunner.ConversationResult baseline,
            ConversationRunner.ConversationResult withCognition,
            List<CognitionMetrics> cognitionMetrics) {

        var prompt = buildJudgePrompt(baseline, withCognition);
        var config = AgentSessionConfig.builder()
                .systemPrompt("You are a conversation quality judge...")
                .userMessage(prompt)
                .timeout(Duration.ofSeconds(120))
                .build();

        var response = ((TestAgentProvider) judge)
                .invokeBlocking(config);
        return parseJudgeResponse(response);
    }

    private String buildJudgePrompt(
            ConversationRunner.ConversationResult a,
            ConversationRunner.ConversationResult b) {
        var sb = new StringBuilder();
        sb.append("Compare these two conversations between Leonardo ");
        sb.append("da Vinci and Nikola Tesla.\n\n");
        sb.append("## Conversation A (Baseline)\n\n");
        for (var t : a.turns()) {
            sb.append(t.speakerName()).append(": ")
              .append(t.dialogue()).append("\n\n");
        }
        sb.append("## Conversation B (With Cognition)\n\n");
        for (var t : b.turns()) {
            sb.append(t.speakerName()).append(": ")
              .append(t.dialogue()).append("\n\n");
        }
        sb.append("Score each on 1-10 for: groundedness, ");
        sb.append("adaptiveness, character_consistency, depth, ");
        sb.append("memory_utilisation.\n");
        sb.append("Respond as JSON: {\"baseline\": {...}, ");
        sb.append("\"cognition\": {...}, \"winner\": \"...\", ");
        sb.append("\"assessment\": \"...\"}");
        return sb.toString();
    }

    private ComparisonResult parseJudgeResponse(String response) {
        // Parse JSON from response — extract scores
        // Graceful fallback if JSON parsing fails
        try {
            var gson = new com.google.gson.Gson();
            var parsed = gson.fromJson(response,
                    com.google.gson.JsonObject.class);
            // ... extract fields ...
            return new ComparisonResult(Map.of(), Map.of(),
                    parsed.get("winner").getAsString(),
                    parsed.get("assessment").getAsString());
        } catch (Exception e) {
            return new ComparisonResult(Map.of(), Map.of(),
                    "unknown", response);
        }
    }
}
```

- [ ] **Step 3: Add Stage 5 + Stage 6 tests**

```java
@Test
void stage5_visualization() throws IOException {
    // Reuse Stage 4 result or re-run
    var compiled = loadCognition();
    var world = loadWorld();
    var descriptors = DescriptorLoader.load(SCENARIO);
    var stack = CognitionStack.from(compiled, agentProvider,
            CognitionStack.Stage.FULL);

    var result = ConversationRunner.builder()
            .agentProvider(agentProvider)
            .cognitionStack(stack)
            .world(world)
            .descriptors(descriptors)
            .maxTurns(4)
            .includeCognition(true)
            .build()
            .run();

    var resultsDir = Path.of("agentic-yaml/src/test/resources/results");

    // Per-turn Mermaid graphs
    for (var m : result.metrics()) {
        var mermaid = MermaidGenerator.generate(m.snapshotAfter());
        Files.writeString(
                resultsDir.resolve("stage-5-turn-" + m.turnNumber()
                        + "-" + m.agentId() + ".mmd"),
                mermaid);
    }

    System.out.println("Stage 5 — per-turn Mermaid graphs written");
}

@Test
void stage6_abComparison() throws IOException {
    var compiled = loadCognition();
    var world = loadWorld();
    var descriptors = DescriptorLoader.load(SCENARIO);

    // Run baseline
    var baselineStack = CognitionStack.from(compiled, agentProvider,
            CognitionStack.Stage.BASELINE);
    var baseline = ConversationRunner.builder()
            .agentProvider(agentProvider)
            .cognitionStack(baselineStack)
            .world(world)
            .descriptors(descriptors)
            .maxTurns(4)
            .includeCognition(false)
            .build()
            .run();

    // Run full cognition
    var fullStack = CognitionStack.from(compiled, agentProvider,
            CognitionStack.Stage.FULL);
    var withCognition = ConversationRunner.builder()
            .agentProvider(agentProvider)
            .cognitionStack(fullStack)
            .world(world)
            .descriptors(descriptors)
            .maxTurns(4)
            .includeCognition(true)
            .build()
            .run();

    // Judge comparison
    var evaluator = new ConversationEvaluator(agentProvider);
    var comparison = evaluator.compare(baseline, withCognition,
            withCognition.metrics());

    System.out.println("\n=== A/B Comparison ===");
    System.out.printf("Winner: %s%n", comparison.winner());
    System.out.printf("Assessment: %s%n", comparison.assessment());

    // Write comparison results
    var resultsDir = Path.of("agentic-yaml/src/test/resources/results");
    var sb = new StringBuilder();
    sb.append("# Stage 6 — A/B Comparison\n\n");
    sb.append("## Winner: ").append(comparison.winner()).append("\n\n");
    sb.append("## Assessment\n\n");
    sb.append(comparison.assessment()).append("\n");
    Files.writeString(resultsDir.resolve("stage-6-comparison.md"),
            sb.toString());
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl agentic-yaml -Pllm -Dtest=HistoricalEncounterLlmTest#stage5_visualization`
Run: `mvn test -pl agentic-yaml -Pllm -Dtest=HistoricalEncounterLlmTest#stage6_abComparison`

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml/
git commit -m "feat(#261): Stage 5-6 — Mermaid visualization + A/B evaluation

Per-turn cognitive graph Mermaid diagrams showing growth. LLM judge
comparison on 5 quality dimensions: groundedness, adaptiveness,
character consistency, depth, memory utilisation.

Refs #261"
```

---

## References

- [2026-09-14-measurement-driven-cognition-design.md] — design spec this plan implements
- [decisions.md] — D1-D6 decision log
- [CognitionStack.java] — existing test-scope orchestrator wiring
- [ConversationRunner.java] — existing conversation loop
- [SocialAvatarCognition.java:94-118] — production signal recording pattern
- [SocialPromptAssembler.java:33-48] — production system-prompt injection
- [MoodOrchestrator, DriveOrchestrator, MentalModelOrchestrator, UserModelOrchestrator, StrategyLearningOrchestrator, NarrativeOrchestrator, GoalProposalOrchestrator] — orchestrator APIs
- [neocortex #333] — cognitive observability epic (future compatibility target)
- [GitHub #261] — focal issue
- [GitHub #258] — predecessor (historical encounter showcase foundation)
