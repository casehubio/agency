package io.casehub.agency.stigmergy.llm;

import io.casehub.api.spi.observation.Observation;

@FunctionalInterface
public interface ObservationHandler {

    void handle(Observation observation, LlmObservation rawObservation,
                ObservationHandlerContext context);
}
