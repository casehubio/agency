# Avatar Social Cognition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #198 — Avatar Tier 2: Personality modulation in speech pipeline
**Issue group:** #198, #199, #200, #201, #202, #203 (+ narrative, goals — new issues)

**Goal:** Wire all 9 social cognition orchestrators into the speech pipeline via an AvatarCognition SPI, giving the avatar personality, mood, drives, mental model, user model, strategy learning, narrative identity, autonomous goals, and proactive initiation.

**Architecture:** Three-layer integration. speech-api gains SPI types (SpeechPromptAssembler, PromptSection, AvatarCognition). blocks provides 8 PromptSection implementations + SocialAvatarCognition compositor + ProactiveSpeechSupport. speech-ws wires AvatarCognition into SpeechWebSocket/SpeechSession with proactive tick loop and signal feedback.

**Tech Stack:** Java 17, JUnit 5, Mockito, CDI (Quarkus ARC), speech-api, blocks, speech-ws modules

## Global Constraints

- speech-api must remain a pure-Java module — no Quarkus, no CDI annotations
- blocks must not depend on speech-ws (only on speech-api)
- speech-ws must not depend on blocks (discovers AvatarCognition via Instance<>)
- All new code uses plain JUnit 5 + Mockito (no Quarkus runtime)
- PromptSection implementations are plain classes (not CDI beans)
- Use ide-tooling for all code navigation and editing
- jspecify 1.0.0 added to speech-api for @Nullable annotations

---

## Batch 1: SPI Foundation

### Task 1: Create speech-api SPI types and move existing types

**Files:**
- Create: `speech-api/src/main/java/io/casehub/blocks/speech/SpeechPromptAssembler.java`
- Create: `speech-api/src/main/java/io/casehub/blocks/speech/AssembledPrompt.java`
- Create: `speech-api/src/main/java/io/casehub/blocks/speech/ConversationTurn.java`
- Create: `speech-api/src/main/java/io/casehub/blocks/speech/PromptContext.java`
- Create: `speech-api/src/main/java/io/casehub/blocks/speech/PromptSection.java`
- Create: `speech-api/src/main/java/io/casehub/blocks/speech/AvatarCognition.java`
- Modify: `speech-api/pom.xml` (add jspecify dependency)
- Modify: `blocks/pom.xml` (add speech-api compile dependency)
- Modify: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/DefaultPromptAssembler.java` (update imports, implement SpeechPromptAssembler)
- Modify: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/SpeechWebSocket.java` (update imports)
- Modify: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/SpeechSession.java` (update imports)
- Delete: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/PromptAssembler.java` (moved)
- Delete: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/AssembledPrompt.java` (moved)
- Rename: `PromptAssembler` → `SpeechPromptAssembler` (disambiguates from conversation.orchestration.PromptAssembler)
- Test: `speech-api/src/test/java/io/casehub/blocks/speech/PromptSectionTest.java`
- Test: `speech-api/src/test/java/io/casehub/blocks/speech/SpeechPromptAssemblerTest.java`

**Interfaces:**
- Produces: `SpeechPromptAssembler` — `AssembledPrompt assemble(String userMessage, List<ConversationTurn> history)`
- Produces: `AssembledPrompt(String systemPrompt, String userPrompt, @Nullable String model)`
- Produces: `ConversationTurn` — moved from speech-ws protocol package
- Produces: `PromptContext(String agentId, String tenantId, @Nullable String subjectId)`
- Produces: `PromptSection` — `@Nullable String contribute(PromptContext context)`
- Produces: `AvatarCognition` — `wrapAssembler()`, `initialize()`, `tick()`, `evaluateProactive()`, `recordInteraction()`

- [ ] **Step 1: Add jspecify to speech-api pom.xml**

Add to speech-api/pom.xml dependencies:

```xml
<dependency>
  <groupId>org.jspecify</groupId>
  <artifactId>jspecify</artifactId>
  <version>1.0.0</version>
</dependency>
```

- [ ] **Step 2: Create SpeechPromptAssembler in speech-api**

```java
package io.casehub.blocks.speech;

