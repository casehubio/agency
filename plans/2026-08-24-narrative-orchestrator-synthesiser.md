# NarrativeOrchestrator + NarrativeSynthesiser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #143 — NarrativeOrchestrator — first-person autobiography synthesis
**Issue group:** #142, #143

**Goal:** Implement the two-component narrative synthesis architecture — a compositor
that caches narrative state and a synthesiser that transforms reflections into
episodes and themes via LLM.

**Architecture:** Split architecture (D3): NarrativeSynthesiser (effectful, LLM-driven)
writes to NarrativeStore; NarrativeOrchestrator (compositor) reads from NarrativeStore
and caches. Both are `@ApplicationScoped` with per-agent `ReentrantLock`. Synthesis is
incremental — episodes additive, themes fully re-derived (D13/D14).

**Tech Stack:** Java 21+, JUnit 5, Mockito, AssertJ, jakarta.json (JSON-P),
Mutiny Multi, casehub-platform-agent-api (AgentProvider)

## Global Constraints

- No Quarkus runtime in tests — plain JUnit 5 with Mockito
- All source under `blocks/src/main/java/io/casehub/blocks/`
- All tests under `blocks/src/test/java/io/casehub/blocks/`
- Build: `mvn --batch-mode test`
- Commits reference `Refs #143`

## Review Addendum

Plan review surfaced 7 findings. Apply these during implementation:

**Task 1 addition:** Modify `NarrativeTick.Updated` — make `previous` `@Nullable`.
First load returns `Updated(null, loaded, ...)`. Add a test for this. (R1-04)

**Task 2 changes:**
- `NarrativeOrchestrator.tick()` first-load path: return `Updated(null, loaded, ...)`
  instead of `Updated(loaded, loaded, ...)`. (R1-04)
- Test helper `stateWith` → `stateWith(String agentId, ...)` parameterized. (R1-07)

**Task 3 changes:**
- **Watermark fix (R1-02):** When reflections are capped by `maxReflectionsPerSynthesis`,
  set `synthesisedAt` to the `generatedAt` of the last consumed reflection (not
  `Instant.now(clock)`). This preserves unconsumed reflections for the next cycle.
  When NOT capped, use `Instant.now(clock)` as before.
- **Degenerate response guard (R1-03):** Before writing state, check:
  (a) if `newEpisodes.isEmpty() && themes.isEmpty()` → `Skipped("empty synthesis result")`
  (b) if `themes.isEmpty() && !allEpisodes.isEmpty()` → `Skipped("no themes produced")`
- **buildThemes tag matching (R1-05):** Accept `List<? extends NarrativeFragment>`
  instead of `List<IndividualEpisode>` — match tags against all fragment types
  including GroupEpisode.
- **Case-insensitive tags (R1-06):** Normalize thematic tags to lowercase for
  matching: `themeTags = spec.thematicTags().stream().map(String::toLowerCase).collect(toSet())`,
  compare with `tag.toLowerCase()`.
- **Additional tests:** Add `synthesise_emptyResult_skipped`,
  `synthesise_emptyThemesWithEpisodes_skipped`, `synthesise_cappedReflections_watermarkPreserved`.

---

## Batch 1: Foundation + Compositor

### Task 1: TokenJaccardDistance visibility + NarrativeModulation clamping

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/TokenJaccardDistance.java`
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeModulation.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeModulationTest.java`

**Interfaces:**
- Produces: `TokenJaccardDistance.distance(String, String)` — now public, callable from `social.narrative` package
- Produces: `NarrativeModulation.compute(NarrativeState)` — now clamps per-axis result to [-1, 1]

- [ ] **Step 1: Write 3 failing clamping tests**

Add to `NarrativeModulationTest.java` using `ide_insert_member`:

```java
@Test
void compute_clampsPositiveOverflow() {
    var theme1 = new DerivedTheme("t1", now, null, List.of(),
            "a", 1.0, Map.of(DriveAxis.CURIOSITY, 0.8), List.of());
    var theme2 = new DerivedTheme("t2", now, null, List.of(),
            "b", 1.0, Map.of(DriveAxis.CURIOSITY, 0.7), List.of());
    var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
            List.of(theme1, theme2), now, 5);

    var mod = NarrativeModulation.compute(state);

    assertThat(mod.get(DriveAxis.CURIOSITY)).isEqualTo(1.0);
}

@Test
void compute_clampsNegativeOverflow() {
    var theme1 = new DerivedTheme("t1", now, null, List.of(),
            "a", 1.0, Map.of(DriveAxis.AFFILIATION, -0.8), List.of());
    var theme2 = new DerivedTheme("t2", now, null, List.of(),
            "b", 1.0, Map.of(DriveAxis.AFFILIATION, -0.6), List.of());
    var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
            List.of(theme1, theme2), now, 5);

    var mod = NarrativeModulation.compute(state);

    assertThat(mod.get(DriveAxis.AFFILIATION)).isEqualTo(-1.0);
}

@Test
void compute_withinBounds_noClamping() {
    var theme = new DerivedTheme("t1", now, null, List.of(),
            "helper", 0.5, Map.of(DriveAxis.COMPETENCE, 0.6), List.of());
    var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
            List.of(theme), now, 3);

    var mod = NarrativeModulation.compute(state);

    assertThat(mod.get(DriveAxis.COMPETENCE)).isCloseTo(0.3, within(0.001));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -Dtest=NarrativeModulationTest`
Expected: 2 new tests FAIL (clamping tests — unclamped sum exceeds bounds).
The `withinBounds` test should PASS (already within bounds).

