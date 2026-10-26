package io.casehub.agency.orchestration.swarm;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SwarmInvocationTrackerTest {

    @Test
    void tryAcquire_succeedsUnderBudget() {
        var tracker = new SwarmInvocationTracker(SwarmInvocationPolicy.defaults());
        assertThat(tracker.tryAcquire("routing")).isTrue();
        assertThat(tracker.callsUsed()).isEqualTo(1);
    }

    @Test
    void tryAcquire_failsWhenExhausted() {
        var policy = new SwarmInvocationPolicy(3, 500_000, 1, Duration.ofSeconds(5), Duration.ofSeconds(30));
        var tracker = new SwarmInvocationTracker(policy);
        assertThat(tracker.tryAcquire("routing")).isTrue();
        assertThat(tracker.tryAcquire("aggregation")).isTrue();
        assertThat(tracker.tryAcquire("routing")).isFalse();
    }

    @Test
    void tryAcquire_provisioningUsesFullBudget() {
        var policy = new SwarmInvocationPolicy(3, 500_000, 1, Duration.ofSeconds(5), Duration.ofSeconds(30));
        var tracker = new SwarmInvocationTracker(policy);
        tracker.tryAcquire("routing");
        tracker.tryAcquire("aggregation");
        assertThat(tracker.tryAcquire("provisioning")).isTrue();
    }

    @Test
    void recordTokens_accumulates() {
        var tracker = new SwarmInvocationTracker(SwarmInvocationPolicy.defaults());
        tracker.recordTokens(100);
        tracker.recordTokens(200);
        assertThat(tracker.tokensUsed()).isEqualTo(300);
    }

    @Test
    void isTokenBudgetExhausted() {
        var policy = new SwarmInvocationPolicy(50, 100, 3, Duration.ofSeconds(5), Duration.ofSeconds(30));
        var tracker = new SwarmInvocationTracker(policy);
        tracker.recordTokens(50);
        assertThat(tracker.isTokenBudgetExhausted()).isFalse();
        tracker.recordTokens(60);
        assertThat(tracker.isTokenBudgetExhausted()).isTrue();
    }
}
