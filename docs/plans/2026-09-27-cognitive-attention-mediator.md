# CognitiveAttentionMediator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #304 — CognitiveAttentionMediator — bridge neocortex attention signals to CognitionCore
**Issue group:** #304

**Goal:** Bridge neocortex's `CognitiveAttentionRequired` CDI events into CognitionCore's tick/prompt lifecycle so agents receive attention signals in their cognitive prompt.

**Architecture:** A CDI mediator bean observes attention events from neocortex and queues them per-principal. CognitionCore drains the queue during its FOUNDATION tick phase, stores the briefing, and renders it as a prompt section. An `AttentionRelevance` utility provides type-safe signal-to-domain mapping for section gating overrides.

**Tech Stack:** Java 21, Quarkus CDI, JUnit 5, AssertJ, Mockito

## Global Constraints

- All new classes in `blocks-core` module, package `io.casehub.blocks.agentic.social` (or `.prompt` for the prompt section)
- CDI wiring in `blocks` module (`BlocksBeans`)
- Types `AttentionBriefing`, `AttentionSignal`, `CognitiveAttentionRequired`, `SignalCategory` are from neocortex `mindmap-api` — already a dependency
- Follow existing CognitionCore patterns: nullable fields, constructor chaining, `safeRun()` for fault isolation
- Build: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -pl blocks-core,blocks`

---

## Batch 1: Foundation — Mediator + Config + Relevance

After this batch: the three standalone components compile and are fully tested, but CognitionCore doesn't use them yet.

### Task 1: CognitiveAttentionMediator

**Files:**
- Create: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitiveAttentionMediator.java`
- Test: `blocks-core/src/test/java/io/casehub/blocks/agentic/social/CognitiveAttentionMediatorTest.java`

