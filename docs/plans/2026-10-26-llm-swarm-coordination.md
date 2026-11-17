# LLM Swarm Coordination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #285 — feat: LLM swarm coordination with experience replay
**Issue group:** #285

**Goal:** Add the 9th PatternDefinition topology (`type: swarm`) with LLM-driven team formation, experience replay from CBR plan traces, collective reasoning aggregation, and adaptive coordination via engine event bridge.

**Architecture:** `LlmSwarmCoordinator` composition root wires `SwarmRoutingStrategy` (team formation), `CollectiveReasoningAggregation` (multi-agent synthesis), `SwarmExperienceSection` (CBR replay), and `SwarmConvergenceTermination`. Uses `ChoreographedDriver` for event-driven execution. Implements engine's `SwarmProvisioningAdvisor` SPI for scaling decisions.

**Tech Stack:** Java 17, JUnit 5, Mockito, Jackson YAML, engine-api (provided)

## Global Constraints

- All new types in package `io.casehub.agency.orchestration.swarm` (agency-core) or `io.casehub.agency.agentic.yaml.spec` (agentic-yaml)
- Tests use plain JUnit 5 + Mockito — no CDI container, no Quarkus test framework
- All records use `@Nullable` from `org.jspecify` for optional fields
- `PatternDefinition.Swarm` record repeats all 12 common fields + 4 swarm-specific fields (same pattern as `Debate`, `Htn`)
- `SwarmContext` is a mutable class (not a record) with `volatile` fields
- Engine-api types (`SwarmConfig`, `DetectedRole`, `DetectedTeam`, `SwarmProgress`, `SwarmProvisioningAdvisor`, `RetrievedExperience`, `ExperienceAnalyser`) are provided dependencies — never duplicate
- Run `mvn --batch-mode test -pl agentic-yaml -Dschema.update` after adding PatternDefinition.Swarm to regenerate schema baseline

---

## Batch 1: YAML surface and type foundation

### Task 1: PatternDefinition.Swarm + YAML serialization

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/ExperienceReplaySpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/SwarmInvocationPolicySpec.java`
- Modify: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/PatternDefinition.java:23` — add `Swarm` record + `@Type`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/execution/PatternType.java` — add `SWARM`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/execution/CapabilityType.java` — add `SWARM_COORDINATION`
- Create: `agentic-yaml/src/test/resources/patterns/swarm.yaml`
- Modify: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/spec/PatternDefinitionSerializationTest.java` — add "swarm" to `@ValueSource` + field test
- Test: `PatternDefinitionSerializationTest` (existing, extended)

**Interfaces:**
- Produces: `ExperienceReplaySpec(int maxTraces, double minSimilarity)` — used by Task 6 (coordinator builder)
- Produces: `SwarmInvocationPolicySpec(int maxCalls, long maxTokens, int provisioningReserve, @Nullable String warnAfter, @Nullable String errorAfter)` — used by Task 5
- Produces: `PatternDefinition.Swarm` record with fields: `ExperienceReplaySpec experience`, `@Nullable String coordinationModel`, `@Nullable SwarmInvocationPolicySpec invocationPolicy`, `int preFilterThreshold` — used by Task 7 (compiler)

- [ ] **Step 1: Create ExperienceReplaySpec record**

```java
package io.casehub.agency.agentic.yaml.spec;

public record ExperienceReplaySpec(int maxTraces, double minSimilarity) {
    public ExperienceReplaySpec {
        if (maxTraces < 0) throw new IllegalArgumentException("maxTraces must be >= 0");
        if (minSimilarity < 0.0 || minSimilarity > 1.0)
            throw new IllegalArgumentException("minSimilarity must be in [0.0, 1.0]");
    }
    public ExperienceReplaySpec() { this(5, 0.3); }
}
```

- [ ] **Step 2: Create SwarmInvocationPolicySpec record**

```java
package io.casehub.agency.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

