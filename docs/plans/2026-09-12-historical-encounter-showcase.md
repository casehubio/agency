# Historical Encounter Showcase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #258 — Historical encounter showcase — full eidos + neurocortex with Vertex LLM
**Issue group:** #258

**Goal:** Shared LLM test infrastructure in blocks (`agentic-yaml` test scope) that wires all YAML configs (pattern, cognition, world, descriptors, conversation) into a running conversation with real LLM calls and observable cognitive state changes.

**Architecture:** Maven profile `-Pllm` activates test-scope dependencies (eidos-runtime, langchain4j-google-ai-gemini, platform-agent-api, neurocortex-memory-api). Three shared test utilities — `TestAgentProvider` (thin LLM wrapper), `CognitionStack` (orchestrator builder from `CompiledCognition`), and `ConversationRunner` (end-to-end conversation loop) — wire the existing YAML compilers and blocks orchestrators into a runnable conversation. Tests gated by `@EnabledIfEnvironmentVariable("GOOGLE_API_KEY")`.

**Tech Stack:** Java 17, JUnit 5, langchain4j 1.14.1 (google-ai-gemini), eidos-runtime, blocks social cognition orchestrators, ConversationOrchestrator

## Global Constraints

- All new code goes in `agentic-yaml/src/test/java/` — no production code changes
- LLM dependencies are test-scope under `-Pllm` Maven profile only
- Default `mvn test` must pass without the profile (no new deps on default classpath)
- Tests guarded by `@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY")`
- Follow existing `ExampleTestBase` patterns — resource loading via `/examples/historical-encounter/`

---

## Batch 1: LLM Test Foundation

### Task 1: Maven profile and TestAgentProvider

**Files:**
- Modify: `agentic-yaml/pom.xml`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/TestAgentProvider.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/TestAgentProviderTest.java`

**Interfaces:**
- Produces: `TestAgentProvider` — implements `AgentProvider`, factory `google(String apiKey) → TestAgentProvider`. Used by CognitionStack (Task 2) and ConversationRunner (Task 4).

- [ ] **Step 1: Add Maven profile to agentic-yaml/pom.xml**

Add at the end of the `<project>` element, before `</project>`:

```xml
<profiles>
    <profile>
        <id>llm</id>
        <dependencies>
            <dependency>
                <groupId>io.casehub</groupId>
                <artifactId>casehub-eidos-runtime</artifactId>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>io.casehub</groupId>
                <artifactId>casehub-platform-agent-api</artifactId>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>io.casehub</groupId>
                <artifactId>casehub-neocortex-memory-api</artifactId>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-google-ai-gemini</artifactId>
                <version>1.14.1</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-core</artifactId>
                <version>1.14.1</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

- [ ] **Step 2: Write failing test for TestAgentProvider**

```java
package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY",
    disabledReason = "No LLM credentials")
class TestAgentProviderTest {

    @Test
    void invokeReturnsTextDelta() {
        var provider = TestAgentProvider.google(System.getenv("GOOGLE_API_KEY"));
        var config = AgentSessionConfig.of(
            "You are a helpful assistant.",
            "Say hello in exactly one word.",
            Duration.ofSeconds(30));

        String response = provider.invokeBlocking(config);

        assertThat(response).isNotBlank();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -Pllm -pl agentic-yaml -Dtest=TestAgentProviderTest -DfailIfNoTests=false`
Expected: FAIL with compilation error (TestAgentProvider not defined)

- [ ] **Step 4: Implement TestAgentProvider**

```java
package io.casehub.blocks.agentic.yaml.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class TestAgentProvider implements AgentProvider {

    private final ChatModel chatModel;

    TestAgentProvider(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public static TestAgentProvider google(String apiKey) {
        var model = GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName("gemini-2.0-flash")
            .build();
        return new TestAgentProvider(model);
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return Multi.createFrom().item(() -> {
            var request = ChatRequest.builder()
                .messages(List.of(
                    SystemMessage.from(config.systemPrompt()),
                    UserMessage.from(config.userPrompt())))
                .build();
            var response = chatModel.chat(request);
            String text = response.aiMessage().text();
            return (AgentEvent) new AgentEvent.TextDelta(text != null ? text : "");
        });
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        throw new UnsupportedOperationException(
            "TestAgentProvider supports invoke() only");
    }

    public String invokeBlocking(AgentSessionConfig config) {
        return invoke(config)
            .filter(e -> e instanceof AgentEvent.TextDelta)
            .map(e -> ((AgentEvent.TextDelta) e).text())
            .collect().with(Collectors.joining())
            .await().atMost(config.timeout() != null
                ? config.timeout() : Duration.ofSeconds(60));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Pllm -pl agentic-yaml -Dtest=TestAgentProviderTest`
