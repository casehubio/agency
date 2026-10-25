package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.api.spi.observation.Observation;

import java.time.Instant;
import java.util.List;

public record LlmObservationResult(
    List<Observation> observations,
    List<LlmObservation> rawObservations,
    double confidence,
    String reasoning,
    Instant batchStart,
    Instant batchEnd,
    int batchEventCount,
    StructuredAgentInvoker.InvocationMetadata invocationMetadata) {

    public LlmObservationResult {
        observations = List.copyOf(observations);
        rawObservations = List.copyOf(rawObservations);
    }
}
