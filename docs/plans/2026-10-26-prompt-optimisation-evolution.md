# Prompt Optimisation Evolution Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #366 — Unify prompt optimisation batch with engine evolution loop
**Issue group:** #366

**Goal:** Register prompt optimisation as a domain participant in the engine evolution loop via four SPI implementations and one support type.

**Architecture:** Implement `ImprovementProposalSource`, `RegressionEvaluator`, `ImprovementCategoryProvider`, and `ConflictStrategy` for a `"prompt-optimisation"` domain in a new `evolution` sub-package. Each SPI impl is a focused adapter delegating to existing agency types. `PromptOptimisationBatch` retains its core logic — no structural changes.

**Tech Stack:** Java 17, JUnit 5, Mockito, engine-api SPIs

## Global Constraints

- All new code in `agency-core` module (production) and `agency` module (tests)
- Package: `io.casehub.agency.optimisation.prompt.evolution`
- No CDI annotations — agency has no Jandex index; consumers register manually
- No Quarkus runtime in tests — plain JUnit 5 + Mockito
- Follow existing agency naming conventions (British spelling: optimisation, not optimization)
- Domain ID constant: `"prompt-optimisation"` — used by all four SPI implementations

---

## Batch 1: Core SPIs — Category Provider, Conflict Strategy, and Outcome Provider

### Task 1: PromptOutcomeProvider functional interface + PromptOptimisationCategoryProvider + PromptConflictStrategy

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/PromptOutcomeProvider.java`
- Create: `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/PromptOptimisationCategoryProvider.java`
- Create: `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/PromptConflictStrategy.java`
- Test: `agency/src/test/java/io/casehub/agency/optimisation/prompt/evolution/PromptOptimisationCategoryProviderTest.java`
- Test: `agency/src/test/java/io/casehub/agency/optimisation/prompt/evolution/PromptConflictStrategyTest.java`

**Interfaces:**
- Consumes: `ImprovementCategoryProvider` (engine-api), `ConflictStrategy` (engine-api), `CategoryDescriptor` (engine-api), `StageDescriptor` (engine-api), `ImprovementRequest` (engine-api), `VariantOutcome` (agency)
- Produces: `PromptOutcomeProvider` (functional interface — `List<VariantOutcome> outcomes(String signatureId)`), `PromptOptimisationCategoryProvider` (returns domain `"prompt-optimisation"`, 1 category, 4 stages), `PromptConflictStrategy` (signature-level mutual exclusion via metadata key `"signatureId"`)

- [ ] **Step 1: Create PromptOutcomeProvider functional interface**

```java
package io.casehub.agency.optimisation.prompt.evolution;

import io.casehub.agency.optimisation.prompt.VariantOutcome;
import java.util.List;

