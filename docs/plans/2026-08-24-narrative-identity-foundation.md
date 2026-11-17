# Narrative Identity + Social Emergence Foundation Types — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #142 — Narrative Identity + Social Emergence — self-story and collective behavior
**Issue group:** #142, #143, #144, #145, #146, #147, #148

**Goal:** Deliver foundation types and SPIs for narrative identity and social emergence — the type contracts that children #143-#148 implement against.

**Architecture:** Two new packages under `social` (narrative, emergence) containing records, sealed interfaces, enums, and SPIs. Integration changes to DriveComposer (new modulation parameter) and CognitiveObservationSections (narrative rendering). All compositor orchestrator implementations deferred to children.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Mockito. No Quarkus runtime in tests.

## Global Constraints

- All types in `io.casehub.blocks.agentic.social.narrative` or `io.casehub.blocks.agentic.social.emergence`
- Records use compact constructors with null checks and defensive copies (platform pattern)
- Sealed interfaces use permits clause
- `@Nullable` annotations from `org.jspecify.annotations`
- Tests are plain JUnit 5 with AssertJ — no Quarkus container
- Source root: `blocks/src/main/java/` and `blocks/src/test/java/`

---

## Batch 1: Narrative Identity Foundation Types

### Task 1: NarrativeFragment sealed hierarchy + NarrativeState

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeFragment.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/IndividualEpisode.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/GroupEpisode.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/DerivedTheme.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeScope.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeState.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeTypesTest.java`

**Interfaces:**
- Consumes: `DriveAxis` from `io.casehub.blocks.agentic.social.drive`
- Produces: `NarrativeFragment` (sealed), `IndividualEpisode`, `GroupEpisode`, `DerivedTheme`, `NarrativeScope`, `NarrativeState` — used by all subsequent tasks

- [ ] **Step 1: Write NarrativeFragment sealed interface**

```java
package io.casehub.blocks.agentic.social.narrative;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.List;

public sealed interface NarrativeFragment
        permits IndividualEpisode, GroupEpisode, DerivedTheme {
    String id();
    Instant from();
    @Nullable Instant to();
    List<String> thematicTags();
}
```

- [ ] **Step 2: Write IndividualEpisode record**

```java
package io.casehub.blocks.agentic.social.narrative;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record IndividualEpisode(
        String id,
        Instant from,
        @org.jspecify.annotations.Nullable Instant to,
        List<String> thematicTags,
        String description,
        double emotionalValence,
        List<String> sourceReflectionIds
) implements NarrativeFragment {
    public IndividualEpisode {
        Objects.requireNonNull(id);
        Objects.requireNonNull(from);
        thematicTags = List.copyOf(thematicTags);
        Objects.requireNonNull(description);
        if (emotionalValence < -1.0 || emotionalValence > 1.0)
            throw new IllegalArgumentException("emotionalValence must be in [-1, 1]");
        sourceReflectionIds = List.copyOf(sourceReflectionIds);
    }
}
```

- [ ] **Step 3: Write GroupEpisode record**

```java
package io.casehub.blocks.agentic.social.narrative;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record GroupEpisode(
        String id,
        Instant from,
        @org.jspecify.annotations.Nullable Instant to,
        List<String> thematicTags,
        String description,
        double emotionalValence,
        Set<String> membershipAtTime,
        Map<String, String> roleAttributions,
        double consensusLevel
) implements NarrativeFragment {
    public GroupEpisode {
        Objects.requireNonNull(id);
        Objects.requireNonNull(from);
        thematicTags = List.copyOf(thematicTags);
        Objects.requireNonNull(description);
        if (emotionalValence < -1.0 || emotionalValence > 1.0)
            throw new IllegalArgumentException("emotionalValence must be in [-1, 1]");
        membershipAtTime = Set.copyOf(membershipAtTime);
        roleAttributions = Map.copyOf(roleAttributions);
        if (consensusLevel < 0.0 || consensusLevel > 1.0)
            throw new IllegalArgumentException("consensusLevel must be in [0, 1]");
    }
}
```

- [ ] **Step 4: Write DerivedTheme record**

```java
package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DerivedTheme(
        String id,
        Instant from,
        @org.jspecify.annotations.Nullable Instant to,
        List<String> thematicTags,
        String label,
        double salience,
        Map<DriveAxis, Double> axisModulationWeights,
        List<String> supportingFragmentIds
) implements NarrativeFragment {
    public DerivedTheme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(from);
        thematicTags = List.copyOf(thematicTags);
        Objects.requireNonNull(label);
        if (salience < 0.0 || salience > 1.0)
            throw new IllegalArgumentException("salience must be in [0, 1]");
        axisModulationWeights = Map.copyOf(axisModulationWeights);
        for (var w : axisModulationWeights.values()) {
            if (w < -1.0 || w > 1.0)
                throw new IllegalArgumentException("axis modulation weight must be in [-1, 1]");
        }
        supportingFragmentIds = List.copyOf(supportingFragmentIds);
    }
}
```

- [ ] **Step 5: Write NarrativeScope enum and NarrativeState record**

NarrativeScope:
```java
package io.casehub.blocks.agentic.social.narrative;

