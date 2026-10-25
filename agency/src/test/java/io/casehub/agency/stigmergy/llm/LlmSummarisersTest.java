package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.perception.observation.ObservationContext;
import io.casehub.agency.perception.observation.ObservationRenderer;
import io.casehub.agency.perception.observation.ObservationResult;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmSummarisersTest {

    @Test
    void observation_producesLlmObservationResult() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        var json = """
            {"observations":[{"patternId":"test","confidence":0.9,"evidence":[],"derivedFrom":[],"details":{},"reasoning":"found it"}]}
            """;
        when(agentProvider.invoke(any())).thenReturn(
            Multi.createFrom().item(new AgentEvent.TextDelta(json)));

        ObservationRenderer<String> renderer = (events, obsCtx) ->
            CompletableFuture.completedFuture(
                new ObservationResult("rendered text", List.of(), events.size(), 0, null));

        var summariser = LlmSummarisers.observation(
            agentProvider,
            "Test system prompt",
            LlmInvocationPolicy.of(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30)),
            renderer,
            Executors.newVirtualThreadPerTaskExecutor());

        var result = summariser.summarise(List.of("event1"), null)
            .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result).isNotNull();
        assertThat(result.observations()).hasSize(1);
        assertThat(result.observations().get(0).patternId()).isEqualTo("test");
        assertThat(result.rawObservations()).hasSize(1);
        assertThat(result.rawObservations().get(0).reasoning()).isEqualTo("found it");
    }

    @Test
    void observation_returnsEmptyOnParseError() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.invoke(any())).thenReturn(
            Multi.createFrom().item(new AgentEvent.TextDelta("not valid json at all")));

        ObservationRenderer<String> renderer = (events, obsCtx) ->
            CompletableFuture.completedFuture(
                new ObservationResult("rendered", List.of(), events.size(), 0, null));

        var summariser = LlmSummarisers.observation(
            agentProvider,
            "Test prompt",
            LlmInvocationPolicy.of(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30)),
            renderer,
            Executors.newVirtualThreadPerTaskExecutor());

        var result = summariser.summarise(List.of("event1"), null)
            .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result).isNotNull();
        assertThat(result.observations()).isEmpty();
        assertThat(result.rawObservations()).isEmpty();
    }

    @Test
    void observation_returnsEmptyOnAgentError() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.invoke(any())).thenReturn(
            Multi.createFrom().failure(new RuntimeException("provider down")));

        ObservationRenderer<String> renderer = (events, obsCtx) ->
            CompletableFuture.completedFuture(
                new ObservationResult("rendered", List.of(), events.size(), 0, null));

        var summariser = LlmSummarisers.observation(
            agentProvider,
            "Test prompt",
            LlmInvocationPolicy.of(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30)),
            renderer,
            Executors.newVirtualThreadPerTaskExecutor());

        var result = summariser.summarise(List.of("event1"), null)
            .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result).isNotNull();
        assertThat(result.observations()).isEmpty();
    }
}
