package io.casehub.agency.orchestration.routing;

import io.casehub.agency.core.RoutingCandidate;

import java.util.List;

public record RoutingContext<T>(
        String task,
        List<RoutingCandidate> candidates,
        T state
) {
    public RoutingContext { candidates = List.copyOf(candidates); }
}
