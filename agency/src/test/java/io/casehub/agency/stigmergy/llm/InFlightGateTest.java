package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.summarisation.EmissionPolicy;
import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.WindowPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class InFlightGateTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    @Test
    void gating_allowsEmission_whenNothingInFlight() {
        ContentSummariser<String, String> delegate = (items, prev) ->
            CompletableFuture.completedFuture("done");
        var gate = new InFlightGate<>(delegate);
        EmissionPolicy<String, Object> gated =
            gate.gating(EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(new LevelEvent<>("a", 1L, LEVEL, null));
        assertThat(gated.shouldEmit(buffer, null, 100L)).isTrue();
    }

    @Test
    void gating_suppressesEmission_whenCallInFlight() {
        var pending = new CompletableFuture<String>();
        ContentSummariser<String, String> delegate = (items, prev) -> pending;
        var gate = new InFlightGate<>(delegate);

        gate.summariser().summarise(List.of("a"), null);

        EmissionPolicy<String, Object> gated =
            gate.gating(EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(new LevelEvent<>("b", 2L, LEVEL, null));
        assertThat(gated.shouldEmit(buffer, null, 200L)).isFalse();
    }

    @Test
    void gating_resumesEmission_afterCallCompletes() {
        var pending = new CompletableFuture<String>();
        ContentSummariser<String, String> delegate = (items, prev) -> pending;
        var gate = new InFlightGate<>(delegate);

        gate.summariser().summarise(List.of("a"), null);

        EmissionPolicy<String, Object> gated =
            gate.gating(EmissionPolicy.window(WindowPolicy.ofCount(1)));
        assertThat(gated.shouldEmit(
            List.of(new LevelEvent<>("b", 2L, LEVEL, null)), null, 200L)).isFalse();

        pending.complete("done");

        assertThat(gated.shouldEmit(
            List.of(new LevelEvent<>("c", 3L, LEVEL, null)), null, 300L)).isTrue();
    }

    @Test
    void gating_resumesEmission_afterCallFails() {
        var pending = new CompletableFuture<String>();
        ContentSummariser<String, String> delegate = (items, prev) -> pending;
        var gate = new InFlightGate<>(delegate);

        gate.summariser().summarise(List.of("a"), null);

        EmissionPolicy<String, Object> gated =
            gate.gating(EmissionPolicy.window(WindowPolicy.ofCount(1)));
        assertThat(gated.shouldEmit(
            List.of(new LevelEvent<>("b", 2L, LEVEL, null)), null, 200L)).isFalse();

        pending.completeExceptionally(new RuntimeException("boom"));

        assertThat(gated.shouldEmit(
            List.of(new LevelEvent<>("c", 3L, LEVEL, null)), null, 300L)).isTrue();
    }

    @Test
    void summariser_delegatesToWrapped() {
        ContentSummariser<String, String> delegate = (items, prev) ->
            CompletableFuture.completedFuture("result:" + items.size());
        var gate = new InFlightGate<>(delegate);
        var result = gate.summariser().summarise(List.of("a", "b"), null)
            .toCompletableFuture().join();
        assertThat(result).isEqualTo("result:2");
    }

    @Test
    void summariser_propagatesPreviousResult() {
        ContentSummariser<String, String> delegate = (items, prev) ->
            CompletableFuture.completedFuture(prev + "+" + items.size());
        var gate = new InFlightGate<>(delegate);
        var result = gate.summariser().summarise(List.of("a"), "base")
            .toCompletableFuture().join();
        assertThat(result).isEqualTo("base+1");
    }

    @Test
    void multipleSequentialCalls_eachTracked() {
        var first = new CompletableFuture<String>();
        var second = new CompletableFuture<String>();
        var callCount = new int[]{0};
        ContentSummariser<String, String> delegate = (items, prev) ->
            callCount[0]++ == 0 ? first : second;
        var gate = new InFlightGate<>(delegate);

        EmissionPolicy<String, Object> gated =
            gate.gating(EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(new LevelEvent<>("x", 1L, LEVEL, null));

        gate.summariser().summarise(List.of("a"), null);
        assertThat(gated.shouldEmit(buffer, null, 100L)).isFalse();

        first.complete("done1");
        assertThat(gated.shouldEmit(buffer, null, 200L)).isTrue();

        gate.summariser().summarise(List.of("b"), null);
        assertThat(gated.shouldEmit(buffer, null, 300L)).isFalse();

        second.complete("done2");
        assertThat(gated.shouldEmit(buffer, null, 400L)).isTrue();
    }
}