@FunctionalInterface
public interface PromptOutcomeProvider {
    List<VariantOutcome> outcomes(String signatureId);
}
```

- [ ] **Step 2: Write failing tests for PromptOptimisationCategoryProvider**

```java
package io.casehub.agency.optimisation.prompt.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class PromptOptimisationCategoryProviderTest {

    private final PromptOptimisationCategoryProvider provider = new PromptOptimisationCategoryProvider();

    @Test
    void domainId_returns_prompt_optimisation() {
        assertThat(provider.domainId()).isEqualTo("prompt-optimisation");
    }

    @Test
    void categories_returns_single_prompt_tuning_category() {
        var categories = provider.categories();
        assertThat(categories).hasSize(1);
        assertThat(categories.getFirst().id()).isEqualTo("prompt-tuning");
        assertThat(categories.getFirst().domainId()).isEqualTo("prompt-optimisation");
    }

    @Test
    void stages_returns_four_ordered_stages_without_gates() {
        var stages = provider.stages();
        assertThat(stages).hasSize(4);
        assertThat(stages.stream().map(s -> s.id()).toList())
                .containsExactly("evaluate", "optimise", "promote", "outcome-recording");
        assertThat(stages.stream().allMatch(s -> !s.gateCheckpoint())).isTrue();
        for (int i = 0; i < stages.size(); i++) {
            assertThat(stages.get(i).ordinal()).isEqualTo(i);
        }
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agency -Dtest=PromptOptimisationCategoryProviderTest`
Expected: Compilation failure — class does not exist

- [ ] **Step 4: Implement PromptOptimisationCategoryProvider**

```java
package io.casehub.agency.optimisation.prompt.evolution;

import io.casehub.api.model.stigmergy.CategoryDescriptor;
import io.casehub.api.model.stigmergy.StageDescriptor;
import io.casehub.api.spi.improvement.ImprovementCategoryProvider;
import java.util.List;

public class PromptOptimisationCategoryProvider implements ImprovementCategoryProvider {

    static final String DOMAIN_ID = "prompt-optimisation";
    static final String CATEGORY_ID = "prompt-tuning";

    private static final List<CategoryDescriptor> CATEGORIES = List.of(
            new CategoryDescriptor(CATEGORY_ID, "Prompt Tuning",
                    "LLM prompt variant optimisation via few-shot curation and instruction refinement",
                    DOMAIN_ID));

    private static final List<StageDescriptor> STAGES = List.of(
            new StageDescriptor("evaluate", "Evaluate", 0, false, DOMAIN_ID),
            new StageDescriptor("optimise", "Optimise", 1, false, DOMAIN_ID),
            new StageDescriptor("promote", "Promote", 2, false, DOMAIN_ID),
            new StageDescriptor("outcome-recording", "Outcome Recording", 3, false, DOMAIN_ID));

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

- [ ] **Step 5: Run category provider tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest=PromptOptimisationCategoryProviderTest`
Expected: 3 tests PASS

- [ ] **Step 6: Write failing tests for PromptConflictStrategy**

```java
package io.casehub.agency.optimisation.prompt.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy.ConflictResult;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromptConflictStrategyTest {

    private final PromptConflictStrategy strategy = new PromptConflictStrategy();

    @Test
    void domainId_returns_prompt_optimisation() {
        assertThat(strategy.domainId()).isEqualTo("prompt-optimisation");
    }

    @Test
    void clear_when_no_active_improvements_share_signature() {
        var request = new ImprovementRequest("prompt-tuning", "prompt-tuning",
                "swarm-team-formation", 1,
                Map.of("signatureId", "swarm-team-formation"), "prompt-optimisation");
        var active = Map.of(
                UUID.randomUUID(), new ImprovementRequest("prompt-tuning", "prompt-tuning",
                        "swarm-provisioning", 1,
                        Map.of("signatureId", "swarm-provisioning"), "prompt-optimisation"));

        var result = strategy.check(request, active, 10);
        assertThat(result).isInstanceOf(ConflictResult.Clear.class);
    }

    @Test
    void conflicting_when_active_improvement_has_same_signature() {
        var blockingId = UUID.randomUUID();
        var request = new ImprovementRequest("prompt-tuning", "prompt-tuning",
                "swarm-team-formation", 1,
                Map.of("signatureId", "swarm-team-formation"), "prompt-optimisation");
        var active = Map.of(
                blockingId, new ImprovementRequest("prompt-tuning", "prompt-tuning",
                        "swarm-team-formation", 1,
                        Map.of("signatureId", "swarm-team-formation"), "prompt-optimisation"));

        var result = strategy.check(request, active, 10);
        assertThat(result).asInstanceOf(type(ConflictResult.Conflicting.class))
                .satisfies(c -> {
                    assertThat(c.blockingImprovementId()).isEqualTo(blockingId);
                    assertThat(c.reason()).contains("swarm-team-formation");
                });
    }

    @Test
    void clear_when_active_improvement_has_no_signature_metadata() {
        var request = new ImprovementRequest("prompt-tuning", "prompt-tuning",
                "swarm-team-formation", 1,
                Map.of("signatureId", "swarm-team-formation"), "prompt-optimisation");
        var active = Map.of(
                UUID.randomUUID(), new ImprovementRequest("prompt-tuning", "prompt-tuning",
                        "other-target", 1, Map.of(), "prompt-optimisation"));

        var result = strategy.check(request, active, 10);
        assertThat(result).isInstanceOf(ConflictResult.Clear.class);
    }
}
```

- [ ] **Step 7: Run conflict strategy tests to verify they fail**

Run: `mvn --batch-mode test -pl agency -Dtest=PromptConflictStrategyTest`
Expected: Compilation failure — class does not exist

- [ ] **Step 8: Implement PromptConflictStrategy**

```java
package io.casehub.agency.optimisation.prompt.evolution;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy;
import java.util.Map;
import java.util.UUID;

public class PromptConflictStrategy implements ConflictStrategy {

    static final String SIGNATURE_KEY = "signatureId";

    @Override
    public String domainId() {
        return PromptOptimisationCategoryProvider.DOMAIN_ID;
    }

    @Override
    public ConflictResult check(ImprovementRequest request,
                                Map<UUID, ImprovementRequest> activeImprovements,
                                int trivialThreshold) {
        var signatureId = request.metadata().get(SIGNATURE_KEY);
        if (signatureId == null) {
            return new ConflictResult.Clear();
        }
        for (var entry : activeImprovements.entrySet()) {
            var activeSignature = entry.getValue().metadata().get(SIGNATURE_KEY);
            if (signatureId.equals(activeSignature)) {
                return new ConflictResult.Conflicting(entry.getKey(),
                        "Prompt tuning already active for signature: " + signatureId);
            }
        }
        return new ConflictResult.Clear();
    }
}
```

- [ ] **Step 9: Run all batch 1 tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest="PromptOptimisationCategoryProviderTest,PromptConflictStrategyTest"`
Expected: 7 tests PASS

- [ ] **Step 10: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/ agency/src/test/java/io/casehub/agency/optimisation/prompt/evolution/
git commit -m "feat(#366): add PromptOutcomeProvider, PromptOptimisationCategoryProvider, and PromptConflictStrategy

Implement ImprovementCategoryProvider and ConflictStrategy for the
prompt-optimisation domain. PromptOutcomeProvider is a @FunctionalInterface
SPI for consumer-supplied outcome data retrieval.

Refs casehubio/agency#366

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 2: Regression Evaluator and Proposal Source

### Task 2: PromptRegressionEvaluator

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/PromptRegressionEvaluator.java`
- Test: `agency/src/test/java/io/casehub/agency/optimisation/prompt/evolution/PromptRegressionEvaluatorTest.java`

**Interfaces:**
- Consumes: `RegressionEvaluator` (engine-api), `HealthScoreSnapshot` (engine-api), `RegressionVerdict` (engine-api)
- Produces: `PromptRegressionEvaluator` (evaluatorId `"prompt-quality"`, domainId `"prompt-optimisation"`, detects regression when per-signature quality drops by more than threshold)

- [ ] **Step 1: Write failing tests for PromptRegressionEvaluator**

```java
package io.casehub.agency.optimisation.prompt.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.casehub.api.model.stigmergy.HealthScoreSnapshot;
import io.casehub.api.model.stigmergy.RegressionVerdict;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromptRegressionEvaluatorTest {

    private final PromptRegressionEvaluator evaluator = new PromptRegressionEvaluator(0.1);

    @Test
    void domainId_and_evaluatorId() {
        assertThat(evaluator.domainId()).isEqualTo("prompt-optimisation");
        assertThat(evaluator.evaluatorId()).isEqualTo("prompt-quality");
    }

    @Test
    void detects_regression_when_signature_quality_drops_beyond_threshold() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(),
                Map.of("prompt:team-formation", 0.85, "prompt:provisioning", 0.9));
        var current = new HealthScoreSnapshot(0.6, Instant.now(),
                Map.of("prompt:team-formation", 0.7, "prompt:provisioning", 0.9));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "prompt-tuning");
        assertThat(verdict).asInstanceOf(type(RegressionVerdict.Detected.class))
                .satisfies(d -> {
                    assertThat(d.confidence()).isGreaterThan(0.1);
                    assertThat(d.reason()).contains("team-formation");
                });
    }

    @Test
    void no_regression_when_quality_stable() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(),
                Map.of("prompt:team-formation", 0.85));
        var current = new HealthScoreSnapshot(0.78, Instant.now(),
                Map.of("prompt:team-formation", 0.83));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "prompt-tuning");
        assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
    }

    @Test
    void no_regression_when_no_prompt_keys_in_snapshots() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(), Map.of("other", 0.9));
        var current = new HealthScoreSnapshot(0.6, Instant.now(), Map.of("other", 0.5));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "prompt-tuning");
        assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agency -Dtest=PromptRegressionEvaluatorTest`
Expected: Compilation failure — class does not exist

- [ ] **Step 3: Implement PromptRegressionEvaluator**

```java
package io.casehub.agency.optimisation.prompt.evolution;