Expected: PASS (requires GOOGLE_API_KEY env var)

- [ ] **Step 6: Verify default profile still works**

Run: `mvn test -pl agentic-yaml`
Expected: PASS — all existing tests pass, LLM tests skipped (class not on classpath)

- [ ] **Step 7: Commit**

```bash
git add agentic-yaml/pom.xml agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/
git commit -m "feat(#258): add -Pllm Maven profile + TestAgentProvider Refs #258"
```

---

## Batch 2: CognitionStack

### Task 2: CognitionStack orchestrator builder

Builds all orchestrators from `CompiledCognition` config records. Uses non-CDI constructors where available. Orchestrators that need heavy infrastructure (PersonalityEvolution, MemoryHygiene, NormDetection, CollectiveGoal) are optional — CognitionStack constructs what's feasible and exposes the rest as null-safe accessors.

**Files:**
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStack.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/CognitionStackTest.java`

**Interfaces:**
- Consumes: `TestAgentProvider` from Task 1, `CompiledCognition` from `CognitionCompiler.compile()`
- Produces: `CognitionStack` with `tick(agentId, tenantId, descriptor)`, `promptSections(agentId, tenantId)`, `mood()`, `drives()`, `userModel()`, `mentalModel()`, `narrative()`, `goals()`. Used by ConversationRunner (Task 4).

- [ ] **Step 1: Write failing test**

```java
package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.yaml.compiler.CognitionCompiler;
import io.casehub.blocks.agentic.yaml.examples.ExampleTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionStackTest extends ExampleTestBase {

    @Test
    void buildsFromCompiledCognition() throws IOException {
        var def = loadCognition("historical-encounter");
        var compiled = new CognitionCompiler().compile(def);
        var stack = CognitionStack.from(compiled, null);

        assertThat(stack.mood()).isNotNull();
        assertThat(stack.drives()).isNotNull();
        assertThat(stack.narrative()).isNotNull();
        assertThat(stack.goals()).isNotNull();
    }

    @Test
    void tickProducesState() throws IOException {
        var def = loadCognition("historical-encounter");
        var compiled = new CognitionCompiler().compile(def);
        var stack = CognitionStack.from(compiled, null);

        stack.tick("leonardo", "test-tenant", null);

        assertThat(stack.mood().currentMood("leonardo", "test-tenant"))
            .isPresent();
        assertThat(stack.drives().currentDrives("leonardo", "test-tenant"))
            .isPresent();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl agentic-yaml -Dtest=CognitionStackTest`
Expected: FAIL — CognitionStack class not found

- [ ] **Step 3: Implement CognitionStack**

```java
package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveComposer;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveSource;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.agentic.social.prompt.PromptSection;
import io.casehub.blocks.agentic.social.prompt.MoodPromptSection;
import io.casehub.blocks.agentic.social.prompt.DrivePromptSection;
import io.casehub.blocks.agentic.social.prompt.MentalModelPromptSection;
import io.casehub.blocks.agentic.social.prompt.UserModelPromptSection;
import io.casehub.blocks.agentic.social.prompt.StrategyPromptSection;
import io.casehub.blocks.agentic.social.prompt.NarrativePromptSection;
import io.casehub.blocks.agentic.yaml.compiler.CompiledCognition;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CognitionStack {

    private final MoodOrchestrator mood;
    private final DriveOrchestrator drives;
    private final @Nullable UserModelOrchestrator userModel;
    private final @Nullable MentalModelOrchestrator mentalModel;
    private final NarrativeOrchestrator narrative;
    private final @Nullable GoalProposalOrchestrator goals;
    private final @Nullable StrategyLearningOrchestrator strategy;

    CognitionStack(MoodOrchestrator mood,
                   DriveOrchestrator drives,
                   @Nullable UserModelOrchestrator userModel,
                   @Nullable MentalModelOrchestrator mentalModel,
                   NarrativeOrchestrator narrative,
                   @Nullable GoalProposalOrchestrator goals,
                   @Nullable StrategyLearningOrchestrator strategy) {
        this.mood = mood;
        this.drives = drives;
        this.userModel = userModel;
        this.mentalModel = mentalModel;
        this.narrative = narrative;
        this.goals = goals;
        this.strategy = strategy;
    }

    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider agentProvider) {
        var mood = new MoodOrchestrator(config.mood());
        var narrativeStore = new io.casehub.blocks.agentic.social.narrative.CbrNarrativeStore();
        var narrative = new NarrativeOrchestrator(narrativeStore);

        // Drive sources — use simple no-op sources when AgentProvider absent
        DriveSource noOp = (agentId, tenantId) ->
            new io.casehub.blocks.agentic.social.drive.DriveIntensity(
                io.casehub.blocks.agentic.social.drive.DriveAxis.CURIOSITY,
                0.5, "baseline");
        var composer = new DriveComposer();
        var drives = new DriveOrchestrator(
            noOp, noOp, noOp, noOp, mood, composer, config.drive());

        UserModelOrchestrator userModel = null;
        MentalModelOrchestrator mentalModel = null;
        StrategyLearningOrchestrator strategy = null;

        if (agentProvider != null) {
            var userProfileStore = new io.casehub.blocks.agentic.social.CbrUserProfileStore();
            userModel = new UserModelOrchestrator(userProfileStore, agentProvider, config.userModel());

            var mentalModelStore = new io.casehub.blocks.agentic.social.CbrMentalModelStore();
            mentalModel = new MentalModelOrchestrator(mentalModelStore, agentProvider, config.mentalModel());
        }

        // GoalProposal — use test constructor with heuristic mappers
        var mappers = List.<io.casehub.blocks.agentic.social.goal.DriveGoalMapper>of(
            new io.casehub.blocks.agentic.social.goal.CuriosityGoalMapper(drives),
            new io.casehub.blocks.agentic.social.goal.CompetenceGoalMapper(drives),
            new io.casehub.blocks.agentic.social.goal.AffiliationGoalMapper(drives),
            new io.casehub.blocks.agentic.social.goal.AutonomyGoalMapper(drives));
        GoalProposalOrchestrator goals = null;
        try {
            goals = new GoalProposalOrchestrator(
                drives, mappers, null, config.goalProposal(), java.time.Clock.systemUTC());
        } catch (Exception ignored) {}

        return new CognitionStack(mood, drives, userModel, mentalModel,
            narrative, goals, strategy);
    }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor) {
        mood.tick(agentId, tenantId);
        narrative.tick(agentId, tenantId);
        if (descriptor != null) {
            drives.tick(agentId, tenantId, descriptor);
            if (goals != null) goals.tick(agentId, tenantId, descriptor);
        }
        if (userModel != null) {
            // UserModel ticks per-subject — driven by conversation signals
        }
        if (mentalModel != null) {
            // MentalModel ticks per-subject — driven by conversation signals
        }
    }

    public List<PromptSection> promptSections(String agentId, String tenantId) {
        var sections = new ArrayList<PromptSection>();
        sections.add(new MoodPromptSection(mood, agentId, tenantId));
        sections.add(new DrivePromptSection(drives, agentId, tenantId));
        sections.add(new NarrativePromptSection(narrative, agentId, tenantId));
        if (userModel != null)
            sections.add(new UserModelPromptSection(userModel, agentId, tenantId));
        if (mentalModel != null)
            sections.add(new MentalModelPromptSection(mentalModel, agentId, tenantId));
        if (strategy != null)
            sections.add(new StrategyPromptSection(strategy, agentId, tenantId));
        return sections;
    }

    public MoodOrchestrator mood() { return mood; }
    public DriveOrchestrator drives() { return drives; }
    public NarrativeOrchestrator narrative() { return narrative; }
    public @Nullable UserModelOrchestrator userModel() { return userModel; }
    public @Nullable MentalModelOrchestrator mentalModel() { return mentalModel; }
    public @Nullable GoalProposalOrchestrator goals() { return goals; }
}
```

Note: The exact prompt section constructors may differ — verify signatures
via `ide_file_structure` on each `*PromptSection.java` before implementing.
The code above shows the intent; adjust constructor args to match actual signatures.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl agentic-yaml -Dtest=CognitionStackTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/
git commit -m "feat(#258): add CognitionStack orchestrator builder Refs #258"
```

