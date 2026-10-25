package io.casehub.agency.stigmergy.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.casehub.api.spi.observation.Observation;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ObservationHandlerContextTest {

    EnvironmentWriter writer = mock(EnvironmentWriter.class);
    UUID caseId = UUID.randomUUID();
    LlmObservationResult batchResult = new LlmObservationResult(
        List.of(), List.of(), 0.9, "r", Instant.now(), Instant.now(), 1,
        StructuredAgentInvoker.InvocationMetadata.EMPTY);

    ObservationHandlerContext ctx = new ObservationHandlerContext(
        caseId, "agent1", "tenant1", writer, batchResult);

    @Test
    void depositSignal_delegatesToWriter() {
        ctx.depositSignal("alert", 0.8);
        verify(writer).depositSignal(caseId, "alert", 0.8, null, "agent1");
    }

    @Test
    void depositSignal_withHalfLife_delegatesToWriter() {
        ctx.depositSignal("alert", 0.8, Duration.ofMinutes(5));
        verify(writer).depositSignal(caseId, "alert", 0.8, Duration.ofMinutes(5), "agent1");
    }

    @Test
    void writeContext_delegatesToWriter() {
        JsonNode value = new TextNode("hello");
        ctx.writeContext("key1", value);
        verify(writer).writeContext(caseId, "key1", value);
    }

    @Test
    void storeObservation_accumulatesPending() {
        var obs1 = new Observation("p1", 0.9, Map.of(), Instant.now());
        var obs2 = new Observation("p2", 0.8, Map.of(), Instant.now());
        ctx.storeObservation(obs1);
        ctx.storeObservation(obs2);
        verifyNoInteractions(writer);
    }

    @Test
    void flushObservations_writesBatchToWriter() {
        var obs1 = new Observation("p1", 0.9, Map.of(), Instant.now());
        var obs2 = new Observation("p2", 0.8, Map.of(), Instant.now());
        ctx.storeObservation(obs1);
        ctx.storeObservation(obs2);
        ctx.flushObservations();
        verify(writer).storeObservations(eq(caseId), eq("agent1"),
            argThat(list -> list.size() == 2));
    }

    @Test
    void flushObservations_noOpWhenEmpty() {
        ctx.flushObservations();
        verify(writer, never()).storeObservations(any(), any(), any());
    }

    @Test
    void flushObservations_clearsPendingAfterFlush() {
        var obs = new Observation("p1", 0.9, Map.of(), Instant.now());
        ctx.storeObservation(obs);
        ctx.flushObservations();
        verify(writer, times(1)).storeObservations(any(), any(), any());

        ctx.flushObservations();
        verify(writer, times(1)).storeObservations(any(), any(), any());
    }

    @Test
    void accessors_returnConstructorValues() {
        assertThat(ctx.caseId()).isEqualTo(caseId);
        assertThat(ctx.agentId()).isEqualTo("agent1");
        assertThat(ctx.batchResult()).isSameAs(batchResult);
    }
}
