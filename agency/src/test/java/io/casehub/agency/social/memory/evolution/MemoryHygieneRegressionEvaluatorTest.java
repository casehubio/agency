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
