package io.casehub.agency.orchestration.topology;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.casehub.agency.orchestration.routing.RoutingStrategy;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingReasonerTest {

    static final AgentRef STUB_AGENT = new AgentRef.ExternalAgent("test-agent",
            input -> CompletableFuture.completedFuture(null));

    @Test
    void summarise_delegatesToRoutingStrategy() throws Exception {
        var decision = new RoutingDecision.SwarmAssignment(
                List.of(STUB_AGENT), Map.of(), "round-robin", "test");
        RoutingStrategy<SwarmContext> strategy = ctx -> decision;
        var swarmContext = new SwarmContext();
        var candidate = new RoutingCandidate(STUB_AGENT, null);

        var reasoner = new RoutingReasoner(strategy, swarmContext, () -> List.of(candidate));
        var event = ObservationEvent.of("candidates-available", "trigger");
        var result = reasoner.summarise(List.of(event), null)
                .toCompletableFuture().get();

        assertThat(result).isInstanceOf(DecisionOutcome.Route.class);
        assertThat(((DecisionOutcome.Route) result).decision()).isEqualTo(decision);
    }

    @Test
    void summarise_passesSwarmContextToStrategy() throws Exception {
        var swarmContext = new SwarmContext();
        var capturedState = new AtomicReference<SwarmContext>();
        RoutingStrategy<SwarmContext> strategy = ctx -> {
            capturedState.set(ctx.state());
            return new RoutingDecision.Unresolvable("test");
        };

        var reasoner = new RoutingReasoner(strategy, swarmContext, List::of);
        reasoner.summarise(List.of(ObservationEvent.of("k", "v")), null)
                .toCompletableFuture().get();

        assertThat(capturedState.get()).isSameAs(swarmContext);
    }
}
