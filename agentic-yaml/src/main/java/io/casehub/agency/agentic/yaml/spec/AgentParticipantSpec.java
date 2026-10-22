package io.casehub.agency.core.yaml.spec;

public record AgentParticipantSpec(
        AgentRefSpec agent,
        String role,
        String systemPrompt) {}
