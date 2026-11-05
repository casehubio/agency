package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.budget.BudgetTelemetryEvent;
import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LatencyDegradationTriggerTest {

    private LlmInvocationPolicy policy() {
        return LlmInvocationPolicy.of(
            Duration.ofSeconds(5), Duration.ofSeconds(8), Duration.ofSeconds(10));
    }

    private BudgetTelemetryEvent.Outcome outcome(long latencyMs) {
        return new BudgetTelemetryEvent.Outcome("corr-1", "model", 100, latencyMs, Instant.now());
    }

    @Test
    void doesNotFire_withNoEvents() {
        var trigger = new LatencyDegradationTrigger(policy(), 5, 3, Duration.ofMinutes(2));
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void doesNotFire_belowBreachCount() {
        var trigger = new LatencyDegradationTrigger(policy(), 5, 3, Duration.ofMinutes(2));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void fires_atBreachCount() {
        var trigger = new LatencyDegradationTrigger(policy(), 5, 3, Duration.ofMinutes(2));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.shouldDegrade()).isTrue();
    }

    @Test
    void doesNotFire_whenLatencyBelowErrorThreshold() {
        var trigger = new LatencyDegradationTrigger(policy(), 5, 3, Duration.ofMinutes(2));
        trigger.accept(outcome(5_000));
        trigger.accept(outcome(5_000));
        trigger.accept(outcome(5_000));
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void recovers_whenEntriesExpire() throws Exception {
        var trigger = new LatencyDegradationTrigger(policy(), 5, 3, Duration.ofMillis(100));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        trigger.accept(outcome(11_000));
        assertThat(trigger.shouldDegrade()).isTrue();

        Thread.sleep(150);
        assertThat(trigger.shouldDegrade()).isFalse();
    }
}
