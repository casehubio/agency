package io.casehub.agency.social.memory.evolution;

import io.casehub.api.model.improvement.ImprovementConfig;
import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ImprovementProposalSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MemoryHygieneProposalSource implements ImprovementProposalSource {

    private static final double TUNING_MARGIN = 0.1;

    private final MemoryHygieneOutcomeProvider outcomeProvider;
    private final double qualityThreshold;
    private final int minSampleSize;

    public MemoryHygieneProposalSource(MemoryHygieneOutcomeProvider outcomeProvider,
                                        double qualityThreshold, int minSampleSize) {
        this.outcomeProvider = outcomeProvider;
        this.qualityThreshold = qualityThreshold;
        this.minSampleSize = minSampleSize;
    }

    public MemoryHygieneProposalSource(MemoryHygieneOutcomeProvider outcomeProvider) {
        this(outcomeProvider, 0.7, 20);
    }

    @Override
    public String sourceId() {
        return "memory-hygiene";
    }

    @Override
    public String domainId() {
        return MemoryHygieneCategoryProvider.DOMAIN_ID;
    }

    @Override
    public List<ImprovementRequest> propose(UUID caseId, String tenancyId,
                                            ImprovementConfig config) {
        var outcome = outcomeProvider.outcomes(tenancyId);
        if (outcome.sampleSize() < minSampleSize) {
            return List.of();
        }

        double quality = computeQuality(outcome);
        if (quality >= qualityThreshold + TUNING_MARGIN) {
            return List.of();
        }

        return List.of(new ImprovementRequest(
                weakestDimension(outcome),
                MemoryHygieneCategoryProvider.CATEGORY_ID,
                tenancyId,
                1,
                Map.of(MemoryHygieneConflictStrategy.AGENT_KEY, tenancyId,
                        "currentQuality", String.format("%.3f", quality)),
                MemoryHygieneCategoryProvider.DOMAIN_ID));
    }

    static double computeQuality(MemoryHygieneOutcome outcome) {
        return outcome.retentionQuality() * 0.4
                + (1.0 - outcome.evictionRate()) * 0.3
                + outcome.consolidationRate() * 0.2
                + (outcome.integrityViolations() == 0 ? 1.0 : 0.0) * 0.1;
    }

    static String weakestDimension(MemoryHygieneOutcome outcome) {
        double retentionScore = outcome.retentionQuality();
        double evictionScore = 1.0 - outcome.evictionRate();
        double consolidationScore = outcome.consolidationRate();

        if (retentionScore <= evictionScore && retentionScore <= consolidationScore) {
            return "retention-threshold";
        }
        if (evictionScore <= consolidationScore) {
            return "confidence-weights";
        }
        return "consolidation-timing";
    }
}
