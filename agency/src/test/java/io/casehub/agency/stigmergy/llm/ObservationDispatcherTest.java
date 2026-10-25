package io.casehub.agency.stigmergy.llm;

import io.casehub.api.spi.observation.Observation;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ObservationDispatcherTest {

    EnvironmentWriter writer = mock(EnvironmentWriter.class);
    UUID caseId = UUID.randomUUID();

    LlmObservation rawLeaf = new LlmObservation("leaf", 0.9,
        List.of("key1"), List.of(), Map.of(), "leaf obs");
    LlmObservation rawDerived = new LlmObservation("derived", 0.85,
        List.of("key2"), List.of("leaf"), Map.of(), "derived obs");

    Observation engineLeaf = new Observation("leaf", 0.9, Map.of(), Instant.now());
    Observation engineDerived = new Observation("derived", 0.85, Map.of(), Instant.now());

    LlmObservationResult result = new LlmObservationResult(
        List.of(engineDerived, engineLeaf),
        List.of(rawDerived, rawLeaf),
        0.87, "reasoning", Instant.now(), Instant.now(), 3,
        StructuredAgentInvoker.InvocationMetadata.EMPTY);

    @Test
    void dispatch_respectsDerivedFromOrder_leavesFirst() {
        List<String> order = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .on("leaf", (obs, raw, ctx) -> order.add("leaf"))
            .on("derived", (obs, raw, ctx) -> order.add("derived"))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, result);
        dispatcher.dispatch(result, ctx);
        assertThat(order).containsExactly("leaf", "derived");
    }

    @Test
    void dispatch_specificHandler_takesPrecedenceOverOnDerived() {
        List<String> hits = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .on("derived", (obs, raw, ctx) -> hits.add("specific"))
            .onDerived((obs, raw, ctx) -> hits.add("generic-derived"))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, result);
        dispatcher.dispatch(result, ctx);
        assertThat(hits).contains("specific");
        assertThat(hits).doesNotContain("generic-derived");
    }

    @Test
    void dispatch_onDerived_handlesUnregisteredDerivedObs() {
        List<String> hits = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .on("leaf", (obs, raw, ctx) -> hits.add("leaf"))
            .onDerived((obs, raw, ctx) -> hits.add("derived-generic:" + obs.patternId()))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, result);
        dispatcher.dispatch(result, ctx);
        assertThat(hits).containsExactly("leaf", "derived-generic:derived");
    }

    @Test
    void dispatch_unhandled_receivesNonDerivedWithNoSpecificHandler() {
        List<String> unhandled = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .onUnhandled((obs, raw, ctx) -> unhandled.add(obs.patternId()))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, result);
        dispatcher.dispatch(result, ctx);
        assertThat(unhandled).containsExactlyInAnyOrder("leaf", "derived");
    }

    @Test
    void dispatch_flushesObservationsAfterAllHandlers() {
        var dispatcher = ObservationDispatcher.builder()
            .on("leaf", (obs, raw, ctx) -> ctx.storeObservation(obs))
            .on("derived", (obs, raw, ctx) -> ctx.storeObservation(obs))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, result);
        dispatcher.dispatch(result, ctx);
        verify(writer).storeObservations(eq(caseId), eq("a1"),
            argThat(list -> list.size() == 2));
    }

    @Test
    void dispatch_handlesCycleGracefully() {
        var rawA = new LlmObservation("A", 0.9,
            List.of(), List.of("B"), Map.of(), "");
        var rawB = new LlmObservation("B", 0.9,
            List.of(), List.of("A"), Map.of(), "");
        var engA = new Observation("A", 0.9, Map.of(), Instant.now());
        var engB = new Observation("B", 0.9, Map.of(), Instant.now());
        var cycleResult = new LlmObservationResult(
            List.of(engA, engB), List.of(rawA, rawB),
            0.9, "", Instant.now(), Instant.now(), 1,
            StructuredAgentInvoker.InvocationMetadata.EMPTY);
        List<String> order = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .on("A", (obs, raw, ctx) -> order.add("A"))
            .on("B", (obs, raw, ctx) -> order.add("B"))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, cycleResult);
        dispatcher.dispatch(cycleResult, ctx);
        assertThat(order).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void dispatch_handlerExceptionDoesNotAbortRemaining() {
        List<String> processed = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .on("leaf", (obs, raw, ctx) -> { throw new RuntimeException("boom"); })
            .on("derived", (obs, raw, ctx) -> processed.add("derived"))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, result);
        dispatcher.dispatch(result, ctx);
        assertThat(processed).contains("derived");
    }

    @Test
    void dispatch_derivedFromNonExistentParent_treatedAsLeaf() {
        var rawOrphan = new LlmObservation("orphan", 0.9,
            List.of(), List.of("missing-parent"), Map.of(), "");
        var engOrphan = new Observation("orphan", 0.9, Map.of(), Instant.now());
        var orphanResult = new LlmObservationResult(
            List.of(engOrphan), List.of(rawOrphan),
            0.9, "", Instant.now(), Instant.now(), 1,
            StructuredAgentInvoker.InvocationMetadata.EMPTY);
        List<String> order = new ArrayList<>();
        var dispatcher = ObservationDispatcher.builder()
            .on("orphan", (obs, raw, ctx) -> order.add("orphan"))
            .build();
        var ctx = new ObservationHandlerContext(
            caseId, "a1", "t1", writer, orphanResult);
        dispatcher.dispatch(orphanResult, ctx);
        assertThat(order).containsExactly("orphan");
    }
}
