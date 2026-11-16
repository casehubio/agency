package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.loop.DecisionContext;
import io.casehub.agency.orchestration.loop.LoopPromptSection;
import io.casehub.api.model.coordination.AgentRole;

import java.util.Map;

public class RoleContextSection implements LoopPromptSection {

    private final Map<String, AgentRole> agentRoles;

    public RoleContextSection(Map<String, AgentRole> agentRoles) {
        this.agentRoles = Map.copyOf(agentRoles);
    }

    @Override
    public String contribute(DecisionContext context) {
        var role = agentRoles.get(context.agentId());
        if (role == null) return "";
        var sb = new StringBuilder("## Your Role\n\n");
        sb.append("You are **").append(context.agentId()).append("**");
        sb.append(" with role **").append(role.name()).append("**\n\n");
        sb.append("### Peers\n\n");
        for (var entry : agentRoles.entrySet()) {
            if (!entry.getKey().equals(context.agentId())) {
                sb.append("- ").append(entry.getKey())
                  .append(": ").append(entry.getValue().name()).append('\n');
            }
        }
        return sb.toString();
    }
}