- [ ] **Step 3: Make TokenJaccardDistance public**

Use `ide_edit_member` on `TokenJaccardDistance.java`:
- Class: change `final class TokenJaccardDistance` → `public final class TokenJaccardDistance`
- Method: change `static double distance` → `public static double distance`

```java
public final class TokenJaccardDistance {

    private TokenJaccardDistance() {}

    public static double distance(String a, String b) {
```

- [ ] **Step 4: Add clamping to NarrativeModulation.compute()**

Use `ide_replace_member` on `NarrativeModulation.java`, member `compute`:

```java
var modulation = new EnumMap<DriveAxis, Double>(DriveAxis.class);
for (var theme : narrative.themes()) {
    for (var entry : theme.axisModulationWeights().entrySet()) {
        modulation.merge(entry.getKey(),
                theme.salience() * entry.getValue(), Double::sum);
    }
}
modulation.replaceAll((axis, value) -> Math.clamp(value, -1.0, 1.0));
return Map.copyOf(modulation);
```

- [ ] **Step 5: Run all tests to verify**

Run: `mvn --batch-mode test -Dtest=NarrativeModulationTest`
Expected: ALL 10 tests PASS (7 existing + 3 new)

- [ ] **Step 6: Run full test suite**

Run: `mvn --batch-mode test`
Expected: ALL tests PASS. `TokenJaccardDistanceTest` still passes (public is a superset of package-private).

- [ ] **Step 7: Verify with ide_diagnostics**

