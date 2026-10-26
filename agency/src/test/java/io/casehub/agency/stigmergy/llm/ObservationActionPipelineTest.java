package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.WindowPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationActionPipelineTest {

    private static final EventLevel INPUT_LEVEL = new EventLevel("input", 0);

    @Test
    void pipelineAutoTriggersActionFromObservation() {
        var observedItems = new ArrayList<String>();
        var actionResults = new ArrayList<ActionResult>();

        var obsSummariser = new io.casehub.agency.summarisation.ContentSummariser<String, LlmObservationResult>() {
            @Override
            public java.util.concurrent.CompletionStage<LlmObservationResult> summarise(
                    List<String> items, LlmObservationResult previous) {
                observedItems.addAll(items);
                var result = new LlmObservationResult(
                        List.of(), List.of(), 0.8, "test observation",
                        java.time.Instant.now(), java.time.Instant.now(), items.size(),
                        io.casehub.agency.core.invoke.StructuredAgentInvoker.InvocationMetadata.EMPTY);
                return CompletableFuture.completedFuture(result);
            }
        };

        var actionSummariser = new io.casehub.agency.summarisation.ContentSummariser<LlmObservationResult, ActionResult>() {
            @Override
            public java.util.concurrent.CompletionStage<ActionResult> summarise(
                    List<LlmObservationResult> items, ActionResult previous) {
                var result = new ActionResult(
                        List.of(), 0.9, "test action",
                        io.casehub.agency.core.invoke.StructuredAgentInvoker.InvocationMetadata.EMPTY);
                return CompletableFuture.completedFuture(result);
            }
        };

        var pipeline = ObservationActionPipeline.<String>builder()
                .observationSummariser(obsSummariser)
                .actionSummariser(actionSummariser)
                .observationWindow(WindowPolicy.ofCount(2))
                .actionWindow(WindowPolicy.ofCount(1))
                .build();

        pipeline.actionOutputBus().subscribe(
                action -> true,
                event -> actionResults.add(event.payload()));

        var now = System.currentTimeMillis();
        pipeline.collectObservation(new LevelEvent<>("event-1", now, INPUT_LEVEL, null));
        pipeline.collectObservation(new LevelEvent<>("event-2", now, INPUT_LEVEL, null));

        pipeline.tick(now).toCompletableFuture().join();

        assertThat(observedItems).containsExactly("event-1", "event-2");

        pipeline.tick(now).toCompletableFuture().join();

        assertThat(actionResults).hasSize(1);
        assertThat(actionResults.get(0).confidence()).isEqualTo(0.9);
    }

    @Test
    void pipelineExposesRunners() {
        var obsSummariser = new io.casehub.agency.summarisation.ContentSummariser<String, LlmObservationResult>() {
            @Override
            public java.util.concurrent.CompletionStage<LlmObservationResult> summarise(
                    List<String> items, LlmObservationResult previous) {
                return CompletableFuture.completedFuture(null);
            }
        };
        var actionSummariser = new io.casehub.agency.summarisation.ContentSummariser<LlmObservationResult, ActionResult>() {
            @Override
            public java.util.concurrent.CompletionStage<ActionResult> summarise(
                    List<LlmObservationResult> items, ActionResult previous) {
                return CompletableFuture.completedFuture(null);
            }
        };

        var pipeline = ObservationActionPipeline.<String>builder()
                .observationSummariser(obsSummariser)
                .actionSummariser(actionSummariser)
                .build();

        assertThat(pipeline.observationRunner()).isNotNull();
        assertThat(pipeline.actionRunner()).isNotNull();
        assertThat(pipeline.actionOutputBus()).isNotNull();
    }
}