import io.casehub.api.model.stigmergy.HealthScoreSnapshot;
import io.casehub.api.model.stigmergy.RegressionVerdict;
import io.casehub.api.spi.improvement.RegressionEvaluator;
import java.util.UUID;

public class PromptRegressionEvaluator implements RegressionEvaluator {

    static final String PREFIX = "prompt:";
    private final double regressionThreshold;

    public PromptRegressionEvaluator(double regressionThreshold) {
        this.regressionThreshold = regressionThreshold;
    }

    public PromptRegressionEvaluator() {
        this(0.1);
    }

    @Override
    public String evaluatorId() {
        return "prompt-quality";
    }

    @Override
    public String domainId() {
        return PromptOptimisationCategoryProvider.DOMAIN_ID;
    }

    @Override
    public RegressionVerdict evaluate(UUID caseId, HealthScoreSnapshot baseline,
                                      HealthScoreSnapshot current, String category) {
        double maxDrop = 0.0;
        String worstSignature = null;

        for (var entry : baseline.componentScores().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX)) continue;
            var currentScore = current.componentScores().get(entry.getKey());
            if (currentScore == null) continue;
            double delta = entry.getValue() - currentScore;
            if (delta > maxDrop) {
                maxDrop = delta;
                worstSignature = entry.getKey().substring(PREFIX.length());
            }
        }

        if (maxDrop > regressionThreshold && worstSignature != null) {
            return new RegressionVerdict.Detected(maxDrop,
                    "Prompt quality regression in " + worstSignature
                            + ": dropped by " + String.format("%.3f", maxDrop));
        }
        return new RegressionVerdict.NoRegression();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest=PromptRegressionEvaluatorTest`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/PromptRegressionEvaluator.java agency/src/test/java/io/casehub/agency/optimisation/prompt/evolution/PromptRegressionEvaluatorTest.java
git commit -m "feat(#366): add PromptRegressionEvaluator — prompt quality regression detection

Implements RegressionEvaluator for prompt-optimisation domain. Compares
per-signature quality scores in HealthScoreSnapshot componentScores
(keyed with 'prompt:' prefix). Reports regression when any signature
drops by more than threshold (default 0.1).

Refs casehubio/agency#366

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 3: PromptOptimisationProposalSource

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/PromptOptimisationProposalSource.java`
- Test: `agency/src/test/java/io/casehub/agency/optimisation/prompt/evolution/PromptOptimisationProposalSourceTest.java`

