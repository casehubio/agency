package io.casehub.agency.orchestration.bridging;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.platform.agent.budget.AutonomyBudgetPolicy;
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

class RoleBridgingIntegrationTest {

  Map<String, AgentRole> agentRoles;

  RoleBridgingStrategy testStrategy = detectedRoles -> detectedRoles.stream()
      .filter(dr -> dr.dominantFeatures().contains("coordination"))
      .map(dr -> new RoleSuggestion(
          "coordinator", StructuralRole.INITIATOR, PlatformRole.COORDINATOR,
          0.85, dr))
      .toList();

  @BeforeEach
  void setUp() {
    agentRoles = Map.of(
        "supervisor", AgentRole.of("supervisor", StructuralRole.INITIATOR),
        "builder", AgentRole.of("builder", StructuralRole.CONTRACTOR),
        "critic", AgentRole.of("critic", StructuralRole.CONTRACTOR));
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
        policy, null, null, null);
  }

  @Test
  void advisory_mode_does_not_modify_roles() {
    var topology = createTopology(testStrategy);
    topology.configure(createContext(RoleBridgingPolicy.ADVISORY));

    var detected = List.of(new DetectedRole("cluster-1", Set.of("builder"),
        BehavioralFingerprint.EMPTY, List.of("coordination"), 5, 0.1));
    topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), detected, SwarmProgress.EMPTY));

    assertThat(topology.describeTopology(UUID.randomUUID()).agentRoles())
        .isEqualTo(agentRoles);
  }

  @Test
  void auto_above_threshold_applies_suggestion_above_threshold() {
    var topology = createTopology(testStrategy);
    var policy = new RoleBridgingPolicy(
        RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    topology.configure(createContext(policy));

    var detected = List.of(new DetectedRole("cluster-1", Set.of("builder"),
        BehavioralFingerprint.EMPTY, List.of("coordination"), 5, 0.1));
    topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), detected, SwarmProgress.EMPTY));

    var roles = topology.describeTopology(UUID.randomUUID()).agentRoles();
    assertThat(roles.get("builder").platformRole()).isEqualTo(PlatformRole.COORDINATOR);
    assertThat(roles.get("builder").structuralRole()).isEqualTo(StructuralRole.INITIATOR);
  }

  @Test
  void auto_above_threshold_ignores_suggestion_below_threshold() {
    RoleBridgingStrategy lowConfidence = detectedRoles -> detectedRoles.stream()
        .map(dr -> new RoleSuggestion("coordinator", StructuralRole.INITIATOR,
            PlatformRole.COORDINATOR, 0.4, dr))
        .toList();

    var topology = createTopology(lowConfidence);
    var policy = new RoleBridgingPolicy(
        RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    topology.configure(createContext(policy));

    var detected = List.of(new DetectedRole("cluster-1", Set.of("builder"),
        BehavioralFingerprint.EMPTY, List.of("coordination"), 5, 0.1));
    topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), detected, SwarmProgress.EMPTY));

    assertThat(topology.describeTopology(UUID.randomUUID()).agentRoles())
        .isEqualTo(agentRoles);
  }
}
