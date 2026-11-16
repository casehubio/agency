package io.casehub.agency.orchestration.degradation;

import io.casehub.platform.agent.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.agency.orchestration.swarm.SwarmFallbackStrategy;
import io.casehub.agency.orchestration.topology.CentralisedTopology;
import io.casehub.agency.orchestration.topology.TopologyContext;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CentralisedTopologyCascadeTest {

    CentralisedTopology<SwarmContext> topology;
    TopologyContext topologyContext;

    @BeforeEach
    void setUp() {
        io.casehub.platform.agent.AgentProvider agentProvider = new io.casehub.platform.agent.AgentProvider() {
            @Override
            public io.smallrye.mutiny.Multi<io.casehub.platform.agent.AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {return io.smallrye.mutiny.Multi.createFrom().empty();}

            @Override
            public io.casehub.platform.agent.AgentSession openSession(io.casehub.platform.agent.AgentSessionInit init)                        {throw new UnsupportedOperationException("no-op");}
        };
        var swarmContext      = new SwarmContext();
        var experienceSection = new io.casehub.agency.orchestration.swarm.SwarmExperienceSection(io.casehub.agency.orchestration.swarm.ExperienceReplayConfig.defaults());
        var routingStrategy   = new io.casehub.agency.orchestration.swarm.SwarmRoutingStrategy(agentProvider, experienceSection, 12);
        var aggregation       = new io.casehub.agency.orchestration.swarm.CollectiveReasoningAggregation(agentProvider, null);
        io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor advisor = ctx ->
                                                                                new io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor.ProvisioningAdvice(false, null, null, "no-op");

        var fallback = new SwarmFallbackStrategy();
        topology = new CentralisedTopology<>(
                swarmContext, routingStrategy, aggregation,
                ctx -> new io.casehub.agency.orchestration.termination.TerminationDecision.Continue(),
                List.of("experience-replay"),
                advisor, List::of,
                new HeuristicRoutingSummariser(fallback, List::of),
                new HeuristicAggregationSummariser(fallback),
                new HeuristicProvisioningSummariser(fallback, () -> 0, () -> 10),
                null);

        var degradationPolicy = DegradationPolicy.defaults();
        topologyContext = new TopologyContext(
                UUID.randomUUID(),
                new TopologyDescriptor(TopologyType.CENTRALISED, Map.of()),
                agentProvider,
                new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
                AutonomyBudgetPolicy.defaults(),
                degradationPolicy);
    }

    @Test
    void configure_wiresThreeLoopsWithCascadeTriggers() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.loopDescriptors()).hasSize(3);
    }

    @Test
    void onDegradation_cascadesAllHeuristic_whenThresholdBreached() {
        topology.configure(topologyContext);

        topology.onDegradation(new SwarmDegradedEvent("centralised-team-formation", "budget exhausted", true));
        topology.onDegradation(new SwarmDegradedEvent("centralised-collective-reasoning", "budget exhausted", true));

        assertThat(topology.loops()).isNotEmpty();
    }

    @Test
    void onDegradation_recoveryRemovesFromDegradedSet() {
        topology.configure(topologyContext);

        topology.onDegradation(new SwarmDegradedEvent("centralised-team-formation", "latency", true));
        topology.onDegradation(new SwarmDegradedEvent("centralised-team-formation", "cleared", false));

        topology.onDegradation(new SwarmDegradedEvent("centralised-collective-reasoning", "budget", true));
        assertThat(topology.loops()).isNotEmpty();
    }
}
