package io.casehub.agency.social.cognition.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.stigmergy.TickTrace;
import io.casehub.api.model.stigmergy.TickTrace.GateResult;
import io.casehub.api.model.stigmergy.TickTrace.GateResult.GateVerdict;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircuitBreakerMoodMapperTest {

    private final CircuitBreakerMoodMapper mapper = new CircuitBreakerMoodMapper();

    private TickTrace traceWithGates(List<GateResult> gates) {
        return new TickTrace(UUID.randomUUID(), Instant.now(),
                TickTrace.TickTrigger.EVENT_DRIVEN, gates, null);
    }

    @Test
    void blocked_gate_produces_negative_shift() {
        var trace = traceWithGates(List.of(
                new GateResult("circuit_breaker_check", GateVerdict.BLOCKED, "open")));
        var signals = mapper.map(trace);
        assertThat(signals).hasSize(1);
        var shift = (MoodSignal.DirectShift) signals.getFirst();
        assertThat(shift.pleasureDelta()).isLessThan(0);
        assertThat(shift.arousalDelta()).isGreaterThan(0);
        assertThat(shift.cause()).contains("blocked");
    }

    @Test
    void recovery_from_blocked_produces_positive_shift() {
        var blocked = traceWithGates(List.of(
                new GateResult("circuit_breaker_check", GateVerdict.BLOCKED, "open")));
        mapper.map(blocked);

        var recovered = traceWithGates(List.of(
                new GateResult("circuit_breaker_check", GateVerdict.PASSED, null)));
        var signals = mapper.map(recovered);
        assertThat(signals).hasSize(1);
        var shift = (MoodSignal.DirectShift) signals.getFirst();
        assertThat(shift.pleasureDelta()).isGreaterThan(0);
        assertThat(shift.cause()).contains("recovered");
    }

    @Test
    void no_signal_when_gates_pass_without_prior_block() {
        var trace = traceWithGates(List.of(
                new GateResult("circuit_breaker_check", GateVerdict.PASSED, null)));
        var signals = mapper.map(trace);
        assertThat(signals).isEmpty();
    }

    @Test
    void no_signal_when_no_circuit_breaker_gate_in_trace() {
        var trace = traceWithGates(List.of(
                new GateResult("evolution_enabled", GateVerdict.PASSED, null)));
        var signals = mapper.map(trace);
        assertThat(signals).isEmpty();
    }
}
