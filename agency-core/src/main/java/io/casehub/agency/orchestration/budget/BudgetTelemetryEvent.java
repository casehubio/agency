package io.casehub.agency.orchestration.budget;

import java.time.Instant;

public sealed interface BudgetTelemetryEvent {

    record Acquisition(
        String correlationId,
        String agentId,
        String topologyId,
        BudgetPriority priority,
        BudgetPressure pressure,
        boolean granted,
        Instant timestamp
    ) implements BudgetTelemetryEvent {}

    record Outcome(
        String correlationId,
        String modelId,
        long tokensConsumed,
        long latencyMs,
        Instant timestamp
    ) implements BudgetTelemetryEvent {}
}
