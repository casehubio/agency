package io.casehub.agency.orchestration.degradation;

import io.casehub.api.model.stigmergy.CircuitBreakerState;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CompositeDegradationTrigger implements StatefulDegradationTrigger {

    private final List<DegradationTrigger> triggers;

    public CompositeDegradationTrigger(List<DegradationTrigger> triggers) {
        this.triggers = List.copyOf(Objects.requireNonNull(triggers));
    }

    @Override
    public boolean shouldDegrade() {
        return triggers.stream().anyMatch(DegradationTrigger::shouldDegrade);
    }

    @Override
    public CircuitBreakerState state() {
        return triggers.stream()
                       .map(t -> t instanceof StatefulDegradationTrigger s
                                 ? s.state()
                                 : t.shouldDegrade() ? CircuitBreakerState.OPEN : CircuitBreakerState.CLOSED)
                       .max(Comparator.comparingInt(CompositeDegradationTrigger::restrictiveness))
                       .orElse(CircuitBreakerState.CLOSED);
    }

    private static int restrictiveness(CircuitBreakerState s) {
        return switch (s) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
        };
    }
}
