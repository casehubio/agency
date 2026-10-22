package io.casehub.agency.orchestration.routing;

public interface RoutingStrategy<T> {
    RoutingDecision route(RoutingContext<T> context);
}
