package io.casehub.agency.social.memory.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
        assertThat(MemoryHygieneProposalSource.computeQuality(outcome)).isCloseTo(1.0, within(1e-10));
    }

    @Test
    void compute_quality_worst_scores() {
        var outcome = new MemoryHygieneOutcome(0.0, 1.0, 0.0, 20, 5, 0, 100);
        assertThat(MemoryHygieneProposalSource.computeQuality(outcome)).isCloseTo(0.0, within(1e-10));
    }
}
