# Cognitive Observation Renderers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #260 — CognitiveObservationSections — beliefs, principles, trust, norms renderers

**Goal:** Add four factory methods to `CognitiveObservationSections` for rendering cognitive profile content into `ObservationSection` format.

**Architecture:** Four new static methods following the existing pattern (static factory method → `ObservationSection.ItemList`). Three new types (`TrustLevel` enum, `TrustSummary` record, `Principle` record) in the affordance package. Uses existing `Belief<T>` and `SocialNorm` types.

**Tech Stack:** Java 17, JUnit 5, AssertJ

## Global Constraints

- All new types in `io.casehub.blocks.summarisation.observation.affordance`
- Follow existing CognitiveObservationSections patterns exactly (static methods, `ObservationSection.items()` factory, empty-message handling)
- No new dependencies — all referenced types already on the classpath
- `@Nullable` annotations from `org.jspecify.annotations`

---

## Batch 1: Cognitive observation renderers

### Task 1: beliefsSection renderer

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSectionsTest.java`

**Interfaces:**
- Consumes: `io.casehub.blocks.agentic.belief.Belief<T>` (key, value, entrenchment)
- Produces: `static ObservationSection beliefsSection(List<Belief<?>>, Set<String> revisedKeys)`

- [ ] **Step 1: Write failing tests for beliefsSection**

Add to `CognitiveObservationSectionsTest.java`:

```java
@Test
void beliefsSection_renders_sorted_by_entrenchment_desc_then_key() {
    var beliefs = List.of(
            Belief.of("honesty", "Always tell the truth", 3),
            Belief.of("caution", "Trust must be earned", 5),
            Belief.of("ambition", "Strive for excellence", 3));
    var section = CognitiveObservationSections.beliefsSection(beliefs, Set.of());
    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).hasSize(3);
    assertThat(items.get(0)).isEqualTo("caution: Trust must be earned");
    assertThat(items.get(1)).isEqualTo("ambition: Strive for excellence");
    assertThat(items.get(2)).isEqualTo("honesty: Always tell the truth");
}

@Test
void beliefsSection_marks_revised_beliefs() {
    var beliefs = List.of(
            Belief.of("honesty", "Always tell the truth", 3),
            Belief.of("caution", "Trust must be earned", 5));
    var section = CognitiveObservationSections.beliefsSection(beliefs, Set.of("honesty"));
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items.get(0)).isEqualTo("caution: Trust must be earned");
    assertThat(items.get(1)).isEqualTo("[REVISED] honesty: Always tell the truth");
}

@Test
void beliefsSection_empty_shows_no_established_beliefs() {
    var section = CognitiveObservationSections.beliefsSection(List.of(), Set.of());
    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var il = (ObservationSection.ItemList) section;
    assertThat(il.items()).isEmpty();
    assertThat(il.emptyMessage()).isEqualTo("No established beliefs.");
}
```

Imports needed:

```java
import io.casehub.blocks.agentic.belief.Belief;
import java.util.Set;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveObservationSectionsTest#beliefsSection* -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — `beliefsSection` method does not exist

- [ ] **Step 3: Implement beliefsSection**

Add to `CognitiveObservationSections.java`:

```java
import io.casehub.blocks.agentic.belief.Belief;
import java.util.Set;
```

```java
public static ObservationSection beliefsSection(List<Belief<?>> beliefs, Set<String> revisedKeys) {
    var items = new ArrayList<String>();
    beliefs.stream()
           .sorted(Comparator.comparingInt(Belief::entrenchment).reversed()
                              .thenComparing(Belief::key))
           .forEach(b -> {
               String prefix = revisedKeys.contains(b.key()) ? "[REVISED] " : "";
               items.add(prefix + b.key() + ": " + b.value());
           });
    if (items.isEmpty()) {
        return ObservationSection.items("Your Beliefs", "No established beliefs.", List.of());
    }
    return ObservationSection.items("Your Beliefs", null, items);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveObservationSectionsTest#beliefsSection*`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java
git add blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSectionsTest.java
git commit -m "feat(#260): add beliefsSection renderer to CognitiveObservationSections"
```

---

### Task 2: principlesSection renderer

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/Principle.java`
- Modify: `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSectionsTest.java`

**Interfaces:**
- Consumes: nothing from prior tasks
- Produces: `record Principle(String text, @Nullable String category)`, `static ObservationSection principlesSection(List<Principle>)`

- [ ] **Step 1: Create Principle record**

Create `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/Principle.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record Principle(String text, @Nullable String category) {
    public Principle {
        Objects.requireNonNull(text);
    }
}
```

- [ ] **Step 2: Write failing tests for principlesSection**

Add to `CognitiveObservationSectionsTest.java`:

