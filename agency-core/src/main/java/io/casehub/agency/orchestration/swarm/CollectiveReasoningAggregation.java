package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.core.AgentResult;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.agency.orchestration.aggregation.AggregationContext;
import io.casehub.agency.orchestration.aggregation.AggregationResult;
import io.casehub.agency.orchestration.aggregation.AggregationStrategy;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class CollectiveReasoningAggregation implements AggregationStrategy<SwarmContext> {

    private static final System.Logger LOG =
            System.getLogger(CollectiveReasoningAggregation.class.getName());

    private final           AgentProvider          agentProvider;
    private final @Nullable SwarmInvocationTracker invocationTracker;
    private final @Nullable SystemPromptCustomiser promptCustomiser;
    private @Nullable       String                 variantSlot;


    public CollectiveReasoningAggregation(AgentProvider agentProvider,
                                          @Nullable SwarmInvocationTracker invocationTracker,
                                          @Nullable SystemPromptCustomiser promptCustomiser) {
        this.agentProvider     = agentProvider;
        this.invocationTracker = invocationTracker;
        this.promptCustomiser  = promptCustomiser;
    }


    public CollectiveReasoningAggregation(AgentProvider agentProvider,
                                          @Nullable SwarmInvocationTracker invocationTracker) {
        this(agentProvider, invocationTracker, null);
    }

    public CollectiveReasoningAggregation(AgentProvider agentProvider) {
        this(agentProvider, null, null);
    }

    public void setVariantSlot(@Nullable String variantSlot) {
        this.variantSlot = variantSlot;
    }


    @Override
    public AggregationResult aggregate(List<AgentResult> results,
                                       AggregationContext<SwarmContext> context) {
        if (results.isEmpty()) {
            return new AggregationResult.Resolved(new SwarmAggregationState(
                    List.of(), List.of(), List.of(), 0.0, List.of()));
        }

        if (invocationTracker != null && !invocationTracker.tryAcquire("aggregation")) {
            LOG.log(System.Logger.Level.WARNING, "LLM budget exhausted for aggregation — collecting without synthesis");
            return collectAllFallback(results);
        }

        var prompt = buildSynthesisPrompt(results);
        if (promptCustomiser != null) {
            prompt = promptCustomiser.customise(prompt,
                                                io.casehub.agency.orchestration.swarm.prompt.SwarmPromptSignatures.COLLECTIVE_REASONING.id(),
                                                variantSlot != null ? variantSlot : "control");
        }
        var userMessage = "Synthesise these agent observations. Respond in JSON: " +
                          "{\"agreements\": [...], \"disagreements\": [...], " +
                          "\"complementaryObservations\": [...], \"confidence\": 0.0-1.0, " +
                          "\"minorityViewpoints\": [...]}";
        var config = AgentSessionConfig.of(prompt, userMessage);

        var invResult = StructuredAgentInvoker.invoke(agentProvider, config, SwarmAggregationState.class);
        if (invResult instanceof StructuredAgentInvoker.InvocationResult.Success<SwarmAggregationState> s) {
            if (invocationTracker != null) {
                invocationTracker.recordTokens(s.metadata().inputTokens() + s.metadata().outputTokens());
            }
            return new AggregationResult.Resolved(s.value());
        }

        LOG.log(System.Logger.Level.WARNING, "Collective reasoning LLM call failed — collecting without synthesis");
        return collectAllFallback(results);
    }

    private String buildSynthesisPrompt(List<AgentResult> results) {
        var sb = new StringBuilder("You are synthesising observations from multiple swarm members.\n");
        sb.append("Your goal is multi-perspective analysis, NOT summarisation.\n\n");
        sb.append("Identify:\n");
        sb.append("- What the members agree on\n");
        sb.append("- Where they disagree and what evidence supports each position\n");
        sb.append("- What each member observes that others don't\n");
        sb.append("- Minority viewpoints that should be preserved\n\n");
        sb.append("## Agent Observations\n\n");

        for (var result : results) {
            sb.append("### %s\n".formatted(result.agent().name()));
            sb.append("%s\n\n".formatted(
                    result.output() != null ? result.output().toString() : "(no output)"));
        }

        return sb.toString();
    }

    private AggregationResult collectAllFallback(List<AgentResult> results) {
        var outputs = results.stream()
                             .map(r -> r.agent().name() + ": " + (r.output() != null ? r.output().toString() : ""))
                             .collect(Collectors.toList());
        return new AggregationResult.Resolved(new SwarmAggregationState(
                outputs, List.of(), List.of(), 0.0, List.of()));
    }
}
