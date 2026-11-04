package io.casehub.agency.orchestration.loop;

import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.TopologyDescriptor;

import java.util.Objects;
import java.util.UUID;

public record DecisionContext(
    UUID caseId,
    String agentId,
    DecisionScope scope,
    CommunicationMode communicationMode,
    TopologyDescriptor topology
) {
    public DecisionContext {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(communicationMode, "communicationMode must not be null");
        Objects.requireNonNull(topology, "topology must not be null");
    }
}