**Interfaces:**
- Consumes: `ImprovementProposalSource` (engine-api), `ImprovementRequest` (engine-api), `ImprovementConfig` (engine-api), `PromptSignature` (agency), `PromptVariantStore` (agency), `PromptQualityMetric` (agency), `PromptOutcomeProvider` (Task 1), `OptimiserConfig` (agency), `PromptOptimisationCategoryProvider.DOMAIN_ID` (Task 1), `PromptOptimisationCategoryProvider.CATEGORY_ID` (Task 1), `PromptConflictStrategy.SIGNATURE_KEY` (Task 1)
- Produces: `PromptOptimisationProposalSource` (sourceId `"prompt-optimisation"`, proposes `ImprovementRequest` per signature needing tuning)

- [ ] **Step 1: Write failing tests for PromptOptimisationProposalSource**

```java
package io.casehub.agency.optimisation.prompt.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.casehub.agency.optimisation.prompt.OptimiserConfig;
import io.casehub.agency.optimisation.prompt.PromptQualityMetric;
import io.casehub.agency.optimisation.prompt.PromptSignature;
import io.casehub.agency.optimisation.prompt.PromptVariant;
import io.casehub.agency.optimisation.prompt.PromptVariantStore;
import io.casehub.agency.optimisation.prompt.VariantOutcome;
import io.casehub.api.model.improvement.ImprovementConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromptOptimisationProposalSourceTest {

    private final PromptVariantStore store = mock(PromptVariantStore.class);
    private final PromptQualityMetric metric = mock(PromptQualityMetric.class);
    private final PromptOutcomeProvider outcomeProvider = mock(PromptOutcomeProvider.class);

    private final PromptSignature sig = new PromptSignature(
            "swarm-team-formation", "Team formation routing",
            "You are a team formation coordinator", String.class, String.class);

    private final OptimiserConfig config = OptimiserConfig.defaults();

    private PromptOptimisationProposalSource source() {
        return new PromptOptimisationProposalSource(
                List.of(sig), store, metric, outcomeProvider, config);
    }

    @Test
    void sourceId_and_domainId() {
        var s = source();
        assertThat(s.sourceId()).isEqualTo("prompt-optimisation");
        assertThat(s.domainId()).isEqualTo("prompt-optimisation");
    }

    @Test
    void proposes_when_quality_below_threshold() {
        var variant = new PromptVariant("swarm-team-formation", "v-1",
                List.of(), null, 0.5, Instant.now(), null, 0);
        when(store.getActive("swarm-team-formation", "control")).thenReturn(variant);
        var outcomes = List.of(
                new VariantOutcome("v-1", "DEGRADED"),
                new VariantOutcome("v-1", "FAILED"));
        // enough outcomes to meet minOutcomeCount=50 — generate 50
        var manyOutcomes = new java.util.ArrayList<VariantOutcome>();
        for (int i = 0; i < 50; i++) manyOutcomes.add(new VariantOutcome("v-1", "DEGRADED"));
        when(outcomeProvider.outcomes("swarm-team-formation")).thenReturn(manyOutcomes);
        when(metric.score(manyOutcomes)).thenReturn(0.35);

        var proposals = source().propose(UUID.randomUUID(), "tenant-1",
                new ImprovementConfig(null, null, null, null, null));
        assertThat(proposals).hasSize(1);
        assertThat(proposals.getFirst().category()).isEqualTo("prompt-tuning");
        assertThat(proposals.getFirst().metadata().get("signatureId"))
                .isEqualTo("swarm-team-formation");
    }

    @Test
    void skips_when_insufficient_outcomes() {
        var variant = new PromptVariant("swarm-team-formation", "v-1",
                List.of(), null, 0.5, Instant.now(), null, 0);
        when(store.getActive("swarm-team-formation", "control")).thenReturn(variant);
        when(outcomeProvider.outcomes("swarm-team-formation")).thenReturn(List.of(
                new VariantOutcome("v-1", "DEGRADED")));

        var proposals = source().propose(UUID.randomUUID(), "tenant-1",
                new ImprovementConfig(null, null, null, null, null));
        assertThat(proposals).isEmpty();
    }

    @Test
    void skips_when_quality_is_healthy() {
        var variant = new PromptVariant("swarm-team-formation", "v-1",
                List.of(), null, 0.9, Instant.now(), null, 0);
        when(store.getActive("swarm-team-formation", "control")).thenReturn(variant);
        var manyOutcomes = new java.util.ArrayList<VariantOutcome>();
        for (int i = 0; i < 50; i++) manyOutcomes.add(new VariantOutcome("v-1", "CONVERGED"));
        when(outcomeProvider.outcomes("swarm-team-formation")).thenReturn(manyOutcomes);
        when(metric.score(manyOutcomes)).thenReturn(0.92);

        var proposals = source().propose(UUID.randomUUID(), "tenant-1",
                new ImprovementConfig(null, null, null, null, null));
        assertThat(proposals).isEmpty();
    }

    @Test
    void skips_when_no_control_variant_exists() {
        when(store.getActive("swarm-team-formation", "control")).thenReturn(null);

        var proposals = source().propose(UUID.randomUUID(), "tenant-1",
                new ImprovementConfig(null, null, null, null, null));
        assertThat(proposals).isEmpty();
    }

    @Test
    void proposes_for_multiple_signatures() {
        var sig2 = new PromptSignature("swarm-provisioning", "Provisioning",
                "You are a provisioner", String.class, String.class);
        var source = new PromptOptimisationProposalSource(
                List.of(sig, sig2), store, metric, outcomeProvider, config);

        for (var s : List.of("swarm-team-formation", "swarm-provisioning")) {
            var variant = new PromptVariant(s, "v-1", List.of(), null, 0.4, Instant.now(), null, 0);
            when(store.getActive(s, "control")).thenReturn(variant);
            var manyOutcomes = new java.util.ArrayList<VariantOutcome>();
            for (int i = 0; i < 50; i++) manyOutcomes.add(new VariantOutcome("v-1", "DEGRADED"));
            when(outcomeProvider.outcomes(s)).thenReturn(manyOutcomes);
            when(metric.score(manyOutcomes)).thenReturn(0.3);
        }

        var proposals = source.propose(UUID.randomUUID(), "tenant-1",
                new ImprovementConfig(null, null, null, null, null));
        assertThat(proposals).hasSize(2);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agency -Dtest=PromptOptimisationProposalSourceTest`
