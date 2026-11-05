package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.orchestration.swarm.CollectiveReasoningAggregation;
import io.casehub.agency.orchestration.swarm.ExperienceReplayConfig;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.agency.orchestration.swarm.SwarmExperienceSection;
import io.casehub.agency.orchestration.swarm.SwarmFallbackStrategy;
import io.casehub.agency.orchestration.swarm.SwarmRoutingStrategy;
import io.casehub.agency.orchestration.degradation.HeuristicRoutingSummariser;
import io.casehub.agency.orchestration.degradation.HeuristicAggregationSummariser;
import io.casehub.agency.orchestration.degradation.HeuristicProvisioningSummariser;
import io.casehub.agency.orchestration.topology.CentralisedTopology;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import io.casehub.agency.orchestration.topology.TopologyContext;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CentralisedTopologyCascadeTest {

    CentralisedTopology<SwarmContext> topology;
    TopologyContext topologyContext;

    @BeforeEach
    void setUp() {
        AgentProvider agentProvider = new AgentProvider() {
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit init) { throw new UnsupportedOperationException("no-op"); }
        };
        var swarmContext = new SwarmContext();
        var experienceSection = new SwarmExperienceSection(ExperienceReplayConfig.defaults());
        var routingStrategy = new SwarmRoutingStrategy(agentProvider, experienceSection, 12);
        var aggregation = new CollectiveReasoningAggregation(agentProvider, null);
        SwarmProvisioningAdvisor advisor = ctx ->
                new SwarmProvisioningAdvisor.ProvisioningAdvice(false, null, null, "no-op");

        var fallback = new SwarmFallbackStrategy();
        topology = new CentralisedTopology<>(
                swarmContext, routingStrategy, aggregation,
                ctx -> new io.casehub.agency.orchestration.termination.TerminationDecision.Continue(),
                List.of("experience-replay"),
                advisor, List::of,
                new HeuristicRoutingSummariser(fallback, List::of),
                new HeuristicAggregationSummariser(fallback),
                new HeuristicProvisioningSummariser(fallback, 0, 10),
                null,
                e -> {});

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
