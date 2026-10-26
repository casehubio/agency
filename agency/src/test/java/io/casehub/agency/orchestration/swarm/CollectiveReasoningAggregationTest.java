package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.orchestration.aggregation.AggregationContext;
import io.casehub.agency.orchestration.aggregation.AggregationResult;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollectiveReasoningAggregationTest {

    private AgentProvider agentProvider;

    @BeforeEach
    void setUp() {
        agentProvider = mock(AgentProvider.class);
    }

    @Test
    void aggregate_synthesisesMultipleResults() {
        mockLlmResponse("""
                {"agreements":["common finding"],"disagreements":["disputed point"],
                 "complementaryObservations":["unique insight"],"confidence":0.85,
                 "minorityViewpoints":["alternative view"]}
                """);

        var aggregation = new CollectiveReasoningAggregation(agentProvider);
        var results = List.of(
                agentResult("analyst", "Analysis: trend A detected"),
                agentResult("responder", "Remediation: trend B observed"));

        var result = aggregation.aggregate(results, new AggregationContext<>(new SwarmContext()));
        assertThat(result).isInstanceOf(AggregationResult.Resolved.class);
        var state = (SwarmAggregationState) ((AggregationResult.Resolved) result).value();
        assertThat(state.agreements()).containsExactly("common finding");
        assertThat(state.disagreements()).containsExactly("disputed point");
        assertThat(state.complementaryObservations()).containsExactly("unique insight");
        assertThat(state.confidence()).isEqualTo(0.85);
        assertThat(state.minorityViewpoints()).containsExactly("alternative view");
    }

    @Test
    void aggregate_fallsBackOnLlmFailure() {
        when(agentProvider.invoke(any())).thenThrow(new RuntimeException("LLM unavailable"));

        var aggregation = new CollectiveReasoningAggregation(agentProvider);
        var results = List.of(agentResult("analyst", "some output"));

        var result = aggregation.aggregate(results, new AggregationContext<>(new SwarmContext()));
        assertThat(result).isInstanceOf(AggregationResult.Resolved.class);
        var state = (SwarmAggregationState) ((AggregationResult.Resolved) result).value();
        assertThat(state.agreements()).isNotEmpty();
        assertThat(state.confidence()).isEqualTo(0.0);
    }

    @Test
    void aggregate_emptyResultsReturnsEmpty() {
        var aggregation = new CollectiveReasoningAggregation(agentProvider);
        var result = aggregation.aggregate(List.of(), new AggregationContext<>(new SwarmContext()));
        assertThat(result).isInstanceOf(AggregationResult.Resolved.class);
        var state = (SwarmAggregationState) ((AggregationResult.Resolved) result).value();
        assertThat(state.agreements()).isEmpty();
        assertThat(state.confidence()).isEqualTo(0.0);
    }

    private void mockLlmResponse(String json) {
        when(agentProvider.invoke(any())).thenAnswer(inv -> {
            var events = new java.util.ArrayList<AgentEvent>();
            events.add(new AgentEvent.TextDelta(json));
            events.add(new AgentEvent.InvocationComplete(10, 20, 0, 0, 0, 0.01, 100L, 80L, "s1", 1, false, null, null));
            return io.smallrye.mutiny.Multi.createFrom().iterable(events);
        });
    }

    private AgentResult agentResult(String name, String output) {
        var ref = AgentRef.external(name, x ->
                java.util.concurrent.CompletableFuture.completedFuture(
                        AgentResult.success(null, "ok")));
        return AgentResult.success(ref, output);
    }
}