import java.util.List;

@FunctionalInterface
public interface SpeechPromptAssembler {
    AssembledPrompt assemble(String userMessage, List<ConversationTurn> history);
}
```

- [ ] **Step 3: Create AssembledPrompt in speech-api**

```java
package io.casehub.blocks.speech;

import org.jspecify.annotations.Nullable;

public record AssembledPrompt(String systemPrompt, String userPrompt, @Nullable String model) {
    public AssembledPrompt(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, null);
    }
}
```

- [ ] **Step 4: Create ConversationTurn in speech-api**

Copy from `speech-ws/src/main/java/io/casehub/blocks/speech/ws/protocol/ConversationTurn.java` to `speech-api/src/main/java/io/casehub/blocks/speech/ConversationTurn.java`. Update package to `io.casehub.blocks.speech`.

- [ ] **Step 5: Create PromptContext in speech-api**

```java
package io.casehub.blocks.speech;

import org.jspecify.annotations.Nullable;

public record PromptContext(String agentId, String tenantId, @Nullable String subjectId) {}
```

- [ ] **Step 6: Create PromptSection in speech-api**

```java
package io.casehub.blocks.speech;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface PromptSection {
    @Nullable String contribute(PromptContext context);
}
```

- [ ] **Step 7: Create AvatarCognition in speech-api**

```java
package io.casehub.blocks.speech;

import org.jspecify.annotations.Nullable;
import java.util.Set;
import java.util.function.Supplier;

public interface AvatarCognition {
    SpeechPromptAssembler wrapAssembler(SpeechPromptAssembler base, String agentId, String tenantId,
                                        Supplier<String> subjectIdSupplier);

    default void initialize(String agentId, String tenantId) {}

    default void tick(String agentId, String tenantId, Set<String> activeSubjects) {}

    default @Nullable String evaluateProactive(String agentId, String tenantId,
                                                String channelContext) {
        return null;
    }

    default void recordInteraction(String agentId, String tenantId,
                                    @Nullable String subjectId,
                                    String userMessage, String response) {}
}
```

- [ ] **Step 8: Add speech-api dependency to blocks pom.xml**

Add to blocks/pom.xml dependencies:

```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-blocks-speech-api</artifactId>
  <version>${project.version}</version>
</dependency>
```

- [ ] **Step 9: Update speech-ws types**

Delete `speech-ws/.../PromptAssembler.java` and `speech-ws/.../AssembledPrompt.java`. Update `DefaultPromptAssembler` to implement `SpeechPromptAssembler` (import from `io.casehub.blocks.speech`). Update all speech-ws imports of `PromptAssembler` → `SpeechPromptAssembler`, `AssembledPrompt` → `io.casehub.blocks.speech.AssembledPrompt`, `ConversationTurn` references. Use `ide_find_references` to find all usages before changing.

- [ ] **Step 10: Write tests**

`PromptSectionTest` — verify null return is accepted, non-null return is a string.
`SpeechPromptAssemblerTest` — verify DefaultPromptAssembler still works after package move.

- [ ] **Step 11: Build and verify**

Run: `mvn --batch-mode install -DskipTests` then `mvn --batch-mode test`
Expected: All existing tests pass with updated imports.

- [ ] **Step 12: Commit**

```bash
git add speech-api/ blocks/pom.xml speech-ws/
git commit -m "feat(#198): add SpeechPromptAssembler SPI + AvatarCognition to speech-api

Move PromptAssembler → SpeechPromptAssembler, AssembledPrompt,
ConversationTurn from speech-ws to speech-api. Add PromptContext,
PromptSection, AvatarCognition SPIs. blocks gains speech-api dep.

Refs #198, #199, #200, #201, #202, #203"
```

---

## Batch 2: PromptSection Implementations

### Task 2: SocialPromptAssembler + PersonalityPromptSection + MoodPromptSection

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialPromptAssembler.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/PersonalityPromptSection.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/MoodPromptSection.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/SocialPromptAssemblerTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/PersonalityPromptSectionTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/MoodPromptSectionTest.java`

