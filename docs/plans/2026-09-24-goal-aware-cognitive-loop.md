# Goal-Aware Cognitive Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** casehubio/blocks#296 — Goal-aware cognitive loop: surface priority/urgency/decay to LLM and trigger case creation
**Issue group:** casehubio/blocks#296

**Goal:** Wire neocortex's computed goal signals (priority, urgency, decay, OCC emotions) into blocks' cognitive loop so the LLM can see, reason about, and act on cognitive goals — with surfacing tracking that enables emotional escalation over time.

**Architecture:** OCC emotion types (Ortony, Clore, Collins) with ALMA PAD projections provide the typed emotion layer. GoalAppraisal SPI in neocortex computes emotions from goal state; CognitiveGoalOrchestrator in blocks ticks as a CognitionTickParticipant, queries MindMap goals, runs appraisal, records surfacing, and caches state for GoalPromptSection to render. SurfacingAggregationPhase in neocortex consolidation tracks what was surfaced and when, enabling the Hope→Fear→Fears-confirmed emotional arc over time.

**Tech Stack:** Java 21, Quarkus 3.32.2, JUnit 5 + Mockito, Maven

## Global Constraints

- Pre-release platform — breaking changes cost nothing
- IntelliJ MCP mandatory for all .java edits — use ide_insert_member, ide_replace_member, ide_edit_member
- TDD: write failing test first, implement to pass, commit
- Both repos (neocortex + blocks) are in the same slot — build with `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -pl <module>`
- All commits reference `Refs #296` or `Closes #296`
- cognitive-api has zero compile dependencies — new types must not import anything outside the module
- GoalAppraisal SPI lives in mindmap-api (takes MindMapNode parameter)
- ExperienceEvent.Action constructor: `new Action(agentId, tenantId, caseId, turnId, timestamp, description, confidence, metadata, capability)`
- Surfacing metadata uses custom keys `"cognitive-event"` and `"goal-node-id"` — NOT ExperienceAttributeKeys reserved keys

---

## Batch 1: OCC Foundation Types + GoalAppraisal SPI [neocortex compiles, tests pass]

### Task 1: EmotionType, CognitiveEmotion, EmotionSource, PadProjection, AlmaPadTable

**Files:**
- Create: `neocortex/cognitive-api/src/main/java/io/casehub/neocortex/cognitive/EmotionType.java`
- Create: `neocortex/cognitive-api/src/main/java/io/casehub/neocortex/cognitive/CognitiveEmotion.java`
- Create: `neocortex/cognitive-api/src/main/java/io/casehub/neocortex/cognitive/EmotionSource.java`
- Create: `neocortex/cognitive-api/src/main/java/io/casehub/neocortex/cognitive/PadProjection.java`
- Create: `neocortex/cognitive-api/src/main/java/io/casehub/neocortex/cognitive/AlmaPadTable.java`
- Test: `neocortex/cognitive-api/src/test/java/io/casehub/neocortex/cognitive/AlmaPadTableTest.java`
- Test: `neocortex/cognitive-api/src/test/java/io/casehub/neocortex/cognitive/CognitiveEmotionTest.java`

**Interfaces:**
- Produces: `EmotionType` enum (22 types), `CognitiveEmotion(EmotionType type, double intensity, String subjectId, Instant onset, EmotionSource source, PadProjection pad)`, `EmotionSource.INTRINSIC|EMPATHIC`, `PadProjection(double pleasure, double arousal, double dominance)`, `AlmaPadTable.lookup(EmotionType) → PadProjection`, `AlmaPadTable.project(EmotionType, double intensity) → PadProjection`

- [ ] **Step 1: Write tests for AlmaPadTable**

```java
// AlmaPadTableTest.java
package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

class AlmaPadTableTest {

    @Test
    void fearHasCorrectBaseValues() {
        var pad = AlmaPadTable.lookup(EmotionType.FEAR);
        assertThat(pad.pleasure()).isEqualTo(-0.64, within(0.01));
        assertThat(pad.arousal()).isEqualTo(0.60, within(0.01));
        assertThat(pad.dominance()).isEqualTo(-0.43, within(0.01));
    }

    @Test
    void hopeHasCorrectBaseValues() {
        var pad = AlmaPadTable.lookup(EmotionType.HOPE);
        assertThat(pad.pleasure()).isEqualTo(0.2, within(0.01));
        assertThat(pad.arousal()).isEqualTo(0.2, within(0.01));
        assertThat(pad.dominance()).isEqualTo(-0.1, within(0.01));
    }

    @Test
    void projectScalesByIntensity() {
        var pad = AlmaPadTable.project(EmotionType.FEAR, 0.5);
        assertThat(pad.pleasure()).isEqualTo(-0.32, within(0.01));
        assertThat(pad.arousal()).isEqualTo(0.30, within(0.01));
        assertThat(pad.dominance()).isEqualTo(-0.215, within(0.01));
    }

    @Test
    void projectAtZeroIntensityReturnsZeroPad() {
        var pad = AlmaPadTable.project(EmotionType.FEAR, 0.0);
        assertThat(pad.pleasure()).isEqualTo(0.0);
        assertThat(pad.arousal()).isEqualTo(0.0);
        assertThat(pad.dominance()).isEqualTo(0.0);
    }

    @ParameterizedTest
    @EnumSource(EmotionType.class)
    void allEmotionTypesHaveMappings(EmotionType type) {
        assertThatCode(() -> AlmaPadTable.lookup(type)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-api -Dtest=AlmaPadTableTest -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`
