package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.stigmergy.SwarmProgress;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ConflictContext(
        CrossSwarmNotification notification,
        BeliefSet<SwarmConclusion> currentBeliefs,
        List<Belief<SwarmConclusion>> contractedBeliefs,
        Map<UUID, SwarmProgress> latestProgress,
        Map<UUID, Set<String>> resourceClaims,
        Map<UUID, SwarmDescriptor> swarmDescriptors
) {
    public ConflictContext {
        Objects.requireNonNull(notification);
        Objects.requireNonNull(currentBeliefs);
        Objects.requireNonNull(contractedBeliefs);
        Objects.requireNonNull(latestProgress);
        Objects.requireNonNull(resourceClaims);
        Objects.requireNonNull(swarmDescriptors);
        contractedBeliefs = List.copyOf(contractedBeliefs);
        latestProgress = Map.copyOf(latestProgress);
        resourceClaims = Map.copyOf(resourceClaims);
        swarmDescriptors = Map.copyOf(swarmDescriptors);
    }
}
