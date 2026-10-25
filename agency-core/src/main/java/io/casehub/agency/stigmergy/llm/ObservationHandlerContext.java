package io.casehub.agency.stigmergy.llm;

import io.casehub.api.spi.observation.Observation;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ObservationHandlerContext {

    private final UUID caseId;
    private final String agentId;
    private final String tenancyId;
    private final EnvironmentWriter writer;
    private final LlmObservationResult batchResult;
    private final List<Observation> pending = new ArrayList<>();

    public ObservationHandlerContext(UUID caseId, String agentId, String tenancyId,
                                    EnvironmentWriter writer,
                                    LlmObservationResult batchResult) {
        this.caseId = caseId;
        this.agentId = agentId;
        this.tenancyId = tenancyId;
        this.writer = writer;
        this.batchResult = batchResult;
    }

    public UUID caseId() {
        return caseId;
    }

    public String agentId() {
        return agentId;
    }

    public LlmObservationResult batchResult() {
        return batchResult;
    }

    public void depositSignal(String name, double strength) {
        writer.depositSignal(caseId, name, strength, null, agentId);
    }

    public void depositSignal(String name, double strength, Duration halfLife) {
        writer.depositSignal(caseId, name, strength, halfLife, agentId);
    }

    public void writeContext(String key, com.fasterxml.jackson.databind.JsonNode value) {
        writer.writeContext(caseId, key, value);
    }

    public void storeObservation(Observation observation) {
        pending.add(observation);
    }

    void flushObservations() {
        if (!pending.isEmpty()) {
            writer.storeObservations(caseId, agentId, List.copyOf(pending));
            pending.clear();
        }
    }
}
