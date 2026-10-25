package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.api.spi.observation.Observation;

import java.time.Instant;
import java.util.List;

public record InterpretationResult(
    List<Observation> observations,
    List<RecommendedAction> recommendedActions,
    double confidence,
    String reasoning,
    Instant batchStart,
    Instant batchEnd,
    int batchEventCount,
    StructuredAgentInvoker.InvocationMetadata invocationMetadata) {

    public InterpretationResult {
        observations = List.copyOf(observations);
        recommendedActions = List.copyOf(recommendedActions);
    }
}
