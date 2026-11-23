package io.casehub.agency.orchestration.bridging;

import io.casehub.api.model.coordination.AgentRole;
import io.casehub.eidos.api.RoleChangePolicy;

public record BridgedRoleState(
    String agentId,
    AgentRole bridgedRole,
    double lastConfidence,
    int holdCycles) {

  public BridgedRoleState withConfidence(double confidence) {
    return new BridgedRoleState(agentId, bridgedRole, confidence, holdCycles + 1);
  }

  public boolean demotionEligible(RoleChangePolicy policy) {
    return holdCycles >= policy.minHoldCycles()
        && lastConfidence < policy.demotionConfidence();
  }
}
