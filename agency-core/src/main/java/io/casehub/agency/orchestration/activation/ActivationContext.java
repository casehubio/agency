package io.casehub.agency.orchestration.activation;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.orchestration.aggregation.AggregationResult;

import java.util.Optional;

public record ActivationContext<T>(
        Object event,
        T state,
        AgentRef agent,
        int activationCount,
        Optional<AggregationResult> lastAggregationResult,
        int consecutiveIdleActivations
) {}
