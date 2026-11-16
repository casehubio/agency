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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConflictResolutionTest {

  @Test
  void two_agents_suggested_as_initiator_highest_confidence_wins() {
    Map<String, AgentRole> roles = Map.of(
        "supervisor", AgentRole.of("supervisor", StructuralRole.INITIATOR),
        "agent-a", AgentRole.of("worker-a", StructuralRole.CONTRACTOR),
        "agent-b", AgentRole.of("worker-b", StructuralRole.CONTRACTOR));

    var clusterA = new DetectedRole("cluster-a", Set.of("agent-a"),
        BehavioralFingerprint.EMPTY, List.of("coordination"), 5, 0.1);
    var clusterB = new DetectedRole("cluster-b", Set.of("agent-b"),
        BehavioralFingerprint.EMPTY, List.of("coordination"), 8, 0.05);

    RoleBridgingStrategy strategy = detectedRoles -> {
      var suggestions = new ArrayList<RoleSuggestion>();
      for (var dr : detectedRoles) {
        double confidence = dr.roleId().equals("cluster-a") ? 0.85 : 0.92;
        suggestions.add(new RoleSuggestion("coordinator", StructuralRole.INITIATOR,
            PlatformRole.COORDINATOR, confidence, dr));
      }
      return suggestions;
    };

    var topology = new HiveTopology(roles,
        new HivePatternAdapter(TestAgentProviders.noOp()),
        OrchestrationPatternType.SUPERVISOR, strategy);
    var policy = new RoleBridgingPolicy(
        RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    var ctx = new TopologyContext(
        UUID.randomUUID(),
        new TopologyDescriptor(TopologyType.CENTRALISED, roles),
        TestAgentProviders.noOp(),
        new DecisionLoopFactory(name -> c -> "", new EventStreamBus<>()),
        AutonomyBudgetPolicy.defaults(), DegradationPolicy.defaults(),
        policy, null, null, null);
    topology.configure(ctx);

    topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), 
        List.of(clusterA, clusterB), SwarmProgress.EMPTY));

    var updatedRoles = topology.describeTopology(UUID.randomUUID()).agentRoles();
    assertThat(updatedRoles.get("agent-b").platformRole()).isEqualTo(PlatformRole.COORDINATOR);
    assertThat(updatedRoles.get("agent-a")).isEqualTo(roles.get("agent-a"));
  }

  @Test
  void equal_confidence_uses_lexicographic_tiebreaker() {
    Map<String, AgentRole> roles = Map.of(
        "supervisor", AgentRole.of("supervisor", StructuralRole.INITIATOR),
        "agent-a", AgentRole.of("worker-a", StructuralRole.CONTRACTOR),
        "agent-b", AgentRole.of("worker-b", StructuralRole.CONTRACTOR));

    var clusterA = new DetectedRole("cluster-a", Set.of("agent-a"),
        BehavioralFingerprint.EMPTY, List.of("coordination"), 5, 0.1);
    var clusterB = new DetectedRole("cluster-b", Set.of("agent-b"),
        BehavioralFingerprint.EMPTY, List.of("coordination"), 5, 0.1);

    RoleBridgingStrategy strategy = detectedRoles -> detectedRoles.stream()
        .map(dr -> new RoleSuggestion("coordinator", StructuralRole.INITIATOR,
            PlatformRole.COORDINATOR, 0.9, dr))
        .toList();

    var topology = new HiveTopology(roles,
        new HivePatternAdapter(TestAgentProviders.noOp()),
        OrchestrationPatternType.SUPERVISOR, strategy);
    var policy = new RoleBridgingPolicy(
        RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    var ctx = new TopologyContext(
        UUID.randomUUID(),
        new TopologyDescriptor(TopologyType.CENTRALISED, roles),
        TestAgentProviders.noOp(),
        new DecisionLoopFactory(name -> c -> "", new EventStreamBus<>()),
        AutonomyBudgetPolicy.defaults(), DegradationPolicy.defaults(),
        policy, null, null, null);
    topology.configure(ctx);

    topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), 
        List.of(clusterA, clusterB), SwarmProgress.EMPTY));

    var updatedRoles = topology.describeTopology(UUID.randomUUID()).agentRoles();
    assertThat(updatedRoles.get("agent-a").platformRole()).isEqualTo(PlatformRole.COORDINATOR);
    assertThat(updatedRoles.get("agent-b")).isEqualTo(roles.get("agent-b"));
  }

  @Test
  void peer_suggestions_allow_multiple_agents() {
    Map<String, AgentRole> roles = Map.of(
        "supervisor", AgentRole.of("supervisor", StructuralRole.INITIATOR),
        "agent-a", AgentRole.of("worker-a", StructuralRole.CONTRACTOR),
        "agent-b", AgentRole.of("worker-b", StructuralRole.CONTRACTOR));

    var clusterA = new DetectedRole("cluster-a", Set.of("agent-a"),
        BehavioralFingerprint.EMPTY, List.of("judging"), 5, 0.1);
    var clusterB = new DetectedRole("cluster-b", Set.of("agent-b"),
        BehavioralFingerprint.EMPTY, List.of("judging"), 5, 0.1);

    RoleBridgingStrategy strategy = detectedRoles -> detectedRoles.stream()
        .map(dr -> new RoleSuggestion("analyst", StructuralRole.CONTRACTOR,
            null, 0.9, dr))
        .toList();

    var topology = new HiveTopology(roles,
        new HivePatternAdapter(TestAgentProviders.noOp()),
        OrchestrationPatternType.SUPERVISOR, strategy);
    var policy = new RoleBridgingPolicy(
        RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    var ctx = new TopologyContext(
        UUID.randomUUID(),
        new TopologyDescriptor(TopologyType.CENTRALISED, roles),
        TestAgentProviders.noOp(),
        new DecisionLoopFactory(name -> c -> "", new EventStreamBus<>()),
        AutonomyBudgetPolicy.defaults(), DegradationPolicy.defaults(),
        policy, null, null, null);
    topology.configure(ctx);

    topology.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(UUID.randomUUID(), 
        List.of(clusterA, clusterB), SwarmProgress.EMPTY));

    var updatedRoles = topology.describeTopology(UUID.randomUUID()).agentRoles();
    assertThat(updatedRoles.get("agent-a").structuralRole()).isEqualTo(StructuralRole.CONTRACTOR);
    assertThat(updatedRoles.get("agent-b").structuralRole()).isEqualTo(StructuralRole.CONTRACTOR);
  }
}