Expected: FAIL — classes don't exist

- [ ] **Step 3: Write tests for CognitiveEmotion**

```java
// CognitiveEmotionTest.java
package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class CognitiveEmotionTest {

    @Test
    void intensityMustBeInRange() {
        assertThatThrownBy(() -> new CognitiveEmotion(
            EmotionType.FEAR, 1.5, "goal-1", Instant.now(),
            EmotionSource.INTRINSIC, new PadProjection(0, 0, 0)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CognitiveEmotion(
            EmotionType.FEAR, -0.1, "goal-1", Instant.now(),
            EmotionSource.INTRINSIC, new PadProjection(0, 0, 0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validEmotionCreatesSuccessfully() {
        var emotion = new CognitiveEmotion(
            EmotionType.FEAR, 0.8, "goal-1", Instant.now(),
            EmotionSource.INTRINSIC, AlmaPadTable.project(EmotionType.FEAR, 0.8));
        assertThat(emotion.type()).isEqualTo(EmotionType.FEAR);
        assertThat(emotion.intensity()).isEqualTo(0.8);
        assertThat(emotion.source()).isEqualTo(EmotionSource.INTRINSIC);
    }

    @Test
    void subjectIdIsRequired() {
        assertThatThrownBy(() -> new CognitiveEmotion(
            EmotionType.FEAR, 0.5, null, Instant.now(),
            EmotionSource.INTRINSIC, new PadProjection(0, 0, 0)))
            .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 4: Implement all types**

Create `EmotionType.java`:
```java
package io.casehub.neocortex.cognitive;

public enum EmotionType {
    HOPE, FEAR, SATISFACTION, DISAPPOINTMENT, RELIEF, FEARS_CONFIRMED,
    JOY, DISTRESS,
    HAPPY_FOR, PITY,
    PRIDE, SHAME, REPROACH, ADMIRATION,
    GRATITUDE, ANGER, REMORSE, GRATIFICATION,
    LOVE, HATE,
    RESENTMENT, GLOATING
}
```

Create `EmotionSource.java`:
```java
package io.casehub.neocortex.cognitive;

public enum EmotionSource {
    INTRINSIC,
    EMPATHIC
}
```

Create `PadProjection.java`:
```java
package io.casehub.neocortex.cognitive;

public record PadProjection(double pleasure, double arousal, double dominance) {
    public static final PadProjection NEUTRAL = new PadProjection(0, 0, 0);
}
```

Create `CognitiveEmotion.java`:
```java
package io.casehub.neocortex.cognitive;

import java.time.Instant;
import java.util.Objects;

public record CognitiveEmotion(
    EmotionType type,
    double intensity,
    String subjectId,
    Instant onset,
    EmotionSource source,
    PadProjection pad
) {
    public CognitiveEmotion {
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(subjectId, "subjectId required");
        Objects.requireNonNull(onset, "onset required");
        Objects.requireNonNull(source, "source required");
        Objects.requireNonNull(pad, "pad required");
        if (intensity < 0.0 || intensity > 1.0)
            throw new IllegalArgumentException("intensity must be in [0, 1], got " + intensity);
    }
}
```

Create `AlmaPadTable.java`:
```java
package io.casehub.neocortex.cognitive;

import java.util.EnumMap;
import java.util.Map;

public final class AlmaPadTable {

    private static final Map<EmotionType, PadProjection> TABLE = new EnumMap<>(EmotionType.class);

