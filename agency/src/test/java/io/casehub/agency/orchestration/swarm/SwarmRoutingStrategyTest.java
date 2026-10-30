package io.casehub.agency.orchestration.swarm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.routing.RoutingContext;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import io.casehub.agency.orchestration.swarm.prompt.SwarmPromptSignatures;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwarmRoutingStrategyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentProvider agentProvider;
    private SwarmExperienceSection experienceSection;

    @BeforeEach
    void setUp() {
        agentProvider = mock(AgentProvider.class);
        experienceSection = new SwarmExperienceSection(ExperienceReplayConfig.defaults());
    }

    @Test
    void route_returnsSwarmAssignmentOnSuccess() {
        mockLlmResponse("""
                {"agents": ["analyst", "responder"], "teams": {"analysis": ["analyst"]}, "strategy": "divide"}
                """);

        var strategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 12);
        var context = routingContext(List.of(
                candidate("analyst"), candidate("responder")));
        var decision = strategy.route(context);

        assertThat(decision).isInstanceOf(RoutingDecision.SwarmAssignment.class);
        var assignment = (RoutingDecision.SwarmAssignment) decision;
        assertThat(assignment.agents()).hasSize(2);
        assertThat(assignment.strategy()).isEqualTo("divide");
        assertThat(assignment.teams()).containsKey("analysis");
    }

    @Test
    void route_fallsBackWhenLlmFails() {
        when(agentProvider.invoke(any())).thenThrow(new RuntimeException("LLM unavailable"));

        var strategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 12);
        var context = routingContext(List.of(candidate("analyst")));
        var decision = strategy.route(context);

        assertThat(decision).isInstanceOf(RoutingDecision.SwarmAssignment.class);
        var assignment = (RoutingDecision.SwarmAssignment) decision;
        assertThat(assignment.strategy()).isEqualTo("heuristic-all");
    }

    @Test
    void route_unresolvableWhenNoCandidates() {
        var strategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 12);
        var context = routingContext(List.of());
        var decision = strategy.route(context);

        assertThat(decision).isInstanceOf(RoutingDecision.Unresolvable.class);
    }

    @Test
    void route_preFiltersLargeCandidateSet() {
        var candidates = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> candidate("agent-" + i))
                .collect(Collectors.toList());

        mockLlmResponse("""
                {"agents": ["agent-1", "agent-2"], "teams": {}, "strategy": "filtered"}
                """);

        var strategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 5);
        var context = routingContext(candidates);
        var decision = strategy.route(context);

        assertThat(decision).isInstanceOf(RoutingDecision.SwarmAssignment.class);
    }

    @Test
    void route_appliesSystemPromptCustomiser() {
        var customiser = mock(io.casehub.agency.optimisation.prompt.SystemPromptCustomiser.class);
        when(customiser.customise(any(), eq(io.casehub.agency.orchestration.swarm.prompt.SwarmPromptSignatures.TEAM_FORMATION.id()), eq("control")))
                .thenAnswer(inv -> inv.getArgument(0) + "\n## Optimised Instructions\nUse diversity.");

        var strategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 12, null, customiser, List.<SwarmPromptSection>of());
        strategy.setVariantSlot("control");

        mockLlmResponse("""
                        {"agents": ["analyst"], "teams": {"t1": ["analyst"]}, "strategy": "solo"}
                        """);

        var configCaptor = org.mockito.ArgumentCaptor.forClass(io.casehub.platform.agent.AgentSessionConfig.class);
        var context      = routingContext(List.of(candidate("analyst")));
        strategy.route(context);

        org.mockito.Mockito.verify(agentProvider).invoke(configCaptor.capture());
        assertThat(configCaptor.getValue().systemPrompt()).contains("Optimised Instructions");
    }

    @Test
    void route_includesAdditionalSections() {
        SwarmPromptSection section  = ctx -> "## Few-Shot Examples\n1. Previous team: [a, b]";
        var                strategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 12, null, null, List.of(section));

        mockLlmResponse("""
                        {"agents": ["analyst"], "teams": {"t1": ["analyst"]}, "strategy": "solo"}
                        """);

        var configCaptor = org.mockito.ArgumentCaptor.forClass(io.casehub.platform.agent.AgentSessionConfig.class);
        var context      = routingContext(List.of(candidate("analyst")));
        strategy.route(context);

        org.mockito.Mockito.verify(agentProvider).invoke(configCaptor.capture());
        assertThat(configCaptor.getValue().systemPrompt()).contains("Few-Shot Examples");
    }


    private void mockLlmResponse(String json) {
        when(agentProvider.invoke(any())).thenAnswer(inv -> {
            var events = new java.util.ArrayList<AgentEvent>();
            events.add(new AgentEvent.TextDelta(json));
            events.add(new AgentEvent.InvocationComplete(10, 20, 0, 0, 0, 0.01, 100L, 80L, "s1", 1, false, null, null));
            return io.smallrye.mutiny.Multi.createFrom().iterable(events);
        });
    }

    private RoutingCandidate candidate(String name) {
        var ref = AgentRef.external(name, x ->
                java.util.concurrent.CompletableFuture.completedFuture(
                        io.casehub.agency.core.AgentResult.success(null, "ok")));
        return new RoutingCandidate(ref, null);
    }

    private RoutingContext<SwarmContext> routingContext(List<RoutingCandidate> candidates) {
        return new RoutingContext<>("swarm-task", candidates, new SwarmContext());
    }
}
