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

import io.casehub.api.model.stigmergy.HealthScoreSnapshot;
import io.casehub.api.model.stigmergy.RegressionVerdict;
import io.casehub.api.spi.improvement.RegressionEvaluator;
import java.util.UUID;

public class PromptRegressionEvaluator implements RegressionEvaluator {

    static final String PREFIX = "prompt:";
    private final double regressionThreshold;

    public PromptRegressionEvaluator(double regressionThreshold) {
        this.regressionThreshold = regressionThreshold;
    }

    public PromptRegressionEvaluator() {
        this(0.1);
    }

    @Override
    public String evaluatorId() {
        return "prompt-quality";
    }

    @Override
    public String domainId() {
        return PromptOptimisationCategoryProvider.DOMAIN_ID;
    }

    @Override
    public RegressionVerdict evaluate(UUID caseId, HealthScoreSnapshot baseline,
                                      HealthScoreSnapshot current, String category) {
        double maxDrop = 0.0;
        String worstSignature = null;

        for (var entry : baseline.componentScores().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX)) continue;
            var currentScore = current.componentScores().get(entry.getKey());
            if (currentScore == null) continue;
            double delta = entry.getValue() - currentScore;
            if (delta > maxDrop) {
                maxDrop = delta;
                worstSignature = entry.getKey().substring(PREFIX.length());
            }
        }

        if (maxDrop > regressionThreshold && worstSignature != null) {
            return new RegressionVerdict.Detected(maxDrop,
                    "Prompt quality regression in " + worstSignature
                            + ": dropped by " + String.format("%.3f", maxDrop));
        }
        return new RegressionVerdict.NoRegression();
    }
}
