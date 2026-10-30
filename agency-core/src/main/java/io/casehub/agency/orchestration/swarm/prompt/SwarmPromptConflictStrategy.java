package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy;

import java.util.Map;
import java.util.UUID;

public class SwarmPromptConflictStrategy implements ConflictStrategy {

    static final String SIGNATURE_KEY = "signatureId";

    @Override
    public String domainId() {
        return SwarmPromptCategoryProvider.DOMAIN_ID;
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
                        "Swarm prompt tuning already active for signature: " + signatureId);
            }
        }
        return new ConflictResult.Clear();
    }
}