public record SwarmInvocationPolicySpec(
        int maxCalls,
        long maxTokens,
        int provisioningReserve,
        @Nullable String warnAfter,
        @Nullable String errorAfter) {
    public SwarmInvocationPolicySpec {
        if (maxCalls < 1) throw new IllegalArgumentException("maxCalls must be >= 1");
        if (maxTokens < 1) throw new IllegalArgumentException("maxTokens must be >= 1");
        if (provisioningReserve < 0) throw new IllegalArgumentException("provisioningReserve must be >= 0");
    }
    public SwarmInvocationPolicySpec() { this(50, 500_000, 3, null, null); }
}
```

- [ ] **Step 3: Add SWARM to PatternType enum**

Add `SWARM` after `HTN` in the enum. Not workflow-shaped. Update `toCapabilityType()` switch: `SWARM -> CapabilityType.SWARM_COORDINATION`.

- [ ] **Step 4: Add SWARM_COORDINATION to CapabilityType enum**

Add `SWARM_COORDINATION(CapabilityCategory.ORCHESTRATION)` to the enum. Update `toPatternType()` switch: `SWARM_COORDINATION -> Optional.of(PatternType.SWARM)`. Set `hasYamlRoot` to `true` in the catalog entry.

- [ ] **Step 5: Add Swarm record to PatternDefinition**

Add `@Type(value = PatternDefinition.Swarm.class, name = "swarm")` to `@JsonSubTypes` on `PatternDefinition`. Add the sealed record following the `Htn` pattern — 12 common fields + 4 swarm-specific:

```java
record Swarm(
    @Nullable String name, @Nullable String description,
    @Nullable RoutingSpec routing, @Nullable List<TerminationSpec> termination,
    @Nullable AggregationSpec aggregation, @Nullable ActivationSpec activation,
    @Nullable DecompositionSpec decomposition, @Nullable FailurePolicy failurePolicy,
    @Nullable String task, List<AgentRefSpec> agents,
    @Nullable JudgmentSpec judgment, @Nullable GovernanceSpec governance,
    @Nullable ExperienceReplaySpec experience,
    @Nullable String coordinationModel,
    @Nullable SwarmInvocationPolicySpec invocationPolicy,
    int preFilterThreshold
) implements PatternDefinition {
    public Swarm { if (agents == null) agents = List.of(); }
}
```

- [ ] **Step 6: Create YAML fixture**

Create `agentic-yaml/src/test/resources/patterns/swarm.yaml`:

```yaml
type: swarm
name: test-swarm
description: Test swarm coordination
coordinationModel: claude-sonnet-4-5
preFilterThreshold: 12
experience:
  maxTraces: 5
  minSimilarity: 0.3
invocationPolicy:
  maxCalls: 50
  maxTokens: 500000
  provisioningReserve: 3
  warnAfter: 5s
  errorAfter: 30s
agents:
  - name: analyst
    description: Analyses data
  - name: responder
    description: Executes actions
termination:
  - type: max-iterations
    maxIterations: 20
```

- [ ] **Step 7: Add "swarm" to serialization test @ValueSource + add swarmFields() test**

Add `"swarm"` to the `@ValueSource(strings = {...})` in the parameterized round-trip test. Add a new test method:

```java
@Test
void swarmFields() throws Exception {
    var def = load("swarm");
    assertThat(def).isInstanceOf(PatternDefinition.Swarm.class);
    var swarm = (PatternDefinition.Swarm) def;
    assertThat(swarm.coordinationModel()).isEqualTo("claude-sonnet-4-5");
    assertThat(swarm.preFilterThreshold()).isEqualTo(12);
    assertThat(swarm.experience()).isNotNull();
    assertThat(swarm.experience().maxTraces()).isEqualTo(5);
    assertThat(swarm.experience().minSimilarity()).isEqualTo(0.3);
    assertThat(swarm.invocationPolicy()).isNotNull();
    assertThat(swarm.invocationPolicy().maxCalls()).isEqualTo(50);
    assertThat(swarm.invocationPolicy().provisioningReserve()).isEqualTo(3);
}
```

- [ ] **Step 8: Run serialization test**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PatternDefinitionSerializationTest`
Expected: All tests pass including the new swarm round-trip and field tests.

- [ ] **Step 9: Regenerate schema baseline**

Run: `mvn --batch-mode test -pl agentic-yaml -Dschema.update`
Expected: Schema file updated to include the swarm topology.

