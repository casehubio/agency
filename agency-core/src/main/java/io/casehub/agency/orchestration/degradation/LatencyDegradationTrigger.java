package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.budget.BudgetTelemetryEvent;
import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

public class LatencyDegradationTrigger implements DegradationTrigger, Consumer<BudgetTelemetryEvent> {

    private final long errorAfterMs;
    private final int breachCount;
    private final long windowDurationMs;
    private final ConcurrentLinkedDeque<Instant> breaches = new ConcurrentLinkedDeque<>();

    public LatencyDegradationTrigger(LlmInvocationPolicy policy,
                                     int windowSize, int breachCount,
                                     Duration windowDuration) {
        Objects.requireNonNull(policy, "policy must not be null");
        this.errorAfterMs = policy.errorAfter().toMillis();
        this.breachCount = breachCount;
        this.windowDurationMs = windowDuration.toMillis();
    }

    @Override
    public void accept(BudgetTelemetryEvent event) {
        if (event instanceof BudgetTelemetryEvent.Outcome outcome) {
            if (outcome.latencyMs() > errorAfterMs) {
                breaches.addLast(outcome.timestamp());
            }
        }
    }

    @Override
    public boolean shouldDegrade() {
        pruneExpired();
        return breaches.size() >= breachCount;
    }

    private void pruneExpired() {
        Instant cutoff = Instant.now().minusMillis(windowDurationMs);
        while (!breaches.isEmpty() && breaches.peekFirst().isBefore(cutoff)) {
            breaches.pollFirst();
        }
    }
}
