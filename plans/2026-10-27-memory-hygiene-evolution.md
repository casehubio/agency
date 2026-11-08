# Memory Hygiene Evolution Loop Participant — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #367 — feat: wire MemoryHygieneOrchestrator as evolution loop participant
**Issue group:** #367

**Goal:** Implement five engine-api SPIs that register memory hygiene as a participant in the engine's continuous evolution loop, enabling automated improvement proposals for retention, consolidation, and confidence scoring parameters.

**Architecture:** Six source classes + one record in `io.casehub.agency.social.memory.evolution`, following the established four-piece pattern from `io.casehub.agency.optimisation.prompt.evolution` (#366) plus `CapabilityArea` (first implementor). All share domain ID `"memory-hygiene"`. Consumer-supplied `MemoryHygieneOutcomeProvider` decouples quality data retrieval from proposal logic.

**Tech Stack:** Java 17, JUnit 5, Mockito, AssertJ. Engine-api improvement SPIs. No CDI container in tests.

## Global Constraints

- Follow Apache License 2.0 header from existing files
- All classes are plain Java — no CDI annotations, consumer constructs
- Domain ID constant `"memory-hygiene"` defined in `MemoryHygieneCategoryProvider` and referenced by all classes
- Package: `io.casehub.agency.social.memory.evolution`
- Source dir: `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/`
- Test dir: `agency/src/test/java/io/casehub/agency/social/memory/evolution/`

---

## Batch 1: Foundation — outcome types + stateless SPIs

After this batch: the outcome record, outcome provider SPI, category provider, and conflict strategy are implemented and tested. These have no internal dependencies.

### Task 1: MemoryHygieneOutcome record + MemoryHygieneOutcomeProvider SPI

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneOutcome.java`
- Create: `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneOutcomeProvider.java`

**Interfaces:**
- Consumes: nothing
- Produces: `MemoryHygieneOutcome` record (used by Tasks 3, 5), `MemoryHygieneOutcomeProvider` functional interface (used by Tasks 3, 5)

- [ ] **Step 1: Create MemoryHygieneOutcome record**

```java
package io.casehub.agency.social.memory.evolution;

public record MemoryHygieneOutcome(
    double retentionQuality,
    double evictionRate,
    double consolidationRate,
    int lowRetentionCount,
    int integrityViolations,
    int reflectionsGenerated,
    int sampleSize) {}
```

- [ ] **Step 2: Create MemoryHygieneOutcomeProvider interface**

```java
package io.casehub.agency.social.memory.evolution;

@FunctionalInterface
public interface MemoryHygieneOutcomeProvider {
    MemoryHygieneOutcome outcomes(String agentId);
}
```

- [ ] **Step 3: Compile**

Run: `mvn --batch-mode compile -pl agency-core`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneOutcome.java agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneOutcomeProvider.java
git commit -m "feat(#367): add MemoryHygieneOutcome record and MemoryHygieneOutcomeProvider SPI"
```

### Task 2: MemoryHygieneCategoryProvider + MemoryHygieneConflictStrategy

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneCategoryProvider.java`
- Create: `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneConflictStrategy.java`
- Create: `agency/src/test/java/io/casehub/agency/social/memory/evolution/MemoryHygieneCategoryProviderTest.java`
- Create: `agency/src/test/java/io/casehub/agency/social/memory/evolution/MemoryHygieneConflictStrategyTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `MemoryHygieneCategoryProvider.DOMAIN_ID` constant `"memory-hygiene"`, `MemoryHygieneCategoryProvider.CATEGORY_ID` constant `"memory-hygiene"`, `MemoryHygieneConflictStrategy.AGENT_KEY` constant `"agentId"`

- [ ] **Step 1: Write MemoryHygieneCategoryProviderTest**

```java
package io.casehub.agency.social.memory.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemoryHygieneCategoryProviderTest {

    private final MemoryHygieneCategoryProvider provider = new MemoryHygieneCategoryProvider();

    @Test
    void domainId_returns_memory_hygiene() {
        assertThat(provider.domainId()).isEqualTo("memory-hygiene");
    }

    @Test
    void categories_returns_single_category() {
        var categories = provider.categories();
        assertThat(categories).hasSize(1);
        assertThat(categories.getFirst().id()).isEqualTo("memory-hygiene");
        assertThat(categories.getFirst().domainId()).isEqualTo("memory-hygiene");
    }

    @Test
    void stages_returns_three_ordered_stages_without_gates() {
        var stages = provider.stages();
        assertThat(stages).hasSize(3);
        assertThat(stages.get(0).id()).isEqualTo("evaluate");
        assertThat(stages.get(0).ordinal()).isEqualTo(0);
        assertThat(stages.get(0).gateCheckpoint()).isFalse();
        assertThat(stages.get(1).id()).isEqualTo("tune");
        assertThat(stages.get(1).ordinal()).isEqualTo(1);
        assertThat(stages.get(1).gateCheckpoint()).isFalse();
        assertThat(stages.get(2).id()).isEqualTo("verify");
        assertThat(stages.get(2).ordinal()).isEqualTo(2);
        assertThat(stages.get(2).gateCheckpoint()).isFalse();
    }
}
```

- [ ] **Step 2: Write MemoryHygieneConflictStrategyTest**

```java
package io.casehub.agency.social.memory.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy.ConflictResult;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryHygieneConflictStrategyTest {

    private final MemoryHygieneConflictStrategy strategy = new MemoryHygieneConflictStrategy();

    @Test
    void domainId_returns_memory_hygiene() {
        assertThat(strategy.domainId()).isEqualTo("memory-hygiene");
    }

    @Test
    void clear_when_different_agentIds() {
        var request = new ImprovementRequest("retention-threshold", "memory-hygiene",
                "agent-1", 1, Map.of("agentId", "agent-1"), "memory-hygiene");
        var active = Map.of(
                UUID.randomUUID(), new ImprovementRequest("confidence-weights", "memory-hygiene",
                        "agent-2", 1, Map.of("agentId", "agent-2"), "memory-hygiene"));

        var result = strategy.check(request, active, 10);
        assertThat(result).isInstanceOf(ConflictResult.Clear.class);
    }

    @Test
    void conflicting_when_same_agentId() {
        var blockingId = UUID.randomUUID();
        var request = new ImprovementRequest("retention-threshold", "memory-hygiene",
                "agent-1", 1, Map.of("agentId", "agent-1"), "memory-hygiene");
        var active = Map.of(
                blockingId, new ImprovementRequest("confidence-weights", "memory-hygiene",
                        "agent-1", 1, Map.of("agentId", "agent-1"), "memory-hygiene"));

        var result = strategy.check(request, active, 10);
        assertThat(result).asInstanceOf(type(ConflictResult.Conflicting.class))
                .satisfies(c -> {
                    assertThat(c.blockingImprovementId()).isEqualTo(blockingId);
                    assertThat(c.reason()).contains("agent-1");
                });
    }

    @Test
    void clear_when_request_has_no_agentId_metadata() {
        var request = new ImprovementRequest("retention-threshold", "memory-hygiene",
                "agent-1", 1, Map.of(), "memory-hygiene");
        var active = Map.of(
                UUID.randomUUID(), new ImprovementRequest("confidence-weights", "memory-hygiene",
                        "agent-1", 1, Map.of("agentId", "agent-1"), "memory-hygiene"));

        var result = strategy.check(request, active, 10);
        assertThat(result).isInstanceOf(ConflictResult.Clear.class);
    }

    @Test
    void clear_when_no_active_improvements() {
        var request = new ImprovementRequest("retention-threshold", "memory-hygiene",
                "agent-1", 1, Map.of("agentId", "agent-1"), "memory-hygiene");

        var result = strategy.check(request, Map.of(), 10);
        assertThat(result).isInstanceOf(ConflictResult.Clear.class);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agency -Dtest="MemoryHygieneCategoryProviderTest,MemoryHygieneConflictStrategyTest"`
Expected: COMPILATION ERROR (classes don't exist yet)

- [ ] **Step 4: Implement MemoryHygieneCategoryProvider**

```java
package io.casehub.agency.social.memory.evolution;

import io.casehub.api.model.stigmergy.CategoryDescriptor;
import io.casehub.api.model.stigmergy.StageDescriptor;
import io.casehub.api.spi.improvement.ImprovementCategoryProvider;
import java.util.List;

public class MemoryHygieneCategoryProvider implements ImprovementCategoryProvider {

    static final String DOMAIN_ID = "memory-hygiene";
    static final String CATEGORY_ID = "memory-hygiene";

    private static final List<CategoryDescriptor> CATEGORIES = List.of(
            new CategoryDescriptor(CATEGORY_ID, "Memory Hygiene Tuning",
                    "Agent memory retention, consolidation, and confidence scoring parameter tuning",
                    DOMAIN_ID));

    private static final List<StageDescriptor> STAGES = List.of(
            new StageDescriptor("evaluate", "Evaluate", 0, false, DOMAIN_ID),
            new StageDescriptor("tune", "Tune", 1, false, DOMAIN_ID),
            new StageDescriptor("verify", "Verify", 2, false, DOMAIN_ID));

    @Override
    public String domainId() {
        return DOMAIN_ID;
    }

    @Override
    public List<CategoryDescriptor> categories() {
        return CATEGORIES;
    }

    @Override
    public List<StageDescriptor> stages() {
        return STAGES;
    }
}
```

- [ ] **Step 5: Implement MemoryHygieneConflictStrategy**

```java
package io.casehub.agency.social.memory.evolution;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy;
import java.util.Map;
import java.util.UUID;

public class MemoryHygieneConflictStrategy implements ConflictStrategy {

    static final String AGENT_KEY = "agentId";

    @Override
    public String domainId() {
        return MemoryHygieneCategoryProvider.DOMAIN_ID;
    }

    @Override
    public ConflictResult check(ImprovementRequest request,
                                Map<UUID, ImprovementRequest> activeImprovements,
                                int trivialThreshold) {
        var agentId = request.metadata().get(AGENT_KEY);
        if (agentId == null) {
            return new ConflictResult.Clear();
        }
        for (var entry : activeImprovements.entrySet()) {
            var activeAgentId = entry.getValue().metadata().get(AGENT_KEY);
            if (agentId.equals(activeAgentId)) {
                return new ConflictResult.Conflicting(entry.getKey(),
                        "Memory hygiene tuning already active for agent: " + agentId);
            }
        }
        return new ConflictResult.Clear();
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest="MemoryHygieneCategoryProviderTest,MemoryHygieneConflictStrategyTest"`
Expected: 8 tests PASS

- [ ] **Step 7: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneCategoryProvider.java agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneConflictStrategy.java agency/src/test/java/io/casehub/agency/social/memory/evolution/MemoryHygieneCategoryProviderTest.java agency/src/test/java/io/casehub/agency/social/memory/evolution/MemoryHygieneConflictStrategyTest.java
git commit -m "feat(#367): add MemoryHygieneCategoryProvider and MemoryHygieneConflictStrategy"
```

## Batch 2: Core — proposal source + regression evaluator

After this batch: the two core SPIs (proposal generation and regression detection) are implemented and tested. These depend on Batch 1's types.

### Task 3: MemoryHygieneProposalSource

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneProposalSource.java`
- Create: `agency/src/test/java/io/casehub/agency/social/memory/evolution/MemoryHygieneProposalSourceTest.java`

**Interfaces:**
- Consumes: `MemoryHygieneOutcomeProvider.outcomes(String) → MemoryHygieneOutcome` (Task 1), `MemoryHygieneCategoryProvider.DOMAIN_ID` / `CATEGORY_ID` (Task 2), `MemoryHygieneConflictStrategy.AGENT_KEY` (Task 2)
- Produces: `MemoryHygieneProposalSource` implementing `ImprovementProposalSource` — `propose(UUID, String, ImprovementConfig) → List<ImprovementRequest>`. Static `computeQuality(MemoryHygieneOutcome) → double`. Static `weakestDimension(MemoryHygieneOutcome) → String`.

- [ ] **Step 1: Write MemoryHygieneProposalSourceTest**

```java
package io.casehub.agency.social.memory.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.improvement.ImprovementConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryHygieneProposalSourceTest {

    private MemoryHygieneProposalSource source(MemoryHygieneOutcomeProvider provider) {
        return new MemoryHygieneProposalSource(provider, 0.7, 20);
    }

    private MemoryHygieneProposalSource source(MemoryHygieneOutcomeProvider provider,
                                                double threshold, int minSample) {
        return new MemoryHygieneProposalSource(provider, threshold, minSample);
    }

    @Test
    void sourceId_and_domainId() {
        var s = source(agentId -> new MemoryHygieneOutcome(1.0, 0.0, 1.0, 0, 0, 5, 50));
        assertThat(s.sourceId()).isEqualTo("memory-hygiene");
        assertThat(s.domainId()).isEqualTo("memory-hygiene");
    }

    @Test
    void proposes_when_quality_below_threshold() {
        var provider = (MemoryHygieneOutcomeProvider) agentId ->
                new MemoryHygieneOutcome(0.3, 0.5, 0.1, 15, 2, 1, 50);
        var proposals = source(provider).propose(UUID.randomUUID(), "agent-1",
                new ImprovementConfig(null, null, null, null, null));

        assertThat(proposals).hasSize(1);
        assertThat(proposals.getFirst().category()).isEqualTo("memory-hygiene");
        assertThat(proposals.getFirst().metadata().get("agentId")).isEqualTo("agent-1");
        assertThat(proposals.getFirst().domainId()).isEqualTo("memory-hygiene");
    }

    @Test
    void no_proposal_when_quality_healthy() {
        var provider = (MemoryHygieneOutcomeProvider) agentId ->
                new MemoryHygieneOutcome(0.95, 0.02, 0.8, 0, 0, 10, 50);
        var proposals = source(provider).propose(UUID.randomUUID(), "agent-1",
                new ImprovementConfig(null, null, null, null, null));

        assertThat(proposals).isEmpty();
    }

    @Test
    void no_proposal_when_insufficient_samples() {
        var provider = (MemoryHygieneOutcomeProvider) agentId ->
                new MemoryHygieneOutcome(0.1, 0.9, 0.0, 20, 5, 0, 5);
        var proposals = source(provider).propose(UUID.randomUUID(), "agent-1",
                new ImprovementConfig(null, null, null, null, null));

        assertThat(proposals).isEmpty();
    }

    @Test
    void weakest_dimension_retention() {
        var provider = (MemoryHygieneOutcomeProvider) agentId ->
                new MemoryHygieneOutcome(0.2, 0.1, 0.8, 5, 0, 3, 50);
        var proposals = source(provider).propose(UUID.randomUUID(), "agent-1",
                new ImprovementConfig(null, null, null, null, null));

        assertThat(proposals).hasSize(1);
        assertThat(proposals.getFirst().improvementType()).isEqualTo("retention-threshold");
    }

    @Test
    void weakest_dimension_eviction() {
        var provider = (MemoryHygieneOutcomeProvider) agentId ->
                new MemoryHygieneOutcome(0.8, 0.9, 0.8, 5, 0, 3, 50);
        var proposals = source(provider, 0.7, 20).propose(UUID.randomUUID(), "agent-1",
                new ImprovementConfig(null, null, null, null, null));

        assertThat(proposals).hasSize(1);
        assertThat(proposals.getFirst().improvementType()).isEqualTo("confidence-weights");
    }

    @Test
    void weakest_dimension_consolidation() {
        var provider = (MemoryHygieneOutcomeProvider) agentId ->
                new MemoryHygieneOutcome(0.8, 0.1, 0.1, 5, 0, 3, 50);
        var proposals = source(provider, 0.7, 20).propose(UUID.randomUUID(), "agent-1",
                new ImprovementConfig(null, null, null, null, null));

        assertThat(proposals).hasSize(1);
        assertThat(proposals.getFirst().improvementType()).isEqualTo("consolidation-timing");
    }

    @Test
    void compute_quality_perfect_scores() {
        var outcome = new MemoryHygieneOutcome(1.0, 0.0, 1.0, 0, 0, 10, 100);
        assertThat(MemoryHygieneProposalSource.computeQuality(outcome)).isEqualTo(1.0);
    }

    @Test
    void compute_quality_worst_scores() {
        var outcome = new MemoryHygieneOutcome(0.0, 1.0, 0.0, 20, 5, 0, 100);
        assertThat(MemoryHygieneProposalSource.computeQuality(outcome)).isEqualTo(0.0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest="MemoryHygieneProposalSourceTest"`
Expected: COMPILATION ERROR

- [ ] **Step 3: Implement MemoryHygieneProposalSource**

```java
package io.casehub.agency.social.memory.evolution;

import io.casehub.api.model.improvement.ImprovementConfig;
import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ImprovementProposalSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MemoryHygieneProposalSource implements ImprovementProposalSource {

    private static final double TUNING_MARGIN = 0.1;

    private final MemoryHygieneOutcomeProvider outcomeProvider;
    private final double qualityThreshold;
    private final int minSampleSize;

    public MemoryHygieneProposalSource(MemoryHygieneOutcomeProvider outcomeProvider,
                                        double qualityThreshold, int minSampleSize) {
        this.outcomeProvider = outcomeProvider;
        this.qualityThreshold = qualityThreshold;
        this.minSampleSize = minSampleSize;
    }

    public MemoryHygieneProposalSource(MemoryHygieneOutcomeProvider outcomeProvider) {
        this(outcomeProvider, 0.7, 20);
    }

    @Override
    public String sourceId() {
        return "memory-hygiene";
    }

    @Override
    public String domainId() {
        return MemoryHygieneCategoryProvider.DOMAIN_ID;
    }

    @Override
    public List<ImprovementRequest> propose(UUID caseId, String tenancyId,
                                            ImprovementConfig config) {
        var outcome = outcomeProvider.outcomes(tenancyId);
        if (outcome.sampleSize() < minSampleSize) {
            return List.of();
        }

        double quality = computeQuality(outcome);
        if (quality >= qualityThreshold + TUNING_MARGIN) {
            return List.of();
        }

        return List.of(new ImprovementRequest(
                weakestDimension(outcome),
                MemoryHygieneCategoryProvider.CATEGORY_ID,
                tenancyId,
                1,
                Map.of(MemoryHygieneConflictStrategy.AGENT_KEY, tenancyId,
                        "currentQuality", String.format("%.3f", quality)),
                MemoryHygieneCategoryProvider.DOMAIN_ID));
    }

    static double computeQuality(MemoryHygieneOutcome outcome) {
        return outcome.retentionQuality() * 0.4
                + (1.0 - outcome.evictionRate()) * 0.3
                + outcome.consolidationRate() * 0.2
                + (outcome.integrityViolations() == 0 ? 1.0 : 0.0) * 0.1;
    }

    static String weakestDimension(MemoryHygieneOutcome outcome) {
        double retentionScore = outcome.retentionQuality();
        double evictionScore = 1.0 - outcome.evictionRate();
        double consolidationScore = outcome.consolidationRate();

        if (retentionScore <= evictionScore && retentionScore <= consolidationScore) {
            return "retention-threshold";
        }
        if (evictionScore <= consolidationScore) {
            return "confidence-weights";
        }
        return "consolidation-timing";
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest="MemoryHygieneProposalSourceTest"`
Expected: 9 tests PASS

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneProposalSource.java agency/src/test/java/io/casehub/agency/social/memory/evolution/MemoryHygieneProposalSourceTest.java
git commit -m "feat(#367): add MemoryHygieneProposalSource — evolution loop proposal integration"
```

### Task 4: MemoryHygieneRegressionEvaluator

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneRegressionEvaluator.java`
- Create: `agency/src/test/java/io/casehub/agency/social/memory/evolution/MemoryHygieneRegressionEvaluatorTest.java`

**Interfaces:**
- Consumes: `MemoryHygieneCategoryProvider.DOMAIN_ID` (Task 2)
- Produces: `MemoryHygieneRegressionEvaluator` implementing `RegressionEvaluator` — `evaluate(UUID, HealthScoreSnapshot, HealthScoreSnapshot, String) → RegressionVerdict`. Static `PREFIX = "memory-hygiene:"`.

- [ ] **Step 1: Write MemoryHygieneRegressionEvaluatorTest**

```java
package io.casehub.agency.social.memory.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.casehub.api.model.stigmergy.HealthScoreSnapshot;
import io.casehub.api.model.stigmergy.RegressionVerdict;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryHygieneRegressionEvaluatorTest {

    private final MemoryHygieneRegressionEvaluator evaluator = new MemoryHygieneRegressionEvaluator();

    @Test
    void evaluatorId_and_domainId() {
        assertThat(evaluator.evaluatorId()).isEqualTo("memory-hygiene");
        assertThat(evaluator.domainId()).isEqualTo("memory-hygiene");
    }

    @Test
    void detects_regression_above_threshold() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(), Map.of(
                "memory-hygiene:retention-quality", 0.9,
                "memory-hygiene:eviction-rate", 0.85));
        var current = new HealthScoreSnapshot(0.5, Instant.now(), Map.of(
                "memory-hygiene:retention-quality", 0.7,
                "memory-hygiene:eviction-rate", 0.85));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "memory-hygiene");
        assertThat(verdict).asInstanceOf(type(RegressionVerdict.Detected.class))
                .satisfies(d -> {
                    assertThat(d.confidence()).isGreaterThan(0.1);
                    assertThat(d.reason()).contains("retention-quality");
                });
    }

    @Test
    void no_regression_when_drop_below_threshold() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(), Map.of(
                "memory-hygiene:retention-quality", 0.9,
                "memory-hygiene:eviction-rate", 0.85));
        var current = new HealthScoreSnapshot(0.75, Instant.now(), Map.of(
                "memory-hygiene:retention-quality", 0.85,
                "memory-hygiene:eviction-rate", 0.84));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "memory-hygiene");
        assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
    }

    @Test
    void ignores_non_prefixed_keys() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(), Map.of(
                "prompt:team-formation", 0.9,
                "memory-hygiene:retention-quality", 0.8));
        var current = new HealthScoreSnapshot(0.5, Instant.now(), Map.of(
                "prompt:team-formation", 0.2,
                "memory-hygiene:retention-quality", 0.78));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "memory-hygiene");
        assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
    }

    @Test
    void no_regression_when_key_missing_from_current() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(), Map.of(
                "memory-hygiene:retention-quality", 0.9));
        var current = new HealthScoreSnapshot(0.8, Instant.now(), Map.of());

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "memory-hygiene");
        assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
    }

    @Test
    void reports_worst_regression_dimension() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(), Map.of(
                "memory-hygiene:retention-quality", 0.9,
                "memory-hygiene:integrity", 0.95));
        var current = new HealthScoreSnapshot(0.5, Instant.now(), Map.of(
                "memory-hygiene:retention-quality", 0.75,
                "memory-hygiene:integrity", 0.5));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "memory-hygiene");
        assertThat(verdict).asInstanceOf(type(RegressionVerdict.Detected.class))
                .satisfies(d -> assertThat(d.reason()).contains("integrity"));
    }

    @Test
    void custom_threshold() {
        var evaluator = new MemoryHygieneRegressionEvaluator(0.3);
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(), Map.of(
                "memory-hygiene:retention-quality", 0.9));
        var current = new HealthScoreSnapshot(0.7, Instant.now(), Map.of(
                "memory-hygiene:retention-quality", 0.72));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "memory-hygiene");
        assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest="MemoryHygieneRegressionEvaluatorTest"`
Expected: COMPILATION ERROR

- [ ] **Step 3: Implement MemoryHygieneRegressionEvaluator**

```java
package io.casehub.agency.social.memory.evolution;

import io.casehub.api.model.stigmergy.HealthScoreSnapshot;
import io.casehub.api.model.stigmergy.RegressionVerdict;
import io.casehub.api.spi.improvement.RegressionEvaluator;
import java.util.UUID;

public class MemoryHygieneRegressionEvaluator implements RegressionEvaluator {

    static final String PREFIX = "memory-hygiene:";
    private final double regressionThreshold;

    public MemoryHygieneRegressionEvaluator(double regressionThreshold) {
        this.regressionThreshold = regressionThreshold;
    }

    public MemoryHygieneRegressionEvaluator() {
        this(0.1);
    }

    @Override
    public String evaluatorId() {
        return "memory-hygiene";
    }

    @Override
    public String domainId() {
        return MemoryHygieneCategoryProvider.DOMAIN_ID;
    }

    @Override
    public RegressionVerdict evaluate(UUID caseId, HealthScoreSnapshot baseline,
                                      HealthScoreSnapshot current, String category) {
        double maxDrop = 0.0;
        String worstDimension = null;

        for (var entry : baseline.componentScores().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX)) continue;
            var currentScore = current.componentScores().get(entry.getKey());
            if (currentScore == null) continue;
            double delta = entry.getValue() - currentScore;
            if (delta > maxDrop) {
                maxDrop = delta;
                worstDimension = entry.getKey().substring(PREFIX.length());
            }
        }

        if (maxDrop > regressionThreshold && worstDimension != null) {
            return new RegressionVerdict.Detected(maxDrop,
                    "Memory hygiene regression in " + worstDimension
                            + ": dropped by " + String.format("%.3f", maxDrop));
        }
        return new RegressionVerdict.NoRegression();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest="MemoryHygieneRegressionEvaluatorTest"`
Expected: 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneRegressionEvaluator.java agency/src/test/java/io/casehub/agency/social/memory/evolution/MemoryHygieneRegressionEvaluatorTest.java
git commit -m "feat(#367): add MemoryHygieneRegressionEvaluator — memory quality regression detection"
```

## Batch 3: CapabilityArea + docs

After this batch: all five SPIs are implemented and tested. CLAUDE.md is updated. Full build passes.

### Task 5: MemoryHygieneCapabilityArea

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneCapabilityArea.java`
- Create: `agency/src/test/java/io/casehub/agency/social/memory/evolution/MemoryHygieneCapabilityAreaTest.java`

**Interfaces:**
- Consumes: `MemoryHygieneOutcomeProvider.outcomes(String) → MemoryHygieneOutcome` (Task 1), `MemoryHygieneProposalSource.computeQuality(MemoryHygieneOutcome) → double` (Task 3)
- Produces: `MemoryHygieneCapabilityArea` implementing `CapabilityArea` — `assess(UUID, String) → CapabilityAreaAssessment`

- [ ] **Step 1: Write MemoryHygieneCapabilityAreaTest**

```java
package io.casehub.agency.social.memory.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment.LandscapePosition;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryHygieneCapabilityAreaTest {

    @Test
    void id_name_description() {
        var area = new MemoryHygieneCapabilityArea(
                agentId -> new MemoryHygieneOutcome(1.0, 0.0, 1.0, 0, 0, 5, 50));
        assertThat(area.id()).isEqualTo("memory-hygiene");
        assertThat(area.name()).isEqualTo("Memory Hygiene");
        assertThat(area.description()).isNotBlank();
    }

    @Test
    void assess_ahead_when_high_quality() {
        var area = new MemoryHygieneCapabilityArea(
                agentId -> new MemoryHygieneOutcome(0.95, 0.02, 0.9, 0, 0, 10, 50));
        var assessment = area.assess(UUID.randomUUID(), "agent-1");

        assertThat(assessment.areaId()).isEqualTo("memory-hygiene");
        assertThat(assessment.healthScore()).isGreaterThanOrEqualTo(0.8);
        assertThat(assessment.landscapePosition()).isEqualTo(LandscapePosition.AHEAD);
        assertThat(assessment.costEstimate()).isEqualTo(0.2, within(0.01));
    }

    @Test
    void assess_at_parity_when_moderate_quality() {
        var area = new MemoryHygieneCapabilityArea(
                agentId -> new MemoryHygieneOutcome(0.7, 0.2, 0.5, 3, 0, 5, 50));
        var assessment = area.assess(UUID.randomUUID(), "agent-1");

        assertThat(assessment.landscapePosition()).isEqualTo(LandscapePosition.AT_PARITY);
    }

    @Test
    void assess_behind_when_low_quality() {
        var area = new MemoryHygieneCapabilityArea(
                agentId -> new MemoryHygieneOutcome(0.3, 0.5, 0.2, 10, 2, 1, 50));
        var assessment = area.assess(UUID.randomUUID(), "agent-1");

        assertThat(assessment.landscapePosition()).isEqualTo(LandscapePosition.BEHIND);
    }

    @Test
    void assess_absent_when_very_low_quality() {
        var area = new MemoryHygieneCapabilityArea(
                agentId -> new MemoryHygieneOutcome(0.0, 1.0, 0.0, 20, 5, 0, 50));
        var assessment = area.assess(UUID.randomUUID(), "agent-1");

        assertThat(assessment.landscapePosition()).isEqualTo(LandscapePosition.ABSENT);
    }

    @Test
    void roi_increases_with_lower_health() {
        var highHealth = new MemoryHygieneCapabilityArea(
                agentId -> new MemoryHygieneOutcome(0.95, 0.02, 0.9, 0, 0, 10, 50));
        var lowHealth = new MemoryHygieneCapabilityArea(
                agentId -> new MemoryHygieneOutcome(0.2, 0.6, 0.1, 15, 3, 1, 50));

        var highAssessment = highHealth.assess(UUID.randomUUID(), "agent-1");
        var lowAssessment = lowHealth.assess(UUID.randomUUID(), "agent-1");

        assertThat(lowAssessment.roi()).isGreaterThan(highAssessment.roi());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest="MemoryHygieneCapabilityAreaTest"`
Expected: COMPILATION ERROR

- [ ] **Step 3: Implement MemoryHygieneCapabilityArea**

```java
package io.casehub.agency.social.memory.evolution;

import io.casehub.api.model.stigmergy.CapabilityAreaAssessment;
import io.casehub.api.model.stigmergy.CapabilityAreaAssessment.LandscapePosition;
import io.casehub.api.spi.improvement.CapabilityArea;
import java.time.Instant;
import java.util.UUID;

public class MemoryHygieneCapabilityArea implements CapabilityArea {

    private static final double COST_ESTIMATE = 0.2;

    private final MemoryHygieneOutcomeProvider outcomeProvider;

    public MemoryHygieneCapabilityArea(MemoryHygieneOutcomeProvider outcomeProvider) {
        this.outcomeProvider = outcomeProvider;
    }

    @Override
    public String id() {
        return "memory-hygiene";
    }

    @Override
    public String name() {
        return "Memory Hygiene";
    }

    @Override
    public String description() {
        return "Agent memory retention, consolidation, and integrity";
    }

    @Override
    public CapabilityAreaAssessment assess(UUID caseId, String tenancyId) {
        var outcome = outcomeProvider.outcomes(tenancyId);
        double healthScore = MemoryHygieneProposalSource.computeQuality(outcome);
        double impact = 1.0 - healthScore;
        double roi = impact / COST_ESTIMATE;

        return new CapabilityAreaAssessment(
                id(), healthScore, mapPosition(healthScore),
                impact, COST_ESTIMATE, roi, Instant.now());
    }

    private static LandscapePosition mapPosition(double healthScore) {
        if (healthScore >= 0.8) return LandscapePosition.AHEAD;
        if (healthScore >= 0.6) return LandscapePosition.AT_PARITY;
        if (healthScore >= 0.3) return LandscapePosition.BEHIND;
        return LandscapePosition.ABSENT;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest="MemoryHygieneCapabilityAreaTest"`
Expected: 6 tests PASS

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test -pl agency -Dtest="MemoryHygiene*Test"`
Expected: All 30 tests PASS

- [ ] **Step 6: Run full build**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/social/memory/evolution/MemoryHygieneCapabilityArea.java agency/src/test/java/io/casehub/agency/social/memory/evolution/MemoryHygieneCapabilityAreaTest.java
git commit -m "feat(#367): add MemoryHygieneCapabilityArea — first CapabilityArea SPI implementation"
```

### Task 6: Update CLAUDE.md key directories

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: all classes from Tasks 1-5
- Produces: updated documentation

- [ ] **Step 1: Add evolution package to Key Directories table in CLAUDE.md**

Add these two rows to the Key Directories table after the existing `agency-core/.../social/memory/` entries:

```markdown
| `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/` | Memory hygiene evolution loop — `MemoryHygieneOutcomeProvider` (`@FunctionalInterface`), `MemoryHygieneOutcome` (quality metrics record), `MemoryHygieneProposalSource` (`ImprovementProposalSource` — composite quality scoring with weakest-dimension routing), `MemoryHygieneRegressionEvaluator` (`RegressionEvaluator` — `memory-hygiene:*` component score monitoring), `MemoryHygieneCategoryProvider` (`ImprovementCategoryProvider` — evaluate/tune/verify stages), `MemoryHygieneConflictStrategy` (`ConflictStrategy` — per-agent mutex), `MemoryHygieneCapabilityArea` (`CapabilityArea` — health scoring with landscape position mapping) |
| `agency/src/test/java/io/casehub/agency/social/memory/evolution/` | Tests for memory hygiene evolution loop |
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(#367): document memory hygiene evolution package in CLAUDE.md"
```

## References

- [2026-10-27-memory-hygiene-evolution-design.md] — design spec this plan implements
- [agency-core/.../optimisation/prompt/evolution/PromptOptimisationProposalSource.java] — proposal source pattern
- [agency-core/.../optimisation/prompt/evolution/PromptRegressionEvaluator.java] — regression evaluator pattern
- [agency-core/.../optimisation/prompt/evolution/PromptOptimisationCategoryProvider.java] — category provider pattern
- [agency-core/.../optimisation/prompt/evolution/PromptConflictStrategy.java] — conflict strategy pattern
- [agency-core/.../optimisation/prompt/evolution/PromptOutcomeProvider.java] — outcome provider pattern
- [engine/api/.../spi/improvement/ImprovementProposalSource.java] — engine SPI
- [engine/api/.../spi/improvement/RegressionEvaluator.java] — engine SPI
- [engine/api/.../spi/improvement/ImprovementCategoryProvider.java] — engine SPI
- [engine/api/.../spi/improvement/ConflictStrategy.java] — engine SPI
- [engine/api/.../spi/improvement/CapabilityArea.java] — engine SPI
- [engine/api/.../model/improvement/ImprovementRequest.java] — proposal record
- [engine/api/.../model/stigmergy/CapabilityAreaAssessment.java] — capability assessment record
- casehubio/agency#367 — focal issue
- casehubio/agency#366 — prompt optimisation evolution (template)
- casehubio/engine#1115 — evolution loop infrastructure
