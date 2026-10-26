package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.aggregation.AggregationResult;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwarmFallbackStrategyTest {

    private final SwarmFallbackStrategy strategy = new SwarmFallbackStrategy();

    @Test
    void fallbackRouting_selectsAllCandidates() {
        var candidates = List.of(candidate("a1"), candidate("a2"));
        var decision = strategy.fallbackRouting(candidates);
        assertThat(decision).isInstanceOf(RoutingDecision.SwarmAssignment.class);
        var assignment = (RoutingDecision.SwarmAssignment) decision;
        assertThat(assignment.agents()).hasSize(2);
        assertThat(assignment.strategy()).isEqualTo("heuristic-all");
    }

    @Test
    void fallbackAggregation_collectsAll() {
        var results = List.of(
                agentResult("a1", "output-1"),
                agentResult("a2", "output-2"));
        var result = strategy.fallbackAggregation(results);
        assertThat(result).isInstanceOf(AggregationResult.Resolved.class);
        var state = (SwarmAggregationState) ((AggregationResult.Resolved) result).value();
        assertThat(state.agreements()).hasSize(2);
        assertThat(state.confidence()).isEqualTo(0.0);
    }

    @Test
    void fallbackProvisioningAdvice_budgetBased() {
        assertThat(strategy.fallbackProvisioningAdvice(5, 10)).isTrue();
        assertThat(strategy.fallbackProvisioningAdvice(10, 10)).isFalse();
    }

    private RoutingCandidate candidate(String name) {
        var ref = AgentRef.external(name, x ->
                java.util.concurrent.CompletableFuture.completedFuture(
                        AgentResult.success(null, "ok")));
        return new RoutingCandidate(ref, null);
    }

    private AgentResult agentResult(String name, String output) {
        var ref = AgentRef.external(name, x ->
                java.util.concurrent.CompletableFuture.completedFuture(
                        AgentResult.success(null, "ok")));
        return AgentResult.success(ref, output);
    }
}
