package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.swarm.CollectiveReasoningAggregation;
import io.casehub.agency.orchestration.swarm.ExperienceReplayConfig;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.orchestration.swarm.SwarmCoordinationEvent;
import io.casehub.agency.orchestration.swarm.SwarmExperienceSection;
import io.casehub.agency.orchestration.swarm.SwarmFallbackStrategy;
import io.casehub.agency.orchestration.swarm.SwarmRoutingStrategy;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CentralisedTopologyTest {

    SwarmContext        swarmContext;
    CentralisedTopology topology;
    TopologyContext     topologyContext;

    @BeforeEach
    void setUp() {
        AgentProvider agentProvider = TestAgentProviders.noOp();
        swarmContext = new SwarmContext();
        var experienceSection = new SwarmExperienceSection(ExperienceReplayConfig.defaults());
        var routingStrategy = new SwarmRoutingStrategy(
                agentProvider, experienceSection, 12);
        var aggregation = new CollectiveReasoningAggregation(agentProvider, null);
        SwarmProvisioningAdvisor advisor = ctx ->
                                                   new SwarmProvisioningAdvisor.ProvisioningAdvice(false, null, null, "no-op");

        topology = new CentralisedTopology(
                swarmContext,
                routingStrategy,
                aggregation,
                ctx -> new io.casehub.agency.orchestration.termination.TerminationDecision.Continue(),
                new SwarmFallbackStrategy(),
                List.of("experience-replay"),
                advisor,
                List::of);

        topologyContext = new TopologyContext(
                UUID.randomUUID(),
                new TopologyDescriptor(TopologyType.CENTRALISED, Map.of()),
                agentProvider,
                new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
                AutonomyBudgetPolicy.defaults());
    }

    @Test
    void describeTopology_returns_centralised() {
        var descriptor = topology.describeTopology(UUID.randomUUID());
        assertThat(descriptor.topologyType()).isEqualTo(TopologyType.CENTRALISED);
        assertThat(descriptor.agentRoles()).isEmpty();
    }

    @Test
    void configure_returns_single_loop_descriptor() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.loopDescriptors()).hasSize(3);

        DecisionLoopDescriptor teamFormation = plan.loopDescriptors().stream()
                                                   .filter(d -> d.name().equals("centralised-team-formation"))
                                                   .findFirst().orElseThrow();
        assertThat(teamFormation.observationStrategy()).isEqualTo(ObservationStrategy.EVENT_DRIVEN);
        assertThat(teamFormation.scope()).isEqualTo(DecisionScope.FULL_AUTONOMY);
        assertThat(teamFormation.communicationMode()).isEqualTo(CommunicationMode.BROADCAST);
        assertThat(teamFormation.promptSectionNames()).containsExactly("experience-replay");

        DecisionLoopDescriptor reasoning = plan.loopDescriptors().stream()
                                               .filter(d -> d.name().equals("centralised-collective-reasoning"))
                                               .findFirst().orElseThrow();
        assertThat(reasoning.scope()).isEqualTo(DecisionScope.FULL_AUTONOMY);

        DecisionLoopDescriptor provisioning = plan.loopDescriptors().stream()
                                                  .filter(d -> d.name().equals("centralised-provisioning"))
                                                  .findFirst().orElseThrow();
        assertThat(provisioning.observationStrategy()).isEqualTo(ObservationStrategy.TICK_DRIVEN);
    }

    @Test
    void configure_returns_non_null_executor() {
        var plan = topology.configure(topologyContext);
        assertThat(plan.executor()).isNotNull();
    }

    @Test
    void configure_creates_three_loops() {
        var plan = topology.configure(topologyContext);
        assertThat(topology.loops()).hasSize(3);
        assertThat(topology.loops().stream().map(l -> l.descriptor().name()))
                .containsExactly(
                        "centralised-team-formation",
                        "centralised-collective-reasoning",
                        "centralised-provisioning");
    }

    @Test
    void onSwarmEvent_feeds_provisioning_observation_after_configure() {
        topology.configure(topologyContext);
        topology.onSwarmEvent(new SwarmCoordinationEvent.ProvisioningChanged(5, 10));
        assertThat(swarmContext.currentSize()).isEqualTo(5);
        assertThat(swarmContext.maxSize()).isEqualTo(10);
    }

    @Test
    void onSwarmEvent_feeds_progress_to_all_loops_after_configure() {
        topology.configure(topologyContext);
        var progress = new SwarmProgress(0.5, 0.3, 0.8, java.time.Instant.now());
        topology.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(progress));
        assertThat(swarmContext.swarmProgress()).isEqualTo(progress);
    }

    @Test
    void onSwarmEvent_feeds_member_completed_after_configure() {
        topology.configure(topologyContext);
        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted("agent-x", null));
        assertThat(swarmContext.completedMembers()).contains("agent-x");
    }


    @Test
    void onSwarmEvent_updates_context_on_progress() {
        var progress = new SwarmProgress(0.5, 0.3, 0.8, java.time.Instant.now());
        topology.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(progress));
        assertThat(swarmContext.swarmProgress()).isEqualTo(progress);
    }

    @Test
    void onSwarmEvent_updates_context_on_member_completed() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted("agent-x", null));
        assertThat(swarmContext.completedMembers()).contains("agent-x");
    }

    @Test
    void onSwarmEvent_updates_context_on_provisioning_changed() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.ProvisioningChanged(3, 10));
        assertThat(swarmContext.currentSize()).isEqualTo(3);
        assertThat(swarmContext.maxSize()).isEqualTo(10);
    }

    @Test
    void onFeedback_does_not_throw() {
        var feedback = new CoordinationFeedback(
                "agent-1",
                new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()),
                FeedbackSignal.SUCCESS);
        topology.onFeedback(feedback);
    }

    @Test
    void executor_handles_scope_gated_outcome() throws Exception {
        var plan = topology.configure(topologyContext);
        var gated = new DecisionOutcome.ScopeGated(
                io.casehub.api.model.coordination.DecisionType.PROVISIONING,
                new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));

        var feedback = plan.executor()
                           .execute("coordinator", gated, topologyContext)
                           .toCompletableFuture().get();

        assertThat(feedback.signal()).isEqualTo(FeedbackSignal.SCOPE_VIOLATION);
        assertThat(feedback.agentId()).isEqualTo("coordinator");
    }

    @Test
    void executor_handles_provision_outcome() throws Exception {
        var plan = topology.configure(topologyContext);
        var advice = new io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor.ProvisioningAdvice(
                true, null, null, "scale up");
        var provision = new DecisionOutcome.Provision(advice);

        var feedback = plan.executor()
                           .execute("coordinator", provision, topologyContext)
                           .toCompletableFuture().get();

        assertThat(feedback.signal()).isEqualTo(FeedbackSignal.SUCCESS);
        assertThat(feedback.agentId()).isEqualTo("coordinator");
    }

}