    static {
        TABLE.put(EmotionType.ADMIRATION,        new PadProjection( 0.5,   0.3,  -0.2));
        TABLE.put(EmotionType.ANGER,             new PadProjection(-0.51,  0.59,  0.25));
        TABLE.put(EmotionType.DISAPPOINTMENT,    new PadProjection(-0.3,   0.1,  -0.4));
        TABLE.put(EmotionType.DISTRESS,          new PadProjection(-0.4,  -0.2,  -0.5));
        TABLE.put(EmotionType.FEAR,              new PadProjection(-0.64,  0.60, -0.43));
        TABLE.put(EmotionType.FEARS_CONFIRMED,   new PadProjection(-0.5,  -0.3,  -0.7));
        TABLE.put(EmotionType.GLOATING,          new PadProjection( 0.3,  -0.3,  -0.1));
        TABLE.put(EmotionType.GRATIFICATION,     new PadProjection( 0.6,   0.5,   0.4));
        TABLE.put(EmotionType.GRATITUDE,         new PadProjection( 0.4,   0.2,  -0.3));
        TABLE.put(EmotionType.HAPPY_FOR,         new PadProjection( 0.4,   0.2,   0.2));
        TABLE.put(EmotionType.HATE,              new PadProjection(-0.6,   0.6,   0.3));
        TABLE.put(EmotionType.HOPE,              new PadProjection( 0.2,   0.2,  -0.1));
        TABLE.put(EmotionType.JOY,               new PadProjection( 0.4,   0.2,   0.1));
        TABLE.put(EmotionType.LOVE,              new PadProjection( 0.4,   0.16, -0.24));
        TABLE.put(EmotionType.PITY,              new PadProjection(-0.4,  -0.2,  -0.5));
        TABLE.put(EmotionType.PRIDE,             new PadProjection( 0.4,   0.3,   0.3));
        TABLE.put(EmotionType.RELIEF,            new PadProjection( 0.2,  -0.3,  -0.4));
        TABLE.put(EmotionType.REMORSE,           new PadProjection(-0.3,   0.1,  -0.6));
        TABLE.put(EmotionType.REPROACH,          new PadProjection(-0.3,  -0.1,   0.4));
        TABLE.put(EmotionType.RESENTMENT,        new PadProjection(-0.2,  -0.3,  -0.2));
        TABLE.put(EmotionType.SATISFACTION,      new PadProjection( 0.3,  -0.2,   0.4));
        TABLE.put(EmotionType.SHAME,             new PadProjection(-0.3,   0.1,  -0.6));
    }

    private AlmaPadTable() {}

    public static PadProjection lookup(EmotionType type) {
        var pad = TABLE.get(type);
        if (pad == null) throw new IllegalArgumentException("No PAD mapping for " + type);
        return pad;
    }

    public static PadProjection project(EmotionType type, double intensity) {
        var base = lookup(type);
        return new PadProjection(
            base.pleasure() * intensity,
            base.arousal() * intensity,
            base.dominance() * intensity
        );
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-api -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/neocortex add cognitive-api/src/
git -C /Users/mdproctor/claude/casehub/slots/203/neocortex commit -m "feat(#296): add OCC emotion types, ALMA PAD table, CognitiveEmotion record in cognitive-api Refs #296"
```

### Task 2: GoalAppraisal SPI + AppraisalContext + HeuristicGoalAppraisal

**Files:**
- Create: `neocortex/mindmap-api/src/main/java/io/casehub/neocortex/mindmap/GoalAppraisal.java`
- Create: `neocortex/mindmap-api/src/main/java/io/casehub/neocortex/mindmap/AppraisalContext.java`
- Create: `neocortex/mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/HeuristicGoalAppraisal.java`
- Test: `neocortex/mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/HeuristicGoalAppraisalTest.java`

**Interfaces:**
- Consumes: `EmotionType`, `CognitiveEmotion`, `EmotionSource`, `PadProjection`, `AlmaPadTable` (from Task 1)
- Consumes: `MindMapNode` (from mindmap-api)
- Produces: `GoalAppraisal` SPI (`appraise(MindMapNode, AppraisalContext) → List<CognitiveEmotion>`), `AppraisalContext` record, `HeuristicGoalAppraisal` implementation

- [ ] **Step 1: Write tests for HeuristicGoalAppraisal — prospect-based emotions**

```java
// HeuristicGoalAppraisalTest.java
package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.*;
import io.casehub.neocortex.mindmap.AppraisalContext;
import io.casehub.neocortex.mindmap.GoalAppraisal;
import io.casehub.neocortex.mindmap.MindMapNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeuristicGoalAppraisalTest {

    private GoalAppraisal appraisal;
    private AppraisalContext ctx;

    @BeforeEach
    void setUp() {
        appraisal = new HeuristicGoalAppraisal();
        ctx = new AppraisalContext("tenant-1", "agent-1",
            PadProjection.NEUTRAL, 0, null, null, Map.of());
    }

    @Test
    void activeGoalLowUrgency_producesHope() {
        var node = goalNode("active", 0.7, 0.2, 0.8);
        var emotions = appraisal.appraise(node, ctx);
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.HOPE);
        assertThat(emotions).noneMatch(e -> e.type() == EmotionType.FEAR);
    }

    @Test
    void activeGoalHighUrgencyNoProgress_producesFear() {
        var ctxWithSurfacing = new AppraisalContext("tenant-1", "agent-1",
            PadProjection.NEUTRAL, 5, null, null, Map.of());
        var node = goalNode("active", 0.85, 0.9, 0.4);
        var emotions = appraisal.appraise(node, ctxWithSurfacing);
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.FEAR && e.intensity() > 0.5);
    }

