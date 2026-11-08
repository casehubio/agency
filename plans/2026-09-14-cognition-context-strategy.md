# CognitionContextStrategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #282 — CognitionContextStrategy SPI — situational context filtering for CognitionCore
**Issue group:** #282

**Goal:** Add three SPIs (SubjectResolver, InteractionMapper, NormFilter) and CognitiveImpact record to let CognitionCore consumers provide situational context filtering.

**Architecture:** Three independent `@FunctionalInterface` SPIs at two integration layers. SubjectResolver and CognitiveImpact integrate as CognitionCore method parameters. InteractionMapper is an adapter-layer SPI consumed by SocialAvatarCognition. NormFilter integrates at prompt assembly. All have `@DefaultBean` passthrough producers.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Mockito (for orchestrator mocking in CognitionCore tests)

## Global Constraints

- Package: `io.casehub.blocks.agentic.social` for SubjectResolver, InteractionMapper, CognitiveImpact; `io.casehub.blocks.agentic.social.emergence` for NormFilter
- All SPIs are `@FunctionalInterface`
- `@DefaultBean` producers in existing `SocialCognitionDefaultBeans`
- AvatarCognition published interface (speech-api) does NOT change
- CognitionCore is not a CDI bean — manually constructed

---

## Batch 1: SPIs and CognitiveImpact record