Expected: Compilation failure — class does not exist

- [ ] **Step 3: Implement PromptOptimisationProposalSource**

```java
package io.casehub.agency.optimisation.prompt.evolution;

import io.casehub.agency.optimisation.prompt.OptimiserConfig;
import io.casehub.agency.optimisation.prompt.PromptQualityMetric;
import io.casehub.agency.optimisation.prompt.PromptSignature;
import io.casehub.agency.optimisation.prompt.PromptVariantStore;
import io.casehub.api.model.improvement.ImprovementConfig;
import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ImprovementProposalSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PromptOptimisationProposalSource implements ImprovementProposalSource {

    private static final double TUNING_MARGIN = 0.1;

    private final List<PromptSignature> signatures;
    private final PromptVariantStore store;
    private final PromptQualityMetric metric;
    private final PromptOutcomeProvider outcomeProvider;
    private final OptimiserConfig optimiserConfig;

    public PromptOptimisationProposalSource(
            List<PromptSignature> signatures,
            PromptVariantStore store,
            PromptQualityMetric metric,
            PromptOutcomeProvider outcomeProvider,
            OptimiserConfig optimiserConfig) {
        this.signatures = List.copyOf(signatures);
        this.store = store;
        this.metric = metric;
        this.outcomeProvider = outcomeProvider;
        this.optimiserConfig = optimiserConfig;
    }

    @Override
    public String sourceId() {
        return "prompt-optimisation";
    }

    @Override
    public String domainId() {
        return PromptOptimisationCategoryProvider.DOMAIN_ID;
    }

    @Override
    public List<ImprovementRequest> propose(UUID caseId, String tenancyId,
                                            ImprovementConfig config) {
        var proposals = new ArrayList<ImprovementRequest>();
        for (var signature : signatures) {
            var control = store.getActive(signature.id(), "control");
            if (control == null) continue;

            var outcomes = outcomeProvider.outcomes(signature.id());
            if (outcomes.size() < optimiserConfig.minOutcomeCount()) continue;

            double quality = metric.score(outcomes);
            if (quality >= optimiserConfig.minQualityThreshold() + TUNING_MARGIN) continue;

            proposals.add(new ImprovementRequest(
                    "prompt-tuning",
                    PromptOptimisationCategoryProvider.CATEGORY_ID,
                    signature.id(),
                    1,
                    Map.of(PromptConflictStrategy.SIGNATURE_KEY, signature.id(),
                            "currentQuality", String.format("%.3f", quality)),
                    PromptOptimisationCategoryProvider.DOMAIN_ID));
        }
        return proposals;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest=PromptOptimisationProposalSourceTest`
