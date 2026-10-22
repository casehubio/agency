package io.casehub.agency.agentic.cognition;

import io.casehub.agency.memory.MemoryHygieneOrchestrator;
import io.casehub.neocortex.memory.KnowledgeGapSummary;

public class MemoryHygieneSpiAdapter implements io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestrator {

    private final MemoryHygieneOrchestrator delegate;

    public MemoryHygieneSpiAdapter(MemoryHygieneOrchestrator delegate) {
        this.delegate = delegate;
    }

    @Override
    public void tick(String agentId, String tenantId) {
        delegate.tick(agentId, tenantId);
    }

    @Override
    public KnowledgeGapSummary knowledgeGaps(String agentId, String tenantId) {
        return delegate.knowledgeGaps(agentId, tenantId);
    }
}
