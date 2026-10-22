package io.casehub.agency.agentic.activation;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.aggregation.AggregationResult;

import java.util.Optional;

public record ActivationContext<T>(
        Object event,
        T state,
        AgentRef agent,
        int activationCount,
        Optional<AggregationResult> lastAggregationResult,
        int consecutiveIdleActivations
) {}
