package io.casehub.agency.orchestration.routing;

import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.routing.RoutingContext;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.casehub.agency.orchestration.routing.RoutingStrategy;

public class SelectAllRouting<T> implements RoutingStrategy<T> {

    @Override
    public RoutingDecision route(RoutingContext<T> context) {
        var refs = context.candidates().stream()
                .map(RoutingCandidate::ref)
                .toList();
        return new RoutingDecision.Selected(refs);
    }
}
