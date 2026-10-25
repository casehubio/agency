package io.casehub.agency.stigmergy.llm;

import java.util.List;

public record LlmObservationResponse(List<LlmObservation> observations) {

    public LlmObservationResponse {
        observations = List.copyOf(observations);
    }
}
