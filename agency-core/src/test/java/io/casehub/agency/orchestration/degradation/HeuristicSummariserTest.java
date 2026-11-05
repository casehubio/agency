package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.casehub.agency.orchestration.swarm.SwarmFallbackStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicSummariserTest {

    static final AgentRef STUB_AGENT = new AgentRef.ExternalAgent("test-agent",
            input -> CompletableFuture.completedFuture(null));

    @Test
    void noOp_returnsPrevious() throws Exception {
        var summariser = new NoOpHeuristicSummariser<String, String>();
        var result = summariser.summarise(List.of("input"), "previous-value").toCompletableFuture().get();
        assertThat(result).isEqualTo("previous-value");
    }

    @Test
    void noOp_returnsNull_whenNoPrevious() throws Exception {
        var summariser = new NoOpHeuristicSummariser<String, String>();
        var result = summariser.summarise(List.of("input"), null).toCompletableFuture().get();
        assertThat(result).isNull();
    }

    @Test
    void routing_producesRouteOutcome() throws Exception {
        var fallback = new SwarmFallbackStrategy();
        var candidate = new RoutingCandidate(STUB_AGENT, null);
        var summariser = new HeuristicRoutingSummariser(fallback, () -> List.of(candidate));

        var result = summariser.summarise(List.of(), null).toCompletableFuture().get();
        assertThat(result).isInstanceOf(DecisionOutcome.Route.class);
        var route = (DecisionOutcome.Route) result;
        assertThat(route.decision()).isInstanceOf(RoutingDecision.SwarmAssignment.class);
        var assignment = (RoutingDecision.SwarmAssignment) route.decision();
        assertThat(assignment.strategy()).isEqualTo("heuristic-all");
    }

    @Test
    void aggregation_producesAggregateOutcome() throws Exception {
        var fallback = new SwarmFallbackStrategy();
        var summariser = new HeuristicAggregationSummariser(fallback);

        var agentResult = AgentResult.success(STUB_AGENT, "output text");
        var event = ObservationEvent.of("member-completed", agentResult);

        var result = summariser.summarise(List.of(event), null).toCompletableFuture().get();
        assertThat(result).isInstanceOf(DecisionOutcome.Aggregate.class);
    }

    @Test
    void provisioning_producesProvisionOutcome() throws Exception {
        var fallback = new SwarmFallbackStrategy();
        var summariser = new HeuristicProvisioningSummariser(fallback, 3, 10);

        var result = summariser.summarise(List.of(), null).toCompletableFuture().get();
        assertThat(result).isInstanceOf(DecisionOutcome.Provision.class);
        var provision = (DecisionOutcome.Provision) result;
        assertThat(provision.advice().shouldProvision()).isTrue();
    }

    @Test
    void provisioning_doesNotProvision_whenAtMax() throws Exception {
        var fallback = new SwarmFallbackStrategy();
        var summariser = new HeuristicProvisioningSummariser(fallback, 10, 10);

        var result = summariser.summarise(List.of(), null).toCompletableFuture().get();
        var provision = (DecisionOutcome.Provision) result;
        assertThat(provision.advice().shouldProvision()).isFalse();
    }
}
