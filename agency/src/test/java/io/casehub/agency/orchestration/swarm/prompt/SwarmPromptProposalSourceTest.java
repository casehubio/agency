package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.OptimiserConfig;
import io.casehub.agency.optimisation.prompt.PromptVariant;
import io.casehub.agency.optimisation.prompt.PromptVariantStore;
import io.casehub.agency.optimisation.prompt.VariantOutcome;
import io.casehub.agency.optimisation.prompt.evolution.PromptOutcomeProvider;
import io.casehub.api.model.improvement.ImprovementConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwarmPromptProposalSourceTest {

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
    void domainId_returns_swarm_prompt_optimisation() {
        var source = new SwarmPromptProposalSource(
                List.of(SwarmPromptSignatures.TEAM_FORMATION),
                mock(PromptVariantStore.class),
                new SwarmOutcomeMetric(),
                mock(PromptOutcomeProvider.class),
                OptimiserConfig.defaults());
        assertThat(source.domainId()).isEqualTo("swarm-prompt-optimisation");
    }

    @Test
    void proposesNothing_whenNoControlVariant() {
        var store = mock(PromptVariantStore.class);
        when(store.getActive(any(), eq("control"))).thenReturn(null);

        var source = new SwarmPromptProposalSource(
                List.of(SwarmPromptSignatures.TEAM_FORMATION),
                store, new SwarmOutcomeMetric(),
                mock(PromptOutcomeProvider.class),
                OptimiserConfig.defaults());
        var proposals = source.propose(UUID.randomUUID(), "t1", new ImprovementConfig(null, null, null, null, null));
        assertThat(proposals).isEmpty();
    }

    @Test
    void proposesNothing_whenQualityAboveThreshold() {
        var store = mock(PromptVariantStore.class);
        when(store.getActive(any(), eq("control"))).thenReturn(
                new PromptVariant("id", "v1", List.of(), null, 0.9, Instant.now(), null, 0));

        var outcomeProvider = mock(PromptOutcomeProvider.class);
        var highQualityOutcomes = new java.util.ArrayList<VariantOutcome>();
        for (int i = 0; i < 60; i++) {
            highQualityOutcomes.add(new VariantOutcome("control", "id", "CONVERGED", 0.9, null, Instant.now()));
        }
        when(outcomeProvider.outcomes(any())).thenReturn(highQualityOutcomes);

        var source = new SwarmPromptProposalSource(
                List.of(SwarmPromptSignatures.TEAM_FORMATION),
                store, new SwarmOutcomeMetric(), outcomeProvider,
                OptimiserConfig.defaults());
        var proposals = source.propose(UUID.randomUUID(), "t1", new ImprovementConfig(null, null, null, null, null));
        assertThat(proposals).isEmpty();
    }
}