- [ ] **Step 10: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/ExperienceReplaySpec.java agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/SwarmInvocationPolicySpec.java agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/PatternDefinition.java agentic-yaml/src/test/resources/patterns/swarm.yaml agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/spec/PatternDefinitionSerializationTest.java agency-core/src/main/java/io/casehub/agency/orchestration/execution/PatternType.java agency-core/src/main/java/io/casehub/agency/orchestration/execution/CapabilityType.java
git commit -m "feat(#285): add PatternDefinition.Swarm topology + YAML serialization"
```

### Task 2: RoutingDecision.SwarmAssignment + SwarmContext + foundation records

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/routing/RoutingDecision.java:8` — add `SwarmAssignment` variant
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmContext.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmPromptSection.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/ExperienceReplayConfig.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmAggregationState.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmInvocationPolicy.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmDegradedEvent.java`
- Modify: `agency/src/test/java/io/casehub/agency/orchestration/routing/RoutingDecisionTest.java` — add SwarmAssignment test
- Test: `RoutingDecisionTest` (existing, extended)

**Interfaces:**
- Consumes: engine-api types (`DetectedRole`, `DetectedTeam`, `SwarmProgress`, `RetrievedExperience`)
- Produces: `RoutingDecision.SwarmAssignment(List<AgentRef> agents, Map<String, List<AgentRef>> teams, @Nullable String strategy, @Nullable String reason)` — used by Tasks 3, 7
- Produces: `SwarmContext` mutable class — used by Tasks 3, 4, 5, 6
- Produces: `SwarmPromptSection` `@FunctionalInterface` — used by Task 3
- Produces: `ExperienceReplayConfig(int maxTraces, double minSimilarity)` — used by Task 3
- Produces: `SwarmAggregationState` record — used by Task 4
- Produces: `SwarmInvocationPolicy(int maxCallsPerExecution, long maxTokensPerExecution, int provisioningReserve, Duration warnAfter, Duration errorAfter)` — used by Task 5
- Produces: `SwarmDegradedEvent` record — used by Task 5

- [ ] **Step 1: Write test for SwarmAssignment**

```java
@Test
void swarmAssignment_defenseiveCopy() {
    var agents = new ArrayList<>(List.of(
        new AgentRef.WorkerAgent("a1", "Agent 1")));
    var teams = new HashMap<>(Map.of("team1", List.of(
        new AgentRef.WorkerAgent("a1", "Agent 1"))));
    var assignment = new RoutingDecision.SwarmAssignment(agents, teams, "divide", "test");
    agents.clear();
    teams.clear();
    assertThat(assignment.agents()).hasSize(1);
    assertThat(assignment.teams()).hasSize(1);
    assertThat(assignment.strategy()).isEqualTo("divide");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=RoutingDecisionTest#swarmAssignment_defenseiveCopy`
Expected: FAIL — `SwarmAssignment` doesn't exist yet.

- [ ] **Step 3: Add SwarmAssignment to RoutingDecision**

Add to `RoutingDecision.java` `permits` clause and add the record:

```java
public sealed interface RoutingDecision permits
        RoutingDecision.Selected, RoutingDecision.Unresolvable,
        RoutingDecision.Escalate, RoutingDecision.SwarmAssignment {
    // ... existing records ...

    record SwarmAssignment(
            List<AgentRef> agents,
            Map<String, List<AgentRef>> teams,
            @Nullable String strategy,
            @Nullable String reason
    ) implements RoutingDecision {
        public SwarmAssignment {
            agents = List.copyOf(agents);
            teams = Map.copyOf(teams);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=RoutingDecisionTest`
Expected: All tests pass.

- [ ] **Step 5: Create SwarmContext**

```java
package io.casehub.agency.orchestration.swarm;

import io.casehub.api.model.stigmergy.DetectedRole;
import io.casehub.api.model.stigmergy.DetectedTeam;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.routing.RetrievedExperience;
import java.util.List;

public class SwarmContext {
    private volatile List<DetectedRole> detectedRoles = List.of();
    private volatile List<DetectedTeam> detectedTeams = List.of();
    private volatile SwarmProgress swarmProgress;
    private volatile String activeStrategy;
    private volatile List<RetrievedExperience> experiences = List.of();

    public void onSwarmInitialised(List<RetrievedExperience> experiences, SwarmProgress progress) {
        this.experiences = List.copyOf(experiences);
        this.swarmProgress = progress;
    }
    public void onRoleChanged(List<DetectedRole> roles, SwarmProgress progress) {
        this.detectedRoles = List.copyOf(roles);
        this.swarmProgress = progress;
    }
    public void onTeamChanged(List<DetectedTeam> teams, SwarmProgress progress) {
        this.detectedTeams = List.copyOf(teams);
        this.swarmProgress = progress;
    }
    public void onProgressUpdated(SwarmProgress progress) { this.swarmProgress = progress; }
    public void setActiveStrategy(String strategy) { this.activeStrategy = strategy; }

    public List<DetectedRole> detectedRoles() { return detectedRoles; }
    public List<DetectedTeam> detectedTeams() { return detectedTeams; }
    public SwarmProgress swarmProgress() { return swarmProgress; }
    public String activeStrategy() { return activeStrategy; }
    public List<RetrievedExperience> experiences() { return experiences; }
}
```

- [ ] **Step 6: Create SwarmPromptSection, ExperienceReplayConfig, SwarmAggregationState, SwarmInvocationPolicy, SwarmDegradedEvent**

`SwarmPromptSection.java`:
```java
@FunctionalInterface
public interface SwarmPromptSection {
    String contribute(SwarmContext context);
}
```

`ExperienceReplayConfig.java`:
```java
public record ExperienceReplayConfig(int maxTraces, double minSimilarity) {
    public ExperienceReplayConfig {
        if (maxTraces < 0) throw new IllegalArgumentException("maxTraces must be >= 0");
        if (minSimilarity < 0.0 || minSimilarity > 1.0)
            throw new IllegalArgumentException("minSimilarity must be in [0.0, 1.0]");
    }
    public static ExperienceReplayConfig defaults() { return new ExperienceReplayConfig(5, 0.3); }
}
```

`SwarmAggregationState.java`:
```java
public record SwarmAggregationState(
        List<String> agreements,
        List<String> disagreements,
        List<String> complementaryObservations,
        double confidence,
        List<String> minorityViewpoints) {
    public SwarmAggregationState {
        agreements = List.copyOf(agreements);
        disagreements = List.copyOf(disagreements);
        complementaryObservations = List.copyOf(complementaryObservations);
        minorityViewpoints = List.copyOf(minorityViewpoints);
    }
}
```

`SwarmInvocationPolicy.java`:
```java
public record SwarmInvocationPolicy(
        int maxCallsPerExecution, long maxTokensPerExecution,
        int provisioningReserve, Duration warnAfter, Duration errorAfter) {
    public SwarmInvocationPolicy {
        if (maxCallsPerExecution < 1) throw new IllegalArgumentException("maxCallsPerExecution must be >= 1");
        if (maxTokensPerExecution < 1) throw new IllegalArgumentException("maxTokensPerExecution must be >= 1");
        if (provisioningReserve < 0) throw new IllegalArgumentException("provisioningReserve must be >= 0");
    }
    public static SwarmInvocationPolicy defaults() {
        return new SwarmInvocationPolicy(50, 500_000, 3, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }
}
```

`SwarmDegradedEvent.java`:
```java
public record SwarmDegradedEvent(String capability, String reason, boolean fallbackActive) {}
```

- [ ] **Step 7: Compile check**

Run: `mvn --batch-mode compile -pl agency-core`
Expected: Compiles successfully.

- [ ] **Step 8: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/routing/RoutingDecision.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/ agency/src/test/java/io/casehub/agency/orchestration/routing/RoutingDecisionTest.java
git commit -m "feat(#285): add SwarmAssignment variant + swarm foundation types"
```

---

## Batch 2: Coordination strategies

### Task 3: SwarmExperienceSection + SwarmRoutingStrategy

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmExperienceSection.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmRoutingStrategy.java`
- Create: `agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmExperienceSectionTest.java`
- Create: `agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmRoutingStrategyTest.java`

**Interfaces:**
- Consumes: `SwarmPromptSection` (Task 2), `SwarmContext` (Task 2), `ExperienceReplayConfig` (Task 2), `RoutingDecision.SwarmAssignment` (Task 2)
- Consumes: engine-api `ExperienceAnalyser.workerSuccessRates()`, `RetrievedExperience`, `CoalitionEvaluator` (agency deliberation/coalition)
- Consumes: `RoutingStrategy<SwarmContext>` interface, `RoutingContext<SwarmContext>`
- Consumes: `StructuredAgentInvoker.invoke()` (agency core/invoke)
- Produces: `SwarmExperienceSection implements SwarmPromptSection` — `String contribute(SwarmContext)` — used by Task 6
- Produces: `SwarmRoutingStrategy implements RoutingStrategy<SwarmContext>` — `RoutingDecision route(RoutingContext<SwarmContext>)` — used by Task 7

- [ ] **Step 1: Write SwarmExperienceSectionTest**

```java
@Test
void contribute_formatsExperiences() {
    var experience = mock(RetrievedExperience.class);
    when(experience.similarity()).thenReturn(0.8);
    when(experience.outcome()).thenReturn("SUCCESS");
    // ... mock plan trace with team data

    var context = new SwarmContext();
    context.onSwarmInitialised(List.of(experience), mockProgress());

    var section = new SwarmExperienceSection(new ExperienceReplayConfig(5, 0.3));
    var text = section.contribute(context);

    assertThat(text).contains("Experience");
    assertThat(text).contains("SUCCESS");
}

@Test
void contribute_filtersLowSimilarity() {
    var lowSim = mock(RetrievedExperience.class);
    when(lowSim.similarity()).thenReturn(0.1);
    var context = new SwarmContext();
    context.onSwarmInitialised(List.of(lowSim), mockProgress());

    var section = new SwarmExperienceSection(new ExperienceReplayConfig(5, 0.3));
    var text = section.contribute(context);

    assertThat(text).isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmExperienceSectionTest`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement SwarmExperienceSection**

`SwarmExperienceSection` implements `SwarmPromptSection`. The `contribute(SwarmContext)` method: filters experiences by `minSimilarity`, takes top `maxTraces`, calls `ExperienceAnalyser.workerSuccessRates()` with the filtered experiences, formats team-level patterns as structured text showing which agent combinations succeeded, roles that emerged, and work distribution patterns.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmExperienceSectionTest`
Expected: PASS.

- [ ] **Step 5: Write SwarmRoutingStrategyTest**

```java
@Test
void route_batchReasoningUnderThreshold() {
    var agentProvider = mock(AgentProvider.class);
    // Mock LLM response returning JSON team assignment
    mockAgentInvocation(agentProvider, """
        {"agents": ["analyst", "responder"], "teams": {"analysis": ["analyst"]}, "strategy": "divide"}
        """);

    var strategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 12);
    var context = routingContext(List.of(analyst, responder), new SwarmContext());
    var decision = strategy.route(context);

    assertThat(decision).isInstanceOf(RoutingDecision.SwarmAssignment.class);
    var assignment = (RoutingDecision.SwarmAssignment) decision;
    assertThat(assignment.agents()).hasSize(2);
    assertThat(assignment.strategy()).isEqualTo("divide");
}

