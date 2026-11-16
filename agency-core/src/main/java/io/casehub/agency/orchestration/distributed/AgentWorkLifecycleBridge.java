package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.loop.AgentDecisionLoop;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.api.model.stigmergy.AgentWorkState;
import io.casehub.api.spi.stigmergy.AgentWorkLifecycleListener;

import java.util.Map;
import java.util.UUID;

public class AgentWorkLifecycleBridge implements AgentWorkLifecycleListener {

    private final Map<String, AgentDecisionLoop> loops;

    public AgentWorkLifecycleBridge(Map<String, AgentDecisionLoop> loops) {
        this.loops = Map.copyOf(loops);
    }

    @Override
    public void onWorkStateChanged(UUID caseId, String agentId, AgentWorkState from, AgentWorkState to) {
        for (var entry : loops.entrySet()) {
            if (!entry.getKey().equals(agentId)) {
                entry.getValue().collect(ObservationEvent.of("peer-state-change",
                        Map.of("agentId", agentId, "from", from.name(), "to", to.name())));
            }
        }
    }

    @Override
    public void onTaskAssigned(UUID caseId, String agentId, String taskId, Map<String, Object> context) {
        for (var entry : loops.entrySet()) {
            if (!entry.getKey().equals(agentId)) {
                entry.getValue().collect(ObservationEvent.of("task-assigned",
                        Map.of("agentId", agentId, "taskId", taskId)));
            }
        }
    }

    @Override
    public void onRoleAdopted(UUID caseId, String agentId, String groupId, String role) {
        for (var entry : loops.entrySet()) {
            entry.getValue().collect(ObservationEvent.of("role-change",
                    Map.of("agentId", agentId, "groupId", groupId, "role", role, "action", "adopted")));
        }
    }

    @Override
    public void onRoleReleased(UUID caseId, String agentId, String groupId, String role) {
        for (var entry : loops.entrySet()) {
            entry.getValue().collect(ObservationEvent.of("role-change",
                    Map.of("agentId", agentId, "groupId", groupId, "role", role, "action", "released")));
        }
    }
}
