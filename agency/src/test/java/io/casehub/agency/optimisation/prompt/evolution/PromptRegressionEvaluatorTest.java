/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