@Test
void route_preFiltersWhenAboveThreshold() {
    // Create 15 agents (above threshold of 12)
    var agents = IntStream.rangeClosed(1, 15)
        .mapToObj(i -> new AgentRef.WorkerAgent("a" + i, "Agent " + i))
        .collect(Collectors.toList());

    var strategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 12);
    var context = routingContext(agents, new SwarmContext());
    strategy.route(context);

    // Verify LLM was called with <= 12 candidates (pre-filtered)
    verify(agentProvider).invoke(argThat(config ->
        !config.systemPrompt().contains("Agent 15")));
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmRoutingStrategyTest`
Expected: FAIL.

- [ ] **Step 7: Implement SwarmRoutingStrategy**

`SwarmRoutingStrategy` implements `RoutingStrategy<SwarmContext>`. The `route()` method: (1) if agent count ≤ `preFilterThreshold`, batch all agents; (2) if above, use `CoalitionEvaluator` greedy capability-coverage to narrow to `preFilterThreshold` candidates; (3) assemble coordinator prompt with experience section + swarm state; (4) invoke LLM via `StructuredAgentInvoker.invoke()` for team assignment; (5) parse JSON response into `SwarmAssignment`.

- [ ] **Step 8: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmRoutingStrategyTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmExperienceSection.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmRoutingStrategy.java agency/src/test/java/io/casehub/agency/orchestration/swarm/
git commit -m "feat(#285): SwarmRoutingStrategy with experience replay + pre-filter fallback"
```