```java
@Test
void principlesSection_renders_with_category() {
    var principles = List.of(
            new Principle("Always tell the truth", "Honesty"),
            new Principle("Stand by your allies", "Loyalty"));
    var section = CognitiveObservationSections.principlesSection(principles);
    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).containsExactly(
            "[Honesty] Always tell the truth",
            "[Loyalty] Stand by your allies");
}

@Test
void principlesSection_renders_without_category() {
    var principles = List.of(
            new Principle("Do no harm", null),
            new Principle("Seek knowledge", "Curiosity"));
    var section = CognitiveObservationSections.principlesSection(principles);
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).containsExactly(
            "Do no harm",
            "[Curiosity] Seek knowledge");
}

@Test
void principlesSection_empty_shows_no_guiding_principles() {
    var section = CognitiveObservationSections.principlesSection(List.of());
    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var il = (ObservationSection.ItemList) section;
    assertThat(il.items()).isEmpty();
    assertThat(il.emptyMessage()).isEqualTo("No guiding principles.");
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveObservationSectionsTest#principlesSection*`
Expected: compilation failure — `principlesSection` method does not exist

- [ ] **Step 4: Implement principlesSection**

Add to `CognitiveObservationSections.java`:

```java
public static ObservationSection principlesSection(List<Principle> principles) {
    var items = principles.stream()
                          .map(p -> p.category() != null
                                  ? "[" + p.category() + "] " + p.text()
                                  : p.text())
                          .toList();
    if (items.isEmpty()) {
        return ObservationSection.items("Your Principles", "No guiding principles.", List.of());
    }
    return ObservationSection.items("Your Principles", null, items);
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveObservationSectionsTest#principlesSection*`
Expected: 3 tests PASS

- [ ] **Step 6: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/Principle.java
git add blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java
git add blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSectionsTest.java
git commit -m "feat(#260): add Principle record and principlesSection renderer"
```

---

### Task 3: trustSection renderer

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/TrustLevel.java`
- Create: `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/TrustSummary.java`
- Modify: `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSectionsTest.java`

**Interfaces:**
- Consumes: nothing from prior tasks
- Produces: `enum TrustLevel { HIGH, MODERATE, LOW, UNKNOWN }`, `record TrustSummary(String subjectName, TrustLevel level, @Nullable String reason)`, `static ObservationSection trustSection(List<TrustSummary>)`

- [ ] **Step 1: Create TrustLevel enum**

Create `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/TrustLevel.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

public enum TrustLevel {
    HIGH, MODERATE, LOW, UNKNOWN
}
```

- [ ] **Step 2: Create TrustSummary record**

Create `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/TrustSummary.java`:

```java
package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record TrustSummary(String subjectName, TrustLevel level, @Nullable String reason) {
    public TrustSummary {
        Objects.requireNonNull(subjectName);
        Objects.requireNonNull(level);
    }
}
```

- [ ] **Step 3: Write failing tests for trustSection**

Add to `CognitiveObservationSectionsTest.java`:

```java
@Test
void trustSection_renders_sorted_by_level_then_name() {
    var summaries = List.of(
            new TrustSummary("Zara", TrustLevel.HIGH, null),
            new TrustSummary("Penelope", TrustLevel.LOW, "betrayed trust"),
            new TrustSummary("Alice", TrustLevel.HIGH, "reliable ally"));
    var section = CognitiveObservationSections.trustSection(summaries);
    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).hasSize(3);
    assertThat(items.get(0)).isEqualTo("Alice: HIGH — reliable ally");
    assertThat(items.get(1)).isEqualTo("Zara: HIGH");
    assertThat(items.get(2)).isEqualTo("Penelope: LOW — betrayed trust");
}

@Test
void trustSection_renders_with_and_without_reason() {
    var summaries = List.of(
            new TrustSummary("Bob", TrustLevel.MODERATE, "occasionally helpful"),
            new TrustSummary("Carol", TrustLevel.UNKNOWN, null));
    var section = CognitiveObservationSections.trustSection(summaries);
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).containsExactly(
            "Bob: MODERATE — occasionally helpful",
            "Carol: UNKNOWN");
}

@Test
void trustSection_empty_shows_no_trust_assessments() {
    var section = CognitiveObservationSections.trustSection(List.of());
    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var il = (ObservationSection.ItemList) section;
    assertThat(il.items()).isEmpty();
    assertThat(il.emptyMessage()).isEqualTo("No trust assessments.");
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveObservationSectionsTest#trustSection*`
Expected: compilation failure — `trustSection` method does not exist

- [ ] **Step 5: Implement trustSection**

Add to `CognitiveObservationSections.java`:

