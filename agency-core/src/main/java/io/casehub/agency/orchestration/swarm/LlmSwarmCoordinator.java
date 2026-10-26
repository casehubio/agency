package io.casehub.agency.orchestration.swarm;

import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LlmSwarmCoordinator {

    private final AgentProvider agentProvider;
    private final SwarmContext context;
    private final SwarmRoutingStrategy routingStrategy;
    private final CollectiveReasoningAggregation aggregation;
    private final SwarmConvergenceTermination convergenceTermination;
    private final SwarmEventSource eventSource;
    private final SwarmInvocationTracker invocationTracker;
    private final SwarmFallbackStrategy fallbackStrategy;
    private final List<SwarmPromptSection> promptSections;

    LlmSwarmCoordinator(AgentProvider agentProvider,
                        SwarmContext context,
                        SwarmRoutingStrategy routingStrategy,
                        CollectiveReasoningAggregation aggregation,
                        SwarmConvergenceTermination convergenceTermination,
                        SwarmEventSource eventSource,
                        SwarmInvocationTracker invocationTracker,
                        SwarmFallbackStrategy fallbackStrategy,
                        List<SwarmPromptSection> promptSections) {
        this.agentProvider = agentProvider;
        this.context = context;
        this.routingStrategy = routingStrategy;
        this.aggregation = aggregation;
        this.convergenceTermination = convergenceTermination;
        this.eventSource = eventSource;
        this.invocationTracker = invocationTracker;
        this.fallbackStrategy = fallbackStrategy;
        this.promptSections = List.copyOf(promptSections);
    }

    public String assembleCoordinatorPrompt(SwarmContext ctx) {
        var sb = new StringBuilder();
        for (var section : promptSections) {
            var contribution = section.contribute(ctx);
            if (contribution != null && !contribution.isEmpty()) {
                sb.append(contribution).append("\n");
            }
        }
        return sb.toString();
    }

    public AgentProvider agentProvider() { return agentProvider; }
    public SwarmContext context() { return context; }
    public SwarmRoutingStrategy routingStrategy() { return routingStrategy; }
    public CollectiveReasoningAggregation aggregation() { return aggregation; }
    public SwarmConvergenceTermination convergenceTermination() { return convergenceTermination; }
    public SwarmEventSource eventSource() { return eventSource; }
    public SwarmInvocationTracker invocationTracker() { return invocationTracker; }
    public SwarmFallbackStrategy fallbackStrategy() { return fallbackStrategy; }
    public List<SwarmPromptSection> promptSections() { return promptSections; }
}
