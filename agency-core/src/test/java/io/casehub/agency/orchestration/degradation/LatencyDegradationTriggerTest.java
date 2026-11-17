package io.casehub.agency.orchestration.degradation;

import io.casehub.api.model.stigmergy.CircuitBreakerState;
import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;
import io.casehub.platform.agent.budget.BudgetTelemetryEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LatencyDegradationTriggerTest {

    private LlmInvocationPolicy policy() {
        return LlmInvocationPolicy.of(
                Duration.ofSeconds(5), Duration.ofSeconds(8), Duration.ofSeconds(10));
    }

    private LatencyCircuitBreakerConfig config() {
        return new LatencyCircuitBreakerConfig(3, Duration.ofMinutes(2),
                                               Duration.ofMillis(100), 2);
    }

    private BudgetTelemetryEvent.Outcome outcome(long latencyMs) {
        return new BudgetTelemetryEvent.Outcome("corr-1", "model", 100, latencyMs, Instant.now());
    }

    // --- CLOSED state ---

    @Test
    void startsInClosedState() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config()).build();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void staysClosed_belowBreachCount() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config()).build();
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void staysClosed_whenLatencyBelowThreshold() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config()).build();
        trigger.accept(outcome(5_000));
        trigger.accept(outcome(5_000));
        trigger.accept(outcome(5_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    // --- CLOSED -> OPEN ---

    @Test
    void opensOnBreachThreshold() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config()).build();
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(trigger.shouldDegrade()).isTrue();
    }

    // --- OPEN -> HALF_OPEN ---

    @Test
    void transitionsToHalfOpen_afterRecoveryTimeout() throws Exception {
        var trigger = LatencyDegradationTrigger.builder(policy(), config()).build();
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.OPEN);

        Thread.sleep(150);

        assertThat(trigger.shouldDegrade()).isFalse();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.HALF_OPEN);
    }

    // --- HALF_OPEN -> CLOSED ---

    @Test
    void closesAfterSuccessfulProbes() throws Exception {
        var config = new LatencyCircuitBreakerConfig(3, Duration.ofMinutes(2),
                                                     Duration.ofMillis(50), 2);
        var trigger = LatencyDegradationTrigger.builder(policy(), config).build();

        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        Thread.sleep(80);
        trigger.shouldDegrade(); // triggers OPEN -> HALF_OPEN

        trigger.accept(outcome(5_000)); // probe success 1
        trigger.accept(outcome(5_000)); // probe success 2
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    // --- HALF_OPEN -> OPEN ---

    @Test
    void reopensOnProbeFailure() throws Exception {
        var config = new LatencyCircuitBreakerConfig(3, Duration.ofMinutes(2),
                                                     Duration.ofMillis(50), 2);
        var trigger = LatencyDegradationTrigger.builder(policy(), config).build();

        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        Thread.sleep(80);
        trigger.shouldDegrade(); // triggers OPEN -> HALF_OPEN

        trigger.accept(outcome(5_000));  // probe success 1
        trigger.accept(outcome(11_000)); // probe failure -> reopen
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(trigger.shouldDegrade()).isTrue();
    }

    // --- Transition listener ---

    @Test
    void transitionListenerFiresOnStateChanges() throws Exception {
        var transitions = new java.util.ArrayList<CircuitBreakerTransition>();
        var config = new LatencyCircuitBreakerConfig(2, Duration.ofMinutes(2),
                                                     Duration.ofMillis(50), 1);
        var trigger = LatencyDegradationTrigger.builder(policy(), config)
                                               .onTransition(transitions::add)
                                               .build();

        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000)); // -> OPEN
        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).from()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(transitions.get(0).to()).isEqualTo(CircuitBreakerState.OPEN);

        Thread.sleep(80);
        trigger.shouldDegrade(); // -> HALF_OPEN
        assertThat(transitions).hasSize(2);
        assertThat(transitions.get(1).from()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(transitions.get(1).to()).isEqualTo(CircuitBreakerState.HALF_OPEN);

        trigger.accept(outcome(5_000)); // -> CLOSED
        assertThat(transitions).hasSize(3);
        assertThat(transitions.get(2).from()).isEqualTo(CircuitBreakerState.HALF_OPEN);
        assertThat(transitions.get(2).to()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    // --- External state supplier ---

    @Test
    void externalOpenForcesDegradation() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config())
                                               .externalState(() -> CircuitBreakerState.OPEN)
                                               .build();
        assertThat(trigger.shouldDegrade()).isTrue();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void externalClosedDoesNotAffectLocalState() {
        var trigger = LatencyDegradationTrigger.builder(policy(), config())
                                               .externalState(() -> CircuitBreakerState.CLOSED)
                                               .build();
        assertThat(trigger.shouldDegrade()).isFalse();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void externalOpenBlocksLocalProbing() throws Exception {
        var externalState = new java.util.concurrent.atomic.AtomicReference<>(CircuitBreakerState.CLOSED);
        var config = new LatencyCircuitBreakerConfig(2, Duration.ofMinutes(2),
                                                     Duration.ofMillis(50), 1);
        var trigger = LatencyDegradationTrigger.builder(policy(), config)
                                               .externalState(externalState::get)
                                               .build();

        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000)); // -> OPEN
        Thread.sleep(80);

        externalState.set(CircuitBreakerState.OPEN);
        // Local transitions to HALF_OPEN but external is OPEN -> effective OPEN
        assertThat(trigger.shouldDegrade()).isTrue();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.OPEN);

        externalState.set(CircuitBreakerState.CLOSED);
        // Now local HALF_OPEN takes effect
        assertThat(trigger.shouldDegrade()).isFalse();
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.HALF_OPEN);
    }

    // --- Window expiry ---

    @Test
    void breachesExpireFromWindow() throws Exception {
        var config = new LatencyCircuitBreakerConfig(3, Duration.ofMillis(100),
                                                     Duration.ofMillis(50), 1);
        var trigger = LatencyDegradationTrigger.builder(policy(), config).build();
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);

        Thread.sleep(150);
        trigger.accept(outcome(11_000));
        assertThat(trigger.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }
}
