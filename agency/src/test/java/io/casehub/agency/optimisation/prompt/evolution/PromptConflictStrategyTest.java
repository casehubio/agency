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
