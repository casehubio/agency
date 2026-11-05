package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.agency.orchestration.topology.HivePatternAdapter;
import io.casehub.agency.orchestration.topology.HiveTopology;
import io.casehub.agency.orchestration.topology.OrchestrationPatternType;
import io.casehub.agency.orchestration.topology.TopologyContext;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HiveTopologyCascadeTest {

    HiveTopology topology;
    TopologyContext topologyContext;

    @BeforeEach
    void setUp() {
        AgentProvider agentProvider = new AgentProvider() {
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit init) { throw new UnsupportedOperationException(); }
        };

        var roles = Map.of(
                "coordinator", AgentRole.of("coordinator", StructuralRole.INITIATOR),
                "worker-1", AgentRole.of("worker-1", StructuralRole.CONTRACTOR),
                "worker-2", AgentRole.of("worker-2", StructuralRole.CONTRACTOR));

        topology = new HiveTopology(roles, new HivePatternAdapter(agentProvider), OrchestrationPatternType.DEBATE);

        topologyContext = new TopologyContext(
                UUID.randomUUID(),
                new TopologyDescriptor(TopologyType.CENTRALISED, roles),
                agentProvider,
                new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
                AutonomyBudgetPolicy.defaults(),
                DegradationPolicy.defaults());
    }

    @Test
    void onDegradation_simplifiesPattern_debateToVoting() {
        topology.configure(topologyContext);
        assertThat(topology.patternType()).isEqualTo(OrchestrationPatternType.DEBATE);

        topology.onDegradation(new SwarmDegradedEvent("hive-coordinator", "budget", true));
        topology.onDegradation(new SwarmDegradedEvent("hive-worker-worker-1", "budget", true));

        assertThat(topology.patternType()).isEqualTo(OrchestrationPatternType.VOTING);
    }

    @Test
    void onDegradation_cascadeIsOneWayRatchet() {
        topology.configure(topologyContext);

        topology.onDegradation(new SwarmDegradedEvent("hive-coordinator", "budget", true));
        topology.onDegradation(new SwarmDegradedEvent("hive-worker-worker-1", "budget", true));
        assertThat(topology.patternType()).isEqualTo(OrchestrationPatternType.VOTING);

        topology.onDegradation(new SwarmDegradedEvent("hive-coordinator", "cleared", false));
        topology.onDegradation(new SwarmDegradedEvent("hive-worker-worker-1", "cleared", false));
        assertThat(topology.patternType()).isEqualTo(OrchestrationPatternType.VOTING);
    }
}
