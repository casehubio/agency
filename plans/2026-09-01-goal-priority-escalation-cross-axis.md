# Goal Priority Escalation + Cross-Axis Composition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #166 — GoalPriorityEscalationPolicy — governed narrative-driven priority escalation
**Issue group:** #166, #160, #159

**Goal:** Extend GoalProposalOrchestrator with governed priority escalation (SECONDARY → PRIMARY via narrative alignment) and cross-axis goal composition (compound goals from multi-axis DerivedThemes).

**Architecture:** Four new pipeline phases in GoalProposalOrchestrator (cross-axis composition, escalation evaluation, demotion evaluation, governance updates). GoalEscalationPolicy SPI in blocks with NarrativeGoalEscalationPolicy implementation. Synthesis-cycle tracking for sustained alignment governance. Engine-side provenance guard for defence-in-depth.

**Tech Stack:** Java 17, JUnit 5, Mockito, CDI (Instance<> for optional dependencies)

## Global Constraints

- Pre-release platform — breaking changes acceptable
- No CDI container in tests — plain JUnit 5 + Mockito
- Compositor pattern (ADR-0001): tick() evaluates and caches, no side effects
- GoalProposalTick.Proposed → Changes rename: zero production callers (test-only)
- Engine provenance guard is additive — no API change
- Cross-axis detection uses positive weights only (negative = suppression)

---

## Batch 1: Foundation Types