**Interfaces:**
- Consumes: `AttentionBriefing`, `CognitiveAttentionRequired`, `SignalCategory` from `io.casehub.neocortex.mindmap`
- Produces: `CognitiveAttentionMediator` with `drainAttention(String principalId) → Optional<AttentionBriefing>`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.CognitiveAttentionRequired;
import io.casehub.neocortex.mindmap.SignalCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveAttentionMediatorTest {

    private final CognitiveAttentionMediator mediator = new CognitiveAttentionMediator();

    @Test
    void drainReturnsEmptyWhenNoEvents() {
        assertThat(mediator.drainAttention("agent1")).isEmpty();
    }

    @Test
    void drainReturnsBriefingAfterEvent() {
        var briefing = briefing("agent1", "tenant1", SignalCategory.URGENCY_SPIKE, 0.9);
        mediator.onAttentionRequired(new CognitiveAttentionRequired(briefing, Instant.now()));

        var result = mediator.drainAttention("agent1");
        assertThat(result).isPresent();
        assertThat(result.get().signals()).hasSize(1);
        assertThat(result.get().signals().get(0).category()).isEqualTo(SignalCategory.URGENCY_SPIKE);
    }

    @Test
    void drainClearsQueue() {
        var briefing = briefing("agent1", "tenant1", SignalCategory.URGENCY_SPIKE, 0.9);
        mediator.onAttentionRequired(new CognitiveAttentionRequired(briefing, Instant.now()));

        mediator.drainAttention("agent1");
        assertThat(mediator.drainAttention("agent1")).isEmpty();
    }

    @Test
    void perPrincipalIsolation() {
        mediator.onAttentionRequired(new CognitiveAttentionRequired(
            briefing("agent1", "t", SignalCategory.URGENCY_SPIKE, 0.9), Instant.now()));
        mediator.onAttentionRequired(new CognitiveAttentionRequired(
            briefing("agent2", "t", SignalCategory.DECAY_DETECTED, 0.5), Instant.now()));

        var r1 = mediator.drainAttention("agent1");
        var r2 = mediator.drainAttention("agent2");
        assertThat(r1).isPresent();
        assertThat(r1.get().signals().get(0).category()).isEqualTo(SignalCategory.URGENCY_SPIKE);
        assertThat(r2).isPresent();
        assertThat(r2.get().signals().get(0).category()).isEqualTo(SignalCategory.DECAY_DETECTED);
    }

    @Test
    void mergeMultipleBriefings() {
        var earlier = Instant.parse("2026-09-27T10:00:00Z");
        var later = Instant.parse("2026-09-27T10:05:00Z");

        var b1 = new AttentionBriefing("agent1", "t",
            List.of(signal(SignalCategory.URGENCY_SPIKE, "node1", "Goal A", 0.9, "deadline")),
            0.7, earlier);
        var b2 = new AttentionBriefing("agent1", "t",
            List.of(signal(SignalCategory.DECAY_DETECTED, "node2", "Spanish", 0.5, "no activity")),
            0.85, later);

        mediator.onAttentionRequired(new CognitiveAttentionRequired(b1, earlier));
        mediator.onAttentionRequired(new CognitiveAttentionRequired(b2, later));

        var result = mediator.drainAttention("agent1").orElseThrow();
        assertThat(result.urgencyP75()).isEqualTo(0.85);
        assertThat(result.generatedAt()).isEqualTo(later);
        assertThat(result.signals()).hasSize(2);
        assertThat(result.signals().get(0).significance())
            .isGreaterThanOrEqualTo(result.signals().get(1).significance());
    }

    @Test
    void mergeDeduplicatesBySourceNodeIdAndCategory() {
        var b1 = new AttentionBriefing("agent1", "t",
            List.of(signal(SignalCategory.URGENCY_SPIKE, "node1", "Goal A", 0.7, "earlier")),
            0.7, Instant.now());
        var b2 = new AttentionBriefing("agent1", "t",
            List.of(signal(SignalCategory.URGENCY_SPIKE, "node1", "Goal A", 0.9, "later")),
            0.8, Instant.now().plusSeconds(10));

        mediator.onAttentionRequired(new CognitiveAttentionRequired(b1, Instant.now()));
        mediator.onAttentionRequired(new CognitiveAttentionRequired(b2, Instant.now()));

        var result = mediator.drainAttention("agent1").orElseThrow();
        assertThat(result.signals()).hasSize(1);
        assertThat(result.signals().get(0).significance()).isEqualTo(0.9);
    }

    private static AttentionBriefing briefing(String principalId, String tenantId,
                                               SignalCategory category, double significance) {
        return new AttentionBriefing(principalId, tenantId,
            List.of(signal(category, "node-1", "Source", significance, "reason")),
            significance, Instant.now());
    }

    private static AttentionSignal signal(SignalCategory category, String nodeId,
                                           String name, double significance, String reason) {
        return new AttentionSignal("agent1", "t", category, nodeId, name, significance, reason);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks-core -Dtest=CognitiveAttentionMediatorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error — `CognitiveAttentionMediator` does not exist

- [ ] **Step 3: Implement CognitiveAttentionMediator**

Use `ide_create_file` to create the mediator class:

```java
package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.CognitiveAttentionRequired;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApplicationScoped
public class CognitiveAttentionMediator {

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<AttentionBriefing>>
        attentionQueues = new ConcurrentHashMap<>();

    void onAttentionRequired(@Observes CognitiveAttentionRequired event) {
        attentionQueues
            .computeIfAbsent(event.briefing().principalId(),
                k -> new ConcurrentLinkedQueue<>())
            .add(event.briefing());
    }

    public Optional<AttentionBriefing> drainAttention(String principalId) {
        var queue = attentionQueues.get(principalId);
        if (queue == null || queue.isEmpty()) return Optional.empty();
        List<AttentionBriefing> drained = new ArrayList<>();
        AttentionBriefing b;
        while ((b = queue.poll()) != null) drained.add(b);
        if (drained.isEmpty()) return Optional.empty();
        if (drained.size() == 1) return Optional.of(drained.getFirst());
        return Optional.of(merge(drained));
    }

    private static AttentionBriefing merge(List<AttentionBriefing> briefings) {
        var last = briefings.getLast();
        var deduped = new LinkedHashMap<String, AttentionSignal>();
        for (var briefing : briefings) {
            for (var signal : briefing.signals()) {
                var key = signal.sourceNodeId() + ":" + signal.category().name();
                deduped.merge(key, signal, (existing, incoming) ->
                    incoming.significance() >= existing.significance() ? incoming : existing);
            }
        }
        var signals = new ArrayList<>(deduped.values());
        signals.sort(Comparator.comparingDouble(AttentionSignal::significance).reversed());
        return new AttentionBriefing(
            last.principalId(), last.tenantId(), signals, last.urgencyP75(), last.generatedAt());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks-core -Dtest=CognitiveAttentionMediatorTest`
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/blocks add blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitiveAttentionMediator.java blocks-core/src/test/java/io/casehub/blocks/agentic/social/CognitiveAttentionMediatorTest.java
git -C /Users/mdproctor/claude/casehub/slots/203/blocks commit -m "feat(#304): CognitiveAttentionMediator — per-principal queue with merge semantics"
```

### Task 2: AttentionRelevance + CognitionConfig

**Files:**
- Create: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/AttentionRelevance.java`
- Modify: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionConfig.java`
- Test: `blocks-core/src/test/java/io/casehub/blocks/agentic/social/AttentionRelevanceTest.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionConfigTest.java`

**Interfaces:**
- Consumes: `SignalCategory`, `AttentionBriefing` from mindmap-api
- Produces: `AttentionRelevance.overrides(AttentionBriefing, Set<SignalCategory>) → boolean`; `CognitionConfig.attentionEnabled()` field

- [ ] **Step 1: Write failing tests for AttentionRelevance**

```java
package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.SignalCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttentionRelevanceTest {

    @Test
    void goalCategoriesOverrideGoals() {
        for (var category : List.of(
                SignalCategory.URGENCY_SPIKE, SignalCategory.GOAL_RECOGNIZED,
                SignalCategory.DECAY_DETECTED, SignalCategory.BLOCKER_RESOLVED,
                SignalCategory.PRIORITY_SHIFT)) {
            var briefing = briefingWith(category);
            assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.GOALS))
                .as("Expected %s to override GOALS", category)
                .isTrue();
        }
    }

    @Test
    void driveShiftOverridesDrives() {
        assertThat(AttentionRelevance.overrides(
            briefingWith(SignalCategory.DRIVE_SHIFT), AttentionRelevance.DRIVES)).isTrue();
    }

    @Test
    void affectChangeOverridesMood() {
        assertThat(AttentionRelevance.overrides(
            briefingWith(SignalCategory.AFFECT_CHANGE), AttentionRelevance.MOOD)).isTrue();
    }

    @Test
    void relationshipStageOverridesMentalModelAndUserModel() {
        var briefing = briefingWith(SignalCategory.RELATIONSHIP_STAGE);
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.MENTAL_MODEL)).isTrue();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.USER_MODEL)).isTrue();
    }

    @Test
    void beliefRevisedOverridesMentalModelButNotUserModel() {
        var briefing = briefingWith(SignalCategory.BELIEF_REVISED);
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.MENTAL_MODEL)).isTrue();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.USER_MODEL)).isFalse();
    }

    @Test
    void mergeCandidateOverridesNothing() {
        var briefing = briefingWith(SignalCategory.MERGE_CANDIDATE);
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.GOALS)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.DRIVES)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.MOOD)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.MENTAL_MODEL)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.USER_MODEL)).isFalse();
    }

    @Test
    void experienceGraduatedOverridesNothing() {
        var briefing = briefingWith(SignalCategory.EXPERIENCE_GRADUATED);
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.GOALS)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.DRIVES)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.MOOD)).isFalse();
    }

    private static AttentionBriefing briefingWith(SignalCategory category) {
        return new AttentionBriefing("agent", "tenant",
            List.of(new AttentionSignal("agent", "tenant", category,
                "node-1", "Source", 0.8, "reason")),
            0.8, Instant.now());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks-core -Dtest=AttentionRelevanceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error — `AttentionRelevance` does not exist

- [ ] **Step 3: Implement AttentionRelevance**

```java
package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.SignalCategory;

import java.util.EnumSet;
import java.util.Set;

import static io.casehub.neocortex.mindmap.SignalCategory.*;

final class AttentionRelevance {

    static final Set<SignalCategory> GOALS = EnumSet.of(
        URGENCY_SPIKE, GOAL_RECOGNIZED, DECAY_DETECTED,
        BLOCKER_RESOLVED, PRIORITY_SHIFT);

    static final Set<SignalCategory> DRIVES = EnumSet.of(DRIVE_SHIFT);

    static final Set<SignalCategory> MOOD = EnumSet.of(AFFECT_CHANGE);

    static final Set<SignalCategory> MENTAL_MODEL = EnumSet.of(
        RELATIONSHIP_STAGE, BELIEF_REVISED);

    static final Set<SignalCategory> USER_MODEL = EnumSet.of(RELATIONSHIP_STAGE);

    static boolean overrides(AttentionBriefing briefing, Set<SignalCategory> relevance) {
        return briefing.signals().stream()
            .anyMatch(s -> relevance.contains(s.category()));
    }

    private AttentionRelevance() {}
}
```

- [ ] **Step 4: Run AttentionRelevance tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks-core -Dtest=AttentionRelevanceTest`
Expected: All 7 tests PASS

- [ ] **Step 5: Update CognitionConfig — add attentionEnabled**

Use `ide_replace_member` to update the record declaration, adding `attentionEnabled` as the 13th field. Update `all()`, `none()`, `with()`, `withDirectives()`, and `without()` methods.

The record becomes:
```java
public record CognitionConfig(
        boolean moodEnabled,
        boolean drivesEnabled,
        boolean mentalModelEnabled,
        boolean userModelEnabled,
        boolean strategyEnabled,
        boolean narrativeEnabled,
        boolean goalsEnabled,
        boolean memoryHygieneEnabled,
        boolean innerLifeEnabled,
        boolean directivePrompts,
        boolean characterDrivesEnabled,
        boolean needsPyramidEnabled,
        boolean attentionEnabled
) { ... }
```

Key changes:
- `all()`: returns `true` for attentionEnabled
- `none()`: returns `false` for attentionEnabled
- `with()`: add `case "attention" ->` that sets attentionEnabled
- `withDirectives()`: preserve attentionEnabled in the copy
- All existing `with()` cases and `withDirectives()`: add attentionEnabled as the 13th arg

- [ ] **Step 6: Verify CognitionConfig tests still pass, add attention test**

Add test to `CognitionConfigTest`:
```java
@Test
void attentionEnabledInAllAndNone() {
    assertThat(CognitionConfig.all().attentionEnabled()).isTrue();
    assertThat(CognitionConfig.none().attentionEnabled()).isFalse();
}

@Test
void withAttention() {
    var config = CognitionConfig.none().with("attention", true);
    assertThat(config.attentionEnabled()).isTrue();
}
```

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks-core,blocks -Dtest="CognitionConfigTest,AttentionRelevanceTest"`
Expected: All tests PASS

- [ ] **Step 7: Run full module build to catch compilation ripple**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -pl blocks-core,blocks`
Expected: BUILD SUCCESS (some tests may use `CognitionConfig.all()` positionally — fix any compilation errors from the new field)

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/blocks add blocks-core/src/main/java/io/casehub/blocks/agentic/social/AttentionRelevance.java blocks-core/src/test/java/io/casehub/blocks/agentic/social/AttentionRelevanceTest.java blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionConfig.java blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionConfigTest.java
git -C /Users/mdproctor/claude/casehub/slots/203/blocks commit -m "feat(#304): AttentionRelevance EnumSet gating + CognitionConfig attentionEnabled"
```

---

## Batch 2: Integration — CognitionCore + PromptSection + CDI Wiring

After this batch: attention signals flow end-to-end from CDI events through CognitionCore to the cognitive prompt. All tests pass.

### Task 3: CognitionCore Integration

**Files:**
- Modify: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java`
- Create: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/AttentionPromptSection.java`
- Test: `blocks-core/src/test/java/io/casehub/blocks/agentic/social/prompt/AttentionPromptSectionTest.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java`

**Interfaces:**
- Consumes: `CognitiveAttentionMediator` (Task 1), `AttentionRelevance` (Task 2), `CognitionConfig.attentionEnabled()` (Task 2)
- Produces: `CognitionCore.lastBriefing()`, attention-aware `promptSections()`, `AttentionPromptSection`

- [ ] **Step 1: Write AttentionPromptSection test**

```java
package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.SignalCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttentionPromptSectionTest {

    @Test
    void rendersSignalsBySignificance() {
        var briefing = new AttentionBriefing("agent", "tenant",
            List.of(
                new AttentionSignal("agent", "tenant", SignalCategory.URGENCY_SPIKE,
                    "n1", "Project deadline", 0.92, "urgency rose past threshold"),
                new AttentionSignal("agent", "tenant", SignalCategory.DECAY_DETECTED,
                    "n2", "Learn Spanish", 0.5, "no activity for 14 days")),
            0.85, Instant.now());

        var section = new AttentionPromptSection(briefing);
        var result = section.contribute(new PromptContext("agent", "tenant", null));

        assertThat(result).isNotNull();
        assertThat(result).contains("Attention required:");
        assertThat(result).contains("[URGENCY_SPIKE] Project deadline");
        assertThat(result).contains("urgency rose past threshold");
        assertThat(result).contains("[DECAY_DETECTED] Learn Spanish");
    }

    @Test
    void returnsNullForEmptySignals() {
        var briefing = new AttentionBriefing("agent", "tenant",
            List.of(), 0.0, Instant.now());
        var section = new AttentionPromptSection(briefing);
        assertThat(section.contribute(new PromptContext("agent", "tenant", null))).isNull();
    }
}
```

- [ ] **Step 2: Implement AttentionPromptSection**

```java
package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.neocortex.mindmap.AttentionBriefing;
import org.jspecify.annotations.Nullable;

public class AttentionPromptSection implements PromptSection {

    private final AttentionBriefing briefing;

    public AttentionPromptSection(AttentionBriefing briefing) {
        this.briefing = briefing;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        var signals = briefing.signals();
        if (signals.isEmpty()) return null;
        var sb = new StringBuilder("Attention required:");
        for (var signal : signals) {
            sb.append("\n- [").append(signal.category().name()).append("] ")
              .append(signal.sourceName());
            if (signal.significance() > 0) {
                sb.append(" (").append(String.format("%.2f", signal.significance())).append(")");
            }
            sb.append(": ").append(signal.reason());
        }
        return sb.toString();
    }
}
```

- [ ] **Step 3: Run AttentionPromptSection tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks-core -Dtest=AttentionPromptSectionTest`
Expected: Both tests PASS

- [ ] **Step 4: Write CognitionCore attention tests**

Add to `CognitionCoreTest.java`:

```java
@Test
void tickDrainsAttentionAtFoundation() {
    var mediator = new CognitiveAttentionMediator();
    var briefing = new AttentionBriefing("agent1", "tenant1",
        List.of(new AttentionSignal("agent1", "tenant1", SignalCategory.URGENCY_SPIKE,
            "n1", "Goal", 0.9, "deadline")),
        0.9, Instant.now());
    mediator.onAttentionRequired(new CognitiveAttentionRequired(briefing, Instant.now()));

    var core = coreWithMediator(mediator);
    core.tick("agent1", "tenant1", null, (a, t) -> Set.of());

    assertThat(core.lastBriefing()).isNotNull();
    assertThat(core.lastBriefing().signals()).hasSize(1);
}

@Test
void tickClearsBriefingWhenNoSignals() {
    var mediator = new CognitiveAttentionMediator();
    var briefing = new AttentionBriefing("agent1", "tenant1",
        List.of(new AttentionSignal("agent1", "tenant1", SignalCategory.URGENCY_SPIKE,
            "n1", "Goal", 0.9, "deadline")),
        0.9, Instant.now());
    mediator.onAttentionRequired(new CognitiveAttentionRequired(briefing, Instant.now()));

    var core = coreWithMediator(mediator);
    core.tick("agent1", "tenant1", null, (a, t) -> Set.of());
    assertThat(core.lastBriefing()).isNotNull();

    core.tick("agent1", "tenant1", null, (a, t) -> Set.of());
    assertThat(core.lastBriefing()).isNull();
}

@Test
void tickDrainsButDiscardsWhenAttentionDisabled() {
    var mediator = new CognitiveAttentionMediator();
    var briefing = new AttentionBriefing("agent1", "tenant1",
        List.of(new AttentionSignal("agent1", "tenant1", SignalCategory.URGENCY_SPIKE,
            "n1", "Goal", 0.9, "deadline")),
        0.9, Instant.now());
    mediator.onAttentionRequired(new CognitiveAttentionRequired(briefing, Instant.now()));

    var core = coreWithMediator(mediator, CognitionConfig.all().with("attention", false));
    core.tick("agent1", "tenant1", null, (a, t) -> Set.of());

    assertThat(core.lastBriefing()).isNull();
    assertThat(mediator.drainAttention("agent1")).isEmpty();
}

@Test
void promptSectionsIncludesAttentionWhenBriefingPresent() {
    var mediator = new CognitiveAttentionMediator();
    mediator.onAttentionRequired(new CognitiveAttentionRequired(
        new AttentionBriefing("agent1", "tenant1",
            List.of(new AttentionSignal("agent1", "tenant1", SignalCategory.URGENCY_SPIKE,
                "n1", "Goal", 0.9, "deadline")),
            0.9, Instant.now()), Instant.now()));

    var core = coreWithMediator(mediator);
    core.tick("agent1", "tenant1", null, (a, t) -> Set.of());

    var sections = core.promptSections();
    var attentionOutput = sections.stream()
        .map(s -> s.contribute(new PromptContext("agent1", "tenant1", null)))
        .filter(s -> s != null && s.contains("Attention required"))
        .findFirst();
    assertThat(attentionOutput).isPresent();
}

@Test
void promptSectionsOverridesDisabledGoalsWhenUrgencySignalPresent() {
    var goals = mock(GoalProposalOrchestrator.class);
    var mediator = new CognitiveAttentionMediator();
    mediator.onAttentionRequired(new CognitiveAttentionRequired(
        new AttentionBriefing("a", "t",
            List.of(new AttentionSignal("a", "t", SignalCategory.URGENCY_SPIKE,
                "n1", "Goal", 0.9, "deadline")),
            0.9, Instant.now()), Instant.now()));

    var config = CognitionConfig.all().with("goals", false);
    var core = coreWithMediator(mediator, config, goals);
    core.tick("a", "t", stubDescriptor(), (aid, tid) -> Set.of());

    var sections = core.promptSections();
    var hasGoals = sections.stream()
        .anyMatch(s -> s instanceof GoalPromptSection);
    assertThat(hasGoals).isTrue();
}
```

Add helper methods to `CognitionCoreTest`:

```java
private static CognitionCore coreWithMediator(CognitiveAttentionMediator mediator) {
    return coreWithMediator(mediator, CognitionConfig.all());
}

private static CognitionCore coreWithMediator(CognitiveAttentionMediator mediator,
                                               CognitionConfig config) {
    return coreWithMediator(mediator, config, null);
}

private static CognitionCore coreWithMediator(CognitiveAttentionMediator mediator,
                                               CognitionConfig config,
                                               GoalProposalOrchestrator goals) {
    var mood = new MoodOrchestrator(MoodConfig.defaults());
    DriveSource baseline = (a, t) ->
            new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
    var drives = new DriveOrchestrator(
            baseline, baseline, baseline, baseline,
            mood, new DriveComposer(), DriveConfig.defaults());
    return new CognitionCore(mood, drives,
            null, null, null, null, goals, null,
            null, null, config, null, null, mediator);
}
```

- [ ] **Step 5: Modify CognitionCore**

Changes to CognitionCore:

1. Add fields:
   ```java
   private final @Nullable CognitiveAttentionMediator attentionMediator;
   private volatile @Nullable AttentionBriefing lastBriefing;
   ```

2. Update canonical constructor (add 14th param `@Nullable CognitiveAttentionMediator attentionMediator`), store it

3. Update all 3 convenience constructors to pass `null` for mediator

4. In `tick()`, at the start before the FOUNDATION comment:
   ```java
   this.lastBriefing = null;
   if (attentionMediator != null) {
       var briefing = attentionMediator.drainAttention(agentId);
       if (config.attentionEnabled()) {
           this.lastBriefing = briefing.orElse(null);
       }
   }
   ```

5. Add `isEnabled` helper:
   ```java
   private boolean isEnabled(boolean configFlag, java.util.Set<io.casehub.neocortex.mindmap.SignalCategory> relevance) {
       return configFlag || (lastBriefing != null && config.attentionEnabled()
           && AttentionRelevance.overrides(lastBriefing, relevance));
   }
   ```

6. In `promptSections()`, replace config-gated checks with `isEnabled()`:
   - `config.moodEnabled()` → `isEnabled(config.moodEnabled(), AttentionRelevance.MOOD)`
   - `config.drivesEnabled()` → `isEnabled(config.drivesEnabled(), AttentionRelevance.DRIVES)`
   - `config.goalsEnabled()` → `isEnabled(config.goalsEnabled(), AttentionRelevance.GOALS)`
   - `config.mentalModelEnabled()` → `isEnabled(config.mentalModelEnabled(), AttentionRelevance.MENTAL_MODEL)`
   - `config.userModelEnabled()` → `isEnabled(config.userModelEnabled(), AttentionRelevance.USER_MODEL)`

7. Add `AttentionPromptSection` to `promptSections()` (before the sectionCustomizer block):
   ```java
   if (config.attentionEnabled() && lastBriefing != null) {
       sections.add(new AttentionPromptSection(lastBriefing));
   }
   ```

8. Add accessor:
   ```java
   public @Nullable AttentionBriefing lastBriefing() { return lastBriefing; }
   ```

- [ ] **Step 6: Run all CognitionCore tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks-core,blocks -Dtest="CognitionCoreTest,AttentionPromptSectionTest,CognitiveAttentionMediatorTest,AttentionRelevanceTest,CognitionConfigTest"`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/blocks add blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/AttentionPromptSection.java blocks-core/src/test/java/io/casehub/blocks/agentic/social/prompt/AttentionPromptSectionTest.java blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java
git -C /Users/mdproctor/claude/casehub/slots/203/blocks commit -m "feat(#304): CognitionCore attention integration — drain, isEnabled, AttentionPromptSection"
```

### Task 4: CDI Wiring — BlocksBeans + SocialAvatarCognition

**Files:**
- Modify: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java`
- Modify: `blocks/src/main/java/io/casehub/blocks/BlocksBeans.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognitionTest.java`

**Interfaces:**
- Consumes: `CognitiveAttentionMediator` (Task 1)
- Produces: fully wired CDI graph — mediator flows from BlocksBeans → SocialAvatarCognition → CognitionCore

- [ ] **Step 1: Update SocialAvatarCognition constructor**

Add `Optional<CognitiveAttentionMediator> attentionMediator` parameter. Pass to CognitionCore via the new constructor:

```java
public SocialAvatarCognition(MoodOrchestrator mood,
                              DriveOrchestrator drives,
                              MentalModelOrchestrator mentalModel,
                              UserModelOrchestrator userModel,
                              StrategyLearningOrchestrator strategy,
                              Optional<NarrativeOrchestrator> narrative,
                              Optional<GoalProposalOrchestrator> goals,
                              Optional<InnerLifeOrchestrator> innerLife,
                              Optional<AgentRegistry> agentRegistry,
                              Optional<CognitiveAttentionMediator> attentionMediator) {
    // ... existing field assignments ...
    this.core = new CognitionCore(mood, drives, userModel, mentalModel, strategy,
            narrative.orElse(null), goals.orElse(null), null,
            innerLife.orElse(null), null, CognitionConfig.all(),
            null, null,
            attentionMediator.orElse(null));
}
```

- [ ] **Step 2: Update BlocksBeans producer**

Add `Instance<CognitiveAttentionMediator>` field and pass to SocialAvatarCognition:

```java
@Inject Instance<CognitiveAttentionMediator> attentionMediatorInstance;

@Produces @ApplicationScoped
public SocialAvatarCognition socialAvatarCognition(
        MoodOrchestrator mood, DriveOrchestrator drives,
        MentalModelOrchestrator mentalModel,
        UserModelOrchestrator userModel,
        StrategyLearningOrchestrator strategy) {
    return new SocialAvatarCognition(
            mood, drives, mentalModel, userModel, strategy,
            optionalFrom(narrativeOrchestratorInstance),
            optionalFrom(goalProposalOrchestratorInstance),
            optionalFrom(innerLifeOrchestratorInstance),
            optionalFrom(agentRegistryInstance),
            optionalFrom(attentionMediatorInstance));
}
```

- [ ] **Step 3: Fix SocialAvatarCognitionTest**

Update test constructor call to pass `Optional.empty()` for the new parameter:

```java
cognition = new SocialAvatarCognition(
        mood, drives, mentalModel, userModel, strategy,
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty());  // attentionMediator
```

- [ ] **Step 4: Run full build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -pl blocks-core,blocks`
Expected: BUILD SUCCESS — all tests pass

- [ ] **Step 5: Run diagnostics**

Use `ide_diagnostics` on modified files to verify no compilation issues remain.

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/blocks add blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java blocks/src/main/java/io/casehub/blocks/BlocksBeans.java blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognitionTest.java
git -C /Users/mdproctor/claude/casehub/slots/203/blocks commit -m "feat(#304): CDI wiring — BlocksBeans + SocialAvatarCognition attention mediator injection"
```

- [ ] **Step 7: Full project build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -DskipTests=false`
Expected: BUILD SUCCESS across all modules. Fix any compilation failures from CognitionConfig field change in other modules (e.g., `agentic-yaml`'s `CognitionStack`).

- [ ] **Step 8: Final commit if any ripple fixes**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/blocks commit -am "fix(#304): compilation ripple from CognitionConfig attentionEnabled field"
```

---

## References

- [2026-09-27-cognitive-attention-mediator-design.md] — design spec this plan implements
- [decisions.md] — D1 (module placement), D2 (config flag + drain-and-discard), D3 (AttentionRelevance EnumSet gating)
- [CognitionCore.java:39-466] — current CognitionCore implementation
- [CognitionConfig.java:1-57] — current CognitionConfig record
- [SocialAvatarCognition.java:26-117] — CDI runtime constructing CognitionCore
- [BlocksBeans.java:304-316] — CDI producer for SocialAvatarCognition
- [neocortex spec §7, lines 474-584] — blocks contract specification
- [GitHub #304] — focal issue
- [GitHub #305] — follow-up: TopN expansion
- [GitHub #306] — follow-up: runtime-customizable overrides
