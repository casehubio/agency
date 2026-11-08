# Cognitive Pipeline Health Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #279 — Strategy learning contribution to showcase dialogue
**Issue group:** #279 (broadened to full cognitive pipeline health)

**Goal:** Fix all "wired but not flowing" gaps across cognitive orchestrators so every pipeline contributes to the showcase conversation, with LLM proof tests that verify each pipeline works end-to-end.

**Architecture:** Seven fixes across CognitionCore, StrategyLearningOrchestrator, CognitionStack, ConversationRunner, and CognitiveImpact. Signal correlation fixes the root cause (turns can't batch), async auto-reflect makes strategy self-sufficient, neurocortex priming enables cold-start, and wiring fixes unblock drives and goals. All changes are in blocks main scope or agentic-yaml test scope.

**Tech Stack:** Java 22, JUnit 5, Mockito, InMemoryCbrCaseMemoryStore, langchain4j (test scope under -Pllm)

## Global Constraints

- No Quarkus runtime in tests — plain JUnit 5 with Mockito
- No new compile dependencies — all types already on classpath
- CognitiveImpact record change is the only API-surface modification
- TemporalDecay and ScopeDecay are @FunctionalInterface from neocortex-memory-api — use no-op lambdas for test wiring
- All code in `blocks/src/main/java/` or `agentic-yaml/src/test/java/`

---

## Batch 1: Foundation — CognitiveImpact extension + compile fixes

### Task 1: Add conversationId to CognitiveImpact and fix compile breaks

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/CognitiveImpact.java:6-17`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStack.java:160-163` (tick SubjectResolver fix)
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/ConversationRunner.java:92-93` (recordInteraction 6-arg fix)
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitiveImpactTest.java`

**Interfaces:**
- Produces: `CognitiveImpact(InteractionSignal, MoodSignal, boolean, EngagementSignal, String conversationId)` — the record all downstream tasks consume
- Produces: `CognitiveImpact.withConversationId(String)` — convenience factory

- [ ] **Step 1: Write failing test for CognitiveImpact conversationId**

```java
// In CognitiveImpactTest.java (new or existing)
@Test
void conversationIdFieldIsAccessible() {
    var impact = new CognitiveImpact(null, null, false, null, "conv-123");
    assertThat(impact.conversationId()).isEqualTo("conv-123");
}

@Test
void fromTextPreservesNullConversationId() {
    var impact = CognitiveImpact.fromText("hello");
    assertThat(impact.conversationId()).isNull();
}

@Test
void withConversationIdFactory() {
    var impact = CognitiveImpact.withConversationId("conv-456");
    assertThat(impact.conversationId()).isEqualTo("conv-456");
    assertThat(impact.userModelSignal()).isNull();
    assertThat(impact.suppressBdiExtraction()).isFalse();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl blocks -Dtest=CognitiveImpactTest -Dsurefire.failIfNoSpecifiedTests=false --batch-mode`
Expected: FAIL — constructor doesn't accept 5 args / method not found

- [ ] **Step 3: Add conversationId field to CognitiveImpact**

```java
public record CognitiveImpact(
        @Nullable InteractionSignal userModelSignal,
        @Nullable MoodSignal moodSignal,
        boolean suppressBdiExtraction,
        @Nullable EngagementSignal strategySignal,
        @Nullable String conversationId) {

    public static CognitiveImpact fromText(String description) {
        return new CognitiveImpact(
                new InteractionSignal.CustomSignal(description, QualitySignal.NEUTRAL),
                null, false, null, null);
    }

    public static CognitiveImpact withConversationId(String conversationId) {
        return new CognitiveImpact(null, null, false, null, conversationId);
    }
}
```

- [ ] **Step 4: Fix all callers of the old 4-arg constructor**

Search for existing `new CognitiveImpact(` across the project and add the 5th `null` arg:

```bash
# Find callers to fix
```
Use `ide_find_references` on `CognitiveImpact` constructor to find all call sites. Add `, null` as the 5th arg to each.

- [ ] **Step 5: Fix CognitionStack.tick() SubjectResolver**

In `CognitionStack.java`, change the `tick(String, String, AgentDescriptor, Set<String>)` method:
```java
public void tick(String agentId, String tenantId,
                 @Nullable AgentDescriptor descriptor,
                 Set<String> activeSubjects) {
    core.tick(agentId, tenantId, descriptor, (a, t) -> activeSubjects);
}
```

- [ ] **Step 6: Fix ConversationRunner.recordInteraction() call**

In `ConversationRunner.java:92-93`, change:
```java
// Before:
cognition.core().recordInteraction(agentId, tenantId,
        subjectId, lastMessage, response);

// After:
cognition.core().recordInteraction(agentId, tenantId,
        subjectId, lastMessage, response, null);
```

- [ ] **Step 7: Run tests to verify everything compiles and passes**

Run: `mvn test --batch-mode -pl blocks`
Expected: PASS — all existing tests still pass, new CognitiveImpactTest passes

- [ ] **Step 8: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/CognitiveImpact.java \
       blocks/src/test/java/io/casehub/blocks/agentic/social/CognitiveImpactTest.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStack.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/ConversationRunner.java
git commit -m "feat(#279): add conversationId to CognitiveImpact, fix #282 compile breaks

Add @Nullable String conversationId field to CognitiveImpact record.
Fix ConversationRunner 5→6 arg recordInteraction call.
Fix CognitionStack.tick() Set<String> → SubjectResolver lambda.

Refs #279"
```

---

## Batch 2: Strategy pipeline — signal correlation + async reflect

### Task 2: Persistent turn accumulation in StrategyLearningOrchestrator

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/StrategyLearningOrchestrator.java:40-606`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/StrategyLearningOrchestratorTest.java`

**Interfaces:**
- Consumes: `EngagementSignal.TurnOutcome` with shared caseId (from CognitiveImpact.conversationId via CognitionCore)
- Consumes: `EngagementSignal.ConversationOutcome` with matching conversationId
- Produces: CBR cases stored from accumulated turns when ConversationOutcome arrives

- [ ] **Step 1: Write failing test for turn accumulation across ticks**

```java
@Test
void turnsAccumulateAcrossTicksAndMatchConversationOutcome() {
    var orchestrator = buildOrchestrator(); // uses InMemoryCbrCaseMemoryStore

    // Record 4 turns with shared conversationId "conv-1"
    for (int i = 0; i < 4; i++) {
        orchestrator.record(turnWithCaseId("conv-1", i), "agent", "subject", "tenant");
        orchestrator.tick("agent", "tenant"); // each tick drains 1 turn
    }

    // Record ConversationOutcome with matching ID
    orchestrator.record(
        new EngagementSignal.ConversationOutcome("conv-1", "summary", 4),
        "agent", "subject", "tenant");
    var result = orchestrator.tick("agent", "tenant");

    // Should have stored a CBR case from all 4 accumulated turns
    assertThat(result).isInstanceOf(StrategyLearningTick.Learned.class);
    var learned = (StrategyLearningTick.Learned) result;
    assertThat(learned.casesStored()).isEqualTo(1);
}

private EngagementSignal.TurnOutcome turnWithCaseId(String caseId, int index) {
    return new EngagementSignal.TurnOutcome(
        new EngagementEvent("agent", "subject", "tenant", null,
            caseId, Instant.now(), "msg-" + index, null,
            Map.of(), true, null, 100, null, null, null),
        Map.of("verbosity", 0.5), "response-" + index);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl blocks -Dtest=StrategyLearningOrchestratorTest#turnsAccumulateAcrossTicksAndMatchConversationOutcome --batch-mode`
Expected: FAIL — turns are drained individually, no case stored

- [ ] **Step 3: Add conversationTurns accumulation map to AgentLearningState**

In `StrategyLearningOrchestrator.AgentLearningState`, add:
```java
final ConcurrentHashMap<String, List<TurnEntry>> conversationTurns = new ConcurrentHashMap<>();
```

- [ ] **Step 4: Modify doTick() to accumulate turns by conversationId**

After the existing drain loop (line 173-175), add accumulation:
```java
for (var entry : drainedTurns) {
    // ... existing stats code ...
    var caseId = entry.signal.event().caseId();
    if (caseId != null && !caseId.isBlank()) {
        state.conversationTurns
            .computeIfAbsent(caseId, k -> new ArrayList<>())
            .add(entry);
    }
}
```

Modify the ConversationOutcome processing block to use accumulated turns:
```java
for (var convEntry : drainedConversations) {
    var conv = convEntry.signal;
    var accumulated = state.conversationTurns.remove(conv.conversationId());
    if (accumulated == null || accumulated.isEmpty()) continue;

    var features = extractFeatures(accumulated, convEntry.subjectId, state.agentId);
    var summary = conv.conversationSummary() != null
            ? conv.conversationSummary()
            : "Conversation with " + convEntry.subjectId + " (" + conv.turnCount() + " turns)";
    var cbrCase = new FeatureVectorCbrCase(
            summary, "-", null, null, features, null, state.agentId);
    cbrStore.store(cbrCase, config.engagementCaseType(), state.agentId,
            config.memoryDomain(), state.tenantId, null, Path.root());
    storedConversations.add(conv.conversationId());
    casesStored++;
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl blocks -Dtest=StrategyLearningOrchestratorTest --batch-mode`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/StrategyLearningOrchestrator.java \
       blocks/src/test/java/io/casehub/blocks/agentic/social/StrategyLearningOrchestratorTest.java
git commit -m "feat(#279): persistent turn accumulation by conversationId

Turns accumulate in a per-conversation map across ticks. When a
ConversationOutcome arrives, accumulated turns are matched and
stored as a single CBR case. Fixes the 1-signal-per-tick gap where
turns were individually drained before ConversationOutcome arrived.

Refs #279"
```

### Task 3: Async auto-reflect with dedicated executor

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/StrategyLearningOrchestrator.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/StrategyLearningOrchestratorTest.java`

**Interfaces:**
- Consumes: CBR cases stored by Task 2
- Produces: `StrategyProfile` with guidelines (via async reflect), available through `currentStrategy()`

- [ ] **Step 1: Write failing test for auto-reflect trigger**

```java
@Test
void tickAutoTriggersReflectWhenEnoughCases() throws Exception {
    var cbrStore = new InMemoryCbrCaseMemoryStore();
    var agentProvider = mock(AgentProvider.class);
    when(agentProvider.invoke(any())).thenReturn(
        Multi.createFrom().item(new AgentEvent.TextDelta(
            "{\"guidelines\":[\"use specific examples\"],\"dimensionDeltas\":{}}")));

    var config = new StrategyLearningConfig(
        1, 2, 50, 10, 0.5, 100, Duration.ofSeconds(1),
        new MemoryDomain("test"), "engagement", "profile");
    var orchestrator = new StrategyLearningOrchestrator(
        new InMemoryStrategyStore(), cbrStore,
        (a, t, s, m) -> List.of(), agentProvider, null, config,
        Clock.systemUTC());

    // Pre-store 2 CBR cases to cross minCasesForReflection threshold
    for (int i = 0; i < 2; i++) {
        storeTestCase(cbrStore, "agent", "tenant", config, i);
    }

    // Record turns and ConversationOutcome to trigger case storage
    for (int j = 0; j < 3; j++) {
        orchestrator.record(turnWithCaseId("conv-x", j), "agent", "subject", "tenant");
    }
    orchestrator.record(
        new EngagementSignal.ConversationOutcome("conv-x", "summary", 3),
        "agent", "subject", "tenant");
    orchestrator.tick("agent", "tenant");

    // Wait for async reflect to complete
    Thread.sleep(2000);

    var strategy = orchestrator.currentStrategy("agent", "tenant");
    assertThat(strategy).isPresent();
    assertThat(strategy.get().guidelines()).isNotEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl blocks -Dtest=StrategyLearningOrchestratorTest#tickAutoTriggersReflectWhenEnoughCases --batch-mode`
Expected: FAIL — no auto-reflect logic exists

- [ ] **Step 3: Add dedicated executor and countAgentCases method**

```java
private static final ExecutorService REFLECT_EXECUTOR =
    Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "strategy-reflect");
        t.setDaemon(true);
        return t;
    });

private int countAgentCases(String agentId, String tenantId) {
    var query = CbrQuery.of(tenantId, config.memoryDomain(), Path.root(),
                    config.engagementCaseType(), Map.of(), config.maxReflectionSources())
            .withMinSimilarity(0.0);
    return (int) cbrStore.retrieveSimilar(query, CbrCase.class).stream()
            .filter(s -> agentId.equals(s.cbrCase().producerAgentId()))
            .count();
}
```

- [ ] **Step 4: Add auto-reflect check at end of doTick()**

After `casesStored++` in the ConversationOutcome processing, before the return:
```java
if (casesStored > 0) {
    int totalCases = countAgentCases(state.agentId, state.tenantId);
    if (totalCases >= config.minCasesForReflection()
            && (state.lastReflectTimestamp == null
                || Duration.between(state.lastReflectTimestamp, clock.instant())
                    .compareTo(config.staleStateTimeout()) > 0)) {
        state.lastReflectTimestamp = clock.instant();
        REFLECT_EXECUTOR.submit(() -> doReflectAsync(state.agentId, state.tenantId));
    }
}
```

- [ ] **Step 5: Add doReflectAsync with lock-free LLM call**

```java
private void doReflectAsync(String agentId, String tenantId) {
    try {
        StrategyProfile profile;
        List<? extends ScoredCbrCase<CbrCase>> cases;

        var lock = tickLocks.computeIfAbsent(stateKey(agentId, tenantId),
                k -> new ReentrantLock());
        lock.lock();
        try {
            profile = currentStrategy(agentId, tenantId)
                    .orElseGet(() -> defaultProfile(agentId, tenantId));
            var query = CbrQuery.of(tenantId, config.memoryDomain(), Path.root(),
                            config.engagementCaseType(), Map.of(), config.maxReflectionSources())
                    .withMinSimilarity(0.0);
            cases = cbrStore.retrieveSimilar(query, CbrCase.class).stream()
                    .filter(s -> agentId.equals(s.cbrCase().producerAgentId()))
                    .toList();
        } finally {
            lock.unlock();
        }

        if (cases.size() < config.minCasesForReflection()) return;

        TrendProfile trends = analyzeTrends(cases);
        var perSubjectSummary = summarizePerSubject(cases);
        var userPrompt = buildReflectionPrompt(profile, trends, List.of(), perSubjectSummary);

        String llmResponse;
        try {
            var sessionConfig = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt);
            var responseText = new StringBuilder();
            agentProvider.invoke(sessionConfig)
                    .subscribe().asStream()
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .forEach(responseText::append);
            llmResponse = responseText.toString();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Async reflect LLM failed for " + agentId, e);
            return;
        }

        var state = states.get(stateKey(agentId, tenantId));
        lock.lock();
        try {
            applyReflectionResult(profile, llmResponse, trends, cases.size(),
                    agentId, tenantId, state);
        } finally {
            lock.unlock();
        }
    } catch (Exception e) {
        LOG.log(Level.WARNING, "Async reflect failed for " + agentId, e);
    }
}
```

- [ ] **Step 6: Run tests**

Run: `mvn test -pl blocks -Dtest=StrategyLearningOrchestratorTest --batch-mode`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/StrategyLearningOrchestrator.java \
       blocks/src/test/java/io/casehub/blocks/agentic/social/StrategyLearningOrchestratorTest.java
git commit -m "feat(#279): async auto-reflect in tick() with dedicated executor

When tick() stores CBR cases and the total count crosses
minCasesForReflection, reflection fires asynchronously on a
dedicated single-thread daemon executor. Lock-free LLM call:
snapshot under lock, invoke LLM without lock, store result under
lock. Keeps tick latency predictable.

Refs #279"
```

### Task 4: ConversationRunner — conversation ID + ConversationOutcome + final tick

**Files:**
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/ConversationRunner.java:46-128`
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java:139-187` (use conversationId from CognitiveImpact)
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java`

**Interfaces:**
- Consumes: `CognitiveImpact.conversationId()` (from Task 1)
- Produces: `EngagementSignal.TurnOutcome` with shared caseId, `EngagementSignal.ConversationOutcome` at end

- [ ] **Step 1: Write failing test for CognitionCore conversationId plumbing**

```java
@Test
void recordInteractionUsesConversationIdFromImpact() {
    var strategy = mock(StrategyLearningOrchestrator.class);
    var core = new CognitionCore(mood, drives, null, null,
            strategy, null, null, null, null);

    var impact = CognitiveImpact.withConversationId("conv-abc");
    core.recordInteraction("agent", "tenant", "subject",
            "hello", "world", impact);

    var captor = ArgumentCaptor.forClass(EngagementSignal.class);
    verify(strategy).record(captor.capture(), eq("agent"), eq("subject"), eq("tenant"));
    var signal = (EngagementSignal.TurnOutcome) captor.getValue();
    assertThat(signal.event().caseId()).isEqualTo("conv-abc");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl blocks -Dtest=CognitionCoreTest#recordInteractionUsesConversationIdFromImpact --batch-mode`
Expected: FAIL — CognitionCore ignores conversationId, uses random UUID

- [ ] **Step 3: Modify CognitionCore.recordInteraction() to use conversationId**

In the strategy recording block (around line 164-183), change the default TurnOutcome construction to use `impact.conversationId()` when available:

```java
if (config.strategyEnabled() && strategy != null) {
    if (impact != null && impact.strategySignal() != null) {
        var strategySignal = impact.strategySignal();
        safeRun(() -> strategy.record(strategySignal,
                agentId, subjectId, tenantId));
    } else {
        String caseId = (impact != null && impact.conversationId() != null)
                ? impact.conversationId()
                : UUID.randomUUID().toString();
        safeRun(() -> strategy.record(
                new EngagementSignal.TurnOutcome(
                        new EngagementEvent(agentId, subjectId,
                                tenantId, null,
                                caseId,
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl blocks -Dtest=CognitionCoreTest --batch-mode`
Expected: PASS

- [ ] **Step 5: Modify ConversationRunner to generate conversation ID and emit ConversationOutcome**

In `ConversationRunner.run()`:

At the start of `run()`, generate a stable conversation ID:
```java
var conversationId = tenantId + "-conv-" + java.util.UUID.randomUUID();
```

Change the `recordInteraction` call (around line 92) to pass CognitiveImpact:
```java
if (!history.isEmpty() && subjectId != null) {
    var lastMessage = history.size() > 1
            ? history.get(history.size() - 2).dialogue() : "";
    cognition.core().recordInteraction(agentId, tenantId,
            subjectId, lastMessage, response,
            CognitiveImpact.withConversationId(conversationId));
}
```

After the for loop, add ConversationOutcome emission and final tick:
```java
// Emit ConversationOutcome for strategy learning
if (cognition.core().strategy() != null) {
    for (var speaker : speakers) {
        var otherSpeaker = speakers.stream()
                .filter(s -> !s.name().equals(speaker.name()))
                .findFirst().map(AgentDescriptor::name).orElse(null);
        cognition.core().strategy().record(
                new EngagementSignal.ConversationOutcome(
                        conversationId,
                        "Historical encounter: " + speakers.size() + " participants, " + history.size() + " turns",
                        history.size()),
                speaker.name(), otherSpeaker, tenantId);
    }
    // Final tick to process the ConversationOutcome
    for (var speaker : speakers) {
        cognition.tick(speaker.name(), tenantId, speaker, subjectIds);
    }
}
```

- [ ] **Step 6: Run full test suite**

Run: `mvn test --batch-mode -pl blocks`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java \
       blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/ConversationRunner.java
git commit -m "feat(#279): conversation ID plumbing + ConversationOutcome emission

CognitionCore.recordInteraction uses conversationId from
CognitiveImpact as caseId (instead of random UUID).
ConversationRunner generates stable conversation ID, passes via
CognitiveImpact, emits ConversationOutcome at end, runs final
tick to process it. Completes the signal correlation pipeline.

Refs #279"
```

---

## Batch 3: Priming + goals + quality

### Task 5: Neurocortex priming in CognitionStack (lazy first-tick)

**Files:**
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStack.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStackTest.java`

**Interfaces:**
- Consumes: `AgentDescriptor.constraints()`, `AgentDescriptor.disposition()` — for deriving synthetic case features
- Consumes: `CbrCaseMemoryStore` (shared instance from CognitionStack.from())
- Produces: ≥ `minCasesForReflection` synthetic CBR cases in the store on first tick

- [ ] **Step 1: Write failing test for lazy priming**

```java
@Test
void firstTickPrimesCbrStoreWithSyntheticCases() {
    var cbrStore = new InMemoryCbrCaseMemoryStore();
    var stack = CognitionStack.from(compiledCognition, mockAgentProvider, Stage.REAL_DRIVES);

    // Before tick: no cases
    var query = CbrQuery.of("tenant", new MemoryDomain("strategy-learning"),
            Path.root(), "engagement-evidence", Map.of(), 50).withMinSimilarity(0.0);
    assertThat(cbrStore.retrieveSimilar(query, CbrCase.class)).isEmpty();

    // Provide descriptor with constraints
    var descriptor = descriptorWithConstraints("precision-in-physics");
    stack.tick("leonardo", "tenant", descriptor, Set.of("nikola"));

    // After first tick: primed cases exist
    var cases = cbrStore.retrieveSimilar(query, CbrCase.class).stream()
            .filter(s -> "leonardo".equals(s.cbrCase().producerAgentId()))
            .toList();
    assertThat(cases).hasSizeGreaterThanOrEqualTo(5); // minCasesForReflection
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl agentic-yaml -Dtest=CognitionStackTest#firstTickPrimesCbrStoreWithSyntheticCases --batch-mode`
Expected: FAIL — no priming logic

- [ ] **Step 3: Add priming flag and primeStrategyCases method**

In `CognitionStack`, add:
```java
private final CbrCaseMemoryStore cbrStore;
private final StrategyLearningConfig strategyConfig;
private final ConcurrentHashMap<String, Boolean> primedAgents = new ConcurrentHashMap<>();
```

Update the constructor and `from()` to pass through `cbrStore` and `strategyConfig`.

Add priming method:
```java
private void primeIfNeeded(String agentId, String tenantId,
                            @Nullable AgentDescriptor descriptor) {
    if (stage.ordinal() < Stage.REAL_DRIVES.ordinal()) return;
    if (descriptor == null) return;
    if (primedAgents.putIfAbsent(agentId + ":" + tenantId, true) != null) return;

    var constraints = descriptor.constraints();
    if (constraints == null || constraints.isEmpty()) return;

    for (int i = 0; i < strategyConfig.minCasesForReflection(); i++) {
        var features = buildSyntheticFeatures(descriptor, i);
        var summary = "Synthetic interaction " + (i + 1) + " derived from "
                + constraints.getFirst().text();
        var cbrCase = new FeatureVectorCbrCase(
                summary, "-", null, null, features, null, agentId);
        cbrStore.store(cbrCase, strategyConfig.engagementCaseType(),
                agentId, strategyConfig.memoryDomain(), tenantId, null, Path.root());
    }
}
```

Add feature builder with variance:
```java
private static Map<String, FeatureValue> buildSyntheticFeatures(
        AgentDescriptor descriptor, int index) {
    int count = 5;
    double progress = (double) index / (count - 1); // 0.0 to 1.0

    var features = new LinkedHashMap<String, FeatureValue>();
    features.put("subjectId", FeatureValue.string("synthetic"));
    features.put("agentId", FeatureValue.string(descriptor.name()));
    features.put("conversationTimestamp",
            FeatureValue.number((double) (Instant.now().toEpochMilli() - (count - index) * 3600_000L)));
    features.put("turnCount", FeatureValue.number(4 + index));
    features.put("avgResponseLength", FeatureValue.number(80 + progress * 170));
    features.put("continuationRate", FeatureValue.number(0.6 + progress * 0.25));
    features.put("meanAffectShift", FeatureValue.number(-0.05 + progress * 0.25));

    // Derive dimension snapshots from constraints
    double formality = 0.5;
    double verbosity = 0.5;
    if (descriptor.constraints() != null) {
        for (var c : descriptor.constraints()) {
            var text = c.text().toLowerCase();
            if (text.contains("precision") || text.contains("exact") || text.contains("rigour")) {
                formality = 0.8; verbosity = 0.3;
            } else if (text.contains("artistic") || text.contains("creative") || text.contains("expressive")) {
                formality = 0.4; verbosity = 0.7;
            }
        }
    }
    features.put("avgSnapshot_verbosity", FeatureValue.number(verbosity));
    features.put("avgSnapshot_formality", FeatureValue.number(formality));
    features.put("avgSnapshot_initiative", FeatureValue.number(0.5));
    features.put("avgSnapshot_directness", FeatureValue.number(0.5));
    features.put("avgSnapshot_questionRate", FeatureValue.number(0.5));

    return Map.copyOf(features);
}
```

- [ ] **Step 4: Call primeIfNeeded from tick()**

In both `tick()` overloads:
```java
public void tick(String agentId, String tenantId,
                 @Nullable AgentDescriptor descriptor,
                 Set<String> activeSubjects) {
    primeIfNeeded(agentId, tenantId, descriptor);
    core.tick(agentId, tenantId, descriptor, (a, t) -> activeSubjects);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl agentic-yaml -Dtest=CognitionStackTest --batch-mode`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStack.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStackTest.java
git commit -m "feat(#279): lazy neurocortex priming for cold-start strategy

On first tick per agent, CognitionStack primes the CBR store with
synthetic interaction cases derived from AgentDescriptor constraints.
Varied feature vectors (continuationRate, meanAffectShift,
avgResponseLength) with upward trends give TrendAnalyzer meaningful
signals. Auto-reflect (Task 3) fires on the primed cases to produce
initial guidelines.

Refs #279"
```

### Task 6: Wire MemoryHygieneOrchestrator + CuriosityDrive + GoalProposalConfig

**Files:**
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStack.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStackTest.java`

**Interfaces:**
- Consumes: `MemoryHygieneOrchestrator`, `CuriosityDrive`, `CuriosityGoalMapper` constructors
- Produces: CognitionCore with non-null memoryHygiene, real CuriosityDrive, CuriosityGoalMapper in FULL stage

- [ ] **Step 1: Write failing test for CuriosityDrive wiring**

```java
@Test
void realDrivesStageWiresCuriosityDrive() {
    var stack = CognitionStack.from(compiledCognition, mockAgentProvider, Stage.REAL_DRIVES);
    assertThat(stack.core().memoryHygiene()).isNotNull();
}

@Test
void fullStageWiresCuriosityGoalMapper() {
    var stack = CognitionStack.from(compiledCognition, mockAgentProvider, Stage.FULL);
    stack.tick("agent", "tenant", descriptor, Set.of("other"));
    // GoalProposalOrchestrator should have CuriosityGoalMapper wired
    assertThat(stack.core().goals()).isNotNull();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl agentic-yaml -Dtest=CognitionStackTest --batch-mode`
Expected: FAIL — memoryHygiene() returns null

- [ ] **Step 3: Wire MemoryHygieneOrchestrator in CognitionStack.from()**

In the `REAL_DRIVES+` block of `from()`, before constructing DriveOrchestrator:
```java
var memoryHygiene = new MemoryHygieneOrchestrator(
    cbrStore,
    new CompositeConfidenceScorer(List.of(
        new WeightedScorer(new ArousalScorer(), 0.5),
        new WeightedScorer(new SurpriseScorer(), 0.5))),
    (score, age) -> score,     // no temporal decay in test
    (score, scope) -> score,   // no scope decay in test
    null,                       // no consolidation summariser
    config.strategyLearning().memoryDomain(),
    List.of(config.strategyLearning().engagementCaseType()),
    RetentionConfig.DEFAULT,
    10,
    0.7,
    event -> {});

var curiosityDrive = new CuriosityDrive(memoryHygiene);
```

Replace the baseline curiosity lambda with the real CuriosityDrive:
```java
drives = new DriveOrchestrator(curiosityDrive, competence,
        affiliation, autonomy, mood, composer, config.drive());
```

Pass memoryHygiene to CognitionCore:
```java
var core = new CognitionCore(mood, drives, userModel, mentalModel,
        strategy, narrative, goals, memoryHygiene, agentProvider);
```

- [ ] **Step 4: Wire CuriosityGoalMapper at FULL stage**

```java
if (stage.ordinal() >= Stage.FULL.ordinal()) {
    var mapperList = List.<DriveGoalMapper>of(
            new CuriosityGoalMapper(curiosityDrive),
            new CompetenceGoalMapper(competence),
            new AffiliationGoalMapper(affiliation, 0.3,
                    java.time.Duration.ofHours(1)),
            new AutonomyGoalMapper(autonomy, 0.5));
    // Use config.goalProposal() but override cooldown for showcase
    var goalConfig = new GoalProposalConfig(
            config.goalProposal().proposalThreshold(),
            config.goalProposal().relevanceThreshold(),
            config.goalProposal().maxDriveGoals(),
            config.goalProposal().staleAfter(),
            Duration.ofSeconds(30),  // showcase-appropriate cooldown
            config.goalProposal().failureAbandonmentThreshold());
    goals = new GoalProposalOrchestrator(drives,
            listInstance(mapperList),
            noOpInstance(), noOpInstance(),
            noOpInstance(), noOpInstance(), noOpInstance(),
            goalConfig, config.goalEscalation());
}
```

- [ ] **Step 5: Run tests**

Run: `mvn test -pl agentic-yaml -Dtest=CognitionStackTest --batch-mode`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStack.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStackTest.java
git commit -m "feat(#279): wire MemoryHygieneOrchestrator, CuriosityDrive, CuriosityGoalMapper

Full drive source chain: MemoryHygieneOrchestrator (11-param, test
defaults) → CuriosityDrive → CuriosityGoalMapper. GoalProposalConfig
cooldown overridden to 30s for showcase. CognitionCore now receives
non-null memoryHygiene.

Refs #279"
```

### Task 7: UserModel quality derivation from mood

**Files:**
- Modify: `blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java:139-187`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java`

**Interfaces:**
- Consumes: `MoodOrchestrator.currentMood()` → `Optional<MoodState>` with `pleasure()`, `arousal()`
- Produces: `QualitySignal.POSITIVE/NEGATIVE/NEUTRAL` derived from mood state, passed to UserModel

- [ ] **Step 1: Write failing test for quality derivation**

```java
@Test
void recordInteractionDerivesPositiveQualityFromHighArousalPositivePleasure() {
    // Set up mood with high arousal + positive pleasure
    mood.record(new MoodSignal.InteractionAppraisal(0.2, 0.4, 0.1, "engaged"),
            "agent", "tenant");
    mood.tick("agent", "tenant");

    var userModel = mock(UserModelOrchestrator.class);
    var core = new CognitionCore(mood, drives, userModel, null,
            null, null, null, null, null);

    core.recordInteraction("agent", "tenant", "subject",
            "hello", "world", null);

    var captor = ArgumentCaptor.forClass(InteractionSignal.class);
    verify(userModel).record(captor.capture(), eq("agent"), eq("subject"), eq("tenant"));
    var signal = (InteractionSignal.CustomSignal) captor.getValue();
    assertThat(signal.quality()).isEqualTo(QualitySignal.POSITIVE);
}

@Test
void recordInteractionDerivesNegativeQualityFromLowArousalNegativePleasure() {
    mood.record(new MoodSignal.InteractionAppraisal(-0.2, -0.2, -0.1, "bored"),
            "agent", "tenant");
    mood.tick("agent", "tenant");

    var userModel = mock(UserModelOrchestrator.class);
    var core = new CognitionCore(mood, drives, userModel, null,
            null, null, null, null, null);

    core.recordInteraction("agent", "tenant", "subject",
            "hello", "world", null);

    var captor = ArgumentCaptor.forClass(InteractionSignal.class);
    verify(userModel).record(captor.capture(), eq("agent"), eq("subject"), eq("tenant"));
    var signal = (InteractionSignal.CustomSignal) captor.getValue();
    assertThat(signal.quality()).isEqualTo(QualitySignal.NEGATIVE);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl blocks -Dtest=CognitionCoreTest#recordInteractionDerivesPositiveQualityFromHighArousalPositivePleasure --batch-mode`
Expected: FAIL — quality is always NEUTRAL

- [ ] **Step 3: Implement quality derivation in recordInteraction()**

Replace the hardcoded NEUTRAL quality:
```java
if (config.userModelEnabled() && userModel != null) {
    var signal = (impact != null && impact.userModelSignal() != null)
            ? impact.userModelSignal()
            : deriveQualitySignal(agentId, tenantId, userMessage);
    safeRun(() -> userModel.record(signal, agentId, subjectId, tenantId));
}
```

Add the derivation method:
```java
private InteractionSignal deriveQualitySignal(String agentId, String tenantId,
                                               String userMessage) {
    QualitySignal quality = QualitySignal.NEUTRAL;
    // Quality reflects mood trajectory (1-turn lag — see spec)
    var currentMood = mood.currentMood(agentId, tenantId);
    if (currentMood.isPresent()) {
        var m = currentMood.get();
        double pleasure = m.pleasure();
        double arousal = m.arousal();
        if (arousal > 0.3 && pleasure > 0.1) {
            quality = QualitySignal.POSITIVE;
        } else if (arousal < -0.1 && pleasure < -0.1) {
            quality = QualitySignal.NEGATIVE;
        }
    }
    return new InteractionSignal.CustomSignal(userMessage, quality);
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl blocks -Dtest=CognitionCoreTest --batch-mode`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add blocks/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java \
       blocks/src/test/java/io/casehub/blocks/agentic/social/CognitionCoreTest.java
git commit -m "feat(#279): derive UserModel quality from mood pleasure+arousal

Replace hardcoded QualitySignal.NEUTRAL with mood-derived quality:
high arousal + positive pleasure = POSITIVE (engaged),
low arousal + negative pleasure = NEGATIVE (bored).
1-turn lag documented — reads previous tick's mood state.

Refs #279"
```

---

## Batch 4: LLM proof tests

### Task 8: Per-orchestrator LLM proof tests

**Files:**
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/HistoricalEncounterLlmTest.java`
- Test: runs under `-Pllm` with `GOOGLE_API_KEY`

**Interfaces:**
- Consumes: All fixes from Batches 1-3
- Produces: 7 proof tests verifying each pipeline contributes to prompts

- [ ] **Step 1: Write per-orchestrator proof tests**

```java
@Test
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", disabledReason = "No LLM")
void strategyContributesByTurn4() {
    var result = runConversation(Stage.REAL_DRIVES, 4);
    var strategyContributed = result.metrics().stream()
            .anyMatch(m -> m.promptSectionContent().containsKey("StrategyPromptSection"));
    assertThat(strategyContributed)
            .as("StrategyPromptSection should contribute by turn 4")
            .isTrue();
}

@Test
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", disabledReason = "No LLM")
void moodEvolvesAcrossTurns() {
    var result = runConversation(Stage.SIGNALS, 6);
    var moodT1 = result.metrics().getFirst().promptSectionContent().get("MoodPromptSection");
    var moodT6 = result.metrics().getLast().promptSectionContent().get("MoodPromptSection");
    assertThat(moodT1).isNotNull();
    assertThat(moodT6).isNotNull();
    assertThat(moodT1).isNotEqualTo(moodT6);
}

@Test
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", disabledReason = "No LLM")
void mentalModelFormsBeliefsAndDesires() {
    var result = runConversation(Stage.SIGNALS, 4);
    var mmContributed = result.metrics().stream()
            .anyMatch(m -> {
                var content = m.promptSectionContent().get("MentalModelPromptSection");
                return content != null && (content.contains("believes") || content.contains("Beliefs"));
            });
    assertThat(mmContributed).isTrue();
}

@Test
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", disabledReason = "No LLM")
void drivesReflectRealSources() {
    var result = runConversation(Stage.REAL_DRIVES, 6);
    var driveContent = result.metrics().getLast().promptSectionContent().get("DrivePromptSection");
    assertThat(driveContent).isNotNull();
    // Verify curiosity is not stuck at exactly 0.5 baseline
    assertThat(driveContent).doesNotContain("0.50 (baseline)");
}

@Test
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", disabledReason = "No LLM")
void userModelDifferentiatesQuality() {
    var result = runConversation(Stage.SIGNALS, 6);
    var umContributed = result.metrics().stream()
            .anyMatch(m -> m.promptSectionContent().containsKey("UserModelPromptSection"));
    assertThat(umContributed).isTrue();
}

@Test
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", disabledReason = "No LLM")
void goalsProposedByTurn6() {
    var result = runConversation(Stage.FULL, 6);
    var goalsContributed = result.metrics().stream()
            .anyMatch(m -> m.promptSectionContent().containsKey("GoalPromptSection"));
    assertThat(goalsContributed)
            .as("GoalPromptSection should contribute by turn 6")
            .isTrue();
}

@Test
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", disabledReason = "No LLM")
void narrativeFormsEpisodesAndThemes() {
    var result = runConversation(Stage.NARRATIVE, 6);
    var narContributed = result.metrics().stream()
            .anyMatch(m -> {
                var content = m.promptSectionContent().get("NarrativePromptSection");
                return content != null && !content.isBlank();
            });
    assertThat(narContributed).isTrue();
}
```

Helper method:
```java
private ConversationRunner.ConversationResult runConversation(
        CognitionStack.Stage stage, int turns) {
    var stack = CognitionStack.from(compiledCognition, agentProvider, stage);
    return ConversationRunner.builder()
            .agentProvider(agentProvider)
            .cognition(stack)
            .world(compiledWorld)
            .descriptors(descriptors)
            .maxTurns(turns)
            .includeCognition(true)
            .build()
            .run();
}
```

- [ ] **Step 2: Run tests (requires GOOGLE_API_KEY)**

Run: `GOOGLE_API_KEY=<key> mvn test -pl agentic-yaml -Pllm -Dtest=HistoricalEncounterLlmTest --batch-mode`
Expected: PASS for all 7 tests

- [ ] **Step 3: Commit**

```bash
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/HistoricalEncounterLlmTest.java
git commit -m "feat(#279): per-orchestrator LLM proof tests

7 tests proving each cognitive pipeline contributes to prompts:
strategy by turn 4, mood evolves, mental model forms beliefs,
drives reflect real sources, user model differentiates quality,
goals proposed by turn 6, narrative forms episodes and themes.
Run under -Pllm with GOOGLE_API_KEY.

Refs #279"
```

---

## References

- [2026-09-15-cognitive-pipeline-health-design.md] — design spec this plan implements
- `StrategyLearningOrchestrator.java:40-606` — strategy orchestrator (record, tick, reflect)
- `CognitionCore.java:33-372` — orchestrator composition and signal dispatch
- `CognitionStack.java:59-513` — test-scope orchestrator wiring
- `ConversationRunner.java:22-208` — conversation loop
- `CognitiveImpact.java:6-17` — record to extend with conversationId
- `MemoryHygieneOrchestrator.java:50-73` — 11-param constructor
- `CuriosityDrive.java:13-15` — single-arg constructor from MemoryHygieneOrchestrator
- `CuriosityGoalMapper.java:12-14` — single-arg constructor from CuriosityDrive
- `GoalProposalConfig.java:24-32` — defaults() with 60-min cooldown
- `WeightedScorer.java:5-12` — wraps ConfidenceScorer with weight
- GitHub #279, #282, #283
