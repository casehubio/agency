package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class DegradationAwareSummariserTest {

    static class StubSummariser implements ContentSummariser<String, String> {
        int callCount = 0;
        @Override
        public CompletionStage<String> summarise(List<String> items, @Nullable String previous) {
            callCount++;
            return CompletableFuture.completedFuture("llm-" + callCount);
        }
    }

    static class HeuristicStub implements ContentSummariser<String, String> {
        int callCount = 0;
        @Override
        public CompletionStage<String> summarise(List<String> items, @Nullable String previous) {
            callCount++;
            return CompletableFuture.completedFuture("heuristic-" + callCount);
        }
    }

    @Test
    void delegatesToLlm_whenNotDegraded() throws Exception {
        var llm = new StubSummariser();
        var heuristic = new HeuristicStub();
        var events = new ArrayList<DegradedEvent>();
        var summariser = new DegradationAwareSummariser<>(llm, heuristic, () -> false, events::add, "test-loop");

        var result = summariser.summarise(List.of("input"), null).toCompletableFuture().get();
        assertThat(result).isEqualTo("llm-1");
        assertThat(heuristic.callCount).isZero();
        assertThat(events).isEmpty();
    }

    @Test
    void delegatesToHeuristic_whenDegraded() throws Exception {
        var llm = new StubSummariser();
        var heuristic = new HeuristicStub();
        var events = new ArrayList<DegradedEvent>();
        var summariser = new DegradationAwareSummariser<>(llm, heuristic, () -> true, events::add, "test-loop");

        var result = summariser.summarise(List.of("input"), null).toCompletableFuture().get();
        assertThat(result).isEqualTo("heuristic-1");
        assertThat(llm.callCount).isZero();
    }

    @Test
    void emitsDegradationEvent_onTransition() throws Exception {
        var llm = new StubSummariser();
        var heuristic = new HeuristicStub();
        var events = new ArrayList<DegradedEvent>();
        boolean[] degraded = {false};
        var summariser = new DegradationAwareSummariser<>(llm, heuristic, () -> degraded[0], events::add, "test-loop");

        summariser.summarise(List.of("input"), null).toCompletableFuture().get();
        assertThat(events).isEmpty();

        degraded[0] = true;
        summariser.summarise(List.of("input"), null).toCompletableFuture().get();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).fallbackActive()).isTrue();
        assertThat(events.get(0).capability()).isEqualTo("test-loop");
    }

    @Test
    void emitsRecoveryEvent_onRecovery() throws Exception {
        var llm = new StubSummariser();
        var heuristic = new HeuristicStub();
        var events = new ArrayList<DegradedEvent>();
        boolean[] degraded = {true};
        var summariser = new DegradationAwareSummariser<>(llm, heuristic, () -> degraded[0], events::add, "test-loop");

        summariser.summarise(List.of("input"), null).toCompletableFuture().get();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).fallbackActive()).isTrue();

        degraded[0] = false;
        summariser.summarise(List.of("input"), null).toCompletableFuture().get();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).fallbackActive()).isFalse();
    }

    @Test
    void doesNotEmitDuplicate_whenAlreadyDegraded() throws Exception {
        var llm = new StubSummariser();
        var heuristic = new HeuristicStub();
        var events = new ArrayList<DegradedEvent>();
        var summariser = new DegradationAwareSummariser<>(llm, heuristic, () -> true, events::add, "test-loop");

        summariser.summarise(List.of("a"), null).toCompletableFuture().get();
        summariser.summarise(List.of("b"), null).toCompletableFuture().get();
        assertThat(events).hasSize(1);
    }

    @Test
    void emitsCollapsed_afterConsecutiveDegradedCalls() throws Exception {
        var llm       = new StubSummariser();
        var heuristic = new HeuristicStub();
        var events    = new ArrayList<DegradedEvent>();
        var summariser = new DegradationAwareSummariser<>(llm, heuristic, () -> true, events::add, "test-loop",
                                                          CollapsePolicy.afterCalls(3));

        summariser.summarise(List.of("a"), null).toCompletableFuture().get();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).collapsed()).isFalse();

        summariser.summarise(List.of("b"), null).toCompletableFuture().get();
        assertThat(events).hasSize(1);

        summariser.summarise(List.of("c"), null).toCompletableFuture().get();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).collapsed()).isTrue();
    }

    @Test
    void recoveryResetsCollapseCounter() throws Exception {
        var       llm       = new StubSummariser();
        var       heuristic = new HeuristicStub();
        var       events    = new ArrayList<DegradedEvent>();
        boolean[] degraded  = {true};
        var summariser = new DegradationAwareSummariser<>(llm, heuristic, () -> degraded[0], events::add, "test-loop",
                                                          CollapsePolicy.afterCalls(3));

        summariser.summarise(List.of("a"), null).toCompletableFuture().get();
        summariser.summarise(List.of("b"), null).toCompletableFuture().get();

        degraded[0] = false;
        summariser.summarise(List.of("c"), null).toCompletableFuture().get();

        degraded[0] = true;
        summariser.summarise(List.of("d"), null).toCompletableFuture().get();
        summariser.summarise(List.of("e"), null).toCompletableFuture().get();

        assertThat(events.stream().filter(DegradedEvent::collapsed).count()).isZero();
    }

    @Test
    void defaultCollapsePolicy_neverCollapses() throws Exception {
        var llm        = new StubSummariser();
        var heuristic  = new HeuristicStub();
        var events     = new ArrayList<DegradedEvent>();
        var summariser = new DegradationAwareSummariser<>(llm, heuristic, () -> true, events::add, "test-loop");

        for (int i = 0; i < 20; i++) {
            summariser.summarise(List.of("x"), null).toCompletableFuture().get();
        }
        assertThat(events.stream().filter(DegradedEvent::collapsed).count()).isZero();
    }


}
