package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.orchestration.execution.DriverEvent;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwarmEventSourceTest {

    private static final SwarmProgress PROGRESS =
            new SwarmProgress(0.5, 0.3, 0.4, Instant.now());

    @Test
    void translatesProgressEventToDriverEvent() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        var received = new ArrayList<DriverEvent>();
        source.subscribe(received::add);

        source.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(PROGRESS));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).source()).isEqualTo("swarm");
        assertThat(received.get(0).payload()).isInstanceOf(SwarmCoordinationEvent.ProgressUpdated.class);
        assertThat(context.swarmProgress()).isSameAs(PROGRESS);
    }

    @Test
    void updatesContextOnRoleChange() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        source.subscribe(e -> {});

        source.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(List.of(), PROGRESS));

        assertThat(context.detectedRoles()).isEmpty();
        assertThat(context.swarmProgress()).isSameAs(PROGRESS);
    }

    @Test
    void updatesContextOnSwarmInitialised() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        source.subscribe(e -> {});

        source.onSwarmEvent(new SwarmCoordinationEvent.SwarmInitialised(List.of(), PROGRESS));

        assertThat(context.experiences()).isEmpty();
        assertThat(context.swarmProgress()).isSameAs(PROGRESS);
    }


    @Test
    void updatesContextOnProvisioningChanged() {
        var context = new SwarmContext();
        var source  = new SwarmEventSource(context);
        source.subscribe(e -> {});

        source.onSwarmEvent(new SwarmCoordinationEvent.ProvisioningChanged(5, 10));

        assertThat(context.currentSize()).isEqualTo(5);
        assertThat(context.maxSize()).isEqualTo(10);
    }

    @Test
    void updatesContextOnMemberCompleted() {
        var context = new SwarmContext();
        var source  = new SwarmEventSource(context);
        source.subscribe(e -> {});

        source.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted("agent-1", null));
        source.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted("agent-2", null));

        assertThat(context.completedMembers()).containsExactly("agent-1", "agent-2");
    }

    @Test
    void cancellationRemovesSubscriber() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        var received = new ArrayList<DriverEvent>();
        var cancellation = source.subscribe(received::add);

        source.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(PROGRESS));
        assertThat(received).hasSize(1);

        cancellation.cancel();
        source.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(PROGRESS));
        assertThat(received).hasSize(1);
    }
}
