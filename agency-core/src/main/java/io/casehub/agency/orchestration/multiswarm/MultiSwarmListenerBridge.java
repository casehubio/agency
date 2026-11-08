package io.casehub.agency.orchestration.multiswarm;

import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.stigmergy.DetectedRole;
import io.casehub.api.model.stigmergy.DetectedTeam;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.model.stigmergy.SwarmTelemetry;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.api.spi.stigmergy.SwarmCoordinationListener;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MultiSwarmListenerBridge implements SwarmCoordinationListener {

    private final CoordinationBus<MultiSwarmSignal> bus;
    private final MultiSwarmRegistry registry;

    public MultiSwarmListenerBridge(CoordinationBus<MultiSwarmSignal> bus, MultiSwarmRegistry registry) {
        this.bus = Objects.requireNonNull(bus);
        this.registry = Objects.requireNonNull(registry);
    }

    @Override
    public void onSwarmInitialised(UUID caseId, UUID swarmId, SwarmProgress progress) {
        publishProgress(swarmId, progress);
    }

    @Override
    public void onProgressUpdated(UUID caseId, UUID swarmId, SwarmProgress progress) {
        publishProgress(swarmId, progress);
    }

    @Override
    public void onRoleChanged(UUID caseId, UUID swarmId, List<DetectedRole> roles, SwarmProgress progress) {
        publishProgress(swarmId, progress);
    }

    @Override
    public void onTeamChanged(UUID caseId, UUID swarmId, List<DetectedTeam> teams, SwarmProgress progress) {
        publishProgress(swarmId, progress);
    }

    @Override
    public void onMemberCompleted(UUID caseId, UUID swarmId, String agentId) {
        var descriptor = registry.get(swarmId);
        if (descriptor == null) return;
        bus.publish(new MultiSwarmSignal.Completion(swarmId, descriptor.capabilities(),
                System.currentTimeMillis(), "agent", agentId));
    }

    private void publishProgress(UUID swarmId, SwarmProgress progress) {
        var descriptor = registry.get(swarmId);
        if (descriptor == null) return;
        bus.publish(new MultiSwarmSignal.Progress(swarmId, descriptor.capabilities(),
                System.currentTimeMillis(), progress));
    }
}
