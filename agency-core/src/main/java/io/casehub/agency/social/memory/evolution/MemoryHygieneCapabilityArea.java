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
