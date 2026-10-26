package io.casehub.agency.social.cognition.evolution;

import io.casehub.api.model.stigmergy.TickTrace;
import io.casehub.api.model.stigmergy.TickTrace.GateResult.GateVerdict;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import java.util.List;

public class CircuitBreakerMoodMapper {

    private static final String CB_GATE = "circuit_breaker_check";
    private boolean previouslyBlocked;

    public List<MoodSignal> map(TickTrace trace) {
        var cbGate = trace.gates().stream()
                .filter(g -> CB_GATE.equals(g.gateName()))
                .findFirst();
        if (cbGate.isEmpty()) return List.of();

        boolean blocked = cbGate.get().verdict() == GateVerdict.BLOCKED;
        try {
            if (blocked) {
                return List.of(new MoodSignal.DirectShift(
                        -0.1, 0.15, -0.05, "evolution:circuit-breaker:blocked"));
            }
            if (previouslyBlocked) {
                return List.of(new MoodSignal.DirectShift(
                        0.05, -0.1, 0.05, "evolution:circuit-breaker:recovered"));
            }
            return List.of();
        } finally {
            previouslyBlocked = blocked;
        }
    }
}
