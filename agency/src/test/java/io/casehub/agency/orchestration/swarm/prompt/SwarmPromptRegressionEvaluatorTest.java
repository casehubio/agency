package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.api.model.stigmergy.HealthScoreSnapshot;
import io.casehub.api.model.stigmergy.RegressionVerdict;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

class SwarmPromptRegressionEvaluatorTest {

    private final SwarmPromptRegressionEvaluator evaluator = new SwarmPromptRegressionEvaluator(0.1);

    @Test
    void domainId_and_evaluatorId() {
        assertThat(evaluator.domainId()).isEqualTo("swarm-prompt-optimisation");
        assertThat(evaluator.evaluatorId()).isEqualTo("swarm-prompt-quality");
    }

    @Test
    void detects_regression_when_quality_drops_beyond_threshold() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(),
                Map.of("swarm-prompt:team-formation", 0.85, "swarm-prompt:provisioning", 0.9));
        var current = new HealthScoreSnapshot(0.6, Instant.now(),
                Map.of("swarm-prompt:team-formation", 0.7, "swarm-prompt:provisioning", 0.9));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "swarm-prompt-tuning");
        assertThat(verdict).asInstanceOf(type(RegressionVerdict.Detected.class))
                .satisfies(d -> {
                    assertThat(d.confidence()).isGreaterThan(0.1);
                    assertThat(d.reason()).contains("team-formation");
                });
    }

    @Test
    void no_regression_when_quality_stable() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(),
                Map.of("swarm-prompt:team-formation", 0.85));
        var current = new HealthScoreSnapshot(0.78, Instant.now(),
                Map.of("swarm-prompt:team-formation", 0.83));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "swarm-prompt-tuning");
        assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
    }

    @Test
    void no_regression_when_no_swarm_prompt_keys() {
        var baseline = new HealthScoreSnapshot(0.8, Instant.now(), Map.of("other", 0.9));
        var current = new HealthScoreSnapshot(0.6, Instant.now(), Map.of("other", 0.5));

        var verdict = evaluator.evaluate(UUID.randomUUID(), baseline, current, "swarm-prompt-tuning");
        assertThat(verdict).isInstanceOf(RegressionVerdict.NoRegression.class);
    }
}