```java
public static ObservationSection trustSection(List<TrustSummary> summaries) {
    var items = new ArrayList<String>();
    summaries.stream()
             .sorted(Comparator.comparingInt((TrustSummary s) -> s.level().ordinal())
                                .thenComparing(TrustSummary::subjectName))
             .forEach(s -> {
                 String entry = s.subjectName() + ": " + s.level().name();
                 if (s.reason() != null) {
                     entry += " — " + s.reason();
                 }
                 items.add(entry);
             });
    if (items.isEmpty()) {
        return ObservationSection.items("Your Trust", "No trust assessments.", List.of());
    }
    return ObservationSection.items("Your Trust", null, items);
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveObservationSectionsTest#trustSection*`
Expected: 3 tests PASS

- [ ] **Step 7: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/TrustLevel.java
git add blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/TrustSummary.java
git add blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java
git add blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSectionsTest.java
git commit -m "feat(#260): add TrustLevel, TrustSummary, and trustSection renderer"
```

---

### Task 4: normsSection renderer

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSectionsTest.java`

**Interfaces:**
- Consumes: `io.casehub.blocks.agentic.social.emergence.SocialNorm` (description, strength), `io.casehub.blocks.agentic.social.emergence.NormStrength` (EMERGING, ESTABLISHED, DECLINING)
- Produces: `static ObservationSection normsSection(List<SocialNorm>)`

- [ ] **Step 1: Write failing tests for normsSection**

Add to `CognitiveObservationSectionsTest.java`:

```java
@Test
void normsSection_renders_sorted_by_strength_then_description() {
    var norms = List.of(
            new SocialNorm("n1", "Take turns speaking", "turn-taking",
                    0.9, 50, Set.of("a", "b"), Instant.now(), Instant.now(),
                    NormStrength.ESTABLISHED),
            new SocialNorm("n2", "Be polite in disagreements", "politeness",
                    0.6, 20, Set.of("a", "c"), Instant.now(), Instant.now(),
                    NormStrength.EMERGING),
            new SocialNorm("n3", "Avoid interruptions", "interruption",
                    0.4, 30, Set.of("a"), Instant.now(), Instant.now(),
                    NormStrength.DECLINING));
    var section = CognitiveObservationSections.normsSection(norms);
    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var items = ((ObservationSection.ItemList) section).items();
    assertThat(items).hasSize(3);
    assertThat(items.get(0)).isEqualTo("[ESTABLISHED] Take turns speaking");
    assertThat(items.get(1)).isEqualTo("[EMERGING] Be polite in disagreements");
    assertThat(items.get(2)).isEqualTo("[DECLINING] Avoid interruptions");
}

@Test
void normsSection_empty_shows_no_active_norms() {
    var section = CognitiveObservationSections.normsSection(List.of());
    assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
    var il = (ObservationSection.ItemList) section;
    assertThat(il.items()).isEmpty();
    assertThat(il.emptyMessage()).isEqualTo("No active norms.");
}
```

Imports needed:

```java
import io.casehub.blocks.agentic.social.emergence.SocialNorm;
import io.casehub.blocks.agentic.social.emergence.NormStrength;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveObservationSectionsTest#normsSection*`
Expected: compilation failure — `normsSection` method does not exist

- [ ] **Step 3: Implement normsSection**

Add imports to `CognitiveObservationSections.java`:

```java
import io.casehub.blocks.agentic.social.emergence.NormStrength;
import io.casehub.blocks.agentic.social.emergence.SocialNorm;
```

Add method:

```java
private static int normStrengthOrder(NormStrength strength) {
    return switch (strength) {
        case ESTABLISHED -> 0;
        case EMERGING -> 1;
        case DECLINING -> 2;
    };
}

public static ObservationSection normsSection(List<SocialNorm> norms) {
    var items = norms.stream()
                     .sorted(Comparator.comparingInt((SocialNorm n) -> normStrengthOrder(n.strength()))
                                       .thenComparing(SocialNorm::description))
                     .map(n -> "[" + n.strength().name() + "] " + n.description())
                     .toList();
    if (items.isEmpty()) {
        return ObservationSection.items("Your Active Norms", "No active norms.", List.of());
    }
    return ObservationSection.items("Your Active Norms", null, items);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveObservationSectionsTest#normsSection*`
Expected: 2 tests PASS

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveObservationSectionsTest`
Expected: all tests PASS (existing + new)

- [ ] **Step 6: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSections.java
git add blocks/src/test/java/io/casehub/blocks/summarisation/observation/affordance/CognitiveObservationSectionsTest.java
git commit -m "feat(#260): add normsSection renderer to CognitiveObservationSections"
```

## References

- [2026-09-14-cognitive-obs-renderers-design.md] — design spec this plan implements
- [CognitiveObservationSections.java] — existing factory method pattern
- [CognitiveObservationSectionsTest.java] — existing test pattern
- [Belief.java] — AGM belief record (key, value, entrenchment)
- [SocialNorm.java] — social emergence record (description, strength)
- [NormStrength.java] — enum (EMERGING, ESTABLISHED, DECLINING)
- [ObservationSection.java] — sealed interface (ItemList, TextBlock, EntityGroup)
- [GitHub #260] — focal issue
