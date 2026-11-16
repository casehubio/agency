package io.casehub.agency.orchestration.bridging;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.degradation.DegradationPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.agency.orchestration.topology.HivePatternAdapter;
import io.casehub.agency.orchestration.topology.HiveTopology;
import io.casehub.agency.orchestration.topology.OrchestrationPatternType;
import io.casehub.agency.orchestration.topology.TestAgentProviders;
import io.casehub.agency.orchestration.topology.TopologyContext;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.PlatformRole;
import io.casehub.api.model.coordination.RoleSuggestion;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.BehavioralFingerprint;
import io.casehub.api.model.stigmergy.DetectedRole;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.coordination.RoleBridgingStrategy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OscillationProtectionTest {

  Map<String, AgentRole> agentRoles;
  HiveTopology topology;

  @BeforeEach
  void setUp() {
    agentRoles = Map.of(
        "supervisor", AgentRole.of("supervisor", StructuralRole.INITIATOR),
        "builder", AgentRole.of("builder", StructuralRole.CONTRACTOR));
  }

  private HiveTopology createTopology(RoleBridgingStrategy strategy) {
    return new HiveTopology(agentRoles,
        new HivePatternAdapter(TestAgentProviders.noOp()),
        OrchestrationPatternType.SUPERVISOR, strategy);
  }

  private TopologyContext createContext(RoleBridgingPolicy policy) {
    return new TopologyContext(
        UUID.randomUUID(),
        new TopologyDescriptor(TopologyType.CENTRALISED, agentRoles),
        TestAgentProviders.noOp(),
        new DecisionLoopFactory(name -> c -> "", new EventStreamBus<>()),
        AutonomyBudgetPolicy.defaults(), DegradationPolicy.defaults(),
        policy, null, null);
  }

  @Test
  void promoted_at_high_confidence_not_demoted_within_hysteresis_band() {
    var strategy = new RoleBridgingStrategy() {
      double confidence = 0.85;
      @Override
      public List<RoleSuggestion> map(List<DetectedRole> detectedRoles) {
        return detectedRoles.stream()
            .map(d -> new RoleSuggestion("coordinator", StructuralRole.INITIATOR,
                PlatformRole.COORDINATOR, confidence, d))
            .toList();
      }
    };

    topology = createTopology(strategy);
    var policy = new RoleBridgingPolicy(
        RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    topology.configure(createContext(policy));

    var detected = List.of(new DetectedRole("cluster-1", Set.of("builder"),
        BehavioralFingerprint.EMPTY, List.of("coordination"), 5, 0.1));
    var event = new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), detected, SwarmProgress.EMPTY);

    topology.onSwarmEvent(event);
    assertThat(topology.describeTopology(UUID.randomUUID()).agentRoles()
        .get("builder").platformRole()).isEqualTo(PlatformRole.COORDINATOR);

    strategy.confidence = 0.6;
    topology.onSwarmEvent(event);
    topology.onSwarmEvent(event);
    topology.onSwarmEvent(event);
    topology.onSwarmEvent(event);

    assertThat(topology.describeTopology(UUID.randomUUID()).agentRoles()
        .get("builder").platformRole()).isEqualTo(PlatformRole.COORDINATOR);
  }

  @Test
  void not_demoted_before_min_hold_cycles() {
    var strategy = new RoleBridgingStrategy() {
      double confidence = 0.85;
      @Override
      public List<RoleSuggestion> map(List<DetectedRole> detectedRoles) {
        return detectedRoles.stream()
            .map(d -> new RoleSuggestion("coordinator", StructuralRole.INITIATOR,
                PlatformRole.COORDINATOR, confidence, d))
            .toList();
      }
    };

    topology = createTopology(strategy);
    var policy = new RoleBridgingPolicy(
        RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    topology.configure(createContext(policy));

    var detected = List.of(new DetectedRole("cluster-1", Set.of("builder"),
        BehavioralFingerprint.EMPTY, List.of("coordination"), 5, 0.1));
    var event = new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), detected, SwarmProgress.EMPTY);

    topology.onSwarmEvent(event);
    assertThat(topology.describeTopology(UUID.randomUUID()).agentRoles()
        .get("builder").platformRole()).isEqualTo(PlatformRole.COORDINATOR);

    strategy.confidence = 0.3;
    topology.onSwarmEvent(event);
    topology.onSwarmEvent(event);
    assertThat(topology.describeTopology(UUID.randomUUID()).agentRoles()
        .get("builder").platformRole()).isEqualTo(PlatformRole.COORDINATOR);

    topology.onSwarmEvent(event);
    assertThat(topology.describeTopology(UUID.randomUUID()).agentRoles()
        .get("builder")).isEqualTo(agentRoles.get("builder"));
  }

  @Test
  void absent_from_suggestions_triggers_demotion_after_hold_cycles() {
    var strategy = new RoleBridgingStrategy() {
      boolean returnSuggestions = true;
      @Override
      public List<RoleSuggestion> map(List<DetectedRole> detectedRoles) {
        if (!returnSuggestions) return List.of();
        return detectedRoles.stream()
            .filter(d -> d.memberAgents().contains("builder"))
            .map(d -> new RoleSuggestion("coordinator", StructuralRole.INITIATOR,
                PlatformRole.COORDINATOR, 0.9, d))
            .toList();
      }
    };

    topology = createTopology(strategy);
    var policy = new RoleBridgingPolicy(
        RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 2);
    topology.configure(createContext(policy));

    var detected = List.of(new DetectedRole("cluster-1", Set.of("builder"),
        BehavioralFingerprint.EMPTY, List.of("coordination"), 5, 0.1));
    topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), detected, SwarmProgress.EMPTY));
    assertThat(topology.describeTopology(UUID.randomUUID()).agentRoles()
        .get("builder").platformRole()).isEqualTo(PlatformRole.COORDINATOR);

    strategy.returnSuggestions = false;
    topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), detected, SwarmProgress.EMPTY));
    topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), detected, SwarmProgress.EMPTY));

    assertThat(topology.describeTopology(UUID.randomUUID()).agentRoles()
        .get("builder")).isEqualTo(agentRoles.get("builder"));
  }
}
