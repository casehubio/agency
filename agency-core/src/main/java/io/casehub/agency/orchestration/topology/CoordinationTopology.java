package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.degradation.DegradedEvent;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.spi.coordination.TopologyProvider;

import java.util.UUID;

public interface CoordinationTopology extends TopologyProvider {

    TopologyDescriptor describeTopology(UUID caseId);

    CoordinationPlan configure(TopologyContext context);

    void onFeedback(CoordinationFeedback feedback);

    default void onDegradation(DegradedEvent event) {}

    @Override
    default TopologyDescriptor topologyFor(String agentId, String tenancyId) {
        throw new UnsupportedOperationException(
                "CoordinationTopology is a runtime topology, not a provider — use the engine's TopologyProvider SPI for agent-level queries");
    }
}
