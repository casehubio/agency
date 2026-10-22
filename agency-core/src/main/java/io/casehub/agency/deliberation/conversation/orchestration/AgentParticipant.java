package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.core.AgentRef;

public record AgentParticipant(
        AgentRef agentRef,
        String role,
        String systemPrompt
) {
    public String agentId() { return agentRef.name(); }
}
