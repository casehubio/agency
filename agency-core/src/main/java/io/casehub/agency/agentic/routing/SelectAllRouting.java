package io.casehub.agency.agentic.routing;

import io.casehub.agency.agentic.RoutingCandidate;

public class SelectAllRouting<T> implements RoutingStrategy<T> {

    @Override
    public RoutingDecision route(RoutingContext<T> context) {
        var refs = context.candidates().stream()
                .map(RoutingCandidate::ref)
                .toList();
        return new RoutingDecision.Selected(refs);
    }
}
