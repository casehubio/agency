package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
public record AgentParticipantSpec(
        AgentRefSpec agent,
        String role,
        @JsonAlias("system-prompt") String systemPrompt) {}
