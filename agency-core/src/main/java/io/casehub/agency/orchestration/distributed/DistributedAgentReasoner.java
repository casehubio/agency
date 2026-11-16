package io.casehub.agency.orchestration.distributed;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.core.invoke.StructuredAgentInvoker.InvocationResult;
import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.loop.SignalDeposit;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.api.spi.observation.Observation;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DistributedAgentReasoner implements ContentSummariser<ObservationEvent, DecisionOutcome> {

    static final String SYSTEM_PROMPT = """
            You are an autonomous agent in a distributed swarm. You coordinate with peers \
            through stigmergy — depositing and reading signals in a shared environment.

            Available actions (emit as JSON):
            - signals: [{name, strength}] — deposit stigmergy signals (need-capacity, excess-capacity, in-progress, etc.)
            - taskClaim: "task-id" — claim a task to work on
            - synthesis: "text" — your local synthesis of peer observations
            - reasoning: "text" — brief explanation of your decision

            Respond ONLY with valid JSON. Do not wrap in markdown fences.""";

    static final String PROMPT_SIGNATURE = "distributed-agent-reasoning";

    private final AgentProvider agentProvider;
    private final @Nullable SystemPromptCustomiser promptCustomiser;
    private volatile String variantSlot = "control";

    public DistributedAgentReasoner(AgentProvider agentProvider,
                                    @Nullable SystemPromptCustomiser promptCustomiser) {
        this.agentProvider = agentProvider;
        this.promptCustomiser = promptCustomiser;
    }

    public void setVariantSlot(String slot) {
        this.variantSlot = slot;
    }

    @Override
    public CompletionStage<DecisionOutcome> summarise(List<ObservationEvent> events,
                                                       @Nullable DecisionOutcome previous) {
        var systemPrompt = promptCustomiser != null
                ? promptCustomiser.customise(SYSTEM_PROMPT, PROMPT_SIGNATURE, variantSlot)
                : SYSTEM_PROMPT;

        var userPrompt = renderObservations(events);
        var config = AgentSessionConfig.of(systemPrompt, userPrompt);

        var result = StructuredAgentInvoker.invokeRaw(agentProvider, config);
        return CompletableFuture.completedFuture(switch (result) {
            case InvocationResult.Success<JsonNode> s -> parseWriteBack(s.value());
            case InvocationResult.ParseError<JsonNode> pe -> noOp();
            case InvocationResult.AgentError<JsonNode> ae -> noOp();
        });
    }

    private String renderObservations(List<ObservationEvent> events) {
        var sb = new StringBuilder("## Current Observations\n\n");
        for (var e : events) {
            sb.append("- **").append(e.key()).append("**: ").append(e.value()).append('\n');
        }
        return sb.toString();
    }

    private DecisionOutcome parseWriteBack(JsonNode json) {
        var signals = new ArrayList<SignalDeposit>();
        var signalsNode = json.path("signals");
        if (signalsNode.isArray()) {
            for (var s : signalsNode) {
                signals.add(new SignalDeposit(
                        s.path("name").asText(), s.path("strength").asDouble(), null));
            }
        }

        var observations = new ArrayList<Observation>();
        var synthesis = json.path("synthesis").asText(null);
        if (synthesis != null && !synthesis.isBlank()) {
            observations.add(new Observation(
                    "synthesis", 1.0, Map.of("text",
                    new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(synthesis)),
                    Instant.now()));
        }

        return new DecisionOutcome.WriteBack(observations, signals, List.of());
    }

    private static DecisionOutcome noOp() {
        return new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
    }
}
