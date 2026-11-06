package io.casehub.agency.deliberation.coalition;

import io.casehub.agency.core.AgentRef;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CoalitionProposal(
        AgentRef proposer,
        String taskId,
        Set<String> requiredCapabilities,
        List<AgentRef> proposedMembers
) {
    public CoalitionProposal {
        Objects.requireNonNull(proposer, "proposer");
        Objects.requireNonNull(taskId, "taskId");
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        proposedMembers = List.copyOf(proposedMembers);
    }
}