**Interfaces:**
- Consumes: `SpeechPromptAssembler`, `AssembledPrompt`, `PromptContext`, `PromptSection` (from Task 1)
- Consumes: `AgentDescriptor.disposition().dispositionProfile()` → `List<DispositionValue>` (eidos-api)
- Consumes: `MoodOrchestrator.currentMood(agentId, tenantId)` → `Optional<MoodState>`
- Produces: `SocialPromptAssembler` — wraps delegate + appends sections with error isolation
- Produces: `PersonalityPromptSection` — renders disposition profile to text
- Produces: `MoodPromptSection` — renders PAD state to text

- [ ] **Step 1: Write SocialPromptAssembler test**

Test that: base prompt is preserved, N sections are appended with `\n\n` separators, null sections are skipped, failing sections are caught and logged (not propagated), PromptContext carries agentId/tenantId/subjectId.

```java
@Test
void appendsSectionsToBasePrompt() {
    var delegate = (SpeechPromptAssembler) (msg, hist) ->
            new AssembledPrompt("base system", "user: " + msg);
    var section1 = (PromptSection) ctx -> "Section 1";
    var section2 = (PromptSection) ctx -> "Section 2";
    var assembler = new SocialPromptAssembler(delegate, List.of(section1, section2),
            "agent1", "tenant1", () -> "subject1");
    var result = assembler.assemble("hello", List.of());
    assertThat(result.systemPrompt()).isEqualTo("base system\n\nSection 1\n\nSection 2");
    assertThat(result.userPrompt()).isEqualTo("user: hello");
}

@Test
void skipsNullSections() {
    var delegate = (SpeechPromptAssembler) (msg, hist) ->
            new AssembledPrompt("base", msg);
    var section = (PromptSection) ctx -> null;
    var assembler = new SocialPromptAssembler(delegate, List.of(section),
            "a", "t", () -> null);
    var result = assembler.assemble("hi", List.of());
    assertThat(result.systemPrompt()).isEqualTo("base");
}

@Test
void isolatesFailingSections() {
    var delegate = (SpeechPromptAssembler) (msg, hist) ->
            new AssembledPrompt("base", msg);
    var failing = (PromptSection) ctx -> { throw new RuntimeException("boom"); };
    var ok = (PromptSection) ctx -> "OK";
    var assembler = new SocialPromptAssembler(delegate, List.of(failing, ok),
            "a", "t", () -> null);
    var result = assembler.assemble("hi", List.of());
    assertThat(result.systemPrompt()).isEqualTo("base\n\nOK");
}
```

- [ ] **Step 2: Implement SocialPromptAssembler**

Per spec §SocialPromptAssembler. Logger, delegate, sections, agentId, tenantId, subjectIdSupplier fields. assemble() builds PromptContext, iterates sections with try-catch.

- [ ] **Step 3: Run tests — verify pass**

- [ ] **Step 4: Write PersonalityPromptSection test**

```java
@Test
void rendersDispositionProfile() {
    var values = List.of(
            new DispositionValue("warmth", 0.8, null),
            new DispositionValue("assertiveness", 0.6, null));
    var section = new PersonalityPromptSection(values);
    var result = section.contribute(new PromptContext("a", "t", null));
    assertThat(result).contains("warmth").contains("assertiveness");
}

@Test
void returnsNullForEmptyProfile() {
    var section = new PersonalityPromptSection(List.of());
    assertThat(section.contribute(new PromptContext("a", "t", null))).isNull();
}
```

- [ ] **Step 5: Implement PersonalityPromptSection**

Takes `List<DispositionValue>` at construction (from AgentDescriptor). Returns null when empty. Renders: "Your personality traits:" followed by each trait with its weight.

- [ ] **Step 6: Write MoodPromptSection test**

