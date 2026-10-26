package io.casehub.agency.orchestration.swarm;

import io.casehub.api.model.stigmergy.DetectedRole;
import io.casehub.api.model.stigmergy.DetectedTeam;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.routing.RetrievedExperience;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class SwarmContext {

    private volatile List<DetectedRole> detectedRoles = List.of();
    private volatile List<DetectedTeam> detectedTeams = List.of();
    private volatile @Nullable SwarmProgress swarmProgress;
    private volatile @Nullable String activeStrategy;
    private volatile List<RetrievedExperience> experiences = List.of();

    public void onSwarmInitialised(List<RetrievedExperience> experiences, SwarmProgress progress) {
        this.swarmProgress = progress;
        this.experiences = List.copyOf(experiences);
    }

    public void onRoleChanged(List<DetectedRole> roles, SwarmProgress progress) {
        this.detectedRoles = List.copyOf(roles);
        this.swarmProgress = progress;
    }

    public void onTeamChanged(List<DetectedTeam> teams, SwarmProgress progress) {
        this.detectedTeams = List.copyOf(teams);
        this.swarmProgress = progress;
    }

    public void onProgressUpdated(SwarmProgress progress) {
        this.swarmProgress = progress;
    }

    public void setActiveStrategy(String strategy) {
        this.activeStrategy = strategy;
    }

    public List<DetectedRole> detectedRoles() { return detectedRoles; }

    public List<DetectedTeam> detectedTeams() { return detectedTeams; }

    public @Nullable SwarmProgress swarmProgress() { return swarmProgress; }

    public @Nullable String activeStrategy() { return activeStrategy; }

    public List<RetrievedExperience> experiences() { return experiences; }
}
