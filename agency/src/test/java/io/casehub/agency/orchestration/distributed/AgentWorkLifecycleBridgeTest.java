package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.loop.AgentDecisionLoop;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.api.model.stigmergy.AgentWorkState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

class AgentWorkLifecycleBridgeTest {

    @Test
    void routesWorkStateChangeToPeerLoops() {
        var loop1 = mock(AgentDecisionLoop.class);
        var loop2 = mock(AgentDecisionLoop.class);
        var bridge = new AgentWorkLifecycleBridge(Map.of("agent-1", loop1, "agent-2", loop2));

        bridge.onWorkStateChanged(UUID.randomUUID(), "agent-1",
                AgentWorkState.IDLE, AgentWorkState.EXECUTING);

        verify(loop2).collect(argThat(e -> e.key().equals("peer-state-change")));
        verify(loop1, never()).collect(any());
    }

    @Test
    void routesTaskAssignedToPeerLoops() {
        var loop1 = mock(AgentDecisionLoop.class);
        var loop2 = mock(AgentDecisionLoop.class);
        var bridge = new AgentWorkLifecycleBridge(Map.of("agent-1", loop1, "agent-2", loop2));

        bridge.onTaskAssigned(UUID.randomUUID(), "agent-1", "task-42", Map.of());

        verify(loop2).collect(argThat(e -> e.key().equals("task-assigned")));
        verify(loop1, never()).collect(any());
    }

    @Test
    void routesRoleAdoptedToAllLoops() {
        var loop1 = mock(AgentDecisionLoop.class);
        var loop2 = mock(AgentDecisionLoop.class);
        var bridge = new AgentWorkLifecycleBridge(Map.of("agent-1", loop1, "agent-2", loop2));

        bridge.onRoleAdopted(UUID.randomUUID(), "agent-1", "group-1", "analyst");

        verify(loop1).collect(argThat(e -> e.key().equals("role-change")));
        verify(loop2).collect(argThat(e -> e.key().equals("role-change")));
    }

    @Test
    void ignoresUnknownAgents() {
        var bridge = new AgentWorkLifecycleBridge(Map.of());
        bridge.onWorkStateChanged(UUID.randomUUID(), "unknown",
                AgentWorkState.IDLE, AgentWorkState.EXECUTING);
    }
}
