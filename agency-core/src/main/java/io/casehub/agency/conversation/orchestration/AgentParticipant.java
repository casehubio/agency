package io.casehub.agency.conversation.orchestration;

import io.casehub.agency.agentic.AgentRef;

public record AgentParticipant(
        AgentRef agentRef,
        String role,
        String systemPrompt
) {
    public String agentId() { return agentRef.name(); }
}
