package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.qhorus.api.gateway.MessageObserver;

@FunctionalInterface
public interface MessageObserverRegistrar {
    Runnable register(MessageObserver observer);
}