```java
@Test
void rendersPadState() {
    var mood = mock(MoodOrchestrator.class);
    when(mood.currentMood("a", "t")).thenReturn(Optional.of(
            new MoodState("a", "t", Instant.now(), 0.5, -0.3, 0.2,
                    "recent interaction", null, Map.of())));
    var section = new MoodPromptSection(mood);
    var result = section.contribute(new PromptContext("a", "t", null));
    assertThat(result).contains("pleasure").contains("arousal").contains("dominance");
}

@Test
void returnsNullWhenNoMoodState() {
    var mood = mock(MoodOrchestrator.class);
    when(mood.currentMood("a", "t")).thenReturn(Optional.empty());
    var section = new MoodPromptSection(mood);
    assertThat(section.contribute(new PromptContext("a", "t", null))).isNull();
}
```

- [ ] **Step 7: Implement MoodPromptSection**

Takes MoodOrchestrator at construction. Queries currentMood(). Returns null when empty. Renders PAD axes with natural language interpretation (positive pleasure = "positive mood", high arousal = "energetic", etc).

- [ ] **Step 8: Run all tests — verify pass**

- [ ] **Step 9: Commit**

```bash
git commit -m "feat(#198): SocialPromptAssembler + Personality + Mood prompt sections

SocialPromptAssembler composes PromptSections with error isolation.
PersonalityPromptSection renders disposition traits.
MoodPromptSection renders PAD emotional state.

Refs #198, #199"
```

### Task 3: DrivePromptSection + NarrativePromptSection + GoalPromptSection

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/DrivePromptSection.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/NarrativePromptSection.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/GoalPromptSection.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/DrivePromptSectionTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/NarrativePromptSectionTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/GoalPromptSectionTest.java`

**Interfaces:**
- Consumes: `DriveOrchestrator.currentDrives(agentId, tenantId)` → `Optional<DriveProfile>`
- Consumes: `NarrativeOrchestrator.currentNarrative(agentId, tenantId)` → `Optional<NarrativeState>`
- Consumes: `GoalProposalOrchestrator.currentProposals(agentId, tenantId)` → `Optional<List<DriveGoalProposal>>`
- Consumes: `CognitiveObservationSections.motivationalStateSection(DriveProfile)` → `ObservationSection`
- Consumes: `CognitiveObservationSections.narrativeSection(NarrativeState)` → `ObservationSection`
- Consumes: `AffordanceRenderer.renderObservation(List<ObservationSection>)` → `String`

- [ ] **Step 1: Write DrivePromptSection test**

Mock DriveOrchestrator, verify it delegates to CognitiveObservationSections.motivationalStateSection() and renders via AffordanceRenderer. Verify null when no drives.

- [ ] **Step 2: Implement DrivePromptSection**

Takes DriveOrchestrator. Queries currentDrives(). Returns null when empty. Delegates rendering to CognitiveObservationSections + AffordanceRenderer.

- [ ] **Step 3: Write NarrativePromptSection test**

Mock NarrativeOrchestrator. Verify delegation to CognitiveObservationSections.narrativeSection(). Verify null when no narrative.

- [ ] **Step 4: Implement NarrativePromptSection**

Same pattern as DrivePromptSection but for narrative.

- [ ] **Step 5: Write GoalPromptSection test**

Mock GoalProposalOrchestrator. Verify rendering of DriveGoalProposal list (axis, priority, description). Verify null when no proposals.

- [ ] **Step 6: Implement GoalPromptSection**

Takes GoalProposalOrchestrator. Queries currentProposals(). Returns null when empty. Renders each proposal: `"- [PRIORITY] description (drive: AXIS, intensity: N)"`.

- [ ] **Step 7: Run all tests — verify pass**

- [ ] **Step 8: Commit**

```bash
git commit -m "feat(#200): Drive + Narrative + Goal prompt sections

DrivePromptSection and NarrativePromptSection delegate rendering
to CognitiveObservationSections + AffordanceRenderer pipeline.
GoalPromptSection renders DriveGoalProposal directly.

Refs #200"
```

### Task 4: MentalModelPromptSection + UserModelPromptSection + StrategyPromptSection

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/MentalModelPromptSection.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/UserModelPromptSection.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/StrategyPromptSection.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/MentalModelPromptSectionTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/UserModelPromptSectionTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/StrategyPromptSectionTest.java`

