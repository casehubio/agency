package io.casehub.agency.orchestration.swarm;

import io.casehub.api.model.stigmergy.DetectedRole;
import io.casehub.api.model.stigmergy.DetectedTeam;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.routing.RetrievedExperience;

import java.util.List;

public sealed interface SwarmCoordinationEvent {
    record SwarmInitialised(List<RetrievedExperience> experiences, SwarmProgress progress) implements SwarmCoordinationEvent {}
    record RoleChanged(List<DetectedRole> roles, SwarmProgress progress) implements SwarmCoordinationEvent {}
    record TeamChanged(List<DetectedTeam> teams, SwarmProgress progress) implements SwarmCoordinationEvent {}
    record ProgressUpdated(SwarmProgress progress) implements SwarmCoordinationEvent {}
    record MemberCompleted(String agentId, Object result) implements SwarmCoordinationEvent {}
    record ProvisioningChanged(int currentSize, int maxSize) implements SwarmCoordinationEvent {}
}
