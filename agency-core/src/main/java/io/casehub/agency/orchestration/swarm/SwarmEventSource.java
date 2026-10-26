package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.orchestration.execution.DriverEvent;
import io.casehub.agency.orchestration.execution.EventSource;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SwarmEventSource implements EventSource {

    private final SwarmContext context;
    private final CopyOnWriteArrayList<Consumer<DriverEvent>> subscribers = new CopyOnWriteArrayList<>();

    public SwarmEventSource(SwarmContext context) {
        this.context = context;
    }

    @Override
    public Cancellation subscribe(Consumer<DriverEvent> sink) {
        subscribers.add(sink);
        return Cancellation.of(() -> subscribers.remove(sink));
    }

    public void onSwarmEvent(SwarmCoordinationEvent event) {
        updateContext(event);
        var driverEvent = new DriverEvent("swarm", event);
        subscribers.forEach(s -> s.accept(driverEvent));
    }

    private void updateContext(SwarmCoordinationEvent event) {
        switch (event) {
            case SwarmCoordinationEvent.SwarmInitialised e -> context.onSwarmInitialised(e.experiences(), e.progress());
            case SwarmCoordinationEvent.RoleChanged e -> context.onRoleChanged(e.roles(), e.progress());
            case SwarmCoordinationEvent.TeamChanged e -> context.onTeamChanged(e.teams(), e.progress());
            case SwarmCoordinationEvent.ProgressUpdated e -> context.onProgressUpdated(e.progress());
            case SwarmCoordinationEvent.MemberCompleted e -> {}
            case SwarmCoordinationEvent.ProvisioningChanged e -> {}
        }
    }
}
