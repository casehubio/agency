package io.casehub.blocks.summarisation.narrative;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecisionNarrativeSummariserTest {

    private static final Instant T1 = Instant.parse("2026-09-11T10:00:00Z");

    @Test
    void summarise_withLlmResponse_producesNarrative() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        var jsonResponse = """
                {"explanation":"Agent analyst-3 was selected because CBR found 5 similar cases.","evidenceSources":["RoutingDecision","CbrRetrieval"],"confidence":0.85}""";
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(jsonResponse)));

        var summariser = new DecisionNarrativeSummariser(agentProvider);
        var step = new StepDecisionSummary("case1", "route-analyst",
                List.of(new SignalDigest("RoutingDecision", "Selected analyst-3", Map.of("selected", "analyst-3"), 0.87)),
                T1, T1.plusSeconds(1));

        var result = summariser.summarise(List.of(step), null).toCompletableFuture().get();

        assertThat(result.caseId()).isEqualTo("case1");
        assertThat(result.explanation()).contains("analyst-3");
        assertThat(result.evidenceSources()).contains("RoutingDecision");
        assertThat(result.confidence()).isEqualTo(0.85);
    }

    @Test
    void summarise_withPreviousNarrative_passedToPrompt() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        var jsonResponse = """
                {"explanation":"Extended narrative.","evidenceSources":["StepOutcome"],"confidence":0.9}""";
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(jsonResponse)));

        var summariser = new DecisionNarrativeSummariser(agentProvider);
        var previous = new DecisionNarrative("case1", List.of("step1"), "Previous explanation.", List.of("RoutingDecision"), 0.8, T1);
        var step = new StepDecisionSummary("case1", "step2",
                List.of(new SignalDigest("StepOutcome", "step2 COMPLETED", Map.of("status", "COMPLETED"), 1.0)),
                T1, T1.plusSeconds(2));

        var result = summariser.summarise(List.of(step), previous).toCompletableFuture().get();

        assertThat(result.stepNames()).contains("step2");
    }

    @Test
    void summarise_llmFailure_fallsBackToTemplate() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM unavailable")));

        var summariser = new DecisionNarrativeSummariser(agentProvider);
        var step = new StepDecisionSummary("case1", "route-analyst",
                List.of(new SignalDigest("RoutingDecision", "Selected analyst-3", Map.of("selected", "analyst-3"), 0.87)),
                T1, T1.plusSeconds(1));

        var result = summariser.summarise(List.of(step), null).toCompletableFuture().get();

        assertThat(result.caseId()).isEqualTo("case1");
        assertThat(result.explanation()).contains("Selected analyst-3");
        assertThat(result.confidence()).isEqualTo(0.87);
    }

    @Test
    void summarise_emptyLlmResponse_fallsBackToTemplate() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().empty());

        var summariser = new DecisionNarrativeSummariser(agentProvider);
        var step = new StepDecisionSummary("case1", "step1",
                List.of(new SignalDigest("StepOutcome", "step1 COMPLETED", Map.of("status", "COMPLETED"), 1.0)),
                T1, T1.plusSeconds(1));

        var result = summariser.summarise(List.of(step), null).toCompletableFuture().get();

        assertThat(result.explanation()).contains("step1 COMPLETED");
    }
}