---

### Task 3: Eidos descriptor loading test utility

Load `descriptors.yaml` through eidos-runtime, resolve MBTI types, render system prompts.

**Files:**
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/DescriptorLoader.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/DescriptorLoaderTest.java`

**Interfaces:**
- Consumes: `ClasspathYamlDescriptorRegistrar.loadFrom()` from eidos-runtime, `SystemPromptRenderer` from eidos-api
- Produces: `DescriptorLoader.load(scenario) → List<AgentDescriptor>`, `DescriptorLoader.renderSystemPrompt(descriptor) → String`. Used by ConversationRunner (Task 4).

- [ ] **Step 1: Write failing test**

```java
package io.casehub.blocks.agentic.yaml.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptorLoaderTest {

    @Test
    void loadsHistoricalEncounterDescriptors() {
        var descriptors = DescriptorLoader.load("historical-encounter");

        assertThat(descriptors).hasSize(3);
        assertThat(descriptors.get(0).name()).isEqualTo("leonardo");
        assertThat(descriptors.get(1).name()).isEqualTo("nikola");
        assertThat(descriptors.get(2).name()).isEqualTo("historian-narrator");
    }

    @Test
    void rendersSystemPrompts() {
        var descriptors = DescriptorLoader.load("historical-encounter");
        var prompt = DescriptorLoader.renderSystemPrompt(descriptors.get(0));

        assertThat(prompt).contains("Leonardo da Vinci");
        assertThat(prompt).isNotBlank();
    }

    @Test
    void resolvesDisposition() {
        var descriptors = DescriptorLoader.load("historical-encounter");
        var leonardo = descriptors.get(0);

        assertThat(leonardo.disposition()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Pllm -pl agentic-yaml -Dtest=DescriptorLoaderTest`
Expected: FAIL — DescriptorLoader class not found

- [ ] **Step 3: Implement DescriptorLoader**

```java
package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentPromptContext;
import io.casehub.eidos.api.SystemPromptRenderer;
import io.casehub.eidos.api.VocabularyRegistry;
import io.casehub.eidos.runtime.registrar.ClasspathYamlDescriptorRegistrar;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class DescriptorLoader {

    public static List<AgentDescriptor> load(String scenario) {
        String path = "/examples/" + scenario + "/descriptors.yaml";
        try (InputStream is = DescriptorLoader.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalArgumentException(
                "Descriptors not found: " + path);
            var registrar = new ClasspathYamlDescriptorRegistrar();
            VocabularyRegistry vocabRegistry = VocabularyRegistry.defaults();
            return registrar.loadFrom(is, vocabRegistry);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + path, e);
        }
    }

    public static String renderSystemPrompt(AgentDescriptor descriptor) {
        var renderer = SystemPromptRenderer.markdown();
        var context = AgentPromptContext.empty();
        var rendered = renderer.render(descriptor, context);
        return rendered.content();
    }
}
```

Note: The exact factory methods on `VocabularyRegistry` and
`SystemPromptRenderer` may differ — verify via `ide_find_class` and
`ide_file_structure` before implementing. The code above shows intent.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Pllm -pl agentic-yaml -Dtest=DescriptorLoaderTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/
git commit -m "feat(#258): add DescriptorLoader for eidos YAML loading Refs #258"
```

---

## Batch 3: Conversation Runner and LLM Tests

### Task 4: ConversationRunner and InMemoryMessageStore

Wires `ConversationOrchestrator` with `CognitionStack`, world observation, and eidos descriptors. Drives the full conversation loop.

**Files:**
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/ConversationRunner.java`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/InMemoryMessageStore.java`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/ConversationPrinter.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/ConversationRunnerTest.java`

**Interfaces:**
- Consumes: `TestAgentProvider` (Task 1), `CognitionStack` (Task 2), `DescriptorLoader` (Task 3), `ConversationOrchestrator`, `CompiledWorld`, `AffordanceRenderer`
- Produces: `ConversationRunner.builder()...build()` → `ConversationOutcome run()`. `ConversationPrinter` logs each turn's dialogue and cognitive state to stdout.

- [ ] **Step 1: Write failing test for InMemoryMessageStore**

```java
package io.casehub.blocks.agentic.yaml.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRunnerTest {

    @Test
    void inMemoryMessageStoreAccumulatesMessages() {
        var store = new InMemoryMessageStore();

        store.dispatch("agent-1", "Hello from agent 1");
        store.dispatch("agent-2", "Hello from agent 2");

        assertThat(store.messages()).hasSize(2);
        assertThat(store.messages().get(0).body()).contains("Hello from agent 1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl agentic-yaml -Dtest=ConversationRunnerTest`
Expected: FAIL — InMemoryMessageStore not found

- [ ] **Step 3: Implement InMemoryMessageStore**

A lightweight in-memory message store implementing the `MessageView`
and dispatch interfaces that `ConversationOrchestrator` expects. Stores
messages in a `CopyOnWriteArrayList`. Each message is a record
implementing `MessageView` with the fields the orchestrator reads:
`id`, `senderId`, `body`, `timestamp`, `messageType`.

```java
package io.casehub.blocks.agentic.yaml.llm;

// Implementation: CopyOnWriteArrayList<SimpleMessage> with
// dispatch(senderId, body) adding entries. messages() returns
// unmodifiable view. SimpleMessage record implements MessageView.
// Exact MessageView interface fields verified via ide_file_structure.
```

- [ ] **Step 4: Implement ConversationPrinter**

A `ConversationListener` that prints each turn's dialogue and cognitive
state to stdout in the format described in the spec:

```
=== Turn N — agentId ===
[dialogue] "text..."
[drives] CURIOSITY: 0.82 (+0.04)  COMPETENCE: 0.61 ...
[mood] pleasure: 0.72 (+0.05)  arousal: 0.58  dominance: 0.52
[convergence] STATE (confidence) — N established, N pending, N disputed
```

Reads state from `CognitionStack` after each dispatch.

- [ ] **Step 5: Implement ConversationRunner builder**

```java
package io.casehub.blocks.agentic.yaml.llm;

// ConversationRunner.builder()
//   .agentProvider(testAgentProvider)
//   .cognition(cognitionStack)
//   .world(compiledWorld)
//   .descriptors(descriptors)
//   .turnPolicy(roundRobinTurnPolicy)
//   .terminationCondition(compositeTermination)
//   .maxTurns(16)  // safety cap
//   .build();
//
// run() method:
// 1. Creates AgentParticipant per descriptor (systemPrompt from DescriptorLoader)
// 2. Creates PromptAssembler composing:
//    - Base system prompt from descriptor
//    - Cognition prompt sections from CognitionStack
//    - World observation from AffordanceRenderer
// 3. Creates AgentInvoker wrapping TestAgentProvider
// 4. Creates ConversationOrchestrator with all components
// 5. Creates initial MessageView to trigger the conversation
// 6. Calls orchestrator.converse() and returns outcome
// 7. ConversationPrinter logs each turn
```

- [ ] **Step 6: Run test to verify InMemoryMessageStore passes**

Run: `mvn test -pl agentic-yaml -Dtest=ConversationRunnerTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/llm/
git commit -m "feat(#258): add ConversationRunner + InMemoryMessageStore Refs #258"
```

---

### Task 5: Historical encounter LLM integration test

End-to-end test loading all 6 YAML files, wiring the full stack, and
running a conversation with a real LLM.

**Files:**
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/HistoricalEncounterLlmTest.java`

**Interfaces:**
- Consumes: `TestAgentProvider` (Task 1), `CognitionStack` (Task 2), `DescriptorLoader` (Task 3), `ConversationRunner` (Task 4), all existing compilers from `ExampleTestBase`

- [ ] **Step 1: Write the integration test**

```java
package io.casehub.blocks.agentic.yaml.examples;

import io.casehub.blocks.agentic.yaml.compiler.CognitionCompiler;
import io.casehub.blocks.agentic.yaml.llm.CognitionStack;
import io.casehub.blocks.agentic.yaml.llm.ConversationRunner;
import io.casehub.blocks.agentic.yaml.llm.DescriptorLoader;
import io.casehub.blocks.agentic.yaml.llm.TestAgentProvider;
import io.casehub.blocks.agentic.yaml.spec.ConversationSpec;
import io.casehub.blocks.agentic.yaml.registry.TurnPolicyRegistry;
import io.casehub.blocks.agentic.yaml.registry.EpistemicRuleRegistry;
import io.casehub.blocks.agentic.yaml.registry.ConvergencePolicyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY",
    disabledReason = "No LLM credentials")
class HistoricalEncounterLlmTest extends ExampleTestBase {

    private static final String SCENARIO = "historical-encounter";
    private TestAgentProvider agentProvider;

    @BeforeEach
    void setUpLlm() {
        agentProvider = TestAgentProvider.google(System.getenv("GOOGLE_API_KEY"));
    }

    @Test
    void descriptorsLoadAndRenderSystemPrompts() {
        var descriptors = DescriptorLoader.load(SCENARIO);

        assertThat(descriptors).hasSize(3);

        for (var d : descriptors) {
            String prompt = DescriptorLoader.renderSystemPrompt(d);
            System.out.println("=== " + d.name() + " system prompt ===");
            System.out.println(prompt.substring(0, Math.min(200, prompt.length())) + "...");
            System.out.println();
            assertThat(prompt).isNotBlank();
        }
    }

    @Test
    void cognitionStackTicksProduceState() throws IOException {
        var def = loadCognition(SCENARIO);
        var compiled = new CognitionCompiler().compile(def);
        var stack = CognitionStack.from(compiled, agentProvider);
        var descriptors = DescriptorLoader.load(SCENARIO);

        stack.tick("leonardo", "test", descriptors.get(0));
        stack.tick("nikola", "test", descriptors.get(1));

        assertThat(stack.mood().currentMood("leonardo", "test")).isPresent();
        assertThat(stack.drives().currentDrives("leonardo", "test")).isPresent();
        assertThat(stack.mood().currentMood("nikola", "test")).isPresent();
    }

    @Test
    void fullConversationRuns() throws IOException {
        var patternSpec = loadPattern(SCENARIO);
        var cogDef = loadCognition(SCENARIO);
        var compiled = new CognitionCompiler().compile(cogDef);
        var worldDef = loadWorld(SCENARIO);
        var compiledWorld = worldCompiler.compile(worldDef);
        var convSpec = load(SCENARIO, "conversation.yaml", ConversationSpec.class);
        var descriptors = DescriptorLoader.load(SCENARIO);
        var stack = CognitionStack.from(compiled, agentProvider);

        var turnPolicy = new TurnPolicyRegistry().resolve(convSpec.turnPolicy());
        var epistemicRule = new EpistemicRuleRegistry().resolve(convSpec.epistemicRule());
        var convergencePolicy = new ConvergencePolicyRegistry().resolve(
            convSpec.convergencePolicy());

        var outcome = ConversationRunner.builder()
            .agentProvider(agentProvider)
            .cognition(stack)
            .world(compiledWorld)
            .descriptors(descriptors)
            .turnPolicy(turnPolicy)
            .maxTurns(6)  // keep short for test cost
            .build()
            .run();

        assertThat(outcome).isNotNull();
        assertThat(outcome.dispatchCount()).isGreaterThan(0);

        System.out.println("=== Conversation complete ===");
        System.out.println("Dispatches: " + outcome.dispatchCount());
        System.out.println("Elapsed: " + outcome.elapsed());
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn test -Pllm -pl agentic-yaml -Dtest=HistoricalEncounterLlmTest`
Expected: PASS (requires GOOGLE_API_KEY)

- [ ] **Step 3: Verify all existing tests still pass**

Run: `mvn test -pl agentic-yaml`
Expected: PASS — all compilation tests pass, LLM tests skipped

- [ ] **Step 4: Commit**

```bash
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/HistoricalEncounterLlmTest.java
git commit -m "feat(#258): add HistoricalEncounterLlmTest — full stack LLM test Refs #258"
```

---

## References

- `specs/issue-258-historical-encounter-showcase/2026-09-12-historical-encounter-showcase-design.md` — design spec
- `agentic-yaml/src/test/resources/examples/historical-encounter/` — 6 YAML fixtures from #257
- `agentic-yaml/src/test/java/.../examples/ExampleTestBase.java` — shared test infrastructure
- `agentic-yaml/src/test/java/.../examples/HistoricalEncounterExampleTest.java` — existing compilation tests
- `src/main/java/.../conversation/orchestration/ConversationOrchestrator.java` — conversation loop (10 constructor args)
- `src/main/java/.../agentic/social/prompt/SocialPromptAssembler.java` — cognition prompt composition
- `src/main/java/.../agentic/social/prompt/SocialAvatarCognition.java` — CDI reference for orchestrator wiring
- `src/main/java/.../agentic/social/drive/DriveOrchestrator.java` — non-CDI constructor at line 67
- `src/main/java/.../agentic/social/goal/GoalProposalOrchestrator.java` — test constructor at line 71
- `eidos/runtime/.../ClasspathYamlDescriptorRegistrar.java` — descriptor loading
- `eidos/api/.../SystemPromptRenderer.java` — system prompt rendering
- `platform/agent-api/.../AgentProvider.java` — LLM invocation SPI
- `casehub/examples/wacky-manor/` — reference Quarkus app
- GitHub #258
