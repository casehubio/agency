package io.casehub.agency.orchestration.swarm;

import java.util.List;

public record SwarmAggregationState(
        List<String> agreements,
        List<String> disagreements,
        List<String> complementaryObservations,
        double confidence,
        List<String> minorityViewpoints) {

    public SwarmAggregationState {
        agreements = List.copyOf(agreements);
        disagreements = List.copyOf(disagreements);
        complementaryObservations = List.copyOf(complementaryObservations);
        minorityViewpoints = List.copyOf(minorityViewpoints);
    }
}