### Task 1: New records and SPIs

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/GoalEscalationPolicy.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/EscalationResult.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/GoalEscalationContext.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/GoalEscalationConfig.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/PriorityAdjustment.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/GovernanceAttributeUpdate.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/CrossAxisGoalEnricher.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/goal/GoalEscalationConfigTest.java`

**Interfaces:**
- Produces: `GoalEscalationPolicy.evaluate(DriveGoalProposal, GoalEscalationContext) → @Nullable EscalationResult`
- Produces: `EscalationResult(GoalPriority priority, String themeLabel, String reason)`
- Produces: `GoalEscalationContext(NarrativeState narrative, DriveProfile drives, AgentDescriptor descriptor)`
- Produces: `GoalEscalationConfig` with `defaults()` factory
- Produces: `PriorityAdjustment(String goalName, GoalPriority newPriority, String reason)`
- Produces: `GovernanceAttributeUpdate(String goalName, Map<String, String> attributeUpdates)`
- Produces: `CrossAxisGoalEnricher.enrich(DriveGoalProposal, NarrativeState, DerivedTheme) → @Nullable DriveGoalProposal`

- [ ] **Step 1: Write GoalEscalationConfig test**

```java
package io.casehub.blocks.agentic.social.goal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GoalEscalationConfigTest {

    @Test
    void defaults_returnsValidConfig() {
        var config = GoalEscalationConfig.defaults();
        assertThat(config.escalationSalienceThreshold()).isEqualTo(0.6);
        assertThat(config.minAxisAlignmentWeight()).isEqualTo(0.3);
        assertThat(config.crossAxisMinWeight()).isEqualTo(0.3);
        assertThat(config.minCrossAxisCount()).isEqualTo(2);
        assertThat(config.escalationCycles()).isEqualTo(2);
        assertThat(config.demotionCycles()).isEqualTo(2);
        assertThat(config.maxPrimaryDriveGoals()).isEqualTo(1);
    }

    @Test
    void rejectsSalienceOutOfRange() {
        assertThatThrownBy(() -> new GoalEscalationConfig(
                1.5, 0.3, 0.3, 2, 2, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMinCrossAxisCountBelow2() {
        assertThatThrownBy(() -> new GoalEscalationConfig(
                0.6, 0.3, 0.3, 1, 2, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEscalationCyclesBelow1() {
        assertThatThrownBy(() -> new GoalEscalationConfig(
                0.6, 0.3, 0.3, 2, 0, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalEscalationConfigTest`
Expected: FAIL — class not found

- [ ] **Step 3: Create all foundation types**

Use `ide_create_file` for each new file. Types match the design spec §Type System exactly:

**GoalEscalationPolicy.java:**
```java
package io.casehub.blocks.agentic.social.goal;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface GoalEscalationPolicy {
    @Nullable EscalationResult evaluate(DriveGoalProposal proposal,
                                         GoalEscalationContext context);
}
```

**EscalationResult.java:**
```java
package io.casehub.blocks.agentic.social.goal;

import io.casehub.eidos.api.GoalPriority;
import java.util.Objects;

public record EscalationResult(
        GoalPriority priority,
        String themeLabel,
        String reason) {
    public EscalationResult {
        Objects.requireNonNull(priority);
        Objects.requireNonNull(themeLabel);
        Objects.requireNonNull(reason);
    }
}
```

**GoalEscalationContext.java:**
```java
package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.eidos.api.AgentDescriptor;
import java.util.Objects;

public record GoalEscalationContext(
        NarrativeState narrative,
        DriveProfile drives,
        AgentDescriptor descriptor) {
    public GoalEscalationContext {
        Objects.requireNonNull(narrative);
        Objects.requireNonNull(drives);
        Objects.requireNonNull(descriptor);
    }
}
```

**GoalEscalationConfig.java:**
```java
package io.casehub.blocks.agentic.social.goal;

public record GoalEscalationConfig(
        double escalationSalienceThreshold,
        double minAxisAlignmentWeight,
        double crossAxisMinWeight,
        int minCrossAxisCount,
        int escalationCycles,
        int demotionCycles,
        int maxPrimaryDriveGoals) {
    public GoalEscalationConfig {
        if (escalationSalienceThreshold < 0.0 || escalationSalienceThreshold > 1.0)
            throw new IllegalArgumentException("escalationSalienceThreshold must be in [0, 1]");
        if (minAxisAlignmentWeight < 0.0 || minAxisAlignmentWeight > 1.0)
            throw new IllegalArgumentException("minAxisAlignmentWeight must be in [0, 1]");
        if (crossAxisMinWeight < 0.0 || crossAxisMinWeight > 1.0)
            throw new IllegalArgumentException("crossAxisMinWeight must be in [0, 1]");
        if (minCrossAxisCount < 2)
            throw new IllegalArgumentException("minCrossAxisCount must be >= 2");
        if (escalationCycles < 1)
            throw new IllegalArgumentException("escalationCycles must be >= 1");
        if (demotionCycles < 1)
            throw new IllegalArgumentException("demotionCycles must be >= 1");
        if (maxPrimaryDriveGoals < 0)
            throw new IllegalArgumentException("maxPrimaryDriveGoals must be >= 0");
    }

    public static GoalEscalationConfig defaults() {
        return new GoalEscalationConfig(0.6, 0.3, 0.3, 2, 2, 2, 1);
    }
}
```

**PriorityAdjustment.java:**
```java
package io.casehub.blocks.agentic.social.goal;

import io.casehub.eidos.api.GoalPriority;
import java.util.Objects;

public record PriorityAdjustment(
        String goalName,
        GoalPriority newPriority,
        String reason) {
    public PriorityAdjustment {
        Objects.requireNonNull(goalName);
        Objects.requireNonNull(newPriority);
        Objects.requireNonNull(reason);
    }
}
```

**GovernanceAttributeUpdate.java:**
```java
package io.casehub.blocks.agentic.social.goal;

import java.util.Map;
import java.util.Objects;

public record GovernanceAttributeUpdate(
        String goalName,
        Map<String, String> attributeUpdates) {
    public GovernanceAttributeUpdate {
        Objects.requireNonNull(goalName);
        attributeUpdates = Map.copyOf(attributeUpdates);
    }
}
```

**CrossAxisGoalEnricher.java:**
```java
package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface CrossAxisGoalEnricher {
    @Nullable DriveGoalProposal enrich(DriveGoalProposal heuristicProposal,
                                        NarrativeState narrative,
                                        DerivedTheme sourceTheme);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalEscalationConfigTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git -C blocks add .
git commit -m "feat(#166): foundation types — GoalEscalationPolicy SPI, config, records"
```

### Task 2: Modified types — DriveGoalProposal + GoalProposalTick

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/DriveGoalProposal.java`
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/GoalProposalTick.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/goal/GoalProposalOrchestratorTest.java`
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/GoalProposalOrchestrator.java`

**Interfaces:**
- Consumes: PriorityAdjustment, GovernanceAttributeUpdate from Task 1
- Produces: `DriveGoalProposal` with `@Nullable GoalPriority suggestedPriority` and `@Nullable Map<String, String> proposalAttributes`
- Produces: `GoalProposalTick.Changes` (renamed from Proposed) with 4 signal lists

- [ ] **Step 1: Write test for DriveGoalProposal backward compatibility**

Add to a new test or existing test file:
```java
@Test
void driveGoalProposal_backwardCompatible() {
    var proposal = new DriveGoalProposal(
            DriveAxis.CURIOSITY, "explore", "desc", "reason", 0.7);
    assertThat(proposal.suggestedPriority()).isNull();
    assertThat(proposal.proposalAttributes()).isNull();
}

@Test
void driveGoalProposal_withEscalation() {
    var attrs = Map.of("escalatedBy", "narrative", "escalation.theme", "explorer");
    var proposal = new DriveGoalProposal(
            DriveAxis.CURIOSITY, "explore", "desc", "reason", 0.7,
            GoalPriority.PRIMARY, attrs);
    assertThat(proposal.suggestedPriority()).isEqualTo(GoalPriority.PRIMARY);
    assertThat(proposal.proposalAttributes()).containsEntry("escalatedBy", "narrative");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalProposalOrchestratorTest#driveGoalProposal_backwardCompatible`
Expected: FAIL — constructor mismatch

- [ ] **Step 3: Modify DriveGoalProposal**

Use `ide_replace_member` to replace the record declaration. Add two new fields and a backward-compatible constructor:

```java
public record DriveGoalProposal(
        DriveAxis axis,
        String goalName,
        String goalDescription,
        String formationReason,
        double driveIntensity,
        @Nullable GoalPriority suggestedPriority,
        @Nullable Map<String, String> proposalAttributes) {
    public DriveGoalProposal {
        Objects.requireNonNull(axis, "axis required");
        Objects.requireNonNull(goalName, "goalName required");
        Objects.requireNonNull(goalDescription, "goalDescription required");
        Objects.requireNonNull(formationReason, "formationReason required");
        if (driveIntensity < 0.0 || driveIntensity > 1.0) {
            throw new IllegalArgumentException(
                    "driveIntensity must be in [0.0, 1.0], got " + driveIntensity);
        }
        proposalAttributes = proposalAttributes != null ? Map.copyOf(proposalAttributes) : null;
    }

    public DriveGoalProposal(DriveAxis axis, String goalName, String goalDescription,
                              String formationReason, double driveIntensity) {
        this(axis, goalName, goalDescription, formationReason, driveIntensity, null, null);
    }
}
```

- [ ] **Step 4: Rename GoalProposalTick.Proposed → Changes and add fields**

Use `ide_replace_member` on GoalProposalTick to replace the `Proposed` record:

```java
public sealed interface GoalProposalTick {
    record NoChange(@Nullable String reason) implements GoalProposalTick {}
    record Changes(
            List<DriveGoalProposal> newProposals,
            List<String> abandonedGoalNames,
            List<PriorityAdjustment> priorityAdjustments,
            List<GovernanceAttributeUpdate> governanceUpdates
    ) implements GoalProposalTick {}
}
```

- [ ] **Step 5: Update GoalProposalOrchestrator to use Changes**

Update `doTick()` return statements from `new GoalProposalTick.Proposed(proposals, abandonments)` to `new GoalProposalTick.Changes(proposals, abandonments, List.of(), List.of())`. Use `ide_replace_member` on `doTick`.

- [ ] **Step 6: Update GoalProposalOrchestratorTest references**

Replace all `GoalProposalTick.Proposed` → `GoalProposalTick.Changes` in the test file using `ide_refactor_rename` or find-and-replace via `ide_replace_text_in_file`.

- [ ] **Step 7: Run full test suite**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalProposalOrchestratorTest`
Expected: PASS — all existing tests pass with renamed type

- [ ] **Step 8: Run diagnostics**

Use `ide_diagnostics` to verify no compile errors across blocks module.

- [ ] **Step 9: Commit**

```bash
git -C blocks add .
git commit -m "feat(#166): DriveGoalProposal fields + GoalProposalTick.Proposed → Changes"
```

---

## Batch 2: Escalation Policy

### Task 3: NarrativeGoalEscalationPolicy

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/NarrativeGoalEscalationPolicy.java`
- Create: `blocks/src/test/java/io/casehub/blocks/agentic/social/goal/NarrativeGoalEscalationPolicyTest.java`

**Interfaces:**
- Consumes: GoalEscalationPolicy, EscalationResult, GoalEscalationContext, GoalEscalationConfig from Task 1
- Consumes: DriveGoalProposal from Task 2
- Produces: `NarrativeGoalEscalationPolicy` implementing `GoalEscalationPolicy`

- [ ] **Step 1: Write test — alignment produces escalation**

```java
package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.*;
import io.casehub.blocks.agentic.social.narrative.*;
import io.casehub.eidos.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class NarrativeGoalEscalationPolicyTest {

    private NarrativeGoalEscalationPolicy policy;
    private GoalEscalationConfig config;

    @BeforeEach
    void setUp() {
        config = GoalEscalationConfig.defaults();
        policy = new NarrativeGoalEscalationPolicy(config);
    }

    @Test
    void escalates_whenThemeAligns() {
        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "explorer-identity", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5), List.of());
        var narrative = narrativeWith(theme);
        var proposal = new DriveGoalProposal(DriveAxis.CURIOSITY,
                "explore-gaps", "desc", "reason", 0.7);
        var context = new GoalEscalationContext(narrative, testProfile(), descriptorWithGoals());

        var result = policy.evaluate(proposal, context);

        assertThat(result).isNotNull();
        assertThat(result.priority()).isEqualTo(GoalPriority.PRIMARY);
        assertThat(result.themeLabel()).isEqualTo("explorer-identity");
    }
}
```

Include helper methods:
```java
    private NarrativeState narrativeWith(DerivedTheme... themes) {
        List<NarrativeFragment> fragments = new ArrayList<>(List.of(themes));
        return new NarrativeState("a1", "t1", NarrativeScope.INDIVIDUAL,
                fragments, Instant.now(), 5);
    }

    private DriveProfile testProfile() {
        return new DriveProfile("a1", "t1",
                Map.of(DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.7, "test")),
                0.7, DriveAxis.CURIOSITY, Instant.now());
    }

    private AgentDescriptor descriptorWithGoals(AgentGoal... goals) {
        return AgentDescriptor.builder()
                .agentId("a1").name("Agent").slot("default").tenancyId("t1")
                .goals(List.of(goals)).build();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=NarrativeGoalEscalationPolicyTest`
Expected: FAIL — class not found

- [ ] **Step 3: Implement NarrativeGoalEscalationPolicy**

```java
package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.GoalPriority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
public class NarrativeGoalEscalationPolicy implements GoalEscalationPolicy {

    private final GoalEscalationConfig config;

    @Inject
    public NarrativeGoalEscalationPolicy(GoalEscalationConfig config) {
        this.config = config;
    }

    @Override
    public @Nullable EscalationResult evaluate(DriveGoalProposal proposal,
                                                GoalEscalationContext context) {
        if (countPrimaryDriveGoals(context.descriptor()) >= config.maxPrimaryDriveGoals()) {
            return null;
        }

        DerivedTheme bestTheme = null;
        double bestScore = 0.0;

        for (var theme : context.narrative().themes()) {
            if (theme.salience() <= config.escalationSalienceThreshold()) continue;

            Double weight = theme.axisModulationWeights().get(proposal.axis());
            if (weight == null || weight <= config.minAxisAlignmentWeight()) continue;

            double score = theme.salience() * weight;
            if (score > bestScore) {
                bestScore = score;
                bestTheme = theme;
            }
        }

        if (bestTheme == null) return null;

        Double bestWeight = bestTheme.axisModulationWeights().get(proposal.axis());
        return new EscalationResult(
                GoalPriority.PRIMARY,
                bestTheme.label(),
                "goal aligns with identity theme '" + bestTheme.label()
                + "' (salience=" + String.format("%.2f", bestTheme.salience())
                + ", axisWeight=" + String.format("%.2f", bestWeight) + ")");
    }

    private int countPrimaryDriveGoals(io.casehub.eidos.api.AgentDescriptor descriptor) {
        int count = 0;
        for (AgentGoal goal : descriptor.goals()) {
            if (goal.priority() == GoalPriority.PRIMARY
                && goal.attributes() != null
                && "drive".equals(goal.attributes().get("source"))) {
                count++;
            }
        }
        return count;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest=NarrativeGoalEscalationPolicyTest#escalates_whenThemeAligns`
Expected: PASS

- [ ] **Step 5: Write remaining tests**

Add these test methods to NarrativeGoalEscalationPolicyTest:

```java
    @Test
    void noEscalation_whenSalienceBelowThreshold() {
        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "weak-theme", 0.3,
                Map.of(DriveAxis.CURIOSITY, 0.5), List.of());
        var context = new GoalEscalationContext(narrativeWith(theme), testProfile(), descriptorWithGoals());
        var proposal = new DriveGoalProposal(DriveAxis.CURIOSITY, "g", "d", "r", 0.7);

        assertThat(policy.evaluate(proposal, context)).isNull();
    }

    @Test
    void noEscalation_whenAxisWeightNegative() {
        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "suppressor", 0.9,
                Map.of(DriveAxis.CURIOSITY, -0.5), List.of());
        var context = new GoalEscalationContext(narrativeWith(theme), testProfile(), descriptorWithGoals());
        var proposal = new DriveGoalProposal(DriveAxis.CURIOSITY, "g", "d", "r", 0.7);

        assertThat(policy.evaluate(proposal, context)).isNull();
    }

    @Test
    void noEscalation_whenAxisWeightBelowThreshold() {
        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "weak-axis", 0.9,
                Map.of(DriveAxis.CURIOSITY, 0.1), List.of());
        var context = new GoalEscalationContext(narrativeWith(theme), testProfile(), descriptorWithGoals());
        var proposal = new DriveGoalProposal(DriveAxis.CURIOSITY, "g", "d", "r", 0.7);

        assertThat(policy.evaluate(proposal, context)).isNull();
    }

    @Test
    void noEscalation_whenPrimaryCapReached() {
        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "strong", 0.9,
                Map.of(DriveAxis.CURIOSITY, 0.8), List.of());
        var existingPrimary = new AgentGoal("existing", "desc", GoalPriority.PRIMARY,
                Visibility.PUBLIC, List.of(), Map.of("source", "drive", "driveAxis", "COMPETENCE"));
        var context = new GoalEscalationContext(narrativeWith(theme), testProfile(),
                descriptorWithGoals(existingPrimary));
        var proposal = new DriveGoalProposal(DriveAxis.CURIOSITY, "g", "d", "r", 0.7);

        assertThat(policy.evaluate(proposal, context)).isNull();
    }

    @Test
    void selectsStrongestTheme_whenMultipleQualify() {
        var weakTheme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "marginal", 0.65, Map.of(DriveAxis.CURIOSITY, 0.35), List.of());
        var strongTheme = new DerivedTheme("t2", Instant.now(), null, List.of(),
                "dominant", 0.95, Map.of(DriveAxis.CURIOSITY, 0.8), List.of());
        var context = new GoalEscalationContext(
                narrativeWith(weakTheme, strongTheme), testProfile(), descriptorWithGoals());
        var proposal = new DriveGoalProposal(DriveAxis.CURIOSITY, "g", "d", "r", 0.7);

        var result = policy.evaluate(proposal, context);

        assertThat(result).isNotNull();
        assertThat(result.themeLabel()).isEqualTo("dominant");
    }

    @Test
    void noEscalation_whenNoThemes() {
        var context = new GoalEscalationContext(narrativeWith(), testProfile(), descriptorWithGoals());
        var proposal = new DriveGoalProposal(DriveAxis.CURIOSITY, "g", "d", "r", 0.7);

        assertThat(policy.evaluate(proposal, context)).isNull();
    }
```

- [ ] **Step 6: Run all policy tests**

Run: `mvn --batch-mode test -pl blocks -Dtest=NarrativeGoalEscalationPolicyTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git -C blocks add .
git commit -m "feat(#166): NarrativeGoalEscalationPolicy — strongest-theme alignment with cap"
```

---

## Batch 3: Orchestrator Pipeline

### Task 4: Cross-axis composition + NarrativeOrchestrator dependency

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/GoalProposalOrchestrator.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/goal/GoalProposalOrchestratorTest.java`

**Interfaces:**
- Consumes: CrossAxisGoalEnricher SPI (optional), GoalEscalationConfig, NarrativeOrchestrator (optional Instance<>)
- Consumes: DerivedTheme.axisModulationWeights, NarrativeState.themes()
- Produces: Cross-axis DriveGoalProposals with `crossAxisWeights` in proposalAttributes

- [ ] **Step 1: Write cross-axis detection test**

```java
    @Test
    void crossAxis_detectsMultiAxisTheme() {
        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "connector", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, 0.4),
                List.of());
        setupNarrative(narrativeWith(theme));
        setupDrives("a1", "t1",
                Map.of(DriveAxis.CURIOSITY, 0.7, DriveAxis.AFFILIATION, 0.6));

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());

        assertThat(tick).isInstanceOf(GoalProposalTick.Changes.class);
        var changes = (GoalProposalTick.Changes) tick;
        var crossAxis = changes.newProposals().stream()
                .filter(p -> p.proposalAttributes() != null
                        && p.proposalAttributes().containsKey("crossAxisWeights"))
                .toList();
        assertThat(crossAxis).hasSize(1);
        assertThat(crossAxis.get(0).axis()).isEqualTo(DriveAxis.CURIOSITY);
    }
```

Helper methods needed:
```java
    private NarrativeOrchestrator narrativeOrchestrator;

    // In setUp, create orchestrator with narrative dependency:
    Instance<NarrativeOrchestrator> narrativeInstance = mock(Instance.class);
    narrativeOrchestrator = mock(NarrativeOrchestrator.class);
    when(narrativeInstance.isResolvable()).thenReturn(true);
    when(narrativeInstance.get()).thenReturn(narrativeOrchestrator);

    // New helper:
    private void setupNarrative(NarrativeState state) {
        when(narrativeOrchestrator.currentNarrative("a1", "t1"))
                .thenReturn(Optional.of(state));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalProposalOrchestratorTest#crossAxis_detectsMultiAxisTheme`
Expected: FAIL — constructor mismatch or cross-axis not implemented

- [ ] **Step 3: Add NarrativeOrchestrator dependency + cross-axis composition to orchestrator**

Modify GoalProposalOrchestrator:

1. Add constructor parameters: `Instance<NarrativeOrchestrator> narrativeOrchestrator`, `GoalEscalationConfig escalationConfig`
2. Add new test constructor accepting these as well
3. Add private method `evaluateCrossAxis()`:

```java
    private List<DriveGoalProposal> evaluateCrossAxis(String agentId, String tenantId,
                                                       DriveProfile profile,
                                                       NarrativeState narrative) {
        List<DriveGoalProposal> compounds = new ArrayList<>();
        for (var theme : narrative.themes()) {
            var positiveAxes = theme.axisModulationWeights().entrySet().stream()
                    .filter(e -> e.getValue() > escalationConfig.crossAxisMinWeight())
                    .sorted(Map.Entry.<DriveAxis, Double>comparingByValue().reversed())
                    .toList();

            if (positiveAxes.size() < escalationConfig.minCrossAxisCount()) continue;

            DriveAxis dominant = positiveAxes.get(0).getKey();
            DriveAxis secondary = positiveAxes.get(1).getKey();

            var dominantIntensity = profile.drives().get(dominant);
            if (dominantIntensity == null) continue;

            String goalName = "compound-" + dominant.name().toLowerCase()
                    + "-" + secondary.name().toLowerCase()
                    + "-" + theme.label().replaceAll("[^a-zA-Z0-9-]", "").toLowerCase();

            var weightsStr = positiveAxes.stream()
                    .map(e -> e.getKey().name() + ":" + String.format("%.2f", e.getValue()))
                    .collect(Collectors.joining(","));

            var attrs = Map.of("crossAxisWeights", weightsStr);

            var proposal = new DriveGoalProposal(dominant, goalName,
                    "Compound goal: " + theme.label() + " across " + dominant + " and " + secondary,
                    "cross-axis theme '" + theme.label() + "' with weights: " + weightsStr,
                    dominantIntensity.intensity(), null, attrs);

            if (crossAxisEnricher != null) {
                var enriched = crossAxisEnricher.enrich(proposal, narrative, theme);
                if (enriched != null) proposal = enriched;
            }

            compounds.add(proposal);
        }
        return compounds;
    }
```

4. Wire into `doTick()` after `evaluateMappers()`: merge cross-axis proposals into the proposals list before ranking.

- [ ] **Step 4: Write test for cross-axis with negative weight (not cross-axis)**

```java
    @Test
    void crossAxis_ignoresNegativeWeights() {
        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "suppressor", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, -0.4),
                List.of());
        setupNarrative(narrativeWith(theme));
        setupDrives("a1", "t1", Map.of(DriveAxis.CURIOSITY, 0.7));

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());

        var changes = (GoalProposalTick.Changes) tick;
        var crossAxis = changes.newProposals().stream()
                .filter(p -> p.proposalAttributes() != null
                        && p.proposalAttributes().containsKey("crossAxisWeights"))
                .toList();
        assertThat(crossAxis).isEmpty();
    }
```

- [ ] **Step 5: Write test for narrative absent (phases 2-4 skipped)**

```java
    @Test
    void narrativeAbsent_skipsEscalationPhases() {
        when(narrativeOrchestrator.currentNarrative("a1", "t1"))
                .thenReturn(Optional.empty());
        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        when(curiosityMapper.evaluate(eq("a1"), eq("t1"), any()))
                .thenReturn(new DriveGoalProposal(DriveAxis.CURIOSITY,
                        "explore", "desc", "reason", 0.7));

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());

        var changes = (GoalProposalTick.Changes) tick;
        assertThat(changes.newProposals()).hasSize(1);
        assertThat(changes.newProposals().get(0).suggestedPriority()).isNull();
    }
```

- [ ] **Step 6: Run all tests**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalProposalOrchestratorTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git -C blocks add .
git commit -m "feat(#159): cross-axis composition + NarrativeOrchestrator dependency"
```

### Task 5: Escalation + demotion + synthesis-cycle tracking

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/GoalProposalOrchestrator.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/goal/GoalProposalOrchestratorTest.java`

**Interfaces:**
- Consumes: GoalEscalationPolicy (Instance<>), GoalEscalationConfig, NarrativeState, PriorityAdjustment, GovernanceAttributeUpdate
- Produces: Escalated DriveGoalProposals with `suggestedPriority = PRIMARY` + provenance attributes
- Produces: PriorityAdjustment demotions + GovernanceAttributeUpdate signals in Changes

- [ ] **Step 1: Write escalation test — proposal escalated after 2 synthesis cycles**

```java
    @Test
    void escalation_afterSustainedAlignment() {
        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "explorer", 0.8, Map.of(DriveAxis.CURIOSITY, 0.5), List.of());
        var synthesisAt1 = Instant.parse("2026-08-23T12:00:00Z");
        var synthesisAt2 = Instant.parse("2026-08-23T13:00:00Z");
        var synthesisAt3 = Instant.parse("2026-08-23T14:00:00Z");

        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        when(curiosityMapper.evaluate(eq("a1"), eq("t1"), any()))
                .thenReturn(new DriveGoalProposal(DriveAxis.CURIOSITY,
                        "explore", "desc", "reason", 0.7));

        // Tick 1: first synthesis — cycle count 1, not yet escalated
        setupNarrative(narrativeWithSynthesisAt(theme, synthesisAt1));
        var tick1 = orchestrator.tick("a1", "t1", descriptorWithGoals());
        assertThat(((GoalProposalTick.Changes) tick1).newProposals().get(0)
                .suggestedPriority()).isNull();

        // Tick 2: second synthesis — cycle count 2, escalated
        advanceClock(Duration.ofMinutes(61));
        setupNarrative(narrativeWithSynthesisAt(theme, synthesisAt2));
        var tick2 = orchestrator.tick("a1", "t1", descriptorWithGoals());
        assertThat(((GoalProposalTick.Changes) tick2).newProposals().get(0)
                .suggestedPriority()).isEqualTo(GoalPriority.PRIMARY);
        assertThat(((GoalProposalTick.Changes) tick2).newProposals().get(0)
                .proposalAttributes()).containsEntry("escalatedBy", "narrative");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalProposalOrchestratorTest#escalation_afterSustainedAlignment`
Expected: FAIL — escalation not implemented

- [ ] **Step 3: Implement escalation evaluation (Phase 3)**

Add to GoalProposalOrchestrator:

1. `Instance<GoalEscalationPolicy> escalationPolicy` constructor parameter
2. Inner tracking class for escalation state:
```java
    static final class EscalationTracker {
        Instant lastCheckedSynthesisAt;
        int alignedCycleCount;
    }
```
3. `ConcurrentHashMap<String, Map<String, EscalationTracker>>` keyed by agent|tenant, then goalName
4. Private method `evaluateEscalation()` — for each proposal:
   - Check if NarrativeState.synthesisedAt() has advanced (new synthesis cycle)
   - Call `GoalEscalationPolicy.evaluate()` — if null, reset tracker
   - If non-null and new synthesis, increment cycle count
   - If cycle count >= escalationCycles, set suggestedPriority + proposalAttributes
5. Same-tick cap: after all proposals evaluated, if multiple have PRIMARY, keep only highest-intensity

- [ ] **Step 4: Write demotion test — PRIMARY goal demoted after alignment loss**

```java
    @Test
    void demotion_afterSustainedMisalignment() {
        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "explorer", 0.8, Map.of(DriveAxis.CURIOSITY, 0.5), List.of());
        var existingPrimary = new AgentGoal("explore", "desc", GoalPriority.PRIMARY,
                Visibility.PUBLIC, List.of(),
                Map.of("source", "drive", "driveAxis", "CURIOSITY",
                       "escalatedBy", "narrative", "escalation.theme", "explorer",
                       "escalation.lastAlignedSynthesisAt", "2026-08-23T12:00:00Z"));

        // Tick 1: theme faded — first misaligned synthesis
        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        setupNarrative(narrativeWithSynthesisAt(Instant.parse("2026-08-23T13:00:00Z")));
        var tick1 = orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));
        assertThat(((GoalProposalTick.Changes) tick1).priorityAdjustments()).isEmpty();

        // Tick 2: still misaligned — second synthesis, demoted
        advanceClock(Duration.ofMinutes(61));
        setupNarrative(narrativeWithSynthesisAt(Instant.parse("2026-08-23T14:00:00Z")));
        var tick2 = orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));
        assertThat(((GoalProposalTick.Changes) tick2).priorityAdjustments()).hasSize(1);
        assertThat(((GoalProposalTick.Changes) tick2).priorityAdjustments().get(0)
                .newPriority()).isEqualTo(GoalPriority.SECONDARY);
    }
```

- [ ] **Step 5: Implement demotion evaluation (Phase 4)**

Add private method `evaluateDemotions()`:
- Iterate existing PRIMARY drive-sourced goals from AgentDescriptor
- Read `escalation.theme` from goal attributes — theme-specific demotion
- Check if the named theme still qualifies (salience + axis weight)
- Track misaligned synthesis cycles using `escalation.lastAlignedSynthesisAt` from attributes
- When misaligned cycle count >= demotionCycles, emit PriorityAdjustment
- Emit GovernanceAttributeUpdate to persist tracking state

Wire into `doTick()` after escalation evaluation.

- [ ] **Step 6: Write test — alignment reset clears demotion counter**

```java
    @Test
    void demotion_resetWhenAlignmentRestored() {
        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "explorer", 0.8, Map.of(DriveAxis.CURIOSITY, 0.5), List.of());
        var existingPrimary = new AgentGoal("explore", "desc", GoalPriority.PRIMARY,
                Visibility.PUBLIC, List.of(),
                Map.of("source", "drive", "driveAxis", "CURIOSITY",
                       "escalatedBy", "narrative", "escalation.theme", "explorer",
                       "escalation.lastAlignedSynthesisAt", "2026-08-23T12:00:00Z"));

        // Tick 1: misaligned
        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        setupNarrative(narrativeWithSynthesisAt(Instant.parse("2026-08-23T13:00:00Z")));
        orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));

        // Tick 2: alignment restored — counter resets
        advanceClock(Duration.ofMinutes(61));
        setupNarrative(narrativeWithSynthesisAt(theme, Instant.parse("2026-08-23T14:00:00Z")));
        var tick2 = orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));
        assertThat(((GoalProposalTick.Changes) tick2).priorityAdjustments()).isEmpty();

        // Tick 3: misaligned again — only 1 cycle, not demoted
        advanceClock(Duration.ofMinutes(61));
        setupNarrative(narrativeWithSynthesisAt(Instant.parse("2026-08-23T15:00:00Z")));
        var tick3 = orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));
        assertThat(((GoalProposalTick.Changes) tick3).priorityAdjustments()).isEmpty();
    }
```

- [ ] **Step 7: Write test — same-tick cap enforcement**

```java
    @Test
    void sameTick_capEnforcement_onlyHighestIntensityEscalated() {
        // Two themes aligning with different axes
        var theme1 = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "explorer", 0.9, Map.of(DriveAxis.CURIOSITY, 0.8), List.of());
        var theme2 = new DerivedTheme("t2", Instant.now(), null, List.of(),
                "connector", 0.9, Map.of(DriveAxis.AFFILIATION, 0.7), List.of());
        // ... setup both mappers, both drives above threshold, 
        //     pre-warm escalation trackers to cycle count 2
        // Assert: only the higher-intensity proposal has PRIMARY
    }
```

- [ ] **Step 8: Run all tests**

Run: `mvn --batch-mode test -pl blocks -Dtest=GoalProposalOrchestratorTest`
Expected: PASS

- [ ] **Step 9: Run diagnostics**

Use `ide_diagnostics` to verify blocks compiles cleanly.

- [ ] **Step 10: Commit**

```bash
git -C blocks add .
git commit -m "feat(#166): escalation + demotion + synthesis-cycle tracking in GoalProposalOrchestrator"
```

---

## Batch 4: Engine Guard + LLM Enricher

### Task 6: DefaultGoalFormationService provenance guard

**Files:**
- Modify: `engine/runtime/src/main/java/io/casehub/engine/internal/routing/DefaultGoalFormationService.java`
- Modify: `engine/runtime/src/test/java/io/casehub/engine/internal/routing/DefaultGoalFormationServiceTest.java`

**Interfaces:**
- Consumes: GoalFormationProposal.ProposedGoal.attributes, GoalPriority
- Produces: Rejection with reason "drive-sourced PRIMARY requires escalation provenance"

- [ ] **Step 1: Write guard test**

```java
    @Test
    void rejectsPrimary_driveSourced_withoutProvenance() {
        registerAgent("a1", "t1");
        var proposal = new GoalFormationProposal(
                List.of(new GoalFormationProposal.ProposedGoal(
                        "explore", "desc", GoalPriority.PRIMARY, "reason",
                        Map.of("source", "drive", "driveAxis", "CURIOSITY"))),
                "test");

        var result = service.propose("a1", "t1", proposal);

        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).reason())
                .contains("escalation provenance");
    }

    @Test
    void acceptsPrimary_driveSourced_withProvenance() {
        registerAgent("a1", "t1");
        var proposal = new GoalFormationProposal(
                List.of(new GoalFormationProposal.ProposedGoal(
                        "explore", "desc", GoalPriority.PRIMARY, "reason",
                        Map.of("source", "drive", "driveAxis", "CURIOSITY",
                               "escalatedBy", "narrative"))),
                "test");

        var result = service.propose("a1", "t1", proposal);

        assertThat(result.registered()).hasSize(1);
    }

    @Test
    void acceptsPrimary_nonDriveSourced() {
        registerAgent("a1", "t1");
        var proposal = new GoalFormationProposal(
                List.of(new GoalFormationProposal.ProposedGoal(
                        "case-goal", "desc", GoalPriority.PRIMARY, "reason", null)),
                "test");

        var result = service.propose("a1", "t1", proposal);

        assertThat(result.registered()).hasSize(1);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest=DefaultGoalFormationServiceTest#rejectsPrimary_driveSourced_withoutProvenance`
Expected: FAIL — guard not implemented

- [ ] **Step 3: Add provenance guard to DefaultGoalFormationService**

Insert the guard check in the `propose()` method's validation loop, after the existing checks (name length, description length, deduplication) and before goal registration:

```java
      if (p.suggestedPriority() == GoalPriority.PRIMARY
          && p.attributes() != null
          && "drive".equals(p.attributes().get("source"))
          && !p.attributes().containsKey("escalatedBy")) {
        rejected.add(new GoalFormationResult.RejectedGoal(
            p.name(), "drive-sourced PRIMARY requires escalation provenance"));
        continue;
      }
```

- [ ] **Step 4: Run all DefaultGoalFormationService tests**

Run: `mvn --batch-mode test -pl blocks -Dtest=DefaultGoalFormationServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git -C engine add .
git commit -m "feat(#166): provenance guard — reject PRIMARY drive-sourced goals without escalatedBy  Refs casehubio/blocks#166"
```

### Task 7: LlmCrossAxisGoalEnricher

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/goal/LlmCrossAxisGoalEnricher.java`
- Create: `blocks/src/test/java/io/casehub/blocks/agentic/social/goal/LlmCrossAxisGoalEnricherTest.java`

**Interfaces:**
- Consumes: CrossAxisGoalEnricher SPI, AgentProvider, DriveGoalProposal, NarrativeState, DerivedTheme
- Produces: Enriched DriveGoalProposal with LLM-generated goalDescription + formationReason

- [ ] **Step 1: Write test — enriches description**

```java
    @Test
    void enriches_compoundGoalDescription() {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.ask(anyString(), anyString()))
                .thenReturn("Explore knowledge gaps in physics by engaging with Dr. Smith");
        var enricher = new LlmCrossAxisGoalEnricher(agentProvider);

        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "curious-connector", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, 0.4), List.of());
        var heuristic = new DriveGoalProposal(DriveAxis.CURIOSITY,
                "compound-curiosity-affiliation-curious-connector",
                "Compound goal: curious-connector across CURIOSITY and AFFILIATION",
                "cross-axis", 0.7, null, Map.of("crossAxisWeights", "CURIOSITY:0.50,AFFILIATION:0.40"));
        var narrative = new NarrativeState("a1", "t1", NarrativeScope.INDIVIDUAL,
                List.of(theme), Instant.now(), 5);

        var result = enricher.enrich(heuristic, narrative, theme);

        assertThat(result).isNotNull();
        assertThat(result.goalDescription()).contains("physics");
        assertThat(result.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(result.driveIntensity()).isEqualTo(0.7);
    }
```

- [ ] **Step 2: Write test — graceful degradation on LLM failure**

```java
    @Test
    void returnsNull_onLlmFailure() {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.ask(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM timeout"));
        var enricher = new LlmCrossAxisGoalEnricher(agentProvider);

        var result = enricher.enrich(someProposal(), someNarrative(), someTheme());

        assertThat(result).isNull();
    }
```

- [ ] **Step 3: Implement LlmCrossAxisGoalEnricher**

```java
@ApplicationScoped
public class LlmCrossAxisGoalEnricher implements CrossAxisGoalEnricher {

    private final AgentProvider agentProvider;

    @Inject
    public LlmCrossAxisGoalEnricher(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public @Nullable DriveGoalProposal enrich(DriveGoalProposal heuristic,
                                               NarrativeState narrative,
                                               DerivedTheme sourceTheme) {
        try {
            String prompt = buildPrompt(heuristic, narrative, sourceTheme);
            String response = agentProvider.ask(
                    "You are a goal formation analyst. Generate a concise, specific "
                    + "compound goal description that combines the given drive axes.",
                    prompt);
            if (response == null || response.isBlank()) return null;

            return new DriveGoalProposal(heuristic.axis(), heuristic.goalName(),
                    response.trim(), "LLM-enriched cross-axis: " + sourceTheme.label(),
                    heuristic.driveIntensity(), heuristic.suggestedPriority(),
                    heuristic.proposalAttributes());
        } catch (Exception e) {
            return null;
        }
    }

    private String buildPrompt(DriveGoalProposal proposal, NarrativeState narrative,
                                DerivedTheme theme) {
        var sb = new StringBuilder();
        sb.append("Theme: ").append(theme.label())
          .append(" (salience: ").append(String.format("%.2f", theme.salience())).append(")\n");
        sb.append("Axes: ").append(theme.axisModulationWeights()).append("\n");
        sb.append("Current goal: ").append(proposal.goalDescription()).append("\n");
        var episodes = narrative.episodes();
        if (!episodes.isEmpty()) {
            sb.append("Recent episodes:\n");
            episodes.stream().limit(3).forEach(e ->
                    sb.append("- ").append(e.description()).append("\n"));
        }
        sb.append("\nGenerate a single sentence describing a compound goal that combines these axes.");
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `mvn --batch-mode test -pl blocks -Dtest=LlmCrossAxisGoalEnricherTest`
Expected: PASS

- [ ] **Step 5: Run full blocks test suite**

Run: `mvn --batch-mode test -pl blocks`
Expected: PASS — all existing + new tests pass

- [ ] **Step 6: Commit**

```bash
git -C blocks add .
git commit -m "feat(#159): LlmCrossAxisGoalEnricher — LLM-enriched compound goal descriptions"
```

---

## References

- [specs/issue-166-goal-priority-escalation/2026-09-01-goal-priority-escalation-cross-axis-design.md] — design spec
- [specs/issue-166-goal-priority-escalation/decisions.md] — 8 validated decisions (D1-D8)
- [GoalProposalOrchestrator.java:78-194] — existing doTick/evaluateMappers flow
- [NarrativeGoalEscalationPolicy] — strongest-theme alignment with count-based cap
- [DefaultGoalFormationService.java:63-115] — existing propose() validation loop
- [GoalProposalOrchestratorTest.java:29-416] — existing test patterns and helpers
- [DriveGoalProposal.java] — existing record (modified with 2 new fields)
- [GoalProposalTick.java] — existing sealed interface (Proposed → Changes rename)
- [DerivedTheme.java] — salience + axisModulationWeights
- [NarrativeModulation.java] — NarrativeState → modulation conversion precedent
- [GitHub #166] — GoalPriorityEscalationPolicy — governed narrative-driven priority escalation
- [GitHub #160] — PRIMARY escalation governance — drive-sourced goal priority elevation
- [GitHub #159] — Cross-axis goal composition — compound goals from multiple drives
