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
import java.util.ArrayList;
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

    private static List<VariantOutcome> generateOutcomes(String variantId, String sigId,
                                                         String outcome, int count) {
        var outcomes = new ArrayList<VariantOutcome>();
        for (int i = 0; i < count; i++) {
            outcomes.add(new VariantOutcome(variantId, sigId, outcome, 1.0, null, Instant.now()));
        }
        return outcomes;
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
        var manyOutcomes = generateOutcomes("v-1", "swarm-team-formation", "DEGRADED", 50);
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
        when(outcomeProvider.outcomes("swarm-team-formation")).thenReturn(
                generateOutcomes("v-1", "swarm-team-formation", "DEGRADED", 1));

        var proposals = source().propose(UUID.randomUUID(), "tenant-1",
                new ImprovementConfig(null, null, null, null, null));
        assertThat(proposals).isEmpty();
    }

    @Test
    void skips_when_quality_is_healthy() {
        var variant = new PromptVariant("swarm-team-formation", "v-1",
                List.of(), null, 0.9, Instant.now(), null, 0);
        when(store.getActive("swarm-team-formation", "control")).thenReturn(variant);
        var manyOutcomes = generateOutcomes("v-1", "swarm-team-formation", "CONVERGED", 50);
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
            var manyOutcomes = generateOutcomes("v-1", s, "DEGRADED", 50);
            when(outcomeProvider.outcomes(s)).thenReturn(manyOutcomes);
            when(metric.score(manyOutcomes)).thenReturn(0.3);
        }

        var proposals = source.propose(UUID.randomUUID(), "tenant-1",
                new ImprovementConfig(null, null, null, null, null));
        assertThat(proposals).hasSize(2);
    }
}
