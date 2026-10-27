package io.casehub.agency.social.memory.evolution;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy;
import java.util.Map;
import java.util.UUID;

public class MemoryHygieneConflictStrategy implements ConflictStrategy {

    static final String AGENT_KEY = "agentId";

    @Override
    public String domainId() {
        return MemoryHygieneCategoryProvider.DOMAIN_ID;
    }

    @Override
    public ConflictResult check(ImprovementRequest request,
                                Map<UUID, ImprovementRequest> activeImprovements,
                                int trivialThreshold) {
        var agentId = request.metadata().get(AGENT_KEY);
        if (agentId == null) {
            return new ConflictResult.Clear();
        }
        for (var entry : activeImprovements.entrySet()) {
            var activeAgentId = entry.getValue().metadata().get(AGENT_KEY);
            if (agentId.equals(activeAgentId)) {
                return new ConflictResult.Conflicting(entry.getKey(),
                        "Memory hygiene tuning already active for agent: " + agentId);
            }
        }
        return new ConflictResult.Clear();
    }
}
