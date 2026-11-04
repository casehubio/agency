package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.swarm.SwarmCoordinationEvent;
import io.casehub.api.spi.coordination.TopologyProvider;

public interface CoordinationTopology extends TopologyProvider {

    CoordinationPlan configure(TopologyContext context);

    void onSwarmEvent(SwarmCoordinationEvent event);

    void onFeedback(CoordinationFeedback feedback);
}
