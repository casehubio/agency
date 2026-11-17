package io.casehub.agency.orchestration.degradation;

import io.casehub.api.model.stigmergy.CircuitBreakerState;

public interface StatefulDegradationTrigger extends DegradationTrigger {
    CircuitBreakerState state();
}
