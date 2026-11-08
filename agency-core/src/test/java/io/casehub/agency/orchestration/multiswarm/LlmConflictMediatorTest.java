package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmConflictMediatorTest {

    UUID swarmA = UUID.randomUUID();
    UUID swarmB = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();

    ConflictContext conflictContext() {
        var finding = new MultiSwarmSignal.Finding(swarmA, Set.of("analysis"),
                System.currentTimeMillis(), "domain", "node-7:healthy", 0.9, Map.of());
        var notification = new CrossSwarmNotification(swarmA, swarmB,
                NotificationType.CONFLICT, finding, 0.8);
        var beliefs = new BeliefSet<SwarmConclusion>()
                .expand(Belief.of("a:domain",
                        new SwarmConclusion(swarmA, "domain", "node-7:healthy", 0.9), 90));
        var contracted = List.of(
                Belief.of("b:domain",
                        new SwarmConclusion(swarmB, "domain", "node-7:degraded", 0.8), 80));
        return new ConflictContext(notification, beliefs, contracted,
                Map.of(swarmA, new SwarmProgress(0.9, 0.8, 0.9, Instant.now())),
                Map.of(),
                Map.of(swarmA, new SwarmDescriptor(swarmA, caseId, TopologyType.CENTRALISED,
                                Set.of("analysis"), Set.of("agent-1"), null),
                        swarmB, new SwarmDescriptor(swarmB, caseId, TopologyType.CENTRALISED,
                                Set.of("remediation"), Set.of("agent-2"), null)));
    }

    static AgentProvider returning(String response) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(response),
                        new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 100L, 80L, null, 0, false, null, null));
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("test stub");
            }
        };
    }

    static AgentProvider capturingAndReturning(AtomicReference<AgentSessionConfig> captured, String response) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                captured.set(config);
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(response),
                        new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 100L, 80L, null, 0, false, null, null));
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("test stub");
            }
        };
    }

    static AgentProvider failing() {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().failure(new RuntimeException("connection refused"));
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("test stub");
            }
        };
    }

    @Test
    void resolves_conflict_from_valid_json() {
        var mediator = new LlmConflictMediator(returning("""
                {"resolution": "node-7:healthy", "updatedConclusion": "node-7:healthy",
                 "confidence": 0.95, "justification": "Higher confidence from analysis swarm"}
                """));

        var result = mediator.mediate(conflictContext());

        assertThat(result).isInstanceOf(MediationResult.Resolved.class);
        var resolved = (MediationResult.Resolved) result;
        assertThat(resolved.resolution()).contains("node-7:healthy");
        assertThat(resolved.updatedBelief()).isNotNull();
        assertThat(resolved.updatedBelief().value().confidence()).isEqualTo(0.95);
    }

    @Test
    void includes_conflict_type_in_system_prompt() {
        var captured = new AtomicReference<AgentSessionConfig>();
        var mediator = new LlmConflictMediator(capturingAndReturning(captured, """
                {"resolution": "resolved", "updatedConclusion": "node-7:healthy",
                 "confidence": 0.9, "justification": "test"}
                """));

        mediator.mediate(conflictContext());

        assertThat(captured.get().systemPrompt()).contains("contradicting conclusions");
    }

    @Test
    void user_prompt_includes_belief_context() {
        var captured = new AtomicReference<AgentSessionConfig>();
        var mediator = new LlmConflictMediator(capturingAndReturning(captured, """
                {"resolution": "resolved", "updatedConclusion": "node-7:healthy",
                 "confidence": 0.9, "justification": "test"}
                """));

        mediator.mediate(conflictContext());

        assertThat(captured.get().userPrompt()).contains("node-7:healthy");
        assertThat(captured.get().userPrompt()).contains("node-7:degraded");
        assertThat(captured.get().userPrompt()).contains("Beliefs Removed by AGM Revision");
    }

    @Test
    void throws_mediation_exception_on_agent_error() {
        var mediator = new LlmConflictMediator(failing());

        assertThatThrownBy(() -> mediator.mediate(conflictContext()))
                .isInstanceOf(MediationException.class);
    }

    @Test
    void throws_mediation_exception_on_unparseable_response() {
        var mediator = new LlmConflictMediator(returning("I cannot help with that"));

        assertThatThrownBy(() -> mediator.mediate(conflictContext()))
                .isInstanceOf(MediationException.class);
    }
}
