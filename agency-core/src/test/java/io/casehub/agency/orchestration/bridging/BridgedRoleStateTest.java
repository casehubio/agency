package io.casehub.agency.orchestration.bridging;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.eidos.api.RoleChangePolicy;
import org.junit.jupiter.api.Test;

class BridgedRoleStateTest {

  static final AgentRole COORDINATOR_ROLE = AgentRole.of("coordinator", StructuralRole.INITIATOR);

  @Test
  void withConfidence_increments_hold_cycles() {
    var state = new BridgedRoleState("agent-1", COORDINATOR_ROLE, 0.8, 0);
    var updated = state.withConfidence(0.75);
    assertThat(updated.holdCycles()).isEqualTo(1);
    assertThat(updated.lastConfidence()).isEqualTo(0.75);
    assertThat(updated.agentId()).isEqualTo("agent-1");
    assertThat(updated.bridgedRole()).isEqualTo(COORDINATOR_ROLE);
  }

  @Test
  void withConfidence_accumulates_hold_cycles() {
    var state = new BridgedRoleState("agent-1", COORDINATOR_ROLE, 0.9, 0);
    state = state.withConfidence(0.85);
    state = state.withConfidence(0.80);
    state = state.withConfidence(0.75);
    assertThat(state.holdCycles()).isEqualTo(3);
  }

  @Test
  void demotionEligible_true_when_both_thresholds_met() {
    var policy = new RoleChangePolicy(
        RoleChangePolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    var state = new BridgedRoleState("agent-1", COORDINATOR_ROLE, 0.3, 4);
    assertThat(state.demotionEligible(policy)).isTrue();
  }

  @Test
  void demotionEligible_false_when_confidence_above_demotion_threshold() {
    var policy = new RoleChangePolicy(
        RoleChangePolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    var state = new BridgedRoleState("agent-1", COORDINATOR_ROLE, 0.6, 4);
    assertThat(state.demotionEligible(policy)).isFalse();
  }

  @Test
  void demotionEligible_false_when_hold_cycles_insufficient() {
    var policy = new RoleChangePolicy(
        RoleChangePolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    var state = new BridgedRoleState("agent-1", COORDINATOR_ROLE, 0.3, 2);
    assertThat(state.demotionEligible(policy)).isFalse();
  }

  @Test
  void demotionEligible_true_at_exact_hold_cycle_boundary() {
    var policy = new RoleChangePolicy(
        RoleChangePolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3);
    var state = new BridgedRoleState("agent-1", COORDINATOR_ROLE, 0.3, 3);
    assertThat(state.demotionEligible(policy)).isTrue();
  }
}
