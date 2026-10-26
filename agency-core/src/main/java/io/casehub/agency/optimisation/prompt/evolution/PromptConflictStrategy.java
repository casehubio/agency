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

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy;
import java.util.Map;
import java.util.UUID;

public class PromptConflictStrategy implements ConflictStrategy {

    static final String SIGNATURE_KEY = "signatureId";

    @Override
    public String domainId() {
        return PromptOptimisationCategoryProvider.DOMAIN_ID;
    }

    @Override
    public ConflictResult check(ImprovementRequest request,
                                Map<UUID, ImprovementRequest> activeImprovements,
                                int trivialThreshold) {
        var signatureId = request.metadata().get(SIGNATURE_KEY);
        if (signatureId == null) {
            return new ConflictResult.Clear();
        }
        for (var entry : activeImprovements.entrySet()) {
            var activeSignature = entry.getValue().metadata().get(SIGNATURE_KEY);
            if (signatureId.equals(activeSignature)) {
                return new ConflictResult.Conflicting(entry.getKey(),
                        "Prompt tuning already active for signature: " + signatureId);
            }
        }
        return new ConflictResult.Clear();
    }
}
