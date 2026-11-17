package io.casehub.agency.orchestration.degradation;

import io.casehub.api.model.stigmergy.CircuitBreakerState;
import java.time.Instant;
import java.util.Objects;

public record CircuitBreakerTransition(
    CircuitBreakerState from,
    CircuitBreakerState to,
    Instant timestamp
) {
    public CircuitBreakerTransition {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (from == to) throw new IllegalArgumentException("from and to must differ");
    }
}