    @Test
    void completedGoal_producesSatisfaction() {
        var node = goalNode("completed", 0.7, 0.0, 1.0);
        var emotions = appraisal.appraise(node, ctx);
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.SATISFACTION);
    }

    @Test
    void blockedGoal_producesDistress() {
        var node = goalNode("blocked", 0.8, 0.7, 0.1);
        var emotions = appraisal.appraise(node, ctx);
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.DISTRESS);
    }

    @Test
    void surfacingGapAmpliFear() {
        var ctxGap3 = new AppraisalContext("tenant-1", "agent-1",
            PadProjection.NEUTRAL, 3, null, null, Map.of());
        var ctxGap7 = new AppraisalContext("tenant-1", "agent-1",
            PadProjection.NEUTRAL, 7, null, null, Map.of());
        var node = goalNode("active", 0.85, 0.8, 0.4);

        var emotions3 = appraisal.appraise(node, ctxGap3);
        var emotions7 = appraisal.appraise(node, ctxGap7);

        double fear3 = emotions3.stream()
            .filter(e -> e.type() == EmotionType.FEAR).findFirst()
            .map(CognitiveEmotion::intensity).orElse(0.0);
        double fear7 = emotions7.stream()
            .filter(e -> e.type() == EmotionType.FEAR).findFirst()
            .map(CognitiveEmotion::intensity).orElse(0.0);

        assertThat(fear7).isGreaterThan(fear3);
    }

    @Test
    void empathicPity_whenRelationshipPresent() {
        var ctxWithRelationship = new AppraisalContext("tenant-1", "agent-1",
            PadProjection.NEUTRAL, 5, null, null,
            Map.of("daughter-node", 0.8));
        var node = goalNode("active", 0.85, 0.9, 0.3);
        // Node has affected-entity property
        when(node.property("affected-entity")).thenReturn(Optional.of("daughter-node"));
        var emotions = appraisal.appraise(node, ctxWithRelationship);
        assertThat(emotions).anyMatch(e ->
            e.type() == EmotionType.PITY && e.source() == EmotionSource.EMPATHIC);
    }

    @Test
    void allEmotionsHaveSourceSet() {
        var node = goalNode("active", 0.7, 0.5, 0.5);
        var emotions = appraisal.appraise(node, ctx);
        assertThat(emotions).allMatch(e -> e.source() != null);
    }

    private MindMapNode goalNode(String status, double priority,
                                  double urgency, double feasibility) {
        var node = mock(MindMapNode.class);
        when(node.id()).thenReturn("goal-" + status);
        when(node.name()).thenReturn("Test goal (" + status + ")");
        when(node.property("status")).thenReturn(Optional.of(status));
        when(node.property("priority")).thenReturn(Optional.of(String.valueOf(priority)));
        when(node.property("urgency")).thenReturn(Optional.of(String.valueOf(urgency)));
        when(node.property("feasibility")).thenReturn(Optional.of(String.valueOf(feasibility)));
        when(node.property("surfaced-count")).thenReturn(Optional.empty());
        when(node.property("affected-entity")).thenReturn(Optional.empty());
        when(node.property("eidos-goal-name")).thenReturn(Optional.empty());
        return node;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=HeuristicGoalAppraisalTest -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`
Expected: FAIL — classes don't exist

- [ ] **Step 3: Create GoalAppraisal SPI and AppraisalContext in mindmap-api**

```java
// GoalAppraisal.java — in mindmap-api
package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import java.util.List;

@FunctionalInterface
public interface GoalAppraisal {
    List<CognitiveEmotion> appraise(MindMapNode goal, AppraisalContext context);
}
```

```java
// AppraisalContext.java — in mindmap-api
package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.PadProjection;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AppraisalContext(
    String tenantId,
    String agentId,
    PadProjection moodBaseline,
    int surfacingCount,
    @Nullable Instant lastProgressAt,
    @Nullable Instant lastSurfacedAt,
    Map<String, Double> relationshipScores
) {
    public AppraisalContext {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(moodBaseline, "moodBaseline required");
        relationshipScores = Map.copyOf(relationshipScores);
    }
}
```

- [ ] **Step 4: Implement HeuristicGoalAppraisal**

Create `HeuristicGoalAppraisal.java` in mindmap-intelligence implementing the appraisal mapping table from the spec §4. Key logic:
- Active + low urgency → Hope(priority × feasibility)
- Active + high urgency + no progress → Fear(priority × urgency × surfacing_gap_factor)
- Blocked → Distress(priority × urgency × (1 - feasibility))
- Completed → Satisfaction(priority)
- Abandoned → Disappointment(prior hope estimate)
- Affected-entity + relationship score → Pity(EMPATHIC)

surfacing_gap_factor = surfacingCount / (surfacingCount + 1)

All emotions carry INTRINSIC source except Pity which carries EMPATHIC.
PAD derived via AlmaPadTable.project().

- [ ] **Step 5: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=HeuristicGoalAppraisalTest -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`
Expected: PASS

- [ ] **Step 6: Add cognitive-api dependency to mindmap-api pom.xml**

mindmap-api needs to import `CognitiveEmotion` and `PadProjection` from cognitive-api. Add the dependency (compile scope — cognitive-api is already a dependency of mindmap-api's parent chain, verify and add if missing).

- [ ] **Step 7: Build full neocortex to verify no breakage**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/neocortex add mindmap-api/src/ mindmap-intelligence/src/ mindmap-api/pom.xml
git -C /Users/mdproctor/claude/casehub/slots/203/neocortex commit -m "feat(#296): GoalAppraisal SPI + HeuristicGoalAppraisal — OCC emotion computation from goal state Refs #296"
```

---

## Batch 2: Surfacing Aggregation + GoalAffectPhase Revision [neocortex consolidation upgraded]

### Task 3: SurfacingAggregationPhase

**Files:**
- Create: `neocortex/mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SurfacingAggregationPhase.java`
- Test: `neocortex/mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SurfacingAggregationPhaseTest.java`

**Interfaces:**
- Consumes: `ConsolidationPhase` SPI, `MindMapStore`, `CaseMemoryStore` (scan experiences)
- Produces: Goal node properties: `surfaced-count`, `first-surfaced-at`, `last-surfaced-at`, `last-progress-at`, `surfacing-progress-gap`

- [ ] **Step 1: Write tests**

Test cases:
1. No surfacing experiences → no node updates
2. One goal-surfaced experience → surfaced-count=1, first-surfaced-at set, last-surfaced-at set
3. Multiple surfacing experiences for same goal → surfaced-count incremented correctly
4. Progress experience after surfacing → last-progress-at updated, surfacing-progress-gap resets
5. Mixed surfacing and progress → gap computed correctly
6. Multiple goals tracked independently

Use InMemoryMindMapStore + InMemoryMemoryStore. Create goal nodes in GOAL subgraph, store domain="experience" memories with cognitive-event=goal-surfaced metadata. Run phase, verify node properties updated.

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=SurfacingAggregationPhaseTest -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`

- [ ] **Step 3: Implement SurfacingAggregationPhase**

@ApplicationScoped @Priority(12) (before GoalAffectPhase @Priority(37)).
- name() returns "surfacing-aggregation"
- run(tenantId, subgraphPriority):
  1. Find GOAL subgraph
  2. Scan CaseMemoryStore for domain="experience" memories with cognitive-event="goal-surfaced" attribute
  3. Group by goal-node-id attribute
  4. For each goal: count surfacings, find min/max timestamps
  5. Scan for any non-surfacing experiences with matching subject → last-progress-at
  6. Compute surfacing-progress-gap = surfacings since last-progress-at
  7. Update node properties via MindMapStore.updateNode()

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/neocortex add mindmap-intelligence/src/
git -C /Users/mdproctor/claude/casehub/slots/203/neocortex commit -m "feat(#296): SurfacingAggregationPhase — consolidation phase tracking goal surfacing history Refs #296"
```

### Task 4: GoalAffectPhase — revised to use GoalAppraisal

**Files:**
- Modify: `neocortex/mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/GoalAffectPhase.java`
- Modify: `neocortex/mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/GoalAffectPhaseTest.java`

**Interfaces:**
- Consumes: `GoalAppraisal` (from Task 2), `SurfacingAggregationPhase` properties (from Task 3)
- Produces: OCC-derived PAD on goal nodes (replaces raw switch statement)

- [ ] **Step 1: Update existing GoalAffectPhase tests**

Add test cases:
1. Active goal with surfacing history → Fear PAD values (not generic active PAD)
2. Completed goal → Satisfaction PAD values match ALMA table
3. Verify GoalAppraisal is called with correct AppraisalContext
4. Existing tests still pass (backwards-compatible PAD output)

- [ ] **Step 2: Run tests to verify new ones fail**

- [ ] **Step 3: Modify GoalAffectPhase**

Replace the raw PAD switch statement with GoalAppraisal delegation:
- Add `Instance<GoalAppraisal>` dependency (CDI injection, graceful degradation)
- In run(): for each goal node, build AppraisalContext from node properties (surfaced-count, etc.)
- Call appraisal.appraise() → get CognitiveEmotions
- Write the dominant emotion's PAD projection to the node via updateNode()
- Fall back to the existing switch statement if GoalAppraisal is not resolvable

- [ ] **Step 4: Run all GoalAffectPhase tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=GoalAffectPhaseTest -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`
Expected: PASS (all existing + new)

- [ ] **Step 5: Run full mindmap-intelligence tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`
Expected: PASS (274 tests)

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/neocortex add mindmap-intelligence/src/
git -C /Users/mdproctor/claude/casehub/slots/203/neocortex commit -m "feat(#296): GoalAffectPhase uses GoalAppraisal SPI — OCC emotions replace raw PAD switch Refs #296"
```

---

## Batch 3: CognitiveGoalOrchestrator + Prompt Rendering [blocks integration working]

### Task 5: CognitiveGoalOrchestrator + CognitionCore.setSectionCustomizer

**Files:**
- Create: `blocks/blocks-core/src/main/java/io/casehub/blocks/agentic/social/goal/CognitiveGoalOrchestrator.java`
- Create: `blocks/blocks-core/src/main/java/io/casehub/blocks/agentic/social/goal/CognitiveGoalConfig.java`
- Create: `blocks/blocks-core/src/main/java/io/casehub/blocks/agentic/social/goal/CognitiveGoalState.java`
- Create: `blocks/blocks-core/src/main/java/io/casehub/blocks/agentic/social/goal/GoalRevision.java`
- Modify: `blocks/blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionConfig.java` (add `cognitiveGoalsEnabled`)
- Modify: `blocks/blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java` (add `setSectionCustomizer`)
- Test: `blocks/blocks/src/test/java/io/casehub/blocks/agentic/social/goal/CognitiveGoalOrchestratorTest.java`

**Interfaces:**
- Consumes: `CognitionTickParticipant`, `CognitionTickContext`, `MindMapStore`, `GoalAppraisal`, `ExperienceRecorder`, `CaseMemoryStore`
- Produces: `CognitiveGoalOrchestrator.currentState(agentId, tenantId) → Optional<CognitiveGoalState>`, `CognitiveGoalOrchestrator.pendingRevisions() → List<GoalRevision>`, `CognitionCore.setSectionCustomizer(UnaryOperator<List<PromptSection>>)`

- [ ] **Step 1: Write tests for CognitiveGoalOrchestrator**

Test cases:
1. tick() queries MindMap GOAL nodes and runs GoalAppraisal
2. tick() records surfacing ExperienceEvent with correct metadata keys
3. tick() records agent emotions as domain="agent-experience" memories
4. tick() caches CognitiveGoalState accessible via currentState()
5. tick() detects decay-signal and produces GoalRevision
6. tick() proposes case creation when priority × feasibility > threshold
7. Surfacing cooldown — in-memory timestamps prevent re-surfacing within cooldown
8. Goals without computed priority (null) are surfaced (cold-start fallthrough)
9. Graceful degradation when ExperienceRecorder not available

Use Mockito for MindMapStore, GoalAppraisal, ExperienceRecorder, CaseMemoryStore.

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks -Dtest=CognitiveGoalOrchestratorTest -f /Users/mdproctor/claude/casehub/slots/203/blocks/pom.xml`

- [ ] **Step 3: Implement CognitiveGoalConfig**

```java
package io.casehub.blocks.agentic.social.goal;

import java.time.Duration;

public record CognitiveGoalConfig(
    double caseCreationThreshold,
    Duration surfacingCooldown,
    double minimumSurfacingPriority,
    double driveWeight
) {
    public static CognitiveGoalConfig defaults() {
        return new CognitiveGoalConfig(0.7, Duration.ofHours(1), 0.1, 0.5);
    }
}
```

- [ ] **Step 4: Implement CognitiveGoalState and GoalRevision**

```java
// CognitiveGoalState.java
package io.casehub.blocks.agentic.social.goal;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.mindmap.MindMapNode;
import java.util.List;

public record CognitiveGoalState(List<GoalEmotion> goals, List<GoalRevision> revisions) {
    public record GoalEmotion(MindMapNode goal, List<CognitiveEmotion> emotions) {}
}
```

```java
// GoalRevision.java
package io.casehub.blocks.agentic.social.goal;

public record GoalRevision(String goalNodeId, String goalName,
                            String decaySignal, String eidosGoalName) {}
```

- [ ] **Step 5: Implement CognitiveGoalOrchestrator**

Implements CognitionTickParticipant. Full tick behaviour per spec §7:
1. Query MindMapStore for GOAL subgraph nodes
2. Select for surfacing (cooldown + priority filter with null fallthrough)
3. Build AppraisalContext (read surfacing properties from nodes)
4. Run GoalAppraisal per selected goal
5. Record surfacing via ExperienceRecorder (ExperienceEvent.Action constructor)
6. Record agent emotions via CaseMemoryStore (domain="agent-experience")
7. Check case creation threshold → GoalFormationService.propose()
8. Check decay-signal → GoalRevision
9. Cache CognitiveGoalState

In-memory `Map<String, Instant>` for per-goal surfacing timestamps (real-time cooldown).

- [ ] **Step 6: Add cognitiveGoalsEnabled to CognitionConfig**

Add the field, update all(), none(), with(), without() methods.

- [ ] **Step 7: Add setSectionCustomizer to CognitionCore**

```java
// In CognitionCore — new field and method
private @Nullable UnaryOperator<List<PromptSection>> sectionCustomizer;

public void setSectionCustomizer(UnaryOperator<List<PromptSection>> customizer) {
    this.sectionCustomizer = customizer;
}
```

In promptSections(), apply customizer BEFORE DirectiveSection wrapping:
```java
var sections = new ArrayList<PromptSection>();
// ... existing section building ...
if (sectionCustomizer != null) {
    sections = new ArrayList<>(sectionCustomizer.apply(sections));
}
if (config.directivePrompts()) {
    return sections.stream().map(DirectiveSection::wrap).toList();
}
return sections;
```

- [ ] **Step 8: Run all tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks -f /Users/mdproctor/claude/casehub/slots/203/blocks/pom.xml`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/blocks add blocks-core/src/ blocks/src/
git -C /Users/mdproctor/claude/casehub/slots/203/blocks commit -m "feat(#296): CognitiveGoalOrchestrator — tick participant querying MindMap goals, recording surfacing, caching state Refs #296"
```

### Task 6: GoalPromptSection extension + SocialAvatarCognition wiring

**Files:**
- Modify: `blocks/blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/GoalPromptSection.java`
- Modify: `blocks/blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java`
- Test: `blocks/blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/GoalPromptSectionTest.java` (extend existing)

**Interfaces:**
- Consumes: `GoalProposalOrchestrator.currentProposals()`, `CognitiveGoalOrchestrator.currentState()`, `CognitiveGoalConfig.driveWeight()`
- Produces: Unified prompt text with OCC emotional tone and surfacing context

- [ ] **Step 1: Write tests for extended GoalPromptSection**

Test cases:
1. Drive goals only (no cognitive orchestrator) → renders as before (backwards compatible)
2. Cognitive goals only (no drive proposals) → renders with OCC emotion labels
3. Both sources merged → ranked by composite priority
4. Dedup via eidos-goal-name → cognitive goal wins, drive metadata preserved
5. Surfacing count > 1 → "(reminded N times over M days)" in output
6. Fear emotion → "[CONCERNED]" prefix
7. Satisfaction emotion → "[COMPLETED]" prefix
8. Pity emotion → "[CONCERNED FOR OTHERS]" prefix

- [ ] **Step 2: Run tests to verify new ones fail**

- [ ] **Step 3: Extend GoalPromptSection**

Add Optional<CognitiveGoalOrchestrator> as second constructor parameter. In contribute(): merge drive proposals and cognitive goals, rank by composite priority (α × drive_intensity + (1-α) × mindmap_priority), render with OCC emotional tone per spec §8.

- [ ] **Step 4: Wire in SocialAvatarCognition**

SocialAvatarCognition constructs CognitiveGoalOrchestrator, registers it via core.addParticipant(TERMINAL, ...), and sets the section customizer on CognitionCore to replace GoalPromptSection with the extended version.

- [ ] **Step 5: Run all blocks tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -f /Users/mdproctor/claude/casehub/slots/203/blocks/pom.xml`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/blocks add blocks-core/src/ blocks/src/
git -C /Users/mdproctor/claude/casehub/slots/203/blocks commit -m "feat(#296): GoalPromptSection unified rendering + SocialAvatarCognition wiring Refs #296"
```

---

## Batch 4: DSL Extension + E2E Scenario Test [feature complete]

### Task 7: CognitionDefinition DSL extension

**Files:**
- Create: `blocks/agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/CognitiveGoalConfigSpec.java`
- Modify: `blocks/agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/CognitionDefinition.java` (add field)
- Test: Verify CognitionDefinition YAML round-trip with cognitiveGoal field

- [ ] **Step 1: Create CognitiveGoalConfigSpec**

```java
package io.casehub.blocks.agentic.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;
import java.time.Duration;

public record CognitiveGoalConfigSpec(
    @Nullable Boolean enabled,
    @Nullable Double caseCreationThreshold,
    @Nullable Duration surfacingCooldown,
    @Nullable Double driveWeight,
    @Nullable Double minimumSurfacingPriority
) {}
```

- [ ] **Step 2: Add cognitiveGoal field to CognitionDefinition**

Add `@Nullable CognitiveGoalConfigSpec cognitiveGoal` as a new constructor parameter.

- [ ] **Step 3: Verify schema generation (if applicable) and YAML round-trip**

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/blocks add agentic-yaml/src/
git -C /Users/mdproctor/claude/casehub/slots/203/blocks commit -m "feat(#296): CognitionDefinition cognitiveGoal DSL extension Refs #296"
```

### Task 8: E2E Scenario Test — Birthday Gift Reminder

**Files:**
- Create: `blocks/blocks/src/test/java/io/casehub/blocks/agentic/social/goal/GoalCognitionIntegrationTest.java`

**Interfaces:**
- Consumes: All types from Tasks 1-6

- [ ] **Step 1: Write the full scenario test**

Ordered test methods (@TestMethodOrder, @TestInstance(PER_CLASS)) with Clock injection:
1. createGoalWithDeadline — GOAL node with target-date = 7 days, priority = 0.85
2. day1_firstSurfacing — tick, verify Hope + mild Fear, surfacing event recorded
3. day1_consolidation — run SurfacingAggregationPhase, verify surfaced-count=1
4. day3_noProgress — advance clock 2 days, tick, verify Fear rising, Hope declining
5. day6_repeatedSurfacing — advance clock 3 days, tick daily, verify Fear(~0.8) + Pity
6. day7_deadline — advance clock 1 day:
   - Branch A (no action): Fears-confirmed(~0.9)
   - Branch B (action taken): Relief(~0.9)
7. progressResets — record progress ExperienceEvent, verify worry resets

Uses InMemoryMindMapStore, InMemoryMemoryStore, mock GoalFormationService. Full feedback loop without an LLM.

- [ ] **Step 2: Run scenario test**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks -Dtest=GoalCognitionIntegrationTest -f /Users/mdproctor/claude/casehub/slots/203/blocks/pom.xml`
Expected: PASS

- [ ] **Step 3: Run full build both repos**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`
Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -f /Users/mdproctor/claude/casehub/slots/203/blocks/pom.xml`
Expected: Both BUILD SUCCESS

- [ ] **Step 4: Update CLAUDE.md and docs**

Update blocks CLAUDE.md: add CognitiveGoalOrchestrator to the social/goal package description, document the CognitionDefinition cognitiveGoal field.

Update blocks contributor-guide.md: add section on cognitive goal integration.

Update neocortex CLAUDE.md: add SurfacingAggregationPhase to mindmap-intelligence, document GoalAppraisal SPI and HeuristicGoalAppraisal, note EmotionType/CognitiveEmotion in cognitive-api.

- [ ] **Step 5: Final commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/blocks add blocks/src/ CLAUDE.md docs/
git -C /Users/mdproctor/claude/casehub/slots/203/blocks commit -m "feat(#296): E2E scenario test — birthday gift reminder with Day 1-7 emotional progression Refs #296"
git -C /Users/mdproctor/claude/casehub/slots/203/neocortex add CLAUDE.md docs/
git -C /Users/mdproctor/claude/casehub/slots/203/neocortex commit -m "docs(#296): update CLAUDE.md and contributor guide for OCC emotion types and GoalAppraisal SPI Refs #296"
```

---

## References

- [2026-09-24-goal-aware-cognitive-loop-design.md] — design spec this plan implements
- [2026-09-23-computational-emotion-research.md] — research foundations (OCC, ALMA, datasets)
- [decisions.md] — 6 design decisions with review findings
- GoalAffectPhase.java:20-109 — existing consolidation phase (replaced in Task 4)
- GoalPromptSection.java:1-42 — existing prompt section (extended in Task 6)
- CognitionCore.java:375-407 — promptSections() method (modified in Task 5)
- CognitionTickParticipant.java:1-6 — extension point interface
- ExperienceEvent.java:1-17 / Action.java:1-28 — surfacing event structure
- ExperienceAttributeKeys.java:1-15 — reserved keys to avoid
- GoalProposalOrchestrator.java:1-501 — existing drive-based orchestrator (unchanged)
- SocialAvatarCognition.java:1-117 — composition root (modified in Task 6)
- CognitionDefinition.java:1-17 — DSL record (extended in Task 7)
- casehubio/blocks#296 — focal issue
- [OCC Model Revisited (Steunebrink 2009)](https://people.idsia.ch/~steunebrink/Publications/KI09_OCC_revisited.pdf)
- [ALMA (Gebhard 2005)](https://alma.dfki.de/papers/aamas05.pdf)