**Interfaces:**
- Consumes: `MentalModelOrchestrator.activeSnapshots(agentId, tenantId)` → `List<MentalModelSnapshot>`
- Consumes: `UserModelOrchestrator.currentProfile(agentId, subjectId, tenantId)` → `UserProfile`
- Consumes: `StrategyLearningOrchestrator.currentStrategy(agentId, tenantId)` → `Optional<StrategyProfile>`

- [ ] **Step 1: Write MentalModelPromptSection test**

Mock MentalModelOrchestrator. Verify BDI rendering (beliefs, desires, intentions from active snapshots). Verify null when no snapshots.

- [ ] **Step 2: Implement MentalModelPromptSection**

Takes MentalModelOrchestrator. Queries activeSnapshots(). Filters to the subject matching PromptContext.subjectId() if set. Returns null when no matching snapshot. Renders beliefs, desires, intentions sections.

- [ ] **Step 3: Write UserModelPromptSection test**

Mock UserModelOrchestrator. Verify profile rendering (familiarity, stage, preferences). Verify null when subjectId is null. Verify null when profile has no data.

- [ ] **Step 4: Implement UserModelPromptSection**

Takes UserModelOrchestrator. Requires PromptContext.subjectId() — returns null if null. Queries currentProfile(agentId, subjectId, tenantId). Renders familiarity, relationship stage, preferences, topics.

- [ ] **Step 5: Write StrategyPromptSection test**

Mock StrategyLearningOrchestrator. Verify strategy rendering (effective/ineffective strategies). Verify null when no strategy.

- [ ] **Step 6: Implement StrategyPromptSection**

Takes StrategyLearningOrchestrator. Queries currentStrategy(). Returns null when empty. Renders effective strategies and strategies to avoid.

- [ ] **Step 7: Run all tests — verify pass**

- [ ] **Step 8: Commit**

```bash
git commit -m "feat(#201): MentalModel + UserModel + Strategy prompt sections

MentalModelPromptSection renders BDI (beliefs, desires, intentions).
UserModelPromptSection renders per-user profile (subject-scoped).
StrategyPromptSection renders effective/ineffective strategies.

Refs #201, #202, #203"
```

---

## Batch 3: Composition Root

### Task 5: SocialAvatarCognition + ProactiveSpeechSupport

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/social/prompt/ProactiveSpeechSupport.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognitionTest.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/prompt/ProactiveSpeechSupportTest.java`

**Interfaces:**
- Consumes: All PromptSection implementations (Tasks 2-4)
- Consumes: All social cognition orchestrators (existing)
- Consumes: `InnerLifeOrchestrator.tick(descriptor, channelContext)` → `InnerLifeTick`
- Consumes: `InnerLifeOrchestrator.observeResponse(descriptor)`
- Consumes: `AgentRegistry.findById(agentId, tenantId)` → `Optional<AgentDescriptor>` (eidos-api)
- Produces: `SocialAvatarCognition implements AvatarCognition` — @ApplicationScoped CDI bean
- Produces: `ProactiveSpeechSupport` — wraps InnerLifeOrchestrator for speech pipeline

- [ ] **Step 1: Write ProactiveSpeechSupport test**

```java
@Test
void returnsContentOnInitiated() {
    var innerLife = mock(InnerLifeOrchestrator.class);
    var descriptor = mock(AgentDescriptor.class);
    when(innerLife.tick(descriptor, "context"))
            .thenReturn(new InnerLifeTick.Initiated("Hello there!", null, 0.8));
    var support = new ProactiveSpeechSupport(innerLife, descriptor);
    assertThat(support.evaluateProactive("context")).isEqualTo("Hello there!");
}

