package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.perception.observation.ObservationResult;
import io.casehub.agency.perception.observation.ObservationRenderer;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterpretationBuilderTest {

    @Test
    void builder_producesObservationSummariser() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        var json = """
            {"observations":[{"patternId":"p1","confidence":0.9,"evidence":[],"derivedFrom":[],"details":{},"reasoning":"r"}]}
            """;
        when(agentProvider.invoke(any())).thenReturn(
            Multi.createFrom().item(new AgentEvent.TextDelta(json)));

        ObservationRenderer<String> renderer = (events, obsCtx) ->
            CompletableFuture.completedFuture(
                new ObservationResult("rendered", List.of(), events.size(), 0, null));

        var summariser = InterpretationBuilder.<String>builder(agentProvider)
            .systemPrompt("Test prompt")
            .invocationPolicy(LlmInvocationPolicy.of(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30)))
            .promptRenderer(renderer)
            .llmExecutor(Executors.newVirtualThreadPerTaskExecutor())
            .buildObservation();

        var result = summariser.summarise(List.of("event1"), null)
            .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result).isNotNull();
        assertThat(result.observations()).hasSize(1);
    }

    @Test
    void builder_acceptsModel() {
        var agentProvider = mock(AgentProvider.class);
        var builder = InterpretationBuilder.<String>builder(agentProvider)
            .model("claude-sonnet-4-20250514");
        assertThat(builder).isNotNull();
    }

    @Test
    void builder_acceptsWatchedKeys() {
        var agentProvider = mock(AgentProvider.class);
        var builder = InterpretationBuilder.<String>builder(agentProvider)
            .watchedKeys(Set.of("risk.*", "transaction.*"));
        assertThat(builder).isNotNull();
    }

    @Test
    void builder_requiresSystemPrompt() {
        var agentProvider = mock(AgentProvider.class);
        ObservationRenderer<String> renderer = (events, obsCtx) ->
            CompletableFuture.completedFuture(ObservationResult.empty(0));

        assertThatThrownBy(() ->
            InterpretationBuilder.<String>builder(agentProvider)
                .promptRenderer(renderer)
                .llmExecutor(Executors.newVirtualThreadPerTaskExecutor())
                .buildObservation())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("systemPrompt");
    }

    @Test
    void builder_requiresPromptRenderer() {
        var agentProvider = mock(AgentProvider.class);
        assertThatThrownBy(() ->
            InterpretationBuilder.<String>builder(agentProvider)
                .systemPrompt("prompt")
                .llmExecutor(Executors.newVirtualThreadPerTaskExecutor())
                .buildObservation())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("promptRenderer");
    }

    @Test
    void builder_requiresLlmExecutor() {
        var agentProvider = mock(AgentProvider.class);
        ObservationRenderer<String> renderer = (events, obsCtx) ->
            CompletableFuture.completedFuture(ObservationResult.empty(0));

        assertThatThrownBy(() ->
            InterpretationBuilder.<String>builder(agentProvider)
                .systemPrompt("prompt")
                .promptRenderer(renderer)
                .buildObservation())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("llmExecutor");
    }

    @Test
    void builder_defaultsInvocationPolicy() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        var json = """
            {"observations":[{"patternId":"p1","confidence":0.9,"evidence":[],"derivedFrom":[],"details":{},"reasoning":"r"}]}
            """;
        when(agentProvider.invoke(any())).thenReturn(
            Multi.createFrom().item(new AgentEvent.TextDelta(json)));

        ObservationRenderer<String> renderer = (events, obsCtx) ->
            CompletableFuture.completedFuture(
                new ObservationResult("rendered", List.of(), events.size(), 0, null));

        var summariser = InterpretationBuilder.<String>builder(agentProvider)
            .systemPrompt("Test prompt")
            .promptRenderer(renderer)
            .llmExecutor(Executors.newVirtualThreadPerTaskExecutor())
            .buildObservation();

        var result = summariser.summarise(List.of("event1"), null)
            .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result.observations()).hasSize(1);
    }
}