Run `ide_diagnostics` on both modified files. Expected: no errors.

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/153/blocks add blocks/src/main/java/io/casehub/blocks/agentic/social/TokenJaccardDistance.java blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeModulation.java blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeModulationTest.java
git -C /Users/mdproctor/claude/casehub/slots/153/blocks commit -m "feat(#143): TokenJaccardDistance public + NarrativeModulation per-axis clamping Refs #143"
```

---

### Task 2: NarrativeOrchestrator — compositor

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeOrchestrator.java`
- Create: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeOrchestratorTest.java`

**Interfaces:**
- Consumes: `NarrativeStore.load(String scopeId, String tenantId)` → `@Nullable NarrativeState`
- Produces: `NarrativeOrchestrator.tick(String agentId, String tenantId)` → `NarrativeTick`
- Produces: `NarrativeOrchestrator.currentNarrative(String agentId, String tenantId)` → `Optional<NarrativeState>`

- [ ] **Step 1: Write all 8 tests**

Create `NarrativeOrchestratorTest.java` using `ide_create_file`:

```java
package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NarrativeOrchestratorTest {

    private static final Instant T1 = Instant.parse("2026-08-24T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-08-24T11:00:00Z");

    private NarrativeStore store;
    private NarrativeOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        store = mock(NarrativeStore.class);
        orchestrator = new NarrativeOrchestrator(store);
    }

    private NarrativeState stateWith(Instant synthesisedAt, List<NarrativeFragment> fragments) {
        return new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL, fragments, synthesisedAt, 5);
    }

    private IndividualEpisode episode(String id) {
        return new IndividualEpisode(id, T1, null, List.of("tag"),
                "desc-" + id, 0.5, List.of());
    }

    private DerivedTheme theme(String label) {
        return new DerivedTheme("t-" + label, T1, null, List.of(),
                label, 0.7, Map.of(DriveAxis.CURIOSITY, 0.3), List.of());
    }

    @Test
    void tick_firstLoad_returnsUpdated() {
        var state = stateWith(T1, List.of(episode("e1"), theme("helper")));
        when(store.load("agent-1", "tenant-1")).thenReturn(state);

        var tick = orchestrator.tick("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeTick.Updated.class);
        var updated = (NarrativeTick.Updated) tick;
        assertThat(updated.current()).isSameAs(state);
        assertThat(updated.newEpisodeIds()).containsExactly("e1");
        assertThat(updated.newThemeLabels()).containsExactly("helper");
    }

    @Test
    void tick_noStateInStore_returnsNoChange() {
        when(store.load("agent-1", "tenant-1")).thenReturn(null);

        var tick = orchestrator.tick("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeTick.NoChange.class);
        assertThat(((NarrativeTick.NoChange) tick).reason())
                .isEqualTo("no narrative in store");
    }

    @Test
    void tick_sameTimestamp_returnsNoChange() {
        var state = stateWith(T1, List.of(episode("e1")));
        when(store.load("agent-1", "tenant-1")).thenReturn(state);

        orchestrator.tick("agent-1", "tenant-1");
        var tick2 = orchestrator.tick("agent-1", "tenant-1");

        assertThat(tick2).isInstanceOf(NarrativeTick.NoChange.class);
        assertThat(((NarrativeTick.NoChange) tick2).reason())
                .isEqualTo("no new synthesis");
    }

    @Test
    void tick_newSynthesis_detectsNewEpisodesAndThemes() {
        var state1 = stateWith(T1, List.of(episode("e1"), theme("helper")));
        when(store.load("agent-1", "tenant-1")).thenReturn(state1);
        orchestrator.tick("agent-1", "tenant-1");

        var state2 = stateWith(T2, List.of(episode("e1"), episode("e2"),
                theme("helper"), theme("expert")));
        when(store.load("agent-1", "tenant-1")).thenReturn(state2);

        var tick = orchestrator.tick("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeTick.Updated.class);
        var updated = (NarrativeTick.Updated) tick;
        assertThat(updated.newEpisodeIds()).containsExactly("e2");
        assertThat(updated.newThemeLabels()).containsExactly("expert");
    }

    @Test
    void currentNarrative_returnsEmpty_beforeTick() {
        assertThat(orchestrator.currentNarrative("agent-1", "tenant-1")).isEmpty();
    }

    @Test
    void currentNarrative_returnsCached_afterTick() {
        var state = stateWith(T1, List.of(episode("e1")));
        when(store.load("agent-1", "tenant-1")).thenReturn(state);

        orchestrator.tick("agent-1", "tenant-1");

        assertThat(orchestrator.currentNarrative("agent-1", "tenant-1"))
                .isPresent()
                .containsSame(state);
    }

    @Test
    void tick_perAgentIsolation() {
        var state1 = stateWith(T1, List.of(episode("e1")));
        var state2 = stateWith(T1, List.of(episode("e2"), episode("e3")));
        when(store.load("agent-1", "tenant-1")).thenReturn(state1);
        when(store.load("agent-2", "tenant-1")).thenReturn(state2);

        orchestrator.tick("agent-1", "tenant-1");
        orchestrator.tick("agent-2", "tenant-1");

        assertThat(orchestrator.currentNarrative("agent-1", "tenant-1").get()
                .episodes()).hasSize(1);
        assertThat(orchestrator.currentNarrative("agent-2", "tenant-1").get()
                .episodes()).hasSize(2);
    }

    @Test
    void tick_threadSafety() throws InterruptedException {
        var state = stateWith(T1, List.of(episode("e1")));
        when(store.load(anyString(), anyString())).thenReturn(state);

        var errors = new AtomicInteger(0);
        var latch = new CountDownLatch(10);
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 10; i++) {
                var agentId = "agent-" + (i % 3);
                executor.submit(() -> {
                    try {
                        orchestrator.tick(agentId, "tenant-1");
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }
        assertThat(errors.get()).isZero();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -Dtest=NarrativeOrchestratorTest`
Expected: ALL FAIL — `NarrativeOrchestrator` class does not exist.

- [ ] **Step 3: Create NarrativeOrchestrator implementation**

Create `NarrativeOrchestrator.java` using `ide_create_file`:

```java
package io.casehub.blocks.agentic.social.narrative;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class NarrativeOrchestrator {

    private final NarrativeStore store;
    private final ConcurrentHashMap<String, NarrativeState> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    @Inject
    public NarrativeOrchestrator(NarrativeStore store) {
        this.store = store;
    }

    public NarrativeTick tick(String agentId, String tenantId) {
        var key = agentId + ":" + tenantId;
        var lock = tickLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            var loaded = store.load(agentId, tenantId);
            var previous = cache.get(key);

            if (loaded == null) {
                return new NarrativeTick.NoChange("no narrative in store");
            }

            cache.put(key, loaded);

            if (previous == null) {
                return new NarrativeTick.Updated(loaded, loaded,
                        loaded.episodes().stream().map(IndividualEpisode::id).toList(),
                        loaded.themes().stream().map(DerivedTheme::label).toList());
            }

            if (loaded.synthesisedAt().equals(previous.synthesisedAt())) {
                return new NarrativeTick.NoChange("no new synthesis");
            }

            var newEpisodeIds = loaded.episodes().stream()
                    .map(IndividualEpisode::id)
                    .filter(id -> previous.episodes().stream()
                            .noneMatch(e -> e.id().equals(id)))
                    .toList();
            var newThemeLabels = loaded.themes().stream()
                    .map(DerivedTheme::label)
                    .filter(label -> previous.themes().stream()
                            .noneMatch(t -> t.label().equalsIgnoreCase(label)))
                    .toList();

            return new NarrativeTick.Updated(previous, loaded,
                    newEpisodeIds, newThemeLabels);
        } finally {
            lock.unlock();
        }
    }

    public Optional<NarrativeState> currentNarrative(String agentId, String tenantId) {
        return Optional.ofNullable(cache.get(agentId + ":" + tenantId));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -Dtest=NarrativeOrchestratorTest`
Expected: ALL 8 tests PASS

- [ ] **Step 5: Verify with ide_diagnostics**

Run `ide_diagnostics` on `NarrativeOrchestrator.java`. Expected: no errors.

- [ ] **Step 6: Run full test suite**

Run: `mvn --batch-mode test`
Expected: ALL tests PASS

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/153/blocks add blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeOrchestrator.java blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeOrchestratorTest.java
git -C /Users/mdproctor/claude/casehub/slots/153/blocks commit -m "feat(#143): add NarrativeOrchestrator compositor — tick + currentNarrative Refs #143"
```

---

## Batch 2: Synthesis Engine

### Task 3: NarrativeSynthesiser — effectful LLM synthesis

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeSynthesiser.java`
- Create: `blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeSynthesiserTest.java`

**Interfaces:**
- Consumes: `NarrativeStore.load/store`, `ReflectionQueryStore.findSince/countSince`, `AgentProvider.invoke`, `TokenJaccardDistance.distance`, `NarrativeConfig`, `NarrativeSynthesisGate`
- Produces: `NarrativeSynthesiser.synthesiseIfNeeded(String agentId, String tenantId)` → `NarrativeSynthesisTick`

- [ ] **Step 1: Write test infrastructure and first gate test**

Create `NarrativeSynthesiserTest.java` using `ide_create_file`. Start with test infrastructure
(helper methods, TestAgentProvider, standard fixtures) and the first gate test:

```java
package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NarrativeSynthesiserTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-08-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private ReflectionQueryStore queryStore;
    private NarrativeStore narrativeStore;
    private TestAgentProvider agentProvider;
    private NarrativeSynthesiser synthesiser;

    static class TestAgentProvider implements AgentProvider {
        private final String response;
        int invocationCount = 0;
        String lastUserPrompt;
        String lastSystemPrompt;
        boolean shouldThrow = false;

        TestAgentProvider(String response) {
            this.response = response;
        }

        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            invocationCount++;
            lastUserPrompt = config.userPrompt();
            lastSystemPrompt = config.systemPrompt();
            if (shouldThrow) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }
            return Multi.createFrom().items(
                    new AgentEvent.TextDelta(response),
                    new AgentEvent.InvocationComplete(
                            100, 50, 0, 0, 0, 0.001, 500L, 400L, "test", 1, false));
        }

        @Override
        public AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }

    private static final String VALID_RESPONSE = """
            {
              "newEpisodes": [
                {
                  "description": "Helped team through production outage",
                  "emotionalValence": 0.7,
                  "thematicTags": ["crisis", "teamwork"],
                  "fromReflections": [0]
                }
              ],
              "themes": [
                {
                  "label": "crisis-helper",
                  "salience": 0.8,
                  "thematicTags": ["crisis"],
                  "axisWeights": {
                    "AFFILIATION": 0.5,
                    "COMPETENCE": 0.3
                  }
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        queryStore = mock(ReflectionQueryStore.class);
        narrativeStore = mock(NarrativeStore.class);
        agentProvider = new TestAgentProvider(VALID_RESPONSE);
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, NarrativeConfig.defaults(), CLOCK);
    }

    private ReflectionEntry reflection(String insight) {
        return new ReflectionEntry("agent-1", "tenant-1", insight,
                EARLIER, List.of("case-1"));
    }

    private NarrativeState existingState(Instant synthesisedAt,
                                          List<NarrativeFragment> fragments) {
        return new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL, fragments, synthesisedAt, 5);
    }

    private void setupReflections(int count) {
        var reflections = new ArrayList<ReflectionEntry>();
        for (int i = 0; i < count; i++) {
            reflections.add(reflection("insight-" + i));
        }
        when(queryStore.countSince("agent-1", "tenant-1", any()))
                .thenReturn(count);
        when(queryStore.findSince("agent-1", "tenant-1", any()))
                .thenReturn(reflections);
    }

    @Test
    void synthesise_insufficientReflections_skipped() {
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(2);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
        assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
                .startsWith("insufficient reflections");
        assertThat(agentProvider.invocationCount).isZero();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -Dtest=NarrativeSynthesiserTest`
Expected: FAIL — `NarrativeSynthesiser` class does not exist.

- [ ] **Step 3: Create NarrativeSynthesiser with gate logic only**

Create `NarrativeSynthesiser.java` using `ide_create_file` with the full implementation:

```java
package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.TokenJaccardDistance;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.io.StringReader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class NarrativeSynthesiser {

    private static final Logger LOG = Logger.getLogger(NarrativeSynthesiser.class.getName());

    static final String SYSTEM_PROMPT = """
            You are synthesising a first-person narrative identity from an agent's \
            reflections and experiences. Given the agent's existing narrative context \
            and new reflections, produce:

            1. New episodes — significant experiences from the reflections. Each \
            episode has a description, emotional valence [-1.0, 1.0], and \
            thematic tags.

            2. Updated themes — identity themes derived from ALL episodes (existing \
            and new). Each theme has a label, salience [0.0, 1.0], thematic tags, \
            and per-axis drive modulation weights.

            Drive axes: CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY.
            Axis weights range [-1.0, 1.0]: positive amplifies the drive, negative \
            dampens it.

            Respond with JSON only. No explanation outside the JSON.""";

    private final AgentProvider agentProvider;
    private final NarrativeStore narrativeStore;
    private final ReflectionQueryStore reflectionQueryStore;
    private final NarrativeConfig config;
    private final Clock clock;

    private final ConcurrentHashMap<String, ReentrantLock> synthesisLocks =
            new ConcurrentHashMap<>();

    @Inject
    public NarrativeSynthesiser(AgentProvider agentProvider,
                                 NarrativeStore narrativeStore,
                                 ReflectionQueryStore reflectionQueryStore,
                                 NarrativeConfig config) {
        this(agentProvider, narrativeStore, reflectionQueryStore,
                config, Clock.systemUTC());
    }

    NarrativeSynthesiser(AgentProvider agentProvider,
                          NarrativeStore narrativeStore,
                          ReflectionQueryStore reflectionQueryStore,
                          NarrativeConfig config,
                          Clock clock) {
        this.agentProvider = agentProvider;
        this.narrativeStore = narrativeStore;
        this.reflectionQueryStore = reflectionQueryStore;
        this.config = config;
        this.clock = clock;
    }

    public NarrativeSynthesisTick synthesiseIfNeeded(String agentId, String tenantId) {
        var key = agentId + ":" + tenantId;
        var lock = synthesisLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            return doSynthesise(agentId, tenantId);
        } finally {
            lock.unlock();
        }
    }

    private NarrativeSynthesisTick doSynthesise(String agentId, String tenantId) {
        var currentState = narrativeStore.load(agentId, tenantId);
        var since = currentState != null ? currentState.synthesisedAt() : Instant.EPOCH;
        var now = Instant.now(clock);

        int count = reflectionQueryStore.countSince(agentId, tenantId, since);
        var gate = config.synthesisGate();
        boolean quietPeriodTriggered = Duration.between(since, now)
                .compareTo(gate.quietPeriodBypass()) >= 0;

        if (count == 0) {
            return new NarrativeSynthesisTick.Skipped("no new reflections");
        }

        if (!quietPeriodTriggered && count < gate.minNewReflections()) {
            return new NarrativeSynthesisTick.Skipped("insufficient reflections: " + count);
        }

        var reflections = reflectionQueryStore.findSince(agentId, tenantId, since);
        if (reflections.size() > config.maxReflectionsPerSynthesis()) {
            reflections = reflections.subList(0, config.maxReflectionsPerSynthesis());
        }

        if (!quietPeriodTriggered) {
            var reflectionText = reflections.stream()
                    .map(ReflectionEntry::insight)
                    .collect(Collectors.joining("\n"));
            var currentNarrativeText = currentState != null
                    ? currentState.episodes().stream()
                    .map(IndividualEpisode::description)
                    .collect(Collectors.joining("\n"))
                    : "";
            double novelty = TokenJaccardDistance.distance(reflectionText, currentNarrativeText);
            if (novelty < gate.noveltyThreshold()) {
                return new NarrativeSynthesisTick.Skipped("low novelty");
            }
        }

        var userPrompt = assembleUserPrompt(currentState, reflections);

        String responseText;
        try {
            responseText = agentProvider.invoke(AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt))
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().asList()
                    .await().indefinitely()
                    .stream().collect(Collectors.joining());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM invocation failed for agent " + agentId, e);
            return new NarrativeSynthesisTick.Skipped("llm failure");
        }

        if (responseText == null || responseText.isBlank()) {
            return new NarrativeSynthesisTick.Skipped("empty response");
        }

        SynthesisResult result;
        try {
            result = parseResponse(responseText);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse synthesis response: " + e.getMessage());
            return new NarrativeSynthesisTick.Skipped("parse failure");
        }

        var newEpisodes = buildEpisodes(result.newEpisodes(), reflections, now);
        var allEpisodes = new ArrayList<IndividualEpisode>();
        if (currentState != null) {
            allEpisodes.addAll(currentState.episodes());
        }
        allEpisodes.addAll(newEpisodes);

        var groupEpisodes = currentState != null
                ? currentState.groupEpisodes()
                : List.<GroupEpisode>of();

        var themes = buildThemes(result.themes(), allEpisodes, now);

        pruneEpisodes(allEpisodes);
        pruneThemes(themes);

        var allFragments = new ArrayList<NarrativeFragment>();
        allFragments.addAll(allEpisodes);
        allFragments.addAll(groupEpisodes);
        allFragments.addAll(themes);

        var state = new NarrativeState(agentId, tenantId,
                NarrativeScope.INDIVIDUAL, allFragments, now, reflections.size());
        narrativeStore.store(state);

        return new NarrativeSynthesisTick.Synthesised(state, reflections.size());
    }

    String assembleUserPrompt(NarrativeState currentState,
                               List<ReflectionEntry> reflections) {
        var sb = new StringBuilder();

        sb.append("## Existing Episodes\n");
        if (currentState != null && !currentState.episodes().isEmpty()) {
            int i = 1;
            for (var ep : currentState.episodes()) {
                sb.append(i++).append(". ").append(ep.description())
                        .append(" (valence: ")
                        .append(String.format("%.1f", ep.emotionalValence()))
                        .append(")\n");
            }
        } else {
            sb.append("None.\n");
        }

        sb.append("\n## Existing Theme Labels\n");
        if (currentState != null && !currentState.themes().isEmpty()) {
            for (var theme : currentState.themes()) {
                sb.append("- ").append(theme.label()).append("\n");
            }
        } else {
            sb.append("None.\n");
        }

        sb.append("\n## New Reflections\n");
        for (int i = 0; i < reflections.size(); i++) {
            sb.append(i).append(". ").append(reflections.get(i).insight()).append("\n");
        }

        sb.append("\n## Response Format\n");
        sb.append("""
                {
                  "newEpisodes": [
                    {
                      "description": "...",
                      "emotionalValence": 0.0,
                      "thematicTags": ["..."],
                      "fromReflections": [0, 2]
                    }
                  ],
                  "themes": [
                    {
                      "label": "...",
                      "salience": 0.0,
                      "thematicTags": ["..."],
                      "axisWeights": {
                        "CURIOSITY": 0.0,
                        "COMPETENCE": 0.0,
                        "AFFILIATION": 0.0,
                        "AUTONOMY": 0.0
                      }
                    }
                  ]
                }
                """);

        return sb.toString();
    }

    record SynthesisResult(List<EpisodeSpec> newEpisodes, List<ThemeSpec> themes) {}
    record EpisodeSpec(String description, double emotionalValence,
                       List<String> thematicTags, List<Integer> fromReflections) {}
    record ThemeSpec(String label, double salience,
                     List<String> thematicTags, Map<String, Double> axisWeights) {}

    SynthesisResult parseResponse(String responseText) {
        var cleaned = responseText.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "")
                    .replaceAll("\\s*```$", "");
        }

        JsonObject json;
        try (var reader = Json.createReader(new StringReader(cleaned))) {
            json = reader.readObject();
        }

        var episodes = new ArrayList<EpisodeSpec>();
        if (json.containsKey("newEpisodes")) {
            var arr = json.getJsonArray("newEpisodes");
            for (int i = 0; i < arr.size(); i++) {
                try {
                    var obj = arr.getJsonObject(i);
                    var desc = obj.getString("description");
                    var valence = obj.getJsonNumber("emotionalValence").doubleValue();
                    var tags = new ArrayList<String>();
                    if (obj.containsKey("thematicTags")) {
                        var tagsArr = obj.getJsonArray("thematicTags");
                        for (int j = 0; j < tagsArr.size(); j++) {
                            tags.add(tagsArr.getString(j));
                        }
                    }
                    var refs = new ArrayList<Integer>();
                    if (obj.containsKey("fromReflections")) {
                        var refsArr = obj.getJsonArray("fromReflections");
                        for (int j = 0; j < refsArr.size(); j++) {
                            refs.add(refsArr.getInt(j));
                        }
                    }
                    episodes.add(new EpisodeSpec(desc, valence, tags, refs));
                } catch (Exception e) {
                    LOG.warning("Skipping invalid episode at index " + i
                            + ": " + e.getMessage());
                }
            }
        }

        var themes = new ArrayList<ThemeSpec>();
        if (json.containsKey("themes")) {
            var arr = json.getJsonArray("themes");
            for (int i = 0; i < arr.size(); i++) {
                try {
                    var obj = arr.getJsonObject(i);
                    var label = obj.getString("label");
                    var salience = obj.getJsonNumber("salience").doubleValue();
                    var tags = new ArrayList<String>();
                    if (obj.containsKey("thematicTags")) {
                        var tagsArr = obj.getJsonArray("thematicTags");
                        for (int j = 0; j < tagsArr.size(); j++) {
                            tags.add(tagsArr.getString(j));
                        }
                    }
                    var weights = new HashMap<String, Double>();
                    if (obj.containsKey("axisWeights")) {
                        var weightsObj = obj.getJsonObject("axisWeights");
                        for (var key : weightsObj.keySet()) {
                            weights.put(key, weightsObj.getJsonNumber(key).doubleValue());
                        }
                    }
                    themes.add(new ThemeSpec(label, salience, tags, weights));
                } catch (Exception e) {
                    LOG.warning("Skipping invalid theme at index " + i
                            + ": " + e.getMessage());
                }
            }
        }

        return new SynthesisResult(episodes, themes);
    }

    private List<IndividualEpisode> buildEpisodes(List<EpisodeSpec> specs,
                                                   List<ReflectionEntry> reflections,
                                                   Instant now) {
        var episodes = new ArrayList<IndividualEpisode>();
        for (var spec : specs) {
            try {
                var valence = Math.clamp(spec.emotionalValence(), -1.0, 1.0);
                var sourceIds = new ArrayList<String>();
                for (var idx : spec.fromReflections()) {
                    if (idx >= 0 && idx < reflections.size()) {
                        sourceIds.addAll(reflections.get(idx).sourceCaseIds());
                    }
                }
                episodes.add(new IndividualEpisode(
                        UUID.randomUUID().toString(), now, null,
                        spec.thematicTags(), spec.description(),
                        valence, sourceIds));
            } catch (Exception e) {
                LOG.warning("Skipping invalid episode: " + e.getMessage());
            }
        }
        return episodes;
    }

    private List<DerivedTheme> buildThemes(List<ThemeSpec> specs,
                                            List<IndividualEpisode> allEpisodes,
                                            Instant now) {
        var themes = new ArrayList<DerivedTheme>();
        for (var spec : specs) {
            try {
                var salience = Math.clamp(spec.salience(), 0.0, 1.0);
                var axisWeights = new EnumMap<DriveAxis, Double>(DriveAxis.class);
                for (var entry : spec.axisWeights().entrySet()) {
                    try {
                        var axis = DriveAxis.valueOf(entry.getKey());
                        axisWeights.put(axis, Math.clamp(entry.getValue(), -1.0, 1.0));
                    } catch (IllegalArgumentException ignored) {
                        LOG.warning("Unknown drive axis: " + entry.getKey());
                    }
                }
                var themeTags = Set.copyOf(spec.thematicTags());
                var supportingIds = new ArrayList<String>();
                if (!themeTags.isEmpty()) {
                    for (var episode : allEpisodes) {
                        if (!episode.thematicTags().isEmpty()) {
                            for (var tag : episode.thematicTags()) {
                                if (themeTags.contains(tag)) {
                                    supportingIds.add(episode.id());
                                    break;
                                }
                            }
                        }
                    }
                }
                themes.add(new DerivedTheme(
                        UUID.randomUUID().toString(), now, null,
                        spec.thematicTags(), spec.label(),
                        salience, axisWeights, supportingIds));
            } catch (Exception e) {
                LOG.warning("Skipping invalid theme: " + e.getMessage());
            }
        }
        return themes;
    }

    private void pruneEpisodes(List<IndividualEpisode> episodes) {
        if (episodes.size() > config.maxEpisodes()) {
            episodes.sort(Comparator.comparing(IndividualEpisode::from));
            while (episodes.size() > config.maxEpisodes()) {
                episodes.removeFirst();
            }
        }
    }

    private void pruneThemes(List<DerivedTheme> themes) {
        themes.removeIf(t -> t.salience() < config.themeSalienceFloor());
        if (themes.size() > config.maxThemes()) {
            themes.sort(Comparator.comparingDouble(DerivedTheme::salience));
            while (themes.size() > config.maxThemes()) {
                themes.removeFirst();
            }
        }
    }
}
```

- [ ] **Step 4: Run first test to verify it passes**

Run: `mvn --batch-mode test -Dtest=NarrativeSynthesiserTest`
Expected: PASS — insufficient reflections test passes.

- [ ] **Step 5: Add remaining tests**

Add all remaining tests to `NarrativeSynthesiserTest.java` using `ide_insert_member`.
Each test targets a specific behavior from the spec:

```java
@Test
void synthesise_noNewReflections_skipped() {
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
    when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
            .thenReturn(0);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
    assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
            .isEqualTo("no new reflections");
}

@Test
void synthesise_lowNovelty_skipped() {
    var existing = existingState(EARLIER, List.of(
            new IndividualEpisode("e1", EARLIER, null, List.of(),
                    "insight-0 insight-1", 0.5, List.of())));
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
    when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
            .thenReturn(5);
    when(queryStore.findSince(eq("agent-1"), eq("tenant-1"), any()))
            .thenReturn(List.of(
                    reflection("insight-0"),
                    reflection("insight-1"),
                    reflection("insight-0"),
                    reflection("insight-1"),
                    reflection("insight-0")));

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
    assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
            .isEqualTo("low novelty");
    assertThat(agentProvider.invocationCount).isZero();
}

@Test
void synthesise_quietPeriodBypass() {
    var oldSynthesis = FIXED_NOW.minus(Duration.ofMinutes(150));
    var existing = existingState(oldSynthesis, List.of());
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
    when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
            .thenReturn(1);
    when(queryStore.findSince(eq("agent-1"), eq("tenant-1"), any()))
            .thenReturn(List.of(reflection("quiet period insight")));

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Synthesised.class);
    assertThat(agentProvider.invocationCount).isEqualTo(1);
}

@Test
void synthesise_quietPeriodBypass_requiresAtLeastOneReflection() {
    var oldSynthesis = FIXED_NOW.minus(Duration.ofMinutes(150));
    var existing = existingState(oldSynthesis, List.of());
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
    when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
            .thenReturn(0);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
}

@Test
void synthesise_firstSynthesis_noExistingState() {
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
    setupReflections(5);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Synthesised.class);
    var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
    assertThat(synthesised.state().episodes()).hasSize(1);
    assertThat(synthesised.state().themes()).hasSize(1);
    assertThat(synthesised.newReflectionsConsumed()).isEqualTo(5);
    verify(narrativeStore).store(any());
}

@Test
void synthesise_incrementalMerge_preservesExistingEpisodes() {
    var existingEpisode = new IndividualEpisode("existing-1", EARLIER, null,
            List.of("old"), "old experience", 0.3, List.of());
    var existing = existingState(EARLIER, List.of(existingEpisode));
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
    setupReflections(5);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
    assertThat(synthesised.state().episodes()).hasSize(2);
    assertThat(synthesised.state().episodes().stream()
            .map(IndividualEpisode::id).toList())
            .contains("existing-1");
}

@Test
void synthesise_themesFullyReDerived() {
    var oldTheme = new DerivedTheme("old-t", EARLIER, null, List.of(),
            "old-theme", 0.5, Map.of(), List.of());
    var existing = existingState(EARLIER, List.of(oldTheme));
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
    setupReflections(5);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
    assertThat(synthesised.state().themes()).hasSize(1);
    assertThat(synthesised.state().themes().getFirst().label())
            .isEqualTo("crisis-helper");
}

@Test
void synthesise_episodePruning_dropsOldest() {
    var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
            2, 10, 0.1, 20);
    synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
            queryStore, config, CLOCK);

    var e1 = new IndividualEpisode("old", EARLIER.minusSeconds(100), null,
            List.of(), "oldest", 0.1, List.of());
    var e2 = new IndividualEpisode("mid", EARLIER, null,
            List.of(), "middle", 0.2, List.of());
    var existing = existingState(EARLIER, List.of(e1, e2));
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
    setupReflections(5);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
    assertThat(synthesised.state().episodes()).hasSize(2);
    assertThat(synthesised.state().episodes().stream()
            .map(IndividualEpisode::id).toList())
            .doesNotContain("old");
}

@Test
void synthesise_themePruning_dropsLowestSalience() {
    var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
            50, 1, 0.1, 20);
    var twoThemeResponse = """
            {
              "newEpisodes": [],
              "themes": [
                {"label": "low", "salience": 0.2, "thematicTags": [], "axisWeights": {}},
                {"label": "high", "salience": 0.9, "thematicTags": [], "axisWeights": {}}
              ]
            }
            """;
    agentProvider = new TestAgentProvider(twoThemeResponse);
    synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
            queryStore, config, CLOCK);
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
    setupReflections(5);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
    assertThat(synthesised.state().themes()).hasSize(1);
    assertThat(synthesised.state().themes().getFirst().label()).isEqualTo("high");
}

@Test
void synthesise_themeSalienceFloor() {
    var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
            50, 10, 0.5, 20);
    var lowSalienceResponse = """
            {
              "newEpisodes": [],
              "themes": [
                {"label": "weak", "salience": 0.3, "thematicTags": [], "axisWeights": {}},
                {"label": "strong", "salience": 0.8, "thematicTags": [], "axisWeights": {}}
              ]
            }
            """;
    agentProvider = new TestAgentProvider(lowSalienceResponse);
    synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
            queryStore, config, CLOCK);
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
    setupReflections(5);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
    assertThat(synthesised.state().themes()).hasSize(1);
    assertThat(synthesised.state().themes().getFirst().label()).isEqualTo("strong");
}

@Test
void synthesise_llmFailure_skipped() {
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
    setupReflections(5);
    agentProvider.shouldThrow = true;

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
    assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
            .isEqualTo("llm failure");
}

@Test
void synthesise_parseFailure_skipped() {
    agentProvider = new TestAgentProvider("not json at all");
    synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
            queryStore, NarrativeConfig.defaults(), CLOCK);
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
    setupReflections(5);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
    assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
            .isEqualTo("parse failure");
}

@Test
void synthesise_partiallyInvalidResponse_writesValidItems() {
    var mixedResponse = """
            {
              "newEpisodes": [
                {"description": "valid episode", "emotionalValence": 0.5, "thematicTags": [], "fromReflections": [0]},
                {"emotionalValence": 999}
              ],
              "themes": [
                {"label": "valid", "salience": 0.7, "thematicTags": [], "axisWeights": {}}
              ]
            }
            """;
    agentProvider = new TestAgentProvider(mixedResponse);
    synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
            queryStore, NarrativeConfig.defaults(), CLOCK);
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
    setupReflections(5);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
    assertThat(synthesised.state().episodes()).hasSize(1);
    assertThat(synthesised.state().themes()).hasSize(1);
}

@Test
void synthesise_reflectionIndexMapping() {
    var r0 = new ReflectionEntry("agent-1", "tenant-1", "insight-0",
            EARLIER, List.of("case-a", "case-b"));
    var r1 = new ReflectionEntry("agent-1", "tenant-1", "insight-1",
            EARLIER, List.of("case-c"));
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
    when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
            .thenReturn(5);
    when(queryStore.findSince(eq("agent-1"), eq("tenant-1"), any()))
            .thenReturn(List.of(r0, r1, reflection("x"), reflection("y"), reflection("z")));

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
    var episode = synthesised.state().episodes().getFirst();
    assertThat(episode.sourceReflectionIds())
            .containsExactly("case-a", "case-b");
}

@Test
void synthesise_themeLabelsIncludedInPrompt() {
    var theme = new DerivedTheme("t1", EARLIER, null, List.of(),
            "existing-theme", 0.6, Map.of(), List.of());
    var existing = existingState(EARLIER, List.of(theme));
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
    setupReflections(5);

    synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    assertThat(agentProvider.lastUserPrompt).contains("existing-theme");
}

@Test
void synthesise_maxReflectionsCapped() {
    var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
            50, 10, 0.1, 3);
    synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
            queryStore, config, CLOCK);
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
    var reflections = new ArrayList<ReflectionEntry>();
    for (int i = 0; i < 10; i++) {
        reflections.add(reflection("insight-" + i));
    }
    when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
            .thenReturn(10);
    when(queryStore.findSince(eq("agent-1"), eq("tenant-1"), any()))
            .thenReturn(reflections);

    var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
    assertThat(synthesised.newReflectionsConsumed()).isEqualTo(3);
}

@Test
void synthesise_writesCorrectNarrativeState() {
    when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
    setupReflections(5);

    synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

    var captor = org.mockito.ArgumentCaptor.forClass(NarrativeState.class);
    verify(narrativeStore).store(captor.capture());
    var written = captor.getValue();
    assertThat(written.scopeId()).isEqualTo("agent-1");
    assertThat(written.tenantId()).isEqualTo("tenant-1");
    assertThat(written.scope()).isEqualTo(NarrativeScope.INDIVIDUAL);
    assertThat(written.synthesisedAt()).isEqualTo(FIXED_NOW);
    assertThat(written.reflectionCountAtSynthesis()).isEqualTo(5);
}

@Test
void synthesise_perAgentLocking() throws InterruptedException {
    when(narrativeStore.load(anyString(), anyString())).thenReturn(null);
    setupReflections(5);

    var errors = new java.util.concurrent.atomic.AtomicInteger(0);
    var latch = new java.util.concurrent.CountDownLatch(4);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
        for (int i = 0; i < 4; i++) {
            executor.submit(() -> {
                try {
                    synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
    }
    assertThat(errors.get()).isZero();
}
```

- [ ] **Step 6: Run all tests**

Run: `mvn --batch-mode test -Dtest=NarrativeSynthesiserTest`
Expected: ALL 18 tests PASS

- [ ] **Step 7: Verify with ide_diagnostics**

Run `ide_diagnostics` on `NarrativeSynthesiser.java`. Expected: no errors.

- [ ] **Step 8: Run full test suite**

Run: `mvn --batch-mode test`
Expected: ALL tests PASS

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/153/blocks add blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/NarrativeSynthesiser.java blocks/src/test/java/io/casehub/blocks/agentic/social/narrative/NarrativeSynthesiserTest.java
git -C /Users/mdproctor/claude/casehub/slots/153/blocks commit -m "feat(#143): add NarrativeSynthesiser — LLM narrative synthesis with gate, merge, prune Refs #143"
```

---

## References

- [2026-08-24-narrative-orchestrator-synthesiser-design.md] — implementation spec this plan implements
- [2026-08-24-narrative-identity-social-emergence-design.md] — parent spec (#142)
- [DriveOrchestrator.java] — compositor pattern reference (per-agent locking, CDI constructor)
- [InnerLifeOrchestrator.java] — LLM invocation + JSON parsing reference
- [DriveOrchestratorTest.java] — test pattern reference (Mockito + fixed Clock)
- [NarrativeModulationTest.java] — existing test patterns for narrative package
- [GE-20260801-bcff35] — TestAgentProvider pattern
- [GE-20260801-0aee7e] — AgentEvent text extraction
- [GE-20260701-5c818b] — AgentProvider not a functional interface
- [GitHub #143] — focal issue
- [GitHub #142] — parent epic
