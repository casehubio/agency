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
        assertThat(assessment.costEstimate()).isCloseTo(0.2, within(0.01));
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
