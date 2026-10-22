package io.casehub.agency.agentic.routing;

public interface RoutingStrategy<T> {
    RoutingDecision route(RoutingContext<T> context);
}
