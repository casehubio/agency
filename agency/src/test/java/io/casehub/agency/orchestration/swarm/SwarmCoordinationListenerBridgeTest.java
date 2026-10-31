package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.orchestration.execution.DriverEvent;
import io.casehub.api.model.stigmergy.BehavioralFingerprint;
import io.casehub.api.model.stigmergy.DetectedRole;
import io.casehub.api.model.stigmergy.DetectedTeam;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SwarmCoordinationListenerBridgeTest {

    private static final UUID CASE_ID = UUID.randomUUID();
    private static final SwarmProgress PROGRESS =
            new SwarmProgress(0.5, 0.3, 0.4, Instant.now());

    @Test
    void isNotBoundBeforeBind() {
        var bridge = new SwarmCoordinationListenerBridge();
        assertThat(bridge.isBound()).isFalse();
    }

    @Test
    void callbacksAreNoOpsBeforeBind() {
        var bridge = new SwarmCoordinationListenerBridge();
        assertThatNoException().isThrownBy(() -> {
            bridge.onSwarmInitialised(CASE_ID, PROGRESS);
            bridge.onRoleChanged(CASE_ID, List.of(), PROGRESS);
            bridge.onTeamChanged(CASE_ID, List.of(), PROGRESS);
            bridge.onProgressUpdated(CASE_ID, PROGRESS);
            bridge.onMemberCompleted(CASE_ID, "agent-1");
            bridge.onProvisioningChanged(CASE_ID, 3, 10);
        });
    }

    @Test
    void delegatesSwarmInitialised() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        var received = new ArrayList<DriverEvent>();
        source.subscribe(received::add);

        var bridge = new SwarmCoordinationListenerBridge();
        bridge.bind(source);
        assertThat(bridge.isBound()).isTrue();

        bridge.onSwarmInitialised(CASE_ID, PROGRESS);

        assertThat(received).hasSize(1);
        var event = (SwarmCoordinationEvent.SwarmInitialised) received.get(0).payload();
        assertThat(event.experiences()).isEmpty();
        assertThat(event.progress()).isSameAs(PROGRESS);
    }

    @Test
    void delegatesRoleChanged() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        var received = new ArrayList<DriverEvent>();
        source.subscribe(received::add);

        var bridge = new SwarmCoordinationListenerBridge();
        bridge.bind(source);

        var role = new DetectedRole("role-1", Set.of("a1"), BehavioralFingerprint.EMPTY, List.of("f1"), 3, 0.1);
        bridge.onRoleChanged(CASE_ID, List.of(role), PROGRESS);

        assertThat(received).hasSize(1);
        var event = (SwarmCoordinationEvent.RoleChanged) received.get(0).payload();
        assertThat(event.roles()).containsExactly(role);
        assertThat(event.progress()).isSameAs(PROGRESS);
    }

    @Test
    void delegatesTeamChanged() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        var received = new ArrayList<DriverEvent>();
        source.subscribe(received::add);

        var bridge = new SwarmCoordinationListenerBridge();
        bridge.bind(source);

        var team = new DetectedTeam("team-1", Set.of("a1", "a2"), Set.of(), 0.8, 2);
        bridge.onTeamChanged(CASE_ID, List.of(team), PROGRESS);

        assertThat(received).hasSize(1);
        var event = (SwarmCoordinationEvent.TeamChanged) received.get(0).payload();
        assertThat(event.teams()).containsExactly(team);
        assertThat(event.progress()).isSameAs(PROGRESS);
    }

    @Test
    void delegatesProgressUpdated() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        var received = new ArrayList<DriverEvent>();
        source.subscribe(received::add);

        var bridge = new SwarmCoordinationListenerBridge();
        bridge.bind(source);

        bridge.onProgressUpdated(CASE_ID, PROGRESS);

        assertThat(received).hasSize(1);
        var event = (SwarmCoordinationEvent.ProgressUpdated) received.get(0).payload();
        assertThat(event.progress()).isSameAs(PROGRESS);
    }

    @Test
    void delegatesMemberCompleted() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        var received = new ArrayList<DriverEvent>();
        source.subscribe(received::add);

        var bridge = new SwarmCoordinationListenerBridge();
        bridge.bind(source);

        bridge.onMemberCompleted(CASE_ID, "agent-42");

        assertThat(received).hasSize(1);
        var event = (SwarmCoordinationEvent.MemberCompleted) received.get(0).payload();
        assertThat(event.agentId()).isEqualTo("agent-42");
        assertThat(event.result()).isNull();
    }

    @Test
    void delegatesProvisioningChanged() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        var received = new ArrayList<DriverEvent>();
        source.subscribe(received::add);

        var bridge = new SwarmCoordinationListenerBridge();
        bridge.bind(source);

        bridge.onProvisioningChanged(CASE_ID, 5, 10);

        assertThat(received).hasSize(1);
        var event = (SwarmCoordinationEvent.ProvisioningChanged) received.get(0).payload();
        assertThat(event.currentSize()).isEqualTo(5);
        assertThat(event.maxSize()).isEqualTo(10);
    }

    @Test
    void doesNotPropagateExceptionsFromEventSource() {
        var context = new SwarmContext();
        var source = new SwarmEventSource(context);
        source.subscribe(e -> { throw new RuntimeException("subscriber failure"); });

        var bridge = new SwarmCoordinationListenerBridge();
        bridge.bind(source);

        assertThatNoException().isThrownBy(() ->
                bridge.onProgressUpdated(CASE_ID, PROGRESS));
    }
}
