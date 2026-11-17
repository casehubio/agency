package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;
import io.casehub.api.model.stigmergy.CircuitBreakerState;
import io.casehub.platform.agent.budget.BudgetTelemetryEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LatencyDegradationTrigger implements StatefulDegradationTrigger, Consumer<BudgetTelemetryEvent> {

    private final long                               errorAfterMs;
    private final int                                breachCount;
    private final long                               windowDurationMs;
    private final long                               recoveryTimeoutMs;
    private final int                                probeSuccessThreshold;
    private final Supplier<CircuitBreakerState>      externalState;
    private final Consumer<CircuitBreakerTransition> transitionListener;

    private final    ConcurrentLinkedDeque<Instant>       breaches          = new ConcurrentLinkedDeque<>();
    private final    AtomicReference<CircuitBreakerState> localState        = new AtomicReference<>(CircuitBreakerState.CLOSED);
    private volatile Instant                              openedAt;
    private final    AtomicInteger                        probeSuccessCount = new AtomicInteger(0);

    private LatencyDegradationTrigger(LlmInvocationPolicy policy,
                                      LatencyCircuitBreakerConfig config,
                                      Supplier<CircuitBreakerState> externalState,
                                      Consumer<CircuitBreakerTransition> transitionListener) {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(config, "config must not be null");
        this.errorAfterMs          = policy.errorAfter().toMillis();
        this.breachCount           = config.breachCount();
        this.windowDurationMs      = config.windowDuration().toMillis();
        this.recoveryTimeoutMs     = config.recoveryTimeout().toMillis();
        this.probeSuccessThreshold = config.probeSuccessThreshold();
        this.externalState         = externalState;
        this.transitionListener    = transitionListener;
    }

    public static Builder builder(LlmInvocationPolicy policy, LatencyCircuitBreakerConfig config) {
        return new Builder(policy, config);
    }

    @Override
    public void accept(BudgetTelemetryEvent event) {
        if (!(event instanceof BudgetTelemetryEvent.Outcome outcome)) {return;}

        boolean breach  = outcome.latencyMs() > errorAfterMs;
        var     current = localState.get();

        switch (current) {
            case CLOSED -> {
                if (breach) {
                    breaches.addLast(outcome.timestamp());
                    pruneExpired();
                    if (breaches.size() >= breachCount) {
                        if (localState.compareAndSet(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN)) {
                            openedAt = Instant.now();
                            emitTransition(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN);
                        }
                    }
                }
            }
            case OPEN -> {}
            case HALF_OPEN -> {
                if (breach) {
                    if (localState.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN)) {
                        openedAt = Instant.now();
                        probeSuccessCount.set(0);
                        emitTransition(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN);
                    }
                } else {
                    int successes = probeSuccessCount.incrementAndGet();
                    if (successes >= probeSuccessThreshold) {
                        if (localState.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED)) {
                            breaches.clear();
                            probeSuccessCount.set(0);
                            emitTransition(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldDegrade() {
        checkRecoveryTimeout();
        return state() == CircuitBreakerState.OPEN;
    }

    @Override
    public CircuitBreakerState state() {
        var local = localState.get();
        if (local == CircuitBreakerState.CLOSED) {
            pruneExpired();
        }
        var external = externalState.get();
        return restrictiveness(local) >= restrictiveness(external) ? local : external;
    }

    private void checkRecoveryTimeout() {
        if (localState.get() == CircuitBreakerState.OPEN) {
            Instant opened = openedAt;
            if (opened != null && Instant.now().toEpochMilli() - opened.toEpochMilli() >= recoveryTimeoutMs) {
                if (localState.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                    probeSuccessCount.set(0);
                    emitTransition(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN);
                }
            }
        }
    }

    private static int restrictiveness(CircuitBreakerState s) {
        return switch (s) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
        };
    }

    private void pruneExpired() {
        Instant cutoff = Instant.now().minusMillis(windowDurationMs);
        while (!breaches.isEmpty() && breaches.peekFirst().isBefore(cutoff)) {
            breaches.pollFirst();
        }
    }

    private void emitTransition(CircuitBreakerState from, CircuitBreakerState to) {
        transitionListener.accept(new CircuitBreakerTransition(from, to, Instant.now()));
    }

    public static class Builder {
        private final LlmInvocationPolicy                policy;
        private final LatencyCircuitBreakerConfig        config;
        private       Supplier<CircuitBreakerState>      externalState;
        private       Consumer<CircuitBreakerTransition> transitionListener;

        private Builder(LlmInvocationPolicy policy, LatencyCircuitBreakerConfig config) {
            this.policy = policy;
            this.config = config;
        }

        public Builder externalState(Supplier<CircuitBreakerState> supplier) {
            this.externalState = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder onTransition(Consumer<CircuitBreakerTransition> listener) {
            this.transitionListener = Objects.requireNonNull(listener);
            return this;
        }

        public LatencyDegradationTrigger build() {
            return new LatencyDegradationTrigger(policy, config,
                                                 externalState != null ? externalState : () -> CircuitBreakerState.CLOSED,
                                                 transitionListener != null ? transitionListener : t -> {});
        }
    }
}
