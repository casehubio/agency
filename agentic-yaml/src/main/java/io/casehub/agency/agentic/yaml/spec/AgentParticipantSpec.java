package io.casehub.agency.agentic.yaml.spec;

public record AgentParticipantSpec(
        AgentRefSpec agent,
        String role,
        String systemPrompt) {}
