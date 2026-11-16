package io.casehub.agency.orchestration.distributed;

import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.agency.orchestration.topology.DistributedTopology;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedCoordinationIntegrationTest {

    @Test
    void builderCreatesWorkingTopologyEndToEnd() {
        var agentProvider = returning("""
            {"signals": [{"name": "need-capacity:analysis", "strength": 0.7}],
             "synthesis": "Initial observation", "reasoning": "Starting work"}
            """);

        var topology = new DistributedTopologyBuilder()
                .agentProvider(agentProvider)
                .agentRoles(Map.of(
                        "agent-1", AgentRole.of("analysis", StructuralRole.PEER),
                        "agent-2", AgentRole.of("research", StructuralRole.PEER)))
                .convergenceThreshold(0.7)
                .buildTopology();

        var descriptor = topology.describeTopology(UUID.randomUUID());
        assertThat(descriptor.topologyType()).isEqualTo(TopologyType.DISTRIBUTED);
        assertThat(descriptor.agentRoles()).hasSize(2);
    }

    @Test
    void topologyRoutesSwarmEventsWithoutError() {
        var topology = new DistributedTopologyBuilder()
                .agentProvider(returning("{}"))
                .agentRoles(Map.of(
                        "a1", AgentRole.of("role-a", StructuralRole.PEER),
                        "a2", AgentRole.of("role-b", StructuralRole.PEER)))
                .buildTopology();

        topology.onSwarmEvent(new SwarmCoordinationEvent.ProvisioningChanged(UUID.randomUUID(), 2, 10));
        topology.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(UUID.randomUUID(), 
                new SwarmProgress(0.5, 0.3, 0.4, Instant.now())));
        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(UUID.randomUUID(), "a1", null));
    }

    @Test
    void writeBackHandlerAcceptsSignals() {
        var deposited = new java.util.ArrayList<String>();
        StigmergyWriteBackHandler.StigmergyWriter writer =
                (agentId, name, strength) -> deposited.add(name);

        var handler = new StigmergyWriteBackHandler(writer, "test-agent");

        var reasoner = new DistributedAgentReasoner(returning("""
            {"signals": [{"name": "in-progress:task-1", "strength": 1.0}],
             "reasoning": "Claiming task"}
            """), null);

        var result = reasoner.summarise(
                java.util.List.of(io.casehub.agency.orchestration.loop.ObservationEvent.of("tick", "")),
                null).toCompletableFuture().join();

        handler.accept(result);
        assertThat(deposited).containsExactly("in-progress:task-1");
    }

    @Test
    void reasonerFallsBackGracefullyOnError() {
        var reasoner = new DistributedAgentReasoner(failing(), null);

        var result = reasoner.summarise(
                java.util.List.of(io.casehub.agency.orchestration.loop.ObservationEvent.of("tick", "")),
                null).toCompletableFuture().join();

        assertThat(result).isInstanceOf(io.casehub.agency.orchestration.loop.DecisionOutcome.WriteBack.class);
        var wb = (io.casehub.agency.orchestration.loop.DecisionOutcome.WriteBack) result;
        assertThat(wb.signals()).isEmpty();
    }

    private static AgentProvider returning(String response) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
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

    private static AgentProvider failing() {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("test stub");
            }
        };
    }
}