public enum NarrativeScope {
    INDIVIDUAL,
    GROUP
}
```

NarrativeState:
```java
package io.casehub.blocks.agentic.social.narrative;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record NarrativeState(
        String scopeId,
        String tenantId,
        NarrativeScope scope,
        List<NarrativeFragment> fragments,
        Instant synthesisedAt,
        int reflectionCountAtSynthesis
) {
    public NarrativeState {
        Objects.requireNonNull(scopeId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(scope);
        fragments = List.copyOf(fragments);
        Objects.requireNonNull(synthesisedAt);
    }

    public List<IndividualEpisode> episodes() {
        return fragments.stream()
                .filter(IndividualEpisode.class::isInstance)
                .map(IndividualEpisode.class::cast)
                .toList();
    }

    public List<GroupEpisode> groupEpisodes() {
        return fragments.stream()
                .filter(GroupEpisode.class::isInstance)
                .map(GroupEpisode.class::cast)
                .toList();
    }

    public List<DerivedTheme> themes() {
        return fragments.stream()
                .filter(DerivedTheme.class::isInstance)
                .map(DerivedTheme.class::cast)
                .toList();
    }

    public @Nullable DerivedTheme dominantTheme() {
        return themes().stream()
                .max(Comparator.comparingDouble(DerivedTheme::salience))
                .orElse(null);
    }
}
```

- [ ] **Step 6: Write NarrativeTypesTest**

```java
package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class NarrativeTypesTest {

    private final Instant now = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void individualEpisode_validConstruction() {
        var episode = new IndividualEpisode("e1", now, null,
                List.of("crisis", "teamwork"), "Helped team through crisis",
                0.8, List.of("r1", "r2"));
        assertThat(episode.id()).isEqualTo("e1");
        assertThat(episode.emotionalValence()).isEqualTo(0.8);
        assertThat(episode.thematicTags()).containsExactly("crisis", "teamwork");
        assertThat(episode.sourceReflectionIds()).containsExactly("r1", "r2");
    }

    @Test
    void individualEpisode_rejectsValenceOutOfRange() {
        assertThatThrownBy(() -> new IndividualEpisode("e1", now, null,
                List.of(), "desc", 1.5, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("emotionalValence");
    }

    @Test
    void individualEpisode_rejectsNegativeValenceOutOfRange() {
        assertThatThrownBy(() -> new IndividualEpisode("e1", now, null,
                List.of(), "desc", -1.5, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void individualEpisode_defensiveCopy() {
        var tags = new java.util.ArrayList<>(List.of("a"));
        var episode = new IndividualEpisode("e1", now, null, tags, "desc", 0.0, List.of());
        tags.add("b");
        assertThat(episode.thematicTags()).containsExactly("a");
    }

    @Test
    void groupEpisode_validConstruction() {
        var episode = new GroupEpisode("g1", now, null, List.of("collaboration"),
                "Joint investigation", 0.6,
                Set.of("agent-a", "agent-b"),
                Map.of("agent-a", "lead", "agent-b", "support"), 0.9);
        assertThat(episode.membershipAtTime()).containsExactlyInAnyOrder("agent-a", "agent-b");
        assertThat(episode.roleAttributions()).hasSize(2);
        assertThat(episode.consensusLevel()).isEqualTo(0.9);
    }

    @Test
    void groupEpisode_rejectsConsensusOutOfRange() {
        assertThatThrownBy(() -> new GroupEpisode("g1", now, null, List.of(),
                "desc", 0.0, Set.of(), Map.of(), 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consensusLevel");
    }

    @Test
    void derivedTheme_validConstruction() {
        var theme = new DerivedTheme("t1", now, null, List.of("helper"),
                "crisis-helper", 0.8,
                Map.of(DriveAxis.AFFILIATION, 0.5, DriveAxis.COMPETENCE, 0.3),
                List.of("e1", "e2"));
        assertThat(theme.label()).isEqualTo("crisis-helper");
        assertThat(theme.salience()).isEqualTo(0.8);
        assertThat(theme.axisModulationWeights()).hasSize(2);
    }

    @Test
    void derivedTheme_rejectsSalienceOutOfRange() {
        assertThatThrownBy(() -> new DerivedTheme("t1", now, null, List.of(),
                "label", 1.5, Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salience");
    }

    @Test
    void derivedTheme_rejectsModulationWeightOutOfRange() {
        assertThatThrownBy(() -> new DerivedTheme("t1", now, null, List.of(),
                "label", 0.5,
                Map.of(DriveAxis.CURIOSITY, 2.0), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("axis modulation weight");
    }

    @Test
    void derivedTheme_allowsNegativeModulationWeight() {
        var theme = new DerivedTheme("t1", now, null, List.of(),
                "label", 0.5,
                Map.of(DriveAxis.AUTONOMY, -0.5), List.of());
        assertThat(theme.axisModulationWeights().get(DriveAxis.AUTONOMY)).isEqualTo(-0.5);
    }

    @Test
    void narrativeState_typedAccessors() {
        var episode = new IndividualEpisode("e1", now, null, List.of(),
                "episode", 0.5, List.of());
        var group = new GroupEpisode("g1", now, null, List.of(),
                "group ep", 0.3, Set.of("a"), Map.of(), 0.8);
        var theme1 = new DerivedTheme("t1", now, null, List.of(),
                "helper", 0.9, Map.of(), List.of());
        var theme2 = new DerivedTheme("t2", now, null, List.of(),
                "expert", 0.4, Map.of(), List.of());

        var state = new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL,
                List.of(episode, group, theme1, theme2), now, 10);

        assertThat(state.episodes()).hasSize(1);
        assertThat(state.groupEpisodes()).hasSize(1);
        assertThat(state.themes()).hasSize(2);
        assertThat(state.dominantTheme().label()).isEqualTo("helper");
    }

    @Test
    void narrativeState_dominantTheme_empty() {
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(), now, 0);
        assertThat(state.dominantTheme()).isNull();
    }

    @Test
    void narrativeState_defensiveCopy() {
        var fragments = new java.util.ArrayList<NarrativeFragment>();
        var episode = new IndividualEpisode("e1", now, null, List.of(),
                "desc", 0.0, List.of());
        fragments.add(episode);
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                fragments, now, 1);
        fragments.add(new IndividualEpisode("e2", now, null, List.of(),
                "other", 0.0, List.of()));
        assertThat(state.fragments()).hasSize(1);
    }

    @Test
    void narrativeScope_values() {
        assertThat(NarrativeScope.values()).containsExactly(
                NarrativeScope.INDIVIDUAL, NarrativeScope.GROUP);
    }

    @Test
    void sealedHierarchy_permits() {
        assertThat(NarrativeFragment.class.getPermittedSubclasses()).hasSize(3);
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=NarrativeTypesTest`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/
git add blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/
git commit -m "feat(#142): add NarrativeFragment sealed hierarchy + NarrativeState Refs #142"
```

### Task 2: Config, tick types, SPIs, NarrativeModulation utility, ReflectionQueryStore

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeSynthesisGate.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeConfig.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeTick.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeSynthesisTick.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeStore.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NoOpNarrativeStore.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeModulation.java`
- Create: `blocks/src/main/java/io/casehub/blocks/memory/ReflectionQueryStore.java`
- Create: `blocks/src/main/java/io/casehub/blocks/memory/NoOpReflectionQueryStore.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeConfigTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeModulationTest.java`

**Interfaces:**
- Consumes: `NarrativeState`, `DerivedTheme`, `DriveAxis` from Task 1
- Consumes: `ReflectionEntry` from `io.casehub.blocks.memory`
- Produces: `NarrativeStore` SPI, `NarrativeModulation.compute()`, `ReflectionQueryStore` SPI — used by DriveComposer integration (Task 4) and children #143-#145

- [ ] **Step 1: Write NarrativeSynthesisGate and NarrativeConfig**

NarrativeSynthesisGate:
```java
package io.casehub.blocks.agentic.social.narrative;

import java.time.Duration;
import java.util.Objects;

public record NarrativeSynthesisGate(
        int minNewReflections,
        double noveltyThreshold,
        Duration quietPeriodBypass) {
    public NarrativeSynthesisGate {
        if (minNewReflections < 1)
            throw new IllegalArgumentException("minNewReflections must be >= 1");
        if (noveltyThreshold < 0.0 || noveltyThreshold > 1.0)
            throw new IllegalArgumentException("noveltyThreshold must be in [0, 1]");
        Objects.requireNonNull(quietPeriodBypass);
    }

    public static NarrativeSynthesisGate defaults() {
        return new NarrativeSynthesisGate(5, 0.3, Duration.ofMinutes(120));
    }
}
```

NarrativeConfig:
```java
package io.casehub.blocks.agentic.social.narrative;

import java.util.Objects;

public record NarrativeConfig(
        NarrativeSynthesisGate synthesisGate,
        int maxEpisodes,
        int maxThemes,
        double themeSalienceFloor,
        int maxReflectionsPerSynthesis) {
    public NarrativeConfig {
        Objects.requireNonNull(synthesisGate);
        if (maxEpisodes < 1) throw new IllegalArgumentException("maxEpisodes must be >= 1");
        if (maxThemes < 1) throw new IllegalArgumentException("maxThemes must be >= 1");
        if (themeSalienceFloor < 0.0 || themeSalienceFloor > 1.0)
            throw new IllegalArgumentException("themeSalienceFloor must be in [0, 1]");
        if (maxReflectionsPerSynthesis < 1)
            throw new IllegalArgumentException("maxReflectionsPerSynthesis must be >= 1");
    }

    public static NarrativeConfig defaults() {
        return new NarrativeConfig(NarrativeSynthesisGate.defaults(), 50, 10, 0.1, 20);
    }
}
```

- [ ] **Step 2: Write NarrativeTick and NarrativeSynthesisTick**

NarrativeTick:
```java
package io.casehub.blocks.agentic.social.narrative;

import org.jspecify.annotations.Nullable;
import java.util.List;

public sealed interface NarrativeTick {
    record NoChange(@Nullable String reason) implements NarrativeTick {}
    record Updated(NarrativeState previous, NarrativeState current,
                   List<String> newEpisodeIds,
                   List<String> newThemeLabels) implements NarrativeTick {}
}
```

NarrativeSynthesisTick:
```java
package io.casehub.blocks.agentic.social.narrative;

public sealed interface NarrativeSynthesisTick {
    record Skipped(String reason) implements NarrativeSynthesisTick {}
    record Synthesised(NarrativeState state,
                       int newReflectionsConsumed) implements NarrativeSynthesisTick {}
}
```

- [ ] **Step 3: Write NarrativeStore SPI and NoOpNarrativeStore**

NarrativeStore:
```java
package io.casehub.blocks.agentic.social.narrative;

import org.jspecify.annotations.Nullable;

public interface NarrativeStore {
    void store(NarrativeState state);
    @Nullable NarrativeState load(String scopeId, String tenantId);
}
```

NoOpNarrativeStore:
```java
package io.casehub.blocks.agentic.social.narrative;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

@DefaultBean
@ApplicationScoped
public class NoOpNarrativeStore implements NarrativeStore {
    @Override
    public void store(NarrativeState state) {}

    @Override
    public @Nullable NarrativeState load(String scopeId, String tenantId) {
        return null;
    }
}
```

- [ ] **Step 4: Write ReflectionQueryStore SPI and NoOp in memory package**

ReflectionQueryStore:
```java
package io.casehub.blocks.memory;

import java.time.Instant;
import java.util.List;

public interface ReflectionQueryStore {
    List<ReflectionEntry> findSince(String agentId, String tenantId, Instant since);
    int countSince(String agentId, String tenantId, Instant since);
}
```

NoOpReflectionQueryStore:
```java
package io.casehub.blocks.memory;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpReflectionQueryStore implements ReflectionQueryStore {
    @Override
    public List<ReflectionEntry> findSince(String agentId, String tenantId, Instant since) {
        return List.of();
    }

    @Override
    public int countSince(String agentId, String tenantId, Instant since) {
        return 0;
    }
}
```

- [ ] **Step 5: Write NarrativeModulation utility**

```java
package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import java.util.EnumMap;
import java.util.Map;

public final class NarrativeModulation {
    private NarrativeModulation() {}

    public static Map<DriveAxis, Double> compute(NarrativeState narrative) {
        var modulation = new EnumMap<DriveAxis, Double>(DriveAxis.class);
        for (var theme : narrative.themes()) {
            for (var entry : theme.axisModulationWeights().entrySet()) {
                modulation.merge(entry.getKey(),
                        theme.salience() * entry.getValue(), Double::sum);
            }
        }
        return Map.copyOf(modulation);
    }
}
```

- [ ] **Step 6: Write NarrativeConfigTest**

```java
package io.casehub.blocks.agentic.social.narrative;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class NarrativeConfigTest {

    @Test
    void synthesisGate_defaults() {
        var gate = NarrativeSynthesisGate.defaults();
        assertThat(gate.minNewReflections()).isEqualTo(5);
        assertThat(gate.noveltyThreshold()).isEqualTo(0.3);
        assertThat(gate.quietPeriodBypass()).isEqualTo(Duration.ofMinutes(120));
    }

    @Test
    void synthesisGate_rejectsZeroReflections() {
        assertThatThrownBy(() -> new NarrativeSynthesisGate(0, 0.3, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void synthesisGate_rejectsNoveltyOutOfRange() {
        assertThatThrownBy(() -> new NarrativeSynthesisGate(5, 1.5, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void config_defaults() {
        var config = NarrativeConfig.defaults();
        assertThat(config.maxEpisodes()).isEqualTo(50);
        assertThat(config.maxThemes()).isEqualTo(10);
        assertThat(config.themeSalienceFloor()).isEqualTo(0.1);
        assertThat(config.maxReflectionsPerSynthesis()).isEqualTo(20);
    }

    @Test
    void config_rejectsInvalidMaxEpisodes() {
        assertThatThrownBy(() -> new NarrativeConfig(
                NarrativeSynthesisGate.defaults(), 0, 10, 0.1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noOpNarrativeStore_loadReturnsNull() {
        var store = new NoOpNarrativeStore();
        assertThat(store.load("agent-1", "tenant-1")).isNull();
    }

    @Test
    void noOpNarrativeStore_storeNoOp() {
        var store = new NoOpNarrativeStore();
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                java.util.List.of(), java.time.Instant.now(), 0);
        assertThatCode(() -> store.store(state)).doesNotThrowAnyException();
    }

    @Test
    void noOpReflectionQueryStore_findSinceReturnsEmpty() {
        var store = new io.casehub.blocks.memory.NoOpReflectionQueryStore();
        assertThat(store.findSince("a", "t", java.time.Instant.EPOCH)).isEmpty();
    }

    @Test
    void noOpReflectionQueryStore_countSinceReturnsZero() {
        var store = new io.casehub.blocks.memory.NoOpReflectionQueryStore();
        assertThat(store.countSince("a", "t", java.time.Instant.EPOCH)).isEqualTo(0);
    }
}
```

- [ ] **Step 7: Write NarrativeModulationTest**

```java
package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class NarrativeModulationTest {

    private final Instant now = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void compute_singleTheme_singleAxis() {
        var theme = new DerivedTheme("t1", now, null, List.of(),
                "helper", 0.8,
                Map.of(DriveAxis.AFFILIATION, 0.5), List.of());
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(theme), now, 5);

        var mod = NarrativeModulation.compute(state);

        assertThat(mod.get(DriveAxis.AFFILIATION)).isCloseTo(0.4, within(0.001));
        assertThat(mod).doesNotContainKey(DriveAxis.CURIOSITY);
    }

    @Test
    void compute_multipleThemes_additiveComposition() {
        var theme1 = new DerivedTheme("t1", now, null, List.of(),
                "helper", 0.8,
                Map.of(DriveAxis.AFFILIATION, 0.5), List.of());
        var theme2 = new DerivedTheme("t2", now, null, List.of(),
                "expert", 0.6,
                Map.of(DriveAxis.AFFILIATION, 0.3, DriveAxis.COMPETENCE, 0.4),
                List.of());
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(theme1, theme2), now, 5);

        var mod = NarrativeModulation.compute(state);

        // AFFILIATION: 0.8 * 0.5 + 0.6 * 0.3 = 0.4 + 0.18 = 0.58
        assertThat(mod.get(DriveAxis.AFFILIATION)).isCloseTo(0.58, within(0.001));
        // COMPETENCE: 0.6 * 0.4 = 0.24
        assertThat(mod.get(DriveAxis.COMPETENCE)).isCloseTo(0.24, within(0.001));
    }

    @Test
    void compute_negativeWeight_dampens() {
        var theme = new DerivedTheme("t1", now, null, List.of(),
                "independent", 0.7,
                Map.of(DriveAxis.AUTONOMY, -0.4), List.of());
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(theme), now, 3);

        var mod = NarrativeModulation.compute(state);

        assertThat(mod.get(DriveAxis.AUTONOMY)).isCloseTo(-0.28, within(0.001));
    }

    @Test
    void compute_noThemes_emptyMap() {
        var episode = new IndividualEpisode("e1", now, null, List.of(),
                "desc", 0.5, List.of());
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(episode), now, 1);

        var mod = NarrativeModulation.compute(state);

        assertThat(mod).isEmpty();
    }

    @Test
    void compute_emptyState_emptyMap() {
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(), now, 0);

        var mod = NarrativeModulation.compute(state);

        assertThat(mod).isEmpty();
    }
}
```

- [ ] **Step 8: Run tests**

Run: `mvn --batch-mode test -pl blocks -Dtest="NarrativeConfigTest,NarrativeModulationTest"`
Expected: All tests PASS

- [ ] **Step 9: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/
git add blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/
git add blocks/src/main/java/io/casehub/blocks/memory/ReflectionQueryStore.java
git add blocks/src/main/java/io/casehub/blocks/memory/NoOpReflectionQueryStore.java
git commit -m "feat(#142): add narrative config, SPIs, NarrativeModulation utility, ReflectionQueryStore Refs #142"
```

## Batch 2: Social Emergence Foundation Types

### Task 3: Norm types + Collective goal types

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/NormStrength.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/SocialNorm.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/NormObservation.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/DetectedNorms.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/NormDetectionTick.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/NormDetectionConfig.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/DriveAlignment.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/CollectiveGoalProposal.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/CollectiveGoalTick.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/CollectiveGoalConfig.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/emergence/EmergenceTypesTest.java`

**Interfaces:**
- Consumes: `DriveAxis` from drive package
- Produces: All emergence types — used by children #146-#148

- [ ] **Step 1: Write NormStrength enum**

```java
package io.casehub.blocks.agentic.social.emergence;

public enum NormStrength {
    EMERGING,
    ESTABLISHED,
    DECLINING
}
```

- [ ] **Step 2: Write SocialNorm record**

```java
package io.casehub.blocks.agentic.social.emergence;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record SocialNorm(
        String normId,
        String description,
        String behavioralPattern,
        double adherenceRate,
        int observationCount,
        Set<String> participatingAgents,
        Instant firstObserved,
        Instant lastObserved,
        NormStrength strength) {
    public SocialNorm {
        Objects.requireNonNull(normId);
        Objects.requireNonNull(description);
        Objects.requireNonNull(behavioralPattern);
        if (adherenceRate < 0.0 || adherenceRate > 1.0)
            throw new IllegalArgumentException("adherenceRate must be in [0, 1]");
        participatingAgents = Set.copyOf(participatingAgents);
        Objects.requireNonNull(firstObserved);
        Objects.requireNonNull(lastObserved);
        Objects.requireNonNull(strength);
    }
}
```

- [ ] **Step 3: Write NormObservation, DetectedNorms, NormDetectionTick, NormDetectionConfig**

NormObservation:
```java
package io.casehub.blocks.agentic.social.emergence;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record NormObservation(
        String observationId,
        String tenantId,
        String behavioralPattern,
        Set<String> involvedAgents,
        String conversationId,
        Instant observedAt,
        boolean patternFollowed) {
    public NormObservation {
        Objects.requireNonNull(observationId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(behavioralPattern);
        involvedAgents = Set.copyOf(involvedAgents);
        Objects.requireNonNull(observedAt);
    }
}
```

DetectedNorms:
```java
package io.casehub.blocks.agentic.social.emergence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DetectedNorms(
        List<SocialNorm> norms,
        int observationsAnalysed,
        Instant analysedAt) {
    public DetectedNorms {
        norms = List.copyOf(norms);
        Objects.requireNonNull(analysedAt);
    }

    public List<SocialNorm> established() {
        return norms.stream()
                .filter(n -> n.strength() == NormStrength.ESTABLISHED)
                .toList();
    }
}
```

NormDetectionTick:
```java
package io.casehub.blocks.agentic.social.emergence;

import org.jspecify.annotations.Nullable;
import java.util.List;

public sealed interface NormDetectionTick {
    record NoChange(@Nullable String reason) implements NormDetectionTick {}
    record Updated(DetectedNorms previous, DetectedNorms current,
                   List<String> newNormIds,
                   List<String> declinedNormIds) implements NormDetectionTick {}
}
```

NormDetectionConfig:
```java
package io.casehub.blocks.agentic.social.emergence;

public record NormDetectionConfig(
        int minObservationsForNorm,
        double establishedThreshold,
        double decliningThreshold,
        int minAgentsForNorm) {
    public NormDetectionConfig {
        if (minObservationsForNorm < 1)
            throw new IllegalArgumentException("minObservationsForNorm must be >= 1");
        if (establishedThreshold < 0.0 || establishedThreshold > 1.0)
            throw new IllegalArgumentException("establishedThreshold must be in [0, 1]");
        if (decliningThreshold < 0.0 || decliningThreshold > 1.0)
            throw new IllegalArgumentException("decliningThreshold must be in [0, 1]");
        if (minAgentsForNorm < 1)
            throw new IllegalArgumentException("minAgentsForNorm must be >= 1");
    }

    public static NormDetectionConfig defaults() {
        return new NormDetectionConfig(10, 0.7, 0.4, 2);
    }
}
```

- [ ] **Step 4: Write DriveAlignment, CollectiveGoalProposal, CollectiveGoalTick, CollectiveGoalConfig**

DriveAlignment:
```java
package io.casehub.blocks.agentic.social.emergence;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record DriveAlignment(
        Set<String> agentIds,
        Map<DriveAxis, Double> alignmentPerAxis,
        double compositeAlignment,
        @Nullable DriveAxis dominantSharedAxis,
        Instant computedAt) {
    public DriveAlignment {
        agentIds = Set.copyOf(agentIds);
        alignmentPerAxis = Map.copyOf(alignmentPerAxis);
        if (compositeAlignment < 0.0 || compositeAlignment > 1.0)
            throw new IllegalArgumentException("compositeAlignment must be in [0, 1]");
        Objects.requireNonNull(computedAt);
    }
}
```

CollectiveGoalProposal:
```java
package io.casehub.blocks.agentic.social.emergence;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import java.util.Objects;
import java.util.Set;

public record CollectiveGoalProposal(
        DriveAlignment alignment,
        String goalDescription,
        Set<String> proposedParticipants,
        DriveAxis primaryAxis) {
    public CollectiveGoalProposal {
        Objects.requireNonNull(alignment);
        Objects.requireNonNull(goalDescription);
        proposedParticipants = Set.copyOf(proposedParticipants);
        Objects.requireNonNull(primaryAxis);
    }
}
```

CollectiveGoalTick:
```java
package io.casehub.blocks.agentic.social.emergence;

import org.jspecify.annotations.Nullable;
import java.util.List;

public sealed interface CollectiveGoalTick {
    record NoChange(@Nullable String reason) implements CollectiveGoalTick {}
    record Proposed(List<CollectiveGoalProposal> proposals,
                    List<DriveAlignment> alignments) implements CollectiveGoalTick {}
}
```

CollectiveGoalConfig:
```java
package io.casehub.blocks.agentic.social.emergence;

import java.time.Duration;
import java.util.Objects;

public record CollectiveGoalConfig(
        double alignmentThreshold,
        int minAlignedAgents,
        Duration cooldown) {
    public CollectiveGoalConfig {
        if (alignmentThreshold < 0.0 || alignmentThreshold > 1.0)
            throw new IllegalArgumentException("alignmentThreshold must be in [0, 1]");
        if (minAlignedAgents < 2)
            throw new IllegalArgumentException("minAlignedAgents must be >= 2");
        Objects.requireNonNull(cooldown);
    }

    public static CollectiveGoalConfig defaults() {
        return new CollectiveGoalConfig(0.6, 2, Duration.ofMinutes(60));
    }
}
```

- [ ] **Step 5: Write EmergenceTypesTest**

```java
package io.casehub.blocks.agentic.social.emergence;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class EmergenceTypesTest {

    private final Instant now = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void socialNorm_validConstruction() {
        var norm = new SocialNorm("n1", "verify before escalating",
                "verify-then-escalate", 0.85, 20,
                Set.of("agent-a", "agent-b"), now, now, NormStrength.ESTABLISHED);
        assertThat(norm.adherenceRate()).isEqualTo(0.85);
        assertThat(norm.strength()).isEqualTo(NormStrength.ESTABLISHED);
        assertThat(norm.participatingAgents()).containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void socialNorm_rejectsAdherenceOutOfRange() {
        assertThatThrownBy(() -> new SocialNorm("n1", "desc", "pattern",
                1.5, 10, Set.of(), now, now, NormStrength.EMERGING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adherenceRate");
    }

    @Test
    void normObservation_validConstruction() {
        var obs = new NormObservation("o1", "tenant-1", "verify-then-escalate",
                Set.of("agent-a", "agent-b"), "conv-1", now, true);
        assertThat(obs.patternFollowed()).isTrue();
        assertThat(obs.involvedAgents()).hasSize(2);
    }

    @Test
    void detectedNorms_establishedFilter() {
        var established = new SocialNorm("n1", "d", "p", 0.8, 15,
                Set.of("a"), now, now, NormStrength.ESTABLISHED);
        var emerging = new SocialNorm("n2", "d", "p", 0.5, 8,
                Set.of("a"), now, now, NormStrength.EMERGING);
        var detected = new DetectedNorms(List.of(established, emerging), 30, now);

        assertThat(detected.established()).hasSize(1);
        assertThat(detected.established().getFirst().normId()).isEqualTo("n1");
    }

    @Test
    void normDetectionConfig_defaults() {
        var config = NormDetectionConfig.defaults();
        assertThat(config.minObservationsForNorm()).isEqualTo(10);
        assertThat(config.establishedThreshold()).isEqualTo(0.7);
        assertThat(config.decliningThreshold()).isEqualTo(0.4);
        assertThat(config.minAgentsForNorm()).isEqualTo(2);
    }

    @Test
    void normDetectionConfig_rejectsInvalid() {
        assertThatThrownBy(() -> new NormDetectionConfig(0, 0.7, 0.4, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void driveAlignment_validConstruction() {
        var alignment = new DriveAlignment(
                Set.of("agent-a", "agent-b"),
                Map.of(DriveAxis.CURIOSITY, 0.9, DriveAxis.AFFILIATION, 0.7),
                0.8, DriveAxis.CURIOSITY, now);
        assertThat(alignment.compositeAlignment()).isEqualTo(0.8);
        assertThat(alignment.dominantSharedAxis()).isEqualTo(DriveAxis.CURIOSITY);
    }

    @Test
    void driveAlignment_rejectsCompositeOutOfRange() {
        assertThatThrownBy(() -> new DriveAlignment(Set.of("a"), Map.of(),
                1.5, null, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void collectiveGoalProposal_validConstruction() {
        var alignment = new DriveAlignment(Set.of("a", "b"),
                Map.of(DriveAxis.CURIOSITY, 0.8), 0.8, DriveAxis.CURIOSITY, now);
        var proposal = new CollectiveGoalProposal(alignment,
                "explore shared knowledge gap",
                Set.of("a", "b"), DriveAxis.CURIOSITY);
        assertThat(proposal.goalDescription()).isEqualTo("explore shared knowledge gap");
    }

    @Test
    void collectiveGoalConfig_defaults() {
        var config = CollectiveGoalConfig.defaults();
        assertThat(config.alignmentThreshold()).isEqualTo(0.6);
        assertThat(config.minAlignedAgents()).isEqualTo(2);
        assertThat(config.cooldown()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void collectiveGoalConfig_rejectsMinAgentsBelow2() {
        assertThatThrownBy(() -> new CollectiveGoalConfig(0.6, 1, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minAlignedAgents");
    }

    @Test
    void normDetectionTick_noChange() {
        var tick = new NormDetectionTick.NoChange("no observations");
        assertThat(tick.reason()).isEqualTo("no observations");
    }

    @Test
    void collectiveGoalTick_noChange() {
        var tick = new CollectiveGoalTick.NoChange("no alignment");
        assertThat(tick.reason()).isEqualTo("no alignment");
    }

    @Test
    void normStrength_values() {
        assertThat(NormStrength.values()).containsExactly(
                NormStrength.EMERGING, NormStrength.ESTABLISHED, NormStrength.DECLINING);
    }
}
```

- [ ] **Step 6: Run tests**

Run: `mvn --batch-mode test -pl blocks -Dtest=EmergenceTypesTest`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/
git add blocks/src/test/java/io/casehub/blocks/agentic/social/emergence/
git commit -m "feat(#142): add social emergence foundation types — norms, alignment, collective goals Refs #142"
```

## Batch 3: DriveComposer Integration + Observation Rendering

### Task 4: DriveConfig + DriveComposer narrative modulation + CognitiveObservationSections

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/drive/DriveConfig.java`
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/drive/DriveComposer.java`
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/drive/DriveOrchestrator.java` (pass null for new param)
- Modify: `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/drive/DriveComposerTest.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/drive/DriveTypesTest.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/drive/DriveOrchestratorTest.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/goal/GoalProposalIntegrationTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSectionsTest.java` (add new test method)

**Interfaces:**
- Consumes: `NarrativeState`, `NarrativeModulation`, `DerivedTheme` from Task 1-2
- Consumes: `DriveComposer.compose()` existing signature
- Produces: Updated `DriveComposer.compose()` with 8th parameter `@Nullable Map<DriveAxis, Double> narrativeModulation`

- [ ] **Step 1: Add `narrativeModulationStrength` to DriveConfig**

Add field at position 11 (after `autonomyConfidenceFloor`):

Current DriveConfig has 10 fields. Add `double narrativeModulationStrength` as the 11th field.

Update `DriveConfig.defaults()` to include: `0.25` (same default strength as personalityModulationStrength).

Use `ide_replace_member` to replace the DriveConfig record body with the updated version including the new field, updated compact constructor, and updated `defaults()`.

- [ ] **Step 2: Add narrative modulation to DriveComposer.compose()**

Insert `@Nullable Map<DriveAxis, Double> narrativeModulation` parameter after `mood` and before `config`:

```java
public DriveProfile compose(Map<DriveAxis, DriveIntensity> rawDrives,
                            @Nullable AgentDisposition disposition,
                            @Nullable MoodState mood,
                            @Nullable Map<DriveAxis, Double> narrativeModulation,
                            DriveConfig config,
                            String agentId, String tenantId, Instant now) {
```

Add `import java.util.Map;` (already present) and add null check on the map import.

In the modulation loop, after personality modulation and before clamping, add:

```java
if (narrativeModulation != null) {
    intensity += narrativeModulation.getOrDefault(axis, 0.0)
                 * config.narrativeModulationStrength();
}
```

- [ ] **Step 3: Update DriveOrchestrator.tick() to pass null**

Change line 97 from:
```java
var newProfile = composer.compose(raw, disposition, mood, config,
        agentId, tenantId, now);
```
to:
```java
var newProfile = composer.compose(raw, disposition, mood, null, config,
        agentId, tenantId, now);
```

- [ ] **Step 4: Update all DriveConfig constructor call sites in tests**

All positional DriveConfig constructors in tests need the new 11th field (`narrativeModulationStrength`). Search for `new DriveConfig(` in test files and add `0.25` at the end:

DriveComposerTest line 46-49: add `, 0.25` after `0.6`
DriveTypesTest line 105: add `, 0.25` after `0.6`

`DriveConfig.defaults()` call sites don't need changes — defaults() already includes the new field.

- [ ] **Step 5: Update all compose() call sites in tests**

All `composer.compose(` calls need `null` inserted after the mood parameter (3rd arg) and before config (4th arg becomes 5th).

DriveComposerTest — 9 calls. Each gains a `null,` after the mood argument:
```java
// Before: composer.compose(raw, null, null, DriveConfig.defaults(), "a", "t", now)
// After:  composer.compose(raw, null, null, null, DriveConfig.defaults(), "a", "t", now)
```

- [ ] **Step 6: Add narrative modulation tests to DriveComposerTest**

Add these test methods to DriveComposerTest:

```java
@Test
void compose_narrativeModulation_amplifiesAxis() {
    var narrativeMod = Map.of(DriveAxis.AFFILIATION, 0.5);

    var profile = composer.compose(uniformRaw(0.5), null, null, narrativeMod,
            DriveConfig.defaults(), "a", "t", now);

    assertThat(profile.drives().get(DriveAxis.AFFILIATION).intensity())
            .isGreaterThan(0.5);
    assertThat(profile.drives().get(DriveAxis.CURIOSITY).intensity())
            .isEqualTo(0.5);
}

@Test
void compose_narrativeModulation_dampensAxis() {
    var narrativeMod = Map.of(DriveAxis.AUTONOMY, -0.6);

    var profile = composer.compose(uniformRaw(0.5), null, null, narrativeMod,
            DriveConfig.defaults(), "a", "t", now);

    assertThat(profile.drives().get(DriveAxis.AUTONOMY).intensity())
            .isLessThan(0.5);
}

@Test
void compose_narrativeModulation_multipleAxes() {
    var narrativeMod = Map.of(
            DriveAxis.AFFILIATION, 0.4,
            DriveAxis.COMPETENCE, 0.3);

    var profile = composer.compose(uniformRaw(0.5), null, null, narrativeMod,
            DriveConfig.defaults(), "a", "t", now);

    assertThat(profile.drives().get(DriveAxis.AFFILIATION).intensity())
            .isGreaterThan(profile.drives().get(DriveAxis.CURIOSITY).intensity());
    assertThat(profile.drives().get(DriveAxis.COMPETENCE).intensity())
            .isGreaterThan(profile.drives().get(DriveAxis.CURIOSITY).intensity());
}

@Test
void compose_nullNarrativeModulation_noEffect() {
    var withNull = composer.compose(uniformRaw(0.5), null, null, null,
            DriveConfig.defaults(), "a", "t", now);
    var withEmpty = composer.compose(uniformRaw(0.5), null, null, Map.of(),
            DriveConfig.defaults(), "a", "t", now);

    for (var axis : DriveAxis.values()) {
        assertThat(withNull.drives().get(axis).intensity())
                .isEqualTo(withEmpty.drives().get(axis).intensity());
    }
}
```

- [ ] **Step 7: Add narrativeSection to CognitiveObservationSections**

Add import for narrative types and a new static method:

```java
public static ObservationSection narrativeSection(NarrativeState state) {
    var items = new ArrayList<String>();
    var dominant = state.dominantTheme();
    if (dominant != null) {
        items.add("Core identity: " + dominant.label()
                + " (salience: " + String.format("%.1f", dominant.salience()) + ")");
    }
    for (var episode : state.episodes()) {
        if (episode.emotionalValence() > 0.3 || episode.emotionalValence() < -0.3) {
            items.add("Memory: " + episode.description());
        }
    }
    for (var theme : state.themes()) {
        if (!theme.equals(dominant) && theme.salience() >= 0.3) {
            items.add("Theme: " + theme.label());
        }
    }
    if (items.isEmpty()) {
        return ObservationSection.items("Self-Narrative", "No established identity yet.", List.of());
    }
    return ObservationSection.items("Self-Narrative", null, items);
}
```

- [ ] **Step 8: Add narrativeSection test**

Find the existing CognitiveObservationSectionsTest and add:

```java
@Test
void narrativeSection_rendersIdentity() {
    var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
            "crisis-helper", 0.9, Map.of(), List.of());
    var episode = new IndividualEpisode("e1", Instant.now(), null, List.of(),
            "Helped team through crisis", 0.8, List.of());
    var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
            List.of(theme, episode), Instant.now(), 5);

    var section = CognitiveObservationSections.narrativeSection(state);

    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).anyMatch(i -> i.contains("crisis-helper"));
    assertThat(items).anyMatch(i -> i.contains("Helped team through crisis"));
}

@Test
void narrativeSection_emptyState() {
    var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
            List.of(), Instant.now(), 0);

    var section = CognitiveObservationSections.narrativeSection(state);

    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var itemList = (ObservationSection.ItemList) section;
    assertThat(itemList.contextLine()).isEqualTo("No established identity yet.");
}
```

- [ ] **Step 9: Run full test suite**

Run: `mvn --batch-mode test -pl blocks`
Expected: All tests PASS (including all existing tests with updated signatures)

- [ ] **Step 10: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/drive/
git add blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java
git add blocks/src/test/java/io/casehub/blocks/agentic/social/drive/
git add blocks/src/test/java/io/casehub/blocks/agentic/social/goal/
git add blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/
git commit -m "feat(#142): add narrative modulation to DriveComposer + narrativeSection rendering Refs #142"
```

## References

- [specs/issue-142-narrative-identity/2026-08-24-narrative-identity-social-emergence-design.md] — design spec
- [specs/issue-142-narrative-identity/decisions.md] — 12 validated decisions
- [docs/adr/ADR-0001-drive-compositor-pattern.md] — compositor pattern
- [DriveComposer.java:16] — compose() method (7 params → 8)
- [DriveConfig.java:7] — config record (10 fields → 11)
- [DriveOrchestrator.java:97] — compose() call site
- [CognitiveObservationSections.java:13] — observation rendering
- [ReflectionStore.java:4] — write-only SPI (ReflectionQueryStore adds read)
- [GE-20260823-11ffe5] — CDI functional interface self-filtering
- [GE-20260820-aa31ab] — memory retention composite masking
- [GitHub #142] — Narrative Identity + Social Emergence epic