Expected: 6 tests PASS

- [ ] **Step 5: Run all evolution tests to verify nothing is broken**

Run: `mvn --batch-mode test -pl agency -Dtest="PromptOptimisationCategoryProviderTest,PromptConflictStrategyTest,PromptRegressionEvaluatorTest,PromptOptimisationProposalSourceTest"`
Expected: 17 tests PASS

- [ ] **Step 6: Run full agency test suite**

Run: `mvn --batch-mode test`
Expected: All tests PASS (no regressions)

- [ ] **Step 7: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/PromptOptimisationProposalSource.java agency/src/test/java/io/casehub/agency/optimisation/prompt/evolution/PromptOptimisationProposalSourceTest.java
git commit -m "feat(#366): add PromptOptimisationProposalSource — evolution loop proposal integration

Implements ImprovementProposalSource for prompt-optimisation domain.
Scans registered PromptSignatures, evaluates current quality via
PromptQualityMetric, and proposes ImprovementRequest for each signature
whose quality falls below the tuning threshold.

Refs casehubio/agency#366

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 3: Documentation update

### Task 4: Update CLAUDE.md and consumer guide

**Files:**
- Modify: `CLAUDE.md` — add `evolution` sub-package to Key Directories table and package documentation
- Modify: `docs/guides/consumer-guide.md` — add evolution integration section (if it exists)

