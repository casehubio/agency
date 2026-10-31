package io.casehub.agency.orchestration.swarm;

import io.casehub.api.model.stigmergy.DetectedRole;
import io.casehub.api.model.stigmergy.DetectedTeam;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.stigmergy.SwarmCoordinationListener;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class SwarmCoordinationListenerBridge implements SwarmCoordinationListener {

    private static final Logger LOG = Logger.getLogger(SwarmCoordinationListenerBridge.class.getName());

    private volatile SwarmEventSource eventSource;

    public void bind(SwarmEventSource eventSource) {
        this.eventSource = eventSource;
    }

    public boolean isBound() {
        return eventSource != null;
    }

    @Override
    public void onSwarmInitialised(UUID caseId, SwarmProgress progress) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.SwarmInitialised(List.of(), progress));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge SwarmInitialised event", e);
        }
    }

    @Override
    public void onRoleChanged(UUID caseId, List<DetectedRole> roles, SwarmProgress progress) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(roles, progress));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge RoleChanged event", e);
        }
    }

    @Override
    public void onTeamChanged(UUID caseId, List<DetectedTeam> teams, SwarmProgress progress) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.TeamChanged(teams, progress));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge TeamChanged event", e);
        }
    }

    @Override
    public void onProgressUpdated(UUID caseId, SwarmProgress progress) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(progress));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge ProgressUpdated event", e);
        }
    }

    @Override
    public void onMemberCompleted(UUID caseId, String agentId) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(agentId, null));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge MemberCompleted event", e);
        }
    }

    @Override
    public void onProvisioningChanged(UUID caseId, int currentSize, int maxSize) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.ProvisioningChanged(currentSize, maxSize));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge ProvisioningChanged event", e);
        }
    }
}
