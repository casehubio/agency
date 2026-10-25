package io.casehub.agency.stigmergy.llm;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.spi.observation.Observation;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface EnvironmentWriter {

    void storeObservations(UUID caseId, String agentId, List<Observation> observations);

    void depositSignal(UUID caseId, String name, double strength,
                       @Nullable Duration halfLife, String sourceAgentId);

    void writeContext(UUID caseId, String key, JsonNode value);
}
