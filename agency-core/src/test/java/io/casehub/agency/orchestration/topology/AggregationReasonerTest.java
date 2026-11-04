package io.casehub.agency.orchestration.topology;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.orchestration.aggregation.AggregationResult;
import io.casehub.agency.orchestration.aggregation.AggregationStrategy;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AggregationReasonerTest {

    static final AgentRef STUB_AGENT = new AgentRef.ExternalAgent("test-agent",
            input -> CompletableFuture.completedFuture(null));

    @Test
    void summarise_delegatesToAggregationStrategy() throws Exception {
        var aggResult = new AggregationResult.Resolved("synthesised output");
        AggregationStrategy<SwarmContext> strategy = (results, ctx) -> aggResult;

        var reasoner = new AggregationReasoner(strategy, new SwarmContext());
        var agentResult = AgentResult.success(STUB_AGENT, "observation text");
        var event = ObservationEvent.of("member-completed", agentResult);
        var result = reasoner.summarise(List.of(event), null)
                .toCompletableFuture().get();

        assertThat(result).isInstanceOf(DecisionOutcome.Aggregate.class);
        assertThat(((DecisionOutcome.Aggregate) result).result()).isEqualTo(aggResult);
    }

    @Test
    void summarise_extractsAgentResultsFromEvents() throws Exception {
        var captured = new AtomicReference<List<AgentResult>>();
        AggregationStrategy<SwarmContext> strategy = (results, ctx) -> {
            captured.set(results);
            return new AggregationResult.Resolved("done");
        };

        var reasoner = new AggregationReasoner(strategy, new SwarmContext());
        var r1 = AgentResult.success(STUB_AGENT, "output1");
        var r2 = AgentResult.success(STUB_AGENT, "output2");
        reasoner.summarise(List.of(
                ObservationEvent.of("member-completed", r1),
                ObservationEvent.of("member-completed", r2),
                ObservationEvent.of("progress-updated", "ignored")
        ), null).toCompletableFuture().get();

        assertThat(captured.get()).containsExactly(r1, r2);
    }
}