@Test
void returnsNullOnSilent() {
    var innerLife = mock(InnerLifeOrchestrator.class);
    var descriptor = mock(AgentDescriptor.class);
    when(innerLife.tick(descriptor, "context"))
            .thenReturn(new InnerLifeTick.Silent(null));
    var support = new ProactiveSpeechSupport(innerLife, descriptor);
    assertThat(support.evaluateProactive("context")).isNull();
}
```

- [ ] **Step 2: Implement ProactiveSpeechSupport**

Per spec §ProactiveSpeechSupport. Logger, innerLife, descriptor fields. evaluateProactive() switches on InnerLifeTick, returns content on Initiated, null on Silent.

- [ ] **Step 3: Write SocialAvatarCognition test — wrapAssembler**

Verify buildSections() creates all 8 sections. Verify wrapAssembler returns a SocialPromptAssembler that includes all sections. Mock all orchestrators.

- [ ] **Step 4: Write SocialAvatarCognition test — tick**

Verify tick() calls mood.tick(), strategy.tick(), narrative.tick(), goals.tick(). Verify subject-scoped: userModel.tick() and mentalModel.tick() called for each subject in activeSubjects.

- [ ] **Step 5: Write SocialAvatarCognition test — recordInteraction**

Verify recordInteraction dispatches to UserModel, MentalModel, Strategy (subject-scoped) and InnerLife.observeResponse (agent-scoped). Verify no dispatch when subjectId is null (for subject-scoped). Verify exception in one orchestrator doesn't prevent others.

- [ ] **Step 6: Write SocialAvatarCognition test — evaluateProactive**

Verify evaluateProactive delegates to InnerLifeOrchestrator when available. Verify returns null when InnerLifeOrchestrator not resolvable. Verify returns null when AgentRegistry not resolvable.

- [ ] **Step 7: Implement SocialAvatarCognition**

Per spec §SocialAvatarCognition. @ApplicationScoped. Injects all orchestrators (some via Instance<> for optional). buildSections() constructs all 8 PromptSections. wrapAssembler(), initialize(), tick(), evaluateProactive(), recordInteraction() per spec. record() helper for exception isolation.

- [ ] **Step 8: Run all tests — verify pass**

- [ ] **Step 9: Commit**

```bash
git commit -m "feat(#198): SocialAvatarCognition composition root + ProactiveSpeechSupport

SocialAvatarCognition @ApplicationScoped implements AvatarCognition.
Composes 8 PromptSections, signal recording, proactive evaluation.
ProactiveSpeechSupport bridges InnerLifeOrchestrator to speech.

Refs #198, #199, #200, #201, #202, #203"
```

---

## Batch 4: Endpoint Integration

### Task 6: AvatarConfig + SpeechWebSocket + SpeechSession wiring

**Files:**
- Modify: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/AvatarConfig.java` (add agentId, tenantId, proactiveTickIntervalSeconds)
- Modify: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/SpeechWebSocket.java` (inject AvatarCognition, wire onOpen)
- Modify: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/SpeechSession.java` (withAvatarCognition, handleProactiveSpeech, recordInteraction, proactive tick loop)
- Test: `speech-ws/src/test/java/io/casehub/blocks/speech/ws/SpeechSessionSocialCognitionTest.java`

**Interfaces:**
- Consumes: `AvatarCognition` (from Task 1, implemented by Task 5)
- Consumes: `SpeechPromptAssembler` (from Task 1)
- Produces: SpeechSession.withAvatarCognition(AvatarCognition, String agentId, String tenantId) — follows withSpeakerServices() pattern
- Produces: SpeechSession.handleProactiveSpeech(String content) — dedicated proactive speech path

- [ ] **Step 1: Write AvatarConfig test — verify new properties**

Verify `agentId()`, `tenantId()`, `proactiveTickIntervalSeconds()` resolve from config with defaults.

- [ ] **Step 2: Update AvatarConfig**

Add `Optional<String> agentId()`, `Optional<String> tenantId()`, `@WithDefault("30") int proactiveTickIntervalSeconds()`.

- [ ] **Step 3: Write SpeechSession social cognition test**

