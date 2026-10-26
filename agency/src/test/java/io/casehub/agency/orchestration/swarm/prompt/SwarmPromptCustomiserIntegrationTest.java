package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.agency.orchestration.aggregation.AggregationContext;
import io.casehub.agency.orchestration.aggregation.AggregationResult;
import io.casehub.agency.orchestration.swarm.CollectiveReasoningAggregation;
import io.casehub.agency.orchestration.swarm.SwarmAggregationState;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SwarmPromptCustomiserIntegrationTest {

    private AgentProvider agentProvider;

    @BeforeEach
    void setUp() {
        agentProvider = mock(AgentProvider.class);
    }

    @Test
    void collectiveReasoning_appliesCustomiser() {
        mockLlmResponse("""
                {"agreements":["ok"],"disagreements":[],"complementaryObservations":[],"confidence":0.9,"minorityViewpoints":[]}
                """);

        SystemPromptCustomiser customiser = (base, sigId, slot) ->
                base + "\n\nADDITIONAL: focus on statistical patterns";

        var aggregation = new CollectiveReasoningAggregation(agentProvider, null, customiser);
        var results = List.of(agentResult("analyst", "findings"));
        aggregation.aggregate(results, new AggregationContext<>(new SwarmContext()));

        var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(configCaptor.capture());
        var capturedConfig = configCaptor.getValue();
        assertThat(capturedConfig.systemPrompt()).contains("ADDITIONAL: focus on statistical patterns");
    }

    @Test
    void collectiveReasoning_worksWithoutCustomiser() {
        mockLlmResponse("""
                {"agreements":["ok"],"disagreements":[],"complementaryObservations":[],"confidence":0.9,"minorityViewpoints":[]}
                """);

        var aggregation = new CollectiveReasoningAggregation(agentProvider);
        var results = List.of(agentResult("analyst", "findings"));
        var result = aggregation.aggregate(results, new AggregationContext<>(new SwarmContext()));

        assertThat(result).isInstanceOf(AggregationResult.Resolved.class);
    }

    @Test
    void collectiveReasoning_customiserReceivesCorrectSignatureId() {
        mockLlmResponse("""
                {"agreements":[],"disagreements":[],"complementaryObservations":[],"confidence":0.5,"minorityViewpoints":[]}
                """);

        SystemPromptCustomiser customiser = mock(SystemPromptCustomiser.class);
        when(customiser.customise(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        var aggregation = new CollectiveReasoningAggregation(agentProvider, null, customiser);
        aggregation.aggregate(List.of(agentResult("a", "x")), new AggregationContext<>(new SwarmContext()));

        verify(customiser).customise(any(), eq("swarm-collective-reasoning"), eq("control"));
    }

    @Test
    void collectiveReasoning_usesSetVariantSlot() {
        mockLlmResponse("""
                {"agreements":[],"disagreements":[],"complementaryObservations":[],"confidence":0.5,"minorityViewpoints":[]}
                """);

        SystemPromptCustomiser customiser = mock(SystemPromptCustomiser.class);
        when(customiser.customise(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        var aggregation = new CollectiveReasoningAggregation(agentProvider, null, customiser);
        aggregation.setVariantSlot("experiment");
        aggregation.aggregate(List.of(agentResult("a", "x")), new AggregationContext<>(new SwarmContext()));

        verify(customiser).customise(any(), eq("swarm-collective-reasoning"), eq("experiment"));
    }

    @Test
    void builder_passesCustomiserToAggregation() {
        mockLlmResponse("""
                {"agreements":["built"],"disagreements":[],"complementaryObservations":[],"confidence":0.8,"minorityViewpoints":[]}
                """);

        SystemPromptCustomiser customiser = mock(SystemPromptCustomiser.class);
        when(customiser.customise(any(), any(), any())).thenAnswer(inv ->
                inv.getArgument(0) + "\n\nBUILDER_WIRED");

        var coordinator = new io.casehub.agency.orchestration.swarm.SwarmCoordinatorBuilder()
                .agentProvider(agentProvider)
                .promptCustomiser(customiser)
                .build();

        var results = List.of(agentResult("agent1", "output1"));
        coordinator.aggregation().aggregate(results, new AggregationContext<>(new SwarmContext()));

        var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(configCaptor.capture());
        assertThat(configCaptor.getValue().systemPrompt()).contains("BUILDER_WIRED");
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
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        return AgentResult.success(ref, output);
    }
}
