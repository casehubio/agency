package io.casehub.agency.speech.ws;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Duration;
import java.util.stream.Collectors;

@Path("/diag")
public class DiagnosticEndpoint {

    @Inject
    Instance<AgentProvider> agentProvider;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String test() {
        if (!agentProvider.isResolvable()) {
            return "UNAVAILABLE: No AgentProvider configured";
        }
        try {
            var config = AgentSessionConfig.of("Be brief.", "Say hello in one word", "claude-haiku-4-5@20251001");
            String result = agentProvider.get().invoke(config)
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().atMost(Duration.ofSeconds(15));
            return "OK: " + result;
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
