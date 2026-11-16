package io.casehub.agency.orchestration.topology;

import io.casehub.api.model.coordination.SwarmCoordinationEvent;

public interface SwarmAwareTopology extends CoordinationTopology {

    void onSwarmEvent(SwarmCoordinationEvent event);
}
