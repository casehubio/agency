# Coordinator Prompt Tuning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #362 — feat: coordinator prompt tuning for swarm coordination
**Issue group:** #362

**Goal:** Wire the three swarm coordinator prompt sites (team formation,
collective reasoning, provisioning) into the existing prompt optimisation
framework for systematic A/B testing and improvement.

**Architecture:** Decorator-based outcome recording on the termination
condition intercepts convergence decisions. The builder selects a single
variant slot per coordinator build and propagates it to all three prompt
sites. Evolution loop types follow the existing `PromptOptimisation*`
pattern.

**Tech Stack:** Java 17, JUnit 5, Mockito, AssertJ

## Global Constraints

- All new classes in `io.casehub.agency.orchestration.swarm.prompt`
  unless modifying existing classes
- New test classes in `io.casehub.agency.orchestration.swarm.prompt`
  test package
- Follow existing naming conventions: `Swarm*` prefix for swarm-specific
  types
- `@Nullable` annotations from `org.jspecify.annotations`
- No CDI container in tests — plain JUnit 5 + Mockito

---

## Batch 1: Foundation — hook existing classes

### Task 1: Add SystemPromptCustomiser to SwarmRoutingStrategy

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmRoutingStrategy.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmRoutingStrategyTest.java`

**Interfaces:**
- Consumes: `SystemPromptCustomiser.customise(String, String, String)`, `SwarmPromptSection.contribute(SwarmContext)`
- Produces: `SwarmRoutingStrategy(AgentProvider, SwarmExperienceSection, int, @Nullable SwarmInvocationTracker, @Nullable SystemPromptCustomiser, List<SwarmPromptSection>)`, `setVariantSlot(@Nullable String)`

- [ ] **Step 1: Write the failing test — customiser applied to prompt**

```java
@Test
void route_appliesSystemPromptCustomiser() {
    var customiser = mock(SystemPromptCustomiser.class);
    when(customiser.customise(any(), eq(SwarmPromptSignatures.TEAM_FORMATION.id()), eq("control")))
            .thenAnswer(inv -> inv.getArgument(0) + "\n## Optimised Instructions\nUse diversity.");

    var strategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 12, null, customiser, List.of());
    strategy.setVariantSlot("control");

    mockLlmResponse("""
            {"agents": ["analyst"], "teams": {"t1": ["analyst"]}, "strategy": "solo"}
            """);

    var context = routingContext(List.of(candidate("analyst")));
    strategy.route(context);

    var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
    verify(agentProvider).invoke(configCaptor.capture());
    assertThat(configCaptor.getValue().systemPrompt()).contains("Optimised Instructions");
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmRoutingStrategyTest#route_appliesSystemPromptCustomiser`
Expected: compilation failure (constructor doesn't exist yet)

- [ ] **Step 3: Write the failing test — additional sections contribute to prompt**

```java
@Test
void route_includesAdditionalSections() {
    SwarmPromptSection section = ctx -> "## Few-Shot Examples\n1. Previous team: [a, b]";
    var strategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 12, null, null, List.of(section));

    mockLlmResponse("""
            {"agents": ["analyst"], "teams": {"t1": ["analyst"]}, "strategy": "solo"}
            """);

    var context = routingContext(List.of(candidate("analyst")));
    strategy.route(context);

    var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
    verify(agentProvider).invoke(configCaptor.capture());
    assertThat(configCaptor.getValue().systemPrompt()).contains("Few-Shot Examples");
}
```

- [ ] **Step 4: Implement SwarmRoutingStrategy changes**

Add to `SwarmRoutingStrategy`:
- New fields: `@Nullable SystemPromptCustomiser promptCustomiser`, `List<SwarmPromptSection> additionalSections`, `volatile @Nullable String variantSlot`
- New 6-arg constructor: `(AgentProvider, SwarmExperienceSection, int, @Nullable SwarmInvocationTracker, @Nullable SystemPromptCustomiser, List<SwarmPromptSection>)`
- `setVariantSlot(@Nullable String)` method
- In `route()`, between `buildPrompt()` (line 63) and `AgentSessionConfig.of()` (line 67):
  - Append additional sections contributions to prompt
  - Apply customiser if present: `prompt = promptCustomiser.customise(prompt, TEAM_FORMATION.id(), variantSlot)`
- Update existing 4-arg and 3-arg constructors to delegate with `null` customiser and `List.of()` sections

- [ ] **Step 5: Run tests — verify both new tests pass**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmRoutingStrategyTest`
Expected: all tests PASS (existing tests use old constructors which delegate)

- [ ] **Step 6: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmRoutingStrategy.java agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmRoutingStrategyTest.java
git commit -m "feat(#362): add SystemPromptCustomiser hook to SwarmRoutingStrategy"
```

### Task 2: Widen LlmSwarmCoordinator to accept TerminationCondition interface

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/LlmSwarmCoordinator.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/LlmSwarmCoordinatorTest.java`

**Interfaces:**
- Consumes: `TerminationCondition<SwarmContext>` (interface)
- Produces: `LlmSwarmCoordinator` field/accessor widened from `SwarmConvergenceTermination` to `TerminationCondition<SwarmContext>`, accessor renamed `terminationCondition()`

- [ ] **Step 1: Write failing test — accepts TerminationCondition**

```java
@Test
void acceptsTerminationConditionInterface() {
    TerminationCondition<SwarmContext> customTermination = ctx -> TerminationDecision.Continue.INSTANCE;
    var coordinator = new LlmSwarmCoordinator(
            agentProvider, new SwarmContext(), routingStrategy, aggregation,
            customTermination, eventSource, tracker, fallback, List.of());
    assertThat(coordinator.terminationCondition()).isSameAs(customTermination);
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=LlmSwarmCoordinatorTest#acceptsTerminationConditionInterface`
Expected: compilation failure (field is `SwarmConvergenceTermination`, accessor is `convergenceTermination()`)

- [ ] **Step 3: Implement LlmSwarmCoordinator changes**

- Change field type: `private final SwarmConvergenceTermination convergenceTermination` → `private final TerminationCondition<SwarmContext> terminationCondition`
- Change constructor parameter type accordingly
- Rename accessor: `convergenceTermination()` → `terminationCondition()`
- Add import for `TerminationCondition`

- [ ] **Step 4: Fix any references to old accessor**

Use `ide_find_references` on the old `convergenceTermination()` accessor to find callers. Update them to use `terminationCondition()`.

- [ ] **Step 5: Run all swarm tests**

Run: `mvn --batch-mode test -pl agency -Dtest="io.casehub.agency.orchestration.swarm.*"`
Expected: all PASS

- [ ] **Step 6: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/LlmSwarmCoordinator.java agency/src/test/java/io/casehub/agency/orchestration/swarm/LlmSwarmCoordinatorTest.java
git commit -m "feat(#362): widen LlmSwarmCoordinator to TerminationCondition interface"
```

### Task 3: Add setVariantSlot to SwarmOptimisedFewShotSection

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmOptimisedFewShotSection.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmOptimisedFewShotSectionTest.java`

**Interfaces:**
- Consumes: `PromptVariantStore.getActive(String signatureId, String slot)`
- Produces: `setVariantSlot(@Nullable String variantSlot)` — when set, `contribute()` uses the provided slot directly instead of calling `selector.selectSlot()`

- [ ] **Step 1: Write failing test — setVariantSlot bypasses selector**

```java
@Test
void usesProvidedVariantSlot_insteadOfSelector() {
    var examples = List.of(
            new FewShotExample("input", "output", "CONVERGED", 0.9, null));
    var variant = new PromptVariant(
            SwarmPromptSignatures.TEAM_FORMATION.id(), "v1",
            examples, null, 0.8, Instant.now(), null, 0);
    when(store.getActive(SwarmPromptSignatures.TEAM_FORMATION.id(), "experiment")).thenReturn(variant);

    var section = new SwarmOptimisedFewShotSection(store, selector);
    section.setVariantSlot("experiment");
    var result = section.contribute(context);

    assertThat(result).contains("input");
    verify(selector, never()).selectSlot(any(), any());
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmOptimisedFewShotSectionTest#usesProvidedVariantSlot_insteadOfSelector`
Expected: compilation failure (`setVariantSlot` doesn't exist)

- [ ] **Step 3: Implement setVariantSlot**

Add to `SwarmOptimisedFewShotSection`:
- New field: `private volatile @Nullable String variantSlot`
- New method: `public void setVariantSlot(@Nullable String variantSlot)`
- In `contribute()`, change slot resolution:
  ```java
  var slot = this.variantSlot != null
      ? this.variantSlot
      : selector.selectSlot(executionId(context), signatureId);
  ```

- [ ] **Step 4: Run all few-shot section tests**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmOptimisedFewShotSectionTest`
Expected: all PASS

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmOptimisedFewShotSection.java agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmOptimisedFewShotSectionTest.java
git commit -m "feat(#362): add setVariantSlot to SwarmOptimisedFewShotSection"
```

## Batch 2: Outcome recording + builder wiring

### Task 4: SwarmOutcomeRecorder — termination decorator

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmOutcomeRecorder.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmOutcomeRecorderTest.java`

**Interfaces:**
- Consumes: `TerminationCondition<SwarmContext>` (delegate), `SwarmOutcomeMapper.map(String, String, double, Duration)`, `PromptVariantStore`
- Produces: `SwarmOutcomeRecorder implements TerminationCondition<SwarmContext>` with `evaluate(TerminationContext<SwarmContext>)`, `outcomes(String signatureId) → List<VariantOutcome>`

- [ ] **Step 1: Write failing test — records outcome on terminal decision**

```java
@Test
void recordsOutcome_onCompleteDecision() {
    var delegate = mock(TerminationCondition.class);
    var store = mock(PromptVariantStore.class);
    when(delegate.evaluate(any())).thenReturn(
            new TerminationDecision.Complete("Swarm converged — consensus: 0.90, stability: 0.85"));

    var recorder = new SwarmOutcomeRecorder(delegate, store, "control");

    var context = new SwarmContext();
    context.updateProgress(new SwarmProgress(0.9, 0.85, 0.5));
    var termCtx = TerminationContext.of(context, 5);

    var decision = recorder.evaluate(termCtx);

    assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
    assertThat(recorder.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).hasSize(1);
    assertThat(recorder.outcomes(SwarmPromptSignatures.COLLECTIVE_REASONING.id())).hasSize(1);
    assertThat(recorder.outcomes(SwarmPromptSignatures.PROVISIONING.id())).hasSize(1);
}
```

- [ ] **Step 2: Write failing test — does not record on Continue**

```java
@Test
void doesNotRecord_onContinueDecision() {
    var delegate = mock(TerminationCondition.class);
    var store = mock(PromptVariantStore.class);
    when(delegate.evaluate(any())).thenReturn(TerminationDecision.Continue.INSTANCE);

    var recorder = new SwarmOutcomeRecorder(delegate, store, "control");
    var termCtx = TerminationContext.of(new SwarmContext(), 1);

    recorder.evaluate(termCtx);

    assertThat(recorder.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).isEmpty();
}
```

- [ ] **Step 3: Write failing test — records only once**

```java
@Test
void recordsOnlyOnce_evenIfEvaluatedMultipleTimes() {
    var delegate = mock(TerminationCondition.class);
    var store = mock(PromptVariantStore.class);
    when(delegate.evaluate(any())).thenReturn(
            new TerminationDecision.Complete("converged"));

    var recorder = new SwarmOutcomeRecorder(delegate, store, "control");
    var context = new SwarmContext();
    context.updateProgress(new SwarmProgress(0.9, 0.85, 0.5));
    var termCtx = TerminationContext.of(context, 5);

    recorder.evaluate(termCtx);
    recorder.evaluate(termCtx);

    assertThat(recorder.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).hasSize(1);
}
```

- [ ] **Step 4: Implement SwarmOutcomeRecorder**

```java
package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.PromptVariantStore;
import io.casehub.agency.optimisation.prompt.VariantOutcome;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SwarmOutcomeRecorder implements TerminationCondition<SwarmContext> {

    private final TerminationCondition<SwarmContext> delegate;
    private final String variantSlot;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<VariantOutcome>> outcomes = new ConcurrentHashMap<>();
    private final AtomicBoolean recorded = new AtomicBoolean(false);

    public SwarmOutcomeRecorder(TerminationCondition<SwarmContext> delegate,
                                PromptVariantStore store,
                                String variantSlot) {
        this.delegate = delegate;
        this.variantSlot = variantSlot;
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<SwarmContext> context) {
        var decision = delegate.evaluate(context);
        if (isTerminal(decision) && recorded.compareAndSet(false, true)) {
            recordOutcomes(context.state(), decision);
        }
        return decision;
    }

    public List<VariantOutcome> outcomes(String signatureId) {
        return List.copyOf(outcomes.getOrDefault(signatureId, new CopyOnWriteArrayList<>()));
    }

    private boolean isTerminal(TerminationDecision decision) {
        return decision instanceof TerminationDecision.Complete
                || decision instanceof TerminationDecision.Failed
                || decision instanceof TerminationDecision.Escalate;
    }

    private void recordOutcomes(SwarmContext state, TerminationDecision decision) {
        var progress = state.swarmProgress();
        double similarity = progress != null ? progress.consensusScore() : 0.0;
        String outcome = extractOutcome(decision);

        for (var sig : List.of(
                SwarmPromptSignatures.TEAM_FORMATION,
                SwarmPromptSignatures.COLLECTIVE_REASONING,
                SwarmPromptSignatures.PROVISIONING)) {
            var mapper = new SwarmOutcomeMapper(sig.id());
            var mapped = mapper.map(variantSlot, outcome, similarity, null);
            outcomes.computeIfAbsent(sig.id(), k -> new CopyOnWriteArrayList<>()).add(mapped);
        }
    }

    private String extractOutcome(TerminationDecision decision) {
        if (decision instanceof TerminationDecision.Complete c) {
            return c.reason().contains("converged") ? "CONVERGED" : "COMPLETED";
        }
        if (decision instanceof TerminationDecision.Escalate) return "DEADLOCK";
        return "FAILED";
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmOutcomeRecorderTest`
Expected: all PASS

- [ ] **Step 6: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmOutcomeRecorder.java agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmOutcomeRecorderTest.java
git commit -m "feat(#362): SwarmOutcomeRecorder — termination decorator for outcome recording"
```

### Task 5: SwarmPromptOutcomeProvider — CDI aggregation

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptOutcomeProvider.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptOutcomeProviderTest.java`

**Interfaces:**
- Consumes: `SwarmOutcomeRecorder.outcomes(String signatureId)`
- Produces: `SwarmPromptOutcomeProvider implements PromptOutcomeProvider` with `register(SwarmOutcomeRecorder)`, `outcomes(String signatureId) → List<VariantOutcome>`

- [ ] **Step 1: Write failing test — aggregates from multiple recorders**

```java
@Test
void aggregatesOutcomesFromMultipleRecorders() {
    var provider = new SwarmPromptOutcomeProvider();

    var recorder1 = recorderWithOutcome("CONVERGED");
    var recorder2 = recorderWithOutcome("DEADLOCK");
    provider.register(recorder1);
    provider.register(recorder2);

    var outcomes = provider.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id());
    assertThat(outcomes).hasSize(2);
}
```

- [ ] **Step 2: Write failing test — empty when no recorders**

```java
@Test
void returnsEmpty_whenNoRecorders() {
    var provider = new SwarmPromptOutcomeProvider();
    assertThat(provider.outcomes(SwarmPromptSignatures.TEAM_FORMATION.id())).isEmpty();
}
```

- [ ] **Step 3: Implement SwarmPromptOutcomeProvider**

```java
package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.VariantOutcome;
import io.casehub.agency.optimisation.prompt.evolution.PromptOutcomeProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@ApplicationScoped
public class SwarmPromptOutcomeProvider implements PromptOutcomeProvider {

    private final CopyOnWriteArrayList<SwarmOutcomeRecorder> recorders = new CopyOnWriteArrayList<>();

    public void register(SwarmOutcomeRecorder recorder) {
        recorders.add(recorder);
    }

    @Override
    public List<VariantOutcome> outcomes(String signatureId) {
        return recorders.stream()
                .flatMap(r -> r.outcomes(signatureId).stream())
                .collect(Collectors.toUnmodifiableList());
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmPromptOutcomeProviderTest`
Expected: all PASS

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptOutcomeProvider.java agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptOutcomeProviderTest.java
git commit -m "feat(#362): SwarmPromptOutcomeProvider — CDI aggregation for batch cycle"
```

### Task 6: Wire SwarmCoordinatorBuilder

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmCoordinatorBuilder.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/CollectiveReasoningAggregation.java` (may need to verify `setVariantSlot` exists)
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmCoordinatorBuilderTest.java` (create if not exists)

**Interfaces:**
- Consumes: `SwarmRoutingStrategy` (new 6-arg constructor), `LlmSwarmCoordinator` (widened type), `VariantSelector.selectSlot(UUID, String)`, `SwarmOutcomeRecorder`, `SwarmPromptOutcomeProvider.register()`
- Produces: `variantSelector(VariantSelector)`, `outcomeProvider(SwarmPromptOutcomeProvider)` builder methods; `build()` wires variant slot and outcome recorder to all sites

- [ ] **Step 1: Write failing test — builder passes customiser to routing strategy**

```java
@Test
void build_passesCustomiserToRoutingStrategy() {
    var customiser = mock(SystemPromptCustomiser.class);
    var coordinator = new SwarmCoordinatorBuilder()
            .agentProvider(mock(AgentProvider.class))
            .promptCustomiser(customiser)
            .build();

    assertThat(coordinator.routingStrategy())
            .extracting("promptCustomiser")
            .isSameAs(customiser);
}
```

- [ ] **Step 2: Write failing test — builder selects variant slot and propagates**

```java
@Test
void build_selectsVariantSlotAndPropagates() {
    var selector = mock(VariantSelector.class);
    when(selector.selectSlot(any(), eq("swarm-coordination"))).thenReturn("experiment");

    var coordinator = new SwarmCoordinatorBuilder()
            .agentProvider(mock(AgentProvider.class))
            .variantSelector(selector)
            .build();

    assertThat(coordinator.routingStrategy())
            .extracting("variantSlot")
            .isEqualTo("experiment");
}
```

- [ ] **Step 3: Write failing test — builder wraps termination with recorder**

```java
@Test
void build_wrapsTerminationWithRecorder_whenOutcomeProviderSet() {
    var provider = new SwarmPromptOutcomeProvider();
    var coordinator = new SwarmCoordinatorBuilder()
            .agentProvider(mock(AgentProvider.class))
            .outcomeProvider(provider)
            .build();

    assertThat(coordinator.terminationCondition())
            .isInstanceOf(SwarmOutcomeRecorder.class);
}
```

- [ ] **Step 4: Implement SwarmCoordinatorBuilder changes**

Add new fields and builder methods:
- `private @Nullable VariantSelector variantSelector`
- `private @Nullable SwarmPromptOutcomeProvider outcomeProvider`
- `public SwarmCoordinatorBuilder variantSelector(VariantSelector selector)`
- `public SwarmCoordinatorBuilder outcomeProvider(SwarmPromptOutcomeProvider provider)`

Update `build()`:
```java
public LlmSwarmCoordinator build() {
    Objects.requireNonNull(agentProvider, "agentProvider is required");

    var context           = new SwarmContext();
    var tracker           = new SwarmInvocationTracker(invocationPolicy);
    var experienceSection = new SwarmExperienceSection(experienceConfig);
    var routingStrategy   = new SwarmRoutingStrategy(agentProvider, experienceSection,
            preFilterThreshold, tracker, promptCustomiser, additionalSections);
    var aggregation       = new CollectiveReasoningAggregation(agentProvider, tracker, promptCustomiser);
    TerminationCondition<SwarmContext> termination = new SwarmConvergenceTermination(convergenceThreshold);
    var eventSource       = new SwarmEventSource(context);
    var fallback          = new SwarmFallbackStrategy();

    // Variant slot: select once, share everywhere (D5)
    String slot = variantSelector != null
            ? variantSelector.selectSlot(java.util.UUID.randomUUID(), "swarm-coordination")
            : "control";
    routingStrategy.setVariantSlot(slot);
    aggregation.setVariantSlot(slot);

    // Outcome recording decorator
    if (outcomeProvider != null) {
        var recorder = new SwarmOutcomeRecorder(termination, new InMemoryPromptVariantStore(), slot);
        outcomeProvider.register(recorder);
        termination = recorder;
    }

    var sections = new ArrayList<SwarmPromptSection>();
    sections.add(experienceSection);
    sections.addAll(additionalSections);

    return new LlmSwarmCoordinator(
            agentProvider, context, routingStrategy, aggregation,
            termination, eventSource, tracker, fallback, sections);
}
```

Note: The variant slot should be selected only once. Refactor if `variantSelector` is used in both the slot assignment and recorder sections — use a local variable computed once.

- [ ] **Step 5: Run all swarm tests**

Run: `mvn --batch-mode test -pl agency -Dtest="io.casehub.agency.orchestration.swarm.*"`
Expected: all PASS

- [ ] **Step 6: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmCoordinatorBuilder.java agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmCoordinatorBuilderTest.java
git commit -m "feat(#362): wire SwarmCoordinatorBuilder — customiser, variant slot, outcome recording"
```

## Batch 3: Evolution loop integration

### Task 7: Evolution loop types — proposal source, regression evaluator, category provider, conflict strategy

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptProposalSource.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptRegressionEvaluator.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptCategoryProvider.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptConflictStrategy.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptProposalSourceTest.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptRegressionEvaluatorTest.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptCategoryProviderTest.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptConflictStrategyTest.java`

**Interfaces:**
- Consumes: `PromptQualityMetric` (SwarmOutcomeMetric), `PromptOutcomeProvider` (SwarmPromptOutcomeProvider), `PromptVariantStore`, `ImprovementRequest`, `HealthScoreSnapshot`
- Produces: Four evolution loop participants with domain ID `"swarm-prompt-optimisation"`

All four classes follow the exact pattern of the existing `PromptOptimisation*` types in `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/`. Read those files as templates.

- [ ] **Step 1: Write failing tests for SwarmPromptConflictStrategy**

```java
@Test
void domainId_returns_swarm_prompt_optimisation() {
    assertThat(new SwarmPromptConflictStrategy().domainId()).isEqualTo("swarm-prompt-optimisation");
}

@Test
void clear_when_no_active_improvements_share_signature() {
    var strategy = new SwarmPromptConflictStrategy();
    var request = new ImprovementRequest("swarm-prompt-tuning", "swarm-prompt-tuning",
            "swarm-team-formation", 1,
            Map.of("signatureId", "swarm-team-formation"), "swarm-prompt-optimisation");
    var active = Map.of(
            UUID.randomUUID(), new ImprovementRequest("swarm-prompt-tuning", "swarm-prompt-tuning",
                    "swarm-provisioning", 1,
                    Map.of("signatureId", "swarm-provisioning"), "swarm-prompt-optimisation"));

    assertThat(strategy.check(request, active, 10)).isInstanceOf(ConflictResult.Clear.class);
}

@Test
void conflict_when_active_improvement_targets_same_signature() {
    var strategy = new SwarmPromptConflictStrategy();
    var request = new ImprovementRequest("swarm-prompt-tuning", "swarm-prompt-tuning",
            "swarm-team-formation", 1,
            Map.of("signatureId", "swarm-team-formation"), "swarm-prompt-optimisation");
    var active = Map.of(
            UUID.randomUUID(), new ImprovementRequest("swarm-prompt-tuning", "swarm-prompt-tuning",
                    "swarm-team-formation", 1,
                    Map.of("signatureId", "swarm-team-formation"), "swarm-prompt-optimisation"));

    assertThat(strategy.check(request, active, 10)).isInstanceOf(ConflictResult.Blocked.class);
}
```

- [ ] **Step 2: Implement SwarmPromptConflictStrategy**

Follow `PromptConflictStrategy` exactly — change domain ID to `"swarm-prompt-optimisation"`.

```java
package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy;
import java.util.Map;
import java.util.UUID;

public class SwarmPromptConflictStrategy implements ConflictStrategy {

    static final String SIGNATURE_KEY = "signatureId";

    @Override
    public String domainId() { return "swarm-prompt-optimisation"; }

    @Override
    public ConflictResult check(ImprovementRequest request,
                                Map<UUID, ImprovementRequest> activeImprovements,
                                int trivialThreshold) {
        var targetSignature = request.metadata().get(SIGNATURE_KEY);
        if (targetSignature == null) return new ConflictResult.Clear();

        for (var entry : activeImprovements.entrySet()) {
            var activeSignature = entry.getValue().metadata().get(SIGNATURE_KEY);
            if (targetSignature.equals(activeSignature)) {
                return new ConflictResult.Blocked(entry.getKey(),
                        "Active improvement already targets signature: " + targetSignature);
            }
        }
        return new ConflictResult.Clear();
    }
}
```

- [ ] **Step 3: Write failing tests for SwarmPromptCategoryProvider**

```java
@Test
void domainId_returns_swarm_prompt_optimisation() {
    assertThat(new SwarmPromptCategoryProvider().domainId()).isEqualTo("swarm-prompt-optimisation");
}

@Test
void categories_returns_single_category() {
    var categories = new SwarmPromptCategoryProvider().categories();
    assertThat(categories).hasSize(1);
    assertThat(categories.get(0).categoryId()).isEqualTo("swarm-prompt-tuning");
}

@Test
void stages_returns_four_stages() {
    assertThat(new SwarmPromptCategoryProvider().stages()).hasSize(4);
}
```

- [ ] **Step 4: Implement SwarmPromptCategoryProvider**

Follow `PromptOptimisationCategoryProvider` — change domain ID and category ID.

- [ ] **Step 5: Write failing tests for SwarmPromptRegressionEvaluator**

```java
@Test
void evaluatorId_returns_swarm_prompt_quality() {
    assertThat(new SwarmPromptRegressionEvaluator().evaluatorId()).isEqualTo("swarm-prompt-quality");
}

@Test
void domainId_returns_swarm_prompt_optimisation() {
    assertThat(new SwarmPromptRegressionEvaluator().domainId()).isEqualTo("swarm-prompt-optimisation");
}

@Test
void noRegression_whenScoresStable() {
    var baseline = snapshotWith(Map.of("swarm-prompt:team-formation", 0.8));
    var current = snapshotWith(Map.of("swarm-prompt:team-formation", 0.78));
    var verdict = new SwarmPromptRegressionEvaluator()
            .evaluate(UUID.randomUUID(), baseline, current, "swarm-prompt-tuning");
    assertThat(verdict.isRegression()).isFalse();
}

@Test
void regression_whenScoreDropsSignificantly() {
    var baseline = snapshotWith(Map.of("swarm-prompt:team-formation", 0.8));
    var current = snapshotWith(Map.of("swarm-prompt:team-formation", 0.5));
    var verdict = new SwarmPromptRegressionEvaluator()
            .evaluate(UUID.randomUUID(), baseline, current, "swarm-prompt-tuning");
    assertThat(verdict.isRegression()).isTrue();
}
```

- [ ] **Step 6: Implement SwarmPromptRegressionEvaluator**

Follow `PromptRegressionEvaluator` — change prefix to `"swarm-prompt:"`, domain ID to `"swarm-prompt-optimisation"`, evaluator ID to `"swarm-prompt-quality"`.

- [ ] **Step 7: Write failing tests for SwarmPromptProposalSource**

```java
@Test
void sourceId_returns_swarm_prompt_optimisation() {
    var source = new SwarmPromptProposalSource(
            List.of(SwarmPromptSignatures.TEAM_FORMATION),
            mock(PromptVariantStore.class),
            new SwarmOutcomeMetric(),
            mock(PromptOutcomeProvider.class),
            OptimiserConfig.defaults());
    assertThat(source.sourceId()).isEqualTo("swarm-prompt-optimisation");
}

@Test
void proposesNothing_whenQualityAboveThreshold() {
    var store = mock(PromptVariantStore.class);
    var outcomeProvider = mock(PromptOutcomeProvider.class);
    when(outcomeProvider.outcomes(any())).thenReturn(List.of(
            outcome("CONVERGED"), outcome("CONVERGED"), outcome("CONVERGED")));

    var source = new SwarmPromptProposalSource(
            List.of(SwarmPromptSignatures.TEAM_FORMATION),
            store, new SwarmOutcomeMetric(), outcomeProvider, OptimiserConfig.defaults());
    var proposals = source.propose(UUID.randomUUID(), "t1", ImprovementConfig.defaults());
    assertThat(proposals).isEmpty();
}
```

- [ ] **Step 8: Implement SwarmPromptProposalSource**

Follow `PromptOptimisationProposalSource` — change domain ID, source ID, scan swarm signatures.

- [ ] **Step 9: Run all tests**

Run: `mvn --batch-mode test -pl agency -Dtest="io.casehub.agency.orchestration.swarm.prompt.*"`
Expected: all PASS

- [ ] **Step 10: Run full test suite**

Run: `mvn --batch-mode test`
Expected: all PASS

- [ ] **Step 11: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptConflictStrategy.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptCategoryProvider.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptRegressionEvaluator.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptProposalSource.java agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptConflictStrategyTest.java agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptCategoryProviderTest.java agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptRegressionEvaluatorTest.java agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/SwarmPromptProposalSourceTest.java
git commit -m "feat(#362): evolution loop integration — proposal source, regression, category, conflict"
```

## References

- [2026-10-30-coordinator-prompt-tuning-design.md] — design spec this plan implements
- `SwarmPromptSignatures.java` — three PromptSignature constants
- `SwarmRoutingStrategy.java:47-82` — route() method, insertion point for customiser
- `SwarmRoutingStrategy.java:117-148` — buildPrompt(), insertion point for sections
- `SwarmCoordinatorBuilder.java:63-82` — build() method to modify
- `LlmSwarmCoordinator.java:9-61` — composition root to widen
- `SwarmConvergenceTermination.java:7-35` — decorator target
- `SwarmOptimisedFewShotSection.java:32-39` — contribute() method to modify
- `PromptConflictStrategy.java` — template for SwarmPromptConflictStrategy
- `PromptOptimisationCategoryProvider.java` — template for SwarmPromptCategoryProvider
- `PromptRegressionEvaluator.java` — template for SwarmPromptRegressionEvaluator
- `PromptOptimisationProposalSource.java` — template for SwarmPromptProposalSource
- GitHub #362 — focal issue
