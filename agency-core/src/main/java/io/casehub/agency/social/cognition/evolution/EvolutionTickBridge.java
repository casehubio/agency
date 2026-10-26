package io.casehub.agency.social.cognition.evolution;

import io.casehub.api.model.stigmergy.TickTrace;

@FunctionalInterface
public interface EvolutionTickBridge {
    TickTrace tick();
}