```java
@Test
void recordInteractionDelegatesToCognition() {
    var cognition = mock(AvatarCognition.class);
    var session = buildSession(); // helper with mocked deps
    session.withAvatarCognition(cognition, "agent1", "tenant1");
    session.handleText("hello", null, null);
    verify(cognition).recordInteraction("agent1", "tenant1", "anonymous",
            eq("hello"), anyString());
}

@Test
void handleProactiveSpeechAddsAssistantTurn() {
    var session = buildSession();
    session.handleProactiveSpeech("I was thinking about something");
    // Verify Response message sent, history has assistant turn, no LLM call
}

@Test
void worksWithoutCognition() {
    var session = buildSession(); // no withAvatarCognition call
    session.handleText("hello", null, null);
    // Verify works as before — no NPE, no signal recording
}
```

- [ ] **Step 4: Implement SpeechSession changes**

Add fields: `AvatarCognition cognition`, `String agentId`, `String tenantId`, `AtomicBoolean turnInProgress`, `volatile boolean isClosed`, `Thread proactiveThread`.

Add `withAvatarCognition(AvatarCognition, String, String)` — sets fields, returns `this` (builder pattern like `withSpeakerServices()`).

Add `handleProactiveSpeech(String content)` per spec — adds assistant turn to history, sends Response message, synthesises TTS, no LLM call, no recordInteraction.

In `handleText()`/`handleStop()` — set `turnInProgress` true at start, false at end. After generating response, call `recordInteraction()` if cognition is set.

Add `startProactiveTick(int intervalSeconds)` — virtual thread with fixed-delay loop. Each tick: check `isClosed`, check `turnInProgress` (skip if busy), call `cognition.tick()` then `cognition.evaluateProactive()`. On content, call `handleProactiveSpeech()`.

Update `close()` — set `isClosed`, interrupt proactive thread.

- [ ] **Step 5: Update SpeechWebSocket.onOpen()**

Inject `Instance<AvatarCognition> avatarCognition`. In onOpen(): if `avatarCognition.isResolvable()` and `agentId`/`tenantId` configured, create `AtomicReference<String>("anonymous")` as subjectId holder, call `initialize()`, wrap assembler via `wrapAssembler()`. Pass cognition to session via `withAvatarCognition()`. Start proactive tick.

- [ ] **Step 6: Run all tests — verify pass**

Run: `mvn --batch-mode test`
Expected: All tests pass including existing 88 speech-ws tests.

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(#198): wire AvatarCognition into SpeechWebSocket + SpeechSession

AvatarConfig gains agentId, tenantId, proactiveTickIntervalSeconds.
SpeechWebSocket discovers AvatarCognition via Instance<>.
SpeechSession gains handleProactiveSpeech(), signal recording,
proactive tick loop with turn-busy gating.

Refs #198, #199, #200, #201, #202, #203"
```

---

## References

- [2026-09-05-avatar-social-cognition-design.md] — design spec this plan implements
- PersonalityEvolutionOrchestrator.java — trait evolution API
- MoodOrchestrator.java:60 — currentMood() query
- DriveOrchestrator.java:152 — currentDrives() query
- InnerLifeOrchestrator.java:88-194 — tick() proactive initiation pipeline
- InnerLifeTick.java — Initiated/Silent sealed outcomes
- MentalModelOrchestrator.java:144 — activeSnapshots() query
- UserModelOrchestrator.java:96 — currentProfile() query
- StrategyLearningOrchestrator.java:141 — currentStrategy() query
- NarrativeOrchestrator.java:64 — currentNarrative() query
- GoalProposalOrchestrator.java:126 — currentProposals() query
- CognitiveObservationSections.java — existing rendering for drives, narrative
- AffordanceRenderer.java — ObservationSection → String rendering
- SpeechWebSocket.java — endpoint wiring (existing withSpeakerServices pattern)
- SpeechSession.java — session lifecycle
- AvatarConfig.java — configuration interface
- InteractionSignal.java — CustomSignal record
- MentalStateSignal.java — VerbalCue record
- EngagementSignal.java — TurnOutcome record
- EngagementEvent (neocortex-memory-api) — 15-field record
- MoodState (neocortex-memory-api) — PAD record
- decisions.md — D1-D6 decision log
- GitHub #198, #199, #200, #201, #202, #203
