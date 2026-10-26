package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.deliberation.coalition.CapabilityCoverageEvaluator;
import io.casehub.agency.deliberation.coalition.CoalitionContext;
import io.casehub.agency.deliberation.coalition.CoalitionProposal;
import io.casehub.agency.orchestration.aggregation.AggregationContext;
import io.casehub.agency.orchestration.aggregation.AggregationResult;
import io.casehub.agency.orchestration.routing.RoutingDecision;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SwarmFallbackStrategy {

    private final CapabilityCoverageEvaluator coalitionEvaluator = new CapabilityCoverageEvaluator();

    public RoutingDecision fallbackRouting(List<RoutingCandidate> candidates) {
        var agents = candidates.stream()
                .map(c -> (AgentRef) c.ref())
                .toList();
        return new RoutingDecision.SwarmAssignment(
                agents, Map.of(), "heuristic-all", "LLM unavailable — all agents selected");
    }

    public AggregationResult fallbackAggregation(List<AgentResult> results) {
        var outputs = results.stream()
                .map(r -> r.agent().name() + ": " + (r.output() != null ? r.output().toString() : ""))
                .collect(Collectors.toList());
        return new AggregationResult.Resolved(new SwarmAggregationState(
                outputs, List.of(), List.of(), 0.0, List.of()));
    }

    public boolean fallbackProvisioningAdvice(int currentSize, int maxSize) {
        return currentSize < maxSize;
    }
}
