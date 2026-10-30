package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.OptimiserConfig;
import io.casehub.agency.optimisation.prompt.PromptQualityMetric;
import io.casehub.agency.optimisation.prompt.PromptSignature;
import io.casehub.agency.optimisation.prompt.PromptVariantStore;
import io.casehub.agency.optimisation.prompt.evolution.PromptOutcomeProvider;
import io.casehub.api.model.improvement.ImprovementConfig;
import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ImprovementProposalSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SwarmPromptProposalSource implements ImprovementProposalSource {

    private static final double TUNING_MARGIN = 0.1;

    private final List<PromptSignature> signatures;
    private final PromptVariantStore store;
    private final PromptQualityMetric metric;
    private final PromptOutcomeProvider outcomeProvider;
    private final OptimiserConfig optimiserConfig;

    public SwarmPromptProposalSource(
            List<PromptSignature> signatures,
            PromptVariantStore store,
            PromptQualityMetric metric,
            PromptOutcomeProvider outcomeProvider,
            OptimiserConfig optimiserConfig) {
        this.signatures = List.copyOf(signatures);
        this.store = store;
        this.metric = metric;
        this.outcomeProvider = outcomeProvider;
        this.optimiserConfig = optimiserConfig;
    }

    @Override
    public String sourceId() {
        return "swarm-prompt-optimisation";
    }

    @Override
    public String domainId() {
        return SwarmPromptCategoryProvider.DOMAIN_ID;
    }

    @Override
    public List<ImprovementRequest> propose(UUID caseId, String tenancyId,
                                            ImprovementConfig config) {
        var proposals = new ArrayList<ImprovementRequest>();
        for (var signature : signatures) {
            var control = store.getActive(signature.id(), "control");
            if (control == null) continue;

            var outcomes = outcomeProvider.outcomes(signature.id());
            if (outcomes.size() < optimiserConfig.minOutcomeCount()) continue;

            double quality = metric.score(outcomes);
            if (quality >= optimiserConfig.minQualityThreshold() + TUNING_MARGIN) continue;

            proposals.add(new ImprovementRequest(
                    "swarm-prompt-tuning",
                    SwarmPromptCategoryProvider.CATEGORY_ID,
                    signature.id(),
                    1,
                    Map.of(SwarmPromptConflictStrategy.SIGNATURE_KEY, signature.id(),
                            "currentQuality", String.format("%.3f", quality)),
                    SwarmPromptCategoryProvider.DOMAIN_ID));
        }
        return proposals;
    }
}