### Task 4: CollectiveReasoningAggregation + SwarmConvergenceTermination

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/CollectiveReasoningAggregation.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmConvergenceTermination.java`
- Create: `agency/src/test/java/io/casehub/agency/orchestration/swarm/CollectiveReasoningAggregationTest.java`
- Create: `agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmConvergenceTerminationTest.java`

**Interfaces:**
- Consumes: `AggregationStrategy<SwarmContext>` (aggregation SPI), `SwarmAggregationState` (Task 2), `StructuredAgentInvoker`
- Consumes: `TerminationCondition<SwarmContext>` (termination SPI), `SwarmProgress` (engine-api)
- Produces: `CollectiveReasoningAggregation implements AggregationStrategy<SwarmContext>` — used by Task 7
- Produces: `SwarmConvergenceTermination implements TerminationCondition<SwarmContext>` — used by Task 7

- [ ] **Step 1: Write CollectiveReasoningAggregationTest**

```java
@Test
void aggregate_synthesisesMultipleResults() {
    var agentProvider = mock(AgentProvider.class);
    mockAgentInvocation(agentProvider, """
        {"agreements":["common finding"],"disagreements":["disputed point"],
         "complementaryObservations":["unique insight"],"confidence":0.85,
         "minorityViewpoints":["alternative view"]}
        """);

    var aggregation = new CollectiveReasoningAggregation(agentProvider);
    var results = List.of(
        new AgentResult("analyst", "Analysis complete: data shows trend A"),
        new AgentResult("responder", "Remediation applied: trend B observed"));

    var result = aggregation.aggregate(results, aggregationContext());
    assertThat(result).isInstanceOf(AggregationResult.Resolved.class);
    var resolved = (AggregationResult.Resolved) result;
    var state = (SwarmAggregationState) resolved.value();
    assertThat(state.agreements()).containsExactly("common finding");
    assertThat(state.confidence()).isEqualTo(0.85);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=CollectiveReasoningAggregationTest`
Expected: FAIL.

- [ ] **Step 3: Implement CollectiveReasoningAggregation**

`CollectiveReasoningAggregation` implements `AggregationStrategy<SwarmContext>`. The `aggregate()` method: assembles a synthesis prompt with all agent results (multi-perspective analysis, not summarisation), invokes LLM via `StructuredAgentInvoker.invoke(agentProvider, config, SwarmAggregationState.class)`, returns `AggregationResult.Resolved` wrapping the parsed state.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=CollectiveReasoningAggregationTest`
Expected: PASS.

- [ ] **Step 5: Write SwarmConvergenceTerminationTest**

```java
@Test
void converged_whenBothMetricsAboveThreshold() {
    var term = new SwarmConvergenceTermination(0.8);
    var ctx = contextWithProgress(0.9, 0.85); // consensus=0.9, stability=0.85
    assertThat(term.evaluate(ctx)).isInstanceOf(TerminationDecision.Complete.class);
}

@Test
void deadlock_whenStableButNoConsensus() {
    var term = new SwarmConvergenceTermination(0.8);
    var ctx = contextWithProgress(0.3, 0.9); // consensus=0.3, stability=0.9
    assertThat(term.evaluate(ctx)).isInstanceOf(TerminationDecision.Escalate.class);
}

@Test
void continue_whenStillEvolving() {
    var term = new SwarmConvergenceTermination(0.8);
    var ctx = contextWithProgress(0.5, 0.4); // both below
    assertThat(term.evaluate(ctx)).isInstanceOf(TerminationDecision.Continue.class);
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmConvergenceTerminationTest`
Expected: FAIL.

- [ ] **Step 7: Implement SwarmConvergenceTermination**

```java
public class SwarmConvergenceTermination implements TerminationCondition<SwarmContext> {
    private final double threshold;
    public SwarmConvergenceTermination(double threshold) { this.threshold = threshold; }

    @Override
    public TerminationDecision evaluate(SwarmContext context) {
        var progress = context.swarmProgress();
        if (progress == null) return new TerminationDecision.Continue();
        double consensus = progress.consensusScore();
        double stability = progress.stabilityScore();
        if (consensus >= threshold && stability >= threshold)
            return new TerminationDecision.Complete("Swarm converged");
        if (stability >= threshold && consensus < threshold)
            return new TerminationDecision.Escalate("Deadlock — stable but disagreeing");
        return new TerminationDecision.Continue();
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmConvergenceTerminationTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/CollectiveReasoningAggregation.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmConvergenceTermination.java agency/src/test/java/io/casehub/agency/orchestration/swarm/
git commit -m "feat(#285): CollectiveReasoningAggregation + SwarmConvergenceTermination"
```

### Task 5: Cost controls + event bridge + fallback

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmInvocationTracker.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmFallbackStrategy.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmEventSource.java`
- Create: `agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmInvocationTrackerTest.java`
- Create: `agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmFallbackStrategyTest.java`
- Create: `agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmEventSourceTest.java`

**Interfaces:**
- Consumes: `SwarmInvocationPolicy` (Task 2), `SwarmDegradedEvent` (Task 2), `SwarmContext` (Task 2)
- Consumes: `EventSource` (agency orchestration/execution), `DriverEvent` (agency orchestration/execution)
- Consumes: `CoalitionEvaluator` (agency deliberation/coalition), `CollectAll` (agency orchestration/aggregation)
- Produces: `SwarmInvocationTracker` — `boolean tryAcquire(String capability)`, `void recordTokens(long tokens)` — used by Task 6
- Produces: `SwarmFallbackStrategy` — heuristic fallbacks for each capability — used by Task 6
- Produces: `SwarmEventSource implements EventSource` — used by Task 7

- [ ] **Step 1: Write SwarmInvocationTrackerTest**

```java
@Test
void tryAcquire_succeedsUnderBudget() {
    var tracker = new SwarmInvocationTracker(SwarmInvocationPolicy.defaults());
    assertThat(tracker.tryAcquire("routing")).isTrue();
}

@Test
void tryAcquire_failsWhenExhausted() {
    var policy = new SwarmInvocationPolicy(2, 500_000, 1, Duration.ofSeconds(5), Duration.ofSeconds(30));
    var tracker = new SwarmInvocationTracker(policy);
    tracker.tryAcquire("routing");
    tracker.tryAcquire("aggregation"); // budget = 2 - 1 (reserve) = 1 non-provisioning call
    // Second non-provisioning call should fail (budget exhausted, 1 reserved for provisioning)
    assertThat(tracker.tryAcquire("routing")).isFalse();
}

@Test
void tryAcquire_provisioningUsesReserve() {
    var policy = new SwarmInvocationPolicy(2, 500_000, 1, Duration.ofSeconds(5), Duration.ofSeconds(30));
    var tracker = new SwarmInvocationTracker(policy);
    tracker.tryAcquire("routing");
    tracker.tryAcquire("aggregation");
    // Provisioning can still use the reserved allocation
    assertThat(tracker.tryAcquire("provisioning")).isTrue();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmInvocationTrackerTest`
Expected: FAIL.

- [ ] **Step 3: Implement SwarmInvocationTracker**

Thread-safe via `AtomicInteger` for call count and `AtomicLong` for token count. `tryAcquire(capability)`: for "provisioning", checks against total maxCalls; for other capabilities, checks against `maxCalls - provisioningReserve`. `recordTokens(long)` accumulates and returns false if over token budget.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmInvocationTrackerTest`
Expected: PASS.

- [ ] **Step 5: Write SwarmEventSourceTest**

```java
@Test
void translatesSwarmCoordinationEventToDriverEvent() {
    var context = new SwarmContext();
    var source = new SwarmEventSource(context);
    var received = new ArrayList<DriverEvent>();
    source.subscribe(received::add);

    var progress = mockProgress(0.5, 0.3);
    source.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(progress));

    assertThat(received).hasSize(1);
    assertThat(received.get(0).source()).isEqualTo("swarm");
    assertThat(context.swarmProgress()).isSameAs(progress);
}
```

- [ ] **Step 6: Run test, verify fails, implement SwarmEventSource**

`SwarmEventSource` implements `EventSource` and the proposed `SwarmCoordinationListener` interface. `subscribe(Consumer<DriverEvent>)` registers a listener. `onSwarmEvent(SwarmCoordinationEvent)` updates `SwarmContext` via the appropriate method, then wraps the event as a `DriverEvent` and delivers to all subscribers.

Note: Since `SwarmCoordinationListener` doesn't exist in engine-api yet, define it locally in the swarm package as a `@FunctionalInterface`. When engine#1112 ships the real SPI, this becomes a bridge adapter.

- [ ] **Step 7: Write SwarmFallbackStrategyTest + implement**

Tests verify: `fallbackRouting()` uses `CoalitionEvaluator`, `fallbackAggregation()` uses `CollectAll`, `fallbackProvisioningAdvice()` returns budget-based heuristic.

- [ ] **Step 8: Run all tests**

Run: `mvn --batch-mode test -pl agency -Dtest="SwarmInvocationTrackerTest,SwarmEventSourceTest,SwarmFallbackStrategyTest"`
Expected: All pass.

- [ ] **Step 9: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmInvocationTracker.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmFallbackStrategy.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmEventSource.java agency/src/test/java/io/casehub/agency/orchestration/swarm/
git commit -m "feat(#285): cost controls, event bridge, and fallback strategy"
```

---

## Batch 3: Coordinator composition and compiler integration

### Task 6: LlmSwarmCoordinator + SwarmProvisioningAdvisorImpl

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/LlmSwarmCoordinator.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmCoordinatorBuilder.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmProvisioningAdvisorImpl.java`
- Create: `agency/src/test/java/io/casehub/agency/orchestration/swarm/LlmSwarmCoordinatorTest.java`
- Create: `agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmProvisioningAdvisorImplTest.java`

**Interfaces:**
- Consumes: `SwarmRoutingStrategy` (Task 3), `CollectiveReasoningAggregation` (Task 4), `SwarmExperienceSection` (Task 3), `SwarmConvergenceTermination` (Task 4), `SwarmInvocationTracker` (Task 5), `SwarmFallbackStrategy` (Task 5), `SwarmEventSource` (Task 5), `SwarmContext` (Task 2), `SwarmPromptSection` (Task 2)
- Consumes: engine-api `SwarmProvisioningAdvisor`, `ProvisioningContext`, `ProvisioningAdvice`
- Produces: `LlmSwarmCoordinator` — composition root — used by Task 7
- Produces: `SwarmCoordinatorBuilder` — builder API — used by Task 7
- Produces: `SwarmProvisioningAdvisorImpl implements SwarmProvisioningAdvisor` — used at runtime

- [ ] **Step 1: Write LlmSwarmCoordinatorTest**

```java
@Test
void coordinatorAssemblesPromptFromSections() {
    var section1 = mock(SwarmPromptSection.class);
    when(section1.contribute(any())).thenReturn("Section 1 content");
    var section2 = mock(SwarmPromptSection.class);
    when(section2.contribute(any())).thenReturn("Section 2 content");

    var coordinator = new SwarmCoordinatorBuilder()
        .agentProvider(mockAgentProvider)
        .promptSections(List.of(section1, section2))
        .invocationPolicy(SwarmInvocationPolicy.defaults())
        .build();

    var prompt = coordinator.assembleCoordinatorPrompt(new SwarmContext());
    assertThat(prompt).contains("Section 1 content");
    assertThat(prompt).contains("Section 2 content");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=LlmSwarmCoordinatorTest`
Expected: FAIL.

- [ ] **Step 3: Implement LlmSwarmCoordinator + SwarmCoordinatorBuilder**

`LlmSwarmCoordinator` is the composition root. Constructor takes all components. `assembleCoordinatorPrompt(SwarmContext)` iterates `SwarmPromptSection` instances, concatenates their contributions with swarm state context. Exposes the routing strategy, aggregation, and termination condition for `ExecutionModel` construction.

`SwarmCoordinatorBuilder` provides a fluent API mirroring `PatternDefinition.Swarm` fields: `.agentProvider()`, `.experienceConfig()`, `.coordinationModel()`, `.invocationPolicy()`, `.preFilterThreshold()`, `.promptSections()`, `.build()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=LlmSwarmCoordinatorTest`
Expected: PASS.

- [ ] **Step 5: Write SwarmProvisioningAdvisorImplTest**

```java
@Test
void advise_delegatesToLlm() {
    mockAgentInvocation(agentProvider, """
        {"shouldProvision": true, "reasoning": "Need more analysts"}
        """);
    var advisor = new SwarmProvisioningAdvisorImpl(agentProvider, context, tracker);
    var advice = advisor.advise(provisioningContext);
    assertThat(advice.shouldProvision()).isTrue();
    assertThat(advice.reasoning()).contains("analysts");
}

@Test
void advise_fallsBackOnLlmFailure() {
    when(agentProvider.invoke(any())).thenThrow(new RuntimeException("LLM unavailable"));
    var advisor = new SwarmProvisioningAdvisorImpl(agentProvider, context, tracker);
    var advice = advisor.advise(provisioningContext);
    // Heuristic fallback — budget-based
    assertThat(advice).isNotNull();
}
```

- [ ] **Step 6: Implement SwarmProvisioningAdvisorImpl**

Implements `SwarmProvisioningAdvisor`. `advise()`: checks `SwarmInvocationTracker.tryAcquire("provisioning")`, invokes LLM if budget available, parses response into `ProvisioningAdvice`. On failure or budget exhaustion, falls back to budget-based heuristic (provision if under budget and capabilities needed).

- [ ] **Step 7: Run tests**

Run: `mvn --batch-mode test -pl agency -Dtest="LlmSwarmCoordinatorTest,SwarmProvisioningAdvisorImplTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/LlmSwarmCoordinator.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmCoordinatorBuilder.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmProvisioningAdvisorImpl.java agency/src/test/java/io/casehub/agency/orchestration/swarm/
git commit -m "feat(#285): LlmSwarmCoordinator composition root + SwarmProvisioningAdvisorImpl"
```

### Task 7: PatternCompiler integration + Patterns.swarm() + full build

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/PatternCompiler.java:47` — add swarm cases
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/pattern/Patterns.java:5` — add `swarm()` method
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmBuilder.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/execution/AbstractExecutionDriver.java` — add SwarmAssignment case
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/execution/ExecutionEventListener.java` — add `onSwarmDegraded` default method
- Modify: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/compiler/PatternCompilerTest.java` — add swarm compilation test

**Interfaces:**
- Consumes: `LlmSwarmCoordinator` (Task 6), `SwarmCoordinatorBuilder` (Task 6), all swarm types from Tasks 1-6
- Produces: Complete compilable swarm topology — `PatternCompiler.compile(PatternDefinition.Swarm)` → `ExecutionModel<SwarmContext>`
- Produces: `Patterns.swarm()` → `SwarmBuilder` → `ExecutionModel<SwarmContext>`

- [ ] **Step 1: Write PatternCompilerTest for swarm**

```java
@Test
void compilesSwarmPattern() {
    var def = load("swarm");
    var model = compiler.compile(def);
    assertThat(model.patternType()).isEqualTo(PatternType.SWARM);
    assertThat(model.routingStrategy()).isInstanceOf(SwarmRoutingStrategy.class);
    assertThat(model.aggregationStrategy()).isInstanceOf(CollectiveReasoningAggregation.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PatternCompilerTest#compilesSwarmPattern`
Expected: FAIL — no swarm case in compiler.

- [ ] **Step 3: Add swarm cases to PatternCompiler**

Add `case PatternDefinition.Swarm swarm ->` to each resolver:
- `resolvePatternType()`: return `PatternType.SWARM`
- `resolveRouting()`: construct `SwarmRoutingStrategy` via `SwarmCoordinatorBuilder` with the spec's fields
- `resolveAggregation()`: construct `CollectiveReasoningAggregation`
- `resolveTermination()`: for `TerminationSpec.Convergence`, construct `SwarmConvergenceTermination` (instead of throwing)

- [ ] **Step 4: Add SwarmAssignment case to AbstractExecutionDriver**

In `executeIteration()` (or `dispatchAgents()`), add handling for `RoutingDecision.SwarmAssignment` — extract `agents()` for dispatch (structurally identical to `Selected`).

- [ ] **Step 5: Add onSwarmDegraded to ExecutionEventListener**

```java
default void onSwarmDegraded(SwarmDegradedEvent event) {}
```

- [ ] **Step 6: Create SwarmBuilder + add Patterns.swarm()**

`SwarmBuilder` extends `AbstractPatternBuilder` (same pattern as `SupervisorBuilder`, `DebateBuilder`). Adds swarm-specific methods: `.coordinationModel()`, `.experience()`, `.invocationPolicy()`, `.preFilterThreshold()`.

Add `public static SwarmBuilder swarm()` to `Patterns.java`.

- [ ] **Step 7: Run PatternCompiler test**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PatternCompilerTest`
Expected: All tests pass.

- [ ] **Step 8: Run full build**

Run: `mvn --batch-mode install`
Expected: All modules compile and all tests pass.

- [ ] **Step 9: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/PatternCompiler.java agency-core/src/main/java/io/casehub/agency/orchestration/pattern/Patterns.java agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmBuilder.java agency-core/src/main/java/io/casehub/agency/orchestration/execution/AbstractExecutionDriver.java agency-core/src/main/java/io/casehub/agency/orchestration/execution/ExecutionEventListener.java agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/compiler/PatternCompilerTest.java
git commit -m "feat(#285): PatternCompiler swarm integration + Patterns.swarm() builder"
```

---

## References

- [2026-10-26-llm-swarm-coordination-design.md] — design spec this plan implements
- `agency-core/src/main/java/io/casehub/agency/orchestration/routing/RoutingDecision.java:8` — sealed routing decision interface
- `agency-core/src/main/java/io/casehub/agency/orchestration/routing/RoutingStrategy.java:3` — routing SPI
- `agency-core/src/main/java/io/casehub/agency/orchestration/aggregation/AggregationStrategy.java:6` — aggregation SPI
- `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/PatternDefinition.java:23` — sealed topology hierarchy
- `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/PatternCompiler.java:47` — spec → ExecutionModel compiler
- `agency-core/src/main/java/io/casehub/agency/orchestration/pattern/Patterns.java:5` — pattern builder entry point
- GitHub #285 — focal issue
- GitHub engine#1112 — swarm execution model dependency