### Task 1: SubjectResolver SPI + CognitiveImpact record

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/SubjectResolver.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/CognitiveImpact.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/InteractionMapper.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/NormFilter.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitiveImpactTest.java`

**Interfaces:**
- Produces: `SubjectResolver` (`Set<String> relevantSubjects(String agentId, String tenantId)`), `InteractionMapper` (`CognitiveImpact mapInteraction(String agentId, String targetId, String interactionType)`), `NormFilter` (`List<SocialNorm> filter(List<SocialNorm> norms, String agentId, String tenantId)`), `CognitiveImpact` record (4 fields + `fromText()` factory)

- [ ] **Step 1: Write CognitiveImpact tests**

```java
package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.relationship.QualitySignal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveImpactTest {

    @Test
    void fromTextCreatesUserModelSignalOnly() {
        var impact = CognitiveImpact.fromText("greeted warmly");
        assertThat(impact.userModelSignal()).isNotNull();
        assertThat(impact.userModelSignal().description()).isEqualTo("greeted warmly");
        assertThat(impact.userModelSignal().quality()).isEqualTo(QualitySignal.NEUTRAL);
        assertThat(impact.moodSignal()).isNull();
        assertThat(impact.suppressBdiExtraction()).isFalse();
        assertThat(impact.strategySignal()).isNull();
    }

    @Test
    void allFieldsNullIsValid() {
        var impact = new CognitiveImpact(null, null, false, null);
        assertThat(impact.userModelSignal()).isNull();
        assertThat(impact.moodSignal()).isNull();
        assertThat(impact.suppressBdiExtraction()).isFalse();
        assertThat(impact.strategySignal()).isNull();
    }

    @Test
    void allFieldsPopulated() {
        var interaction = new InteractionSignal.CustomSignal("stole item", QualitySignal.NEGATIVE);
        var mood = new MoodSignal.DirectShift(-0.3, 0.2, -0.1, "theft");
        var impact = new CognitiveImpact(interaction, mood, true, null);
        assertThat(impact.userModelSignal()).isEqualTo(interaction);
        assertThat(impact.moodSignal()).isEqualTo(mood);
        assertThat(impact.suppressBdiExtraction()).isTrue();
        assertThat(impact.strategySignal()).isNull();
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveImpactTest -DfailIfNoTests=false`
Expected: Compilation error — `CognitiveImpact` not found.

- [ ] **Step 3: Create all four types**

`SubjectResolver.java`:
```java
package io.casehub.blocks.agentic.social;

import java.util.Set;

@FunctionalInterface
public interface SubjectResolver {
    Set<String> relevantSubjects(String agentId, String tenantId);
}
```

`InteractionMapper.java`:
```java
package io.casehub.blocks.agentic.social;

@FunctionalInterface
public interface InteractionMapper {
    CognitiveImpact mapInteraction(String agentId, String targetId,
                                    String interactionType);
}
```

`CognitiveImpact.java`:
```java
package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.relationship.QualitySignal;
import org.jspecify.annotations.Nullable;

public record CognitiveImpact(
        @Nullable InteractionSignal userModelSignal,
        @Nullable MoodSignal moodSignal,
        boolean suppressBdiExtraction,
        @Nullable EngagementSignal strategySignal) {

    public static CognitiveImpact fromText(String description) {
        return new CognitiveImpact(
                new InteractionSignal.CustomSignal(description, QualitySignal.NEUTRAL),
                null, false, null);
    }
}
```

`NormFilter.java`:
```java
package io.casehub.blocks.agentic.social.emergence;

import java.util.List;

@FunctionalInterface
public interface NormFilter {
    List<SocialNorm> filter(List<SocialNorm> norms, String agentId, String tenantId);
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitiveImpactTest`
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/SubjectResolver.java blocks/src/main/java/io/casehub/blocks/agentic/social/InteractionMapper.java blocks/src/main/java/io/casehub/blocks/agentic/social/CognitiveImpact.java blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/NormFilter.java blocks/src/test/java/io/casehub/blocks/agentic/social/CognitiveImpactTest.java
git commit -m "feat(#282): add SubjectResolver, InteractionMapper, NormFilter SPIs + CognitiveImpact record"
```

---

## Batch 2: CognitionCore method changes

### Task 2: Change CognitionCore.tick() to accept SubjectResolver

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java` (tick method signature — line 94)
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java` (new or existing)

**Interfaces:**
- Consumes: `SubjectResolver` from Task 1
- Produces: `CognitionCore.tick(agentId, tenantId, descriptor, resolver)` — takes `SubjectResolver` instead of `Set<String>`

- [ ] **Step 1: Write failing test for resolver-based tick**

```java
@Test
void tickCallsResolverForSubjects() {
    var resolver = (SubjectResolver) (aid, tid) -> Set.of("subject-a", "subject-b");
    core.tick("agent1", "tenant1", descriptor, resolver);
    // Verify userModel.tick was called for both subjects
    verify(userModel).tick("agent1", "subject-a", "tenant1");
    verify(userModel).tick("agent1", "subject-b", "tenant1");
}
```

- [ ] **Step 2: Run — verify fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitionCoreTest#tickCallsResolverForSubjects`
Expected: Compilation error — `tick()` doesn't accept `SubjectResolver`.

- [ ] **Step 3: Change tick() signature**

In `CognitionCore.java`, change:
```java
// From:
public void tick(String agentId, String tenantId,
                 @Nullable AgentDescriptor descriptor,
                 Set<String> activeSubjects)

// To:
public void tick(String agentId, String tenantId,
                 @Nullable AgentDescriptor descriptor,
                 SubjectResolver resolver)
```

Change the subject iteration inside `tick()`:
```java
// From:
for (String subjectId : activeSubjects) {

// To:
for (String subjectId : resolver.relevantSubjects(agentId, tenantId)) {
```

Update all constructors' `tick()` callers. Update `SocialAvatarCognition`:
```java
// initialize():
core.tick(agentId, tenantId, descriptor, (aid, tid) -> Set.of());

// tick():
core.tick(agentId, tenantId, descriptor, (aid, tid) -> activeSubjects);
```

- [ ] **Step 4: Run full test suite — verify green**

Run: `mvn --batch-mode test -pl blocks`
Expected: All tests pass (existing tests updated for new signature).

- [ ] **Step 5: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java blocks/src/test/java/io/casehub/blocks/agentic/social/
git commit -m "feat(#282): change CognitionCore.tick() to accept SubjectResolver"
```

### Task 3: Add CognitiveImpact parameter to CognitionCore.recordInteraction()

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java` (recordInteraction — line 135)
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java` (line 88-91)
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java`

**Interfaces:**
- Consumes: `CognitiveImpact` from Task 1
- Produces: `CognitionCore.recordInteraction(agentId, tenantId, subjectId, userMessage, response, impact)` — `@Nullable CognitiveImpact` as 6th parameter

- [ ] **Step 1: Write failing tests for impact-based recording**

```java
@Test
void recordInteractionWithMoodSignalSkipsLlmAppraisal() {
    var mood = new MoodSignal.DirectShift(-0.2, 0.1, 0.0, "theft");
    var impact = new CognitiveImpact(null, mood, false, null);
    core.recordInteraction("agent1", "tenant1", "subject1",
            "stole the candlestick", "how dare you", impact);
    // Verify mood.record was called with the provided signal, NOT LLM appraisal
    verify(moodOrchestrator).record(eq(mood), eq("agent1"), eq("tenant1"));
    // Verify LLM was NOT invoked for appraisal
    verifyNoInteractions(agentProvider);
}

@Test
void recordInteractionWithSuppressBdiSkipsExtraction() {
    var impact = new CognitiveImpact(null, null, true, null);
    core.recordInteraction("agent1", "tenant1", "subject1",
            "I plan to steal", "interesting", impact);
    // mentalModel.record should NOT be called
    verifyNoInteractions(mentalModel);
}

@Test
void recordInteractionWithNullImpactUsesDefaults() {
    core.recordInteraction("agent1", "tenant1", "subject1",
            "hello", "hi there", null);
    // Default behaviour — userModel gets CustomSignal, mood gets LLM appraisal
    verify(userModel).record(any(InteractionSignal.class),
            eq("agent1"), eq("subject1"), eq("tenant1"));
}
```

- [ ] **Step 2: Run — verify fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=CognitionCoreTest -DfailIfNoTests=false`
Expected: Compilation error — `recordInteraction` doesn't accept 6 args.

- [ ] **Step 3: Add CognitiveImpact parameter and conditional logic**

Add `@Nullable CognitiveImpact impact` as the 6th parameter to `recordInteraction()`. Inside the method:

```java
public void recordInteraction(String agentId, String tenantId,
                               @Nullable String subjectId,
                               String userMessage, String response,
                               @Nullable CognitiveImpact impact) {
    // Mood channel
    if (config.moodEnabled()) {
        if (impact != null && impact.moodSignal() != null) {
            mood.record(impact.moodSignal(), agentId, tenantId);
        } else {
            safeRun(() -> appraiseMood(agentId, tenantId, userMessage, response));
        }
    }

    if (subjectId != null) {
        // UserModel channel
        if (config.userModelEnabled() && userModel != null) {
            var signal = (impact != null && impact.userModelSignal() != null)
                    ? impact.userModelSignal()
                    : new InteractionSignal.CustomSignal(
                            userMessage, QualitySignal.NEUTRAL);
            safeRun(() -> userModel.record(signal, agentId, subjectId, tenantId));
        }

        // MentalModel channel (BDI extraction)
        if (config.mentalModelEnabled() && mentalModel != null
                && (impact == null || !impact.suppressBdiExtraction())) {
            safeRun(() -> extractAndRecordMentalState(
                    agentId, subjectId, tenantId, userMessage));
        }

        // Strategy channel
        if (config.strategyEnabled() && strategy != null) {
            if (impact != null && impact.strategySignal() != null) {
                safeRun(() -> strategy.record(impact.strategySignal(),
                        agentId, subjectId, tenantId));
            } else {
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
}
```

Update `SocialAvatarCognition.recordInteraction()`:
```java
core.recordInteraction(agentId, tenantId, subjectId, userMessage, response, null);
```

- [ ] **Step 4: Run full test suite — verify green**

Run: `mvn --batch-mode test -pl blocks`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java blocks/src/test/java/io/casehub/blocks/agentic/social/
git commit -m "feat(#282): add CognitiveImpact parameter to CognitionCore.recordInteraction()"
```

---

## Batch 3: DefaultBean producers + CLAUDE.md

### Task 4: DefaultBean producers and documentation

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/SocialCognitionDefaultBeans.java`
- Modify: `blocks/src/test/java/io/casehub/blocks/agentic/social/SocialCognitionDefaultBeansTest.java`
- Modify: `CLAUDE.md` (social package entry)

**Interfaces:**
- Consumes: `SubjectResolver`, `InteractionMapper`, `NormFilter` from Task 1

- [ ] **Step 1: Write failing tests for new producers**

Add to `SocialCognitionDefaultBeansTest.java`:
```java
@Test
void subjectResolverProducesEmptySet() {
    SubjectResolver resolver = beans.subjectResolver();
    assertThat(resolver.relevantSubjects("any", "any")).isEmpty();
}

@Test
void interactionMapperProducesPassthrough() {
    InteractionMapper mapper = beans.interactionMapper();
    CognitiveImpact impact = mapper.mapInteraction("a", "b", "greet");
    assertThat(impact.userModelSignal()).isNotNull();
    assertThat(impact.userModelSignal().description()).isEqualTo("greet");
    assertThat(impact.moodSignal()).isNull();
    assertThat(impact.suppressBdiExtraction()).isFalse();
}

@Test
void normFilterProducesPassthrough() {
    NormFilter filter = beans.normFilter();
    var norm = new SocialNorm("n1", "be polite", "politeness", 0.8, 5,
            Set.of("a", "b"), Instant.now(), Instant.now(), NormStrength.ESTABLISHED);
    var result = filter.filter(List.of(norm), "agent", "tenant");
    assertThat(result).containsExactly(norm);
}
```

- [ ] **Step 2: Run — verify fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=SocialCognitionDefaultBeansTest`
Expected: Compilation error — methods don't exist on `SocialCognitionDefaultBeans`.

- [ ] **Step 3: Add producers to SocialCognitionDefaultBeans**

```java
@Produces
@DefaultBean
@Singleton
SubjectResolver subjectResolver() {
    return (agentId, tenantId) -> Set.of();
}

@Produces
@DefaultBean
@Singleton
InteractionMapper interactionMapper() {
    return (agentId, targetId, interactionType) ->
            CognitiveImpact.fromText(interactionType);
}

@Produces
@DefaultBean
@Singleton
NormFilter normFilter() {
    return (norms, agentId, tenantId) -> norms;
}
```

- [ ] **Step 4: Run full test suite — verify green**

Run: `mvn --batch-mode test -pl blocks`
Expected: All tests pass.

- [ ] **Step 5: Update CLAUDE.md**

Add to the `agentic/social` package entry: `SubjectResolver` (`@FunctionalInterface` SPI for situational subject scoping), `InteractionMapper` (`@FunctionalInterface` SPI for domain event → `CognitiveImpact` mapping), `CognitiveImpact` (typed override record for 4 orchestrator channels).

Add to the `agentic/social/emergence` package entry: `NormFilter` (`@FunctionalInterface` SPI for situational norm filtering).

- [ ] **Step 6: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/SocialCognitionDefaultBeans.java blocks/src/test/java/io/casehub/blocks/agentic/social/SocialCognitionDefaultBeansTest.java CLAUDE.md
git commit -m "feat(#282): add DefaultBean producers for SubjectResolver, InteractionMapper, NormFilter"
```

## References

- [2026-09-14-cognition-context-strategy-design.md] — design spec this plan implements
- [CognitionCore.java:94-133] — tick() method with activeSubjects parameter
- [CognitionCore.java:135-169] — recordInteraction() with 4 hardcoded channels
- [SocialAvatarCognition.java:64-96] — sole production caller of both methods
- [MoodSignal.java] — DirectShift variant for external mood override
- [EngagementSignal.java] — TurnOutcome structure for strategy channel
- [InteractionSignal.java] — sealed interface, CustomSignal variant
- [SocialNorm.java] — norm record type for NormFilter
- [CognitiveObservationSections.java:156] — normsSection() prompt assembly integration point
- [SocialCognitionDefaultBeans.java] — existing DefaultBean producers from #280
- [GitHub #282] — focal issue
- [GitHub #280] — DefaultBean pattern precedent