**Interfaces:**
- Consumes: All types from Tasks 1-3
- Produces: Updated documentation

- [ ] **Step 1: Add evolution sub-package to CLAUDE.md Key Directories table**

Add a row to the Key Directories table:

```
| `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/` | Evolution loop integration — `PromptOptimisationProposalSource` (`ImprovementProposalSource`), `PromptRegressionEvaluator` (`RegressionEvaluator`), `PromptOptimisationCategoryProvider` (`ImprovementCategoryProvider`), `PromptConflictStrategy` (`ConflictStrategy`), `PromptOutcomeProvider` (`@FunctionalInterface`) |
| `agency/src/test/java/io/casehub/agency/optimisation/prompt/evolution/` | Tests for evolution loop integration |
```

- [ ] **Step 2: Add package documentation section to CLAUDE.md**

Add after the existing `io.casehub.agency.optimisation.prompt.runtime` section:

```markdown
## Package: `io.casehub.agency.optimisation.prompt.evolution`

Evolution loop integration — registers prompt optimisation as a domain participant in the engine's continuous evolution pipeline (engine#1115/1148). All implementations use domain ID `"prompt-optimisation"`.

| Class | What it does |
|-------|-------------|
| `PromptOutcomeProvider` | `@FunctionalInterface` SPI: `List<VariantOutcome> outcomes(String signatureId)`. Consumer-supplied outcome data retrieval. |
| `PromptOptimisationProposalSource` | `ImprovementProposalSource` impl (sourceId: `"prompt-optimisation"`). Scans registered `PromptSignature` instances, evaluates quality via `PromptQualityMetric`, proposes tuning when quality drops below threshold. |
| `PromptRegressionEvaluator` | `RegressionEvaluator` impl (evaluatorId: `"prompt-quality"`). Compares per-signature quality scores in `HealthScoreSnapshot.componentScores()` (keyed with `prompt:` prefix). Reports regression when any signature drops by more than threshold (default 0.1). |
| `PromptOptimisationCategoryProvider` | `ImprovementCategoryProvider` impl. Single category `"prompt-tuning"`, four stages: evaluate → optimise → promote → outcome-recording. No approval gates. |
| `PromptConflictStrategy` | `ConflictStrategy` impl. Signature-level mutual exclusion — conflicts when an active improvement targets the same `PromptSignature.id()` via metadata key `signatureId`. |
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(#366): document prompt optimisation evolution integration in CLAUDE.md

Add evolution sub-package to Key Directories table and add package
documentation section for the five new types.

Refs casehubio/agency#366

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## References

- [2026-10-26-unify-prompt-optimisation-evolution-design.md] — design spec this plan implements
- [ImprovementProposalSource.java] — engine SPI for proposal sources
- [RegressionEvaluator.java] — engine SPI for regression detection
- [ImprovementCategoryProvider.java] — engine SPI for category/stage declaration
- [ConflictStrategy.java] — engine SPI for concurrent improvement conflict detection
- [DevtownProposalSource.java] — reference domain implementation in devtown
- [DevtownRegressionEvaluator.java] — reference domain implementation in devtown
- [PromptOptimisationBatch.java] — existing agency batch orchestrator (unchanged)
- [VariantSelector.java] — existing agency A/B selector with circuit breaker (unchanged)
- [GitHub #366] — focal issue
- [GitHub engine#1115] — continuous evolution loop (dependency, landed)
- [GitHub #362] — swarm prompt optimisation wiring (dependency, landed)
