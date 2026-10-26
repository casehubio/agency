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

import io.casehub.agency.optimisation.prompt.OptimiserConfig;
import io.casehub.agency.optimisation.prompt.PromptQualityMetric;
import io.casehub.agency.optimisation.prompt.PromptSignature;
import io.casehub.agency.optimisation.prompt.PromptVariantStore;
import io.casehub.api.model.improvement.ImprovementConfig;
import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ImprovementProposalSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PromptOptimisationProposalSource implements ImprovementProposalSource {

    private static final double TUNING_MARGIN = 0.1;

    private final List<PromptSignature> signatures;
    private final PromptVariantStore store;
    private final PromptQualityMetric metric;
    private final PromptOutcomeProvider outcomeProvider;
    private final OptimiserConfig optimiserConfig;

    public PromptOptimisationProposalSource(
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
        return "prompt-optimisation";
    }

    @Override
    public String domainId() {
        return PromptOptimisationCategoryProvider.DOMAIN_ID;
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
                    "prompt-tuning",
                    PromptOptimisationCategoryProvider.CATEGORY_ID,
                    signature.id(),
                    1,
                    Map.of(PromptConflictStrategy.SIGNATURE_KEY, signature.id(),
                            "currentQuality", String.format("%.3f", quality)),
                    PromptOptimisationCategoryProvider.DOMAIN_ID));
        }
        return proposals;
    }
}
