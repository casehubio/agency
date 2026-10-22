package io.casehub.agency.summarisation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

class ContentSummariserTest {

    static final EventLevel LEVEL = new EventLevel("test", 1);

    @Test
    void asSummariser_carriesPreviousState() {
        ContentSummariser<String, String> cs = (items, prev) ->
            CompletableFuture.completedFuture(
                (prev == null ? "" : prev + " ") + String.join(",", items));

        StatefulSummariser<String, String, String> s = cs.asSummariser();

        var batch1 = List.of(new LevelEvent<>("a", 100L, LEVEL, null));
        var r1 = s.summarise(batch1, null).toCompletableFuture().join();
        assertThat(r1.outputs()).containsExactly("a");
        assertThat(r1.newState()).isEqualTo("a");

        var batch2 = List.of(new LevelEvent<>("b", 200L, LEVEL, null));
        var r2 = s.summarise(batch2, r1.newState()).toCompletableFuture().join();
        assertThat(r2.outputs()).containsExactly("a b");
        assertThat(r2.newState()).isEqualTo("a b");
    }

    @Test
    void asSummariser_worksInRunner_withStateManagement() {
        ContentSummariser<String, String> cs = (items, prev) ->
            CompletableFuture.completedFuture(
                (prev == null ? "" : prev + "+") + items.size());

        var outputBus = new EventStreamBus<String>();
        var captured = new java.util.ArrayList<String>();
        outputBus.subscribe(s -> true, e -> captured.add(e.payload()));

        var runner = new SummarisationRunner<>(
            WindowPolicy.ofCount(1), cs.asSummariser(), outputBus,
            new EventLevel("out", 2));

        runner.collect(new LevelEvent<>("x", 100L, LEVEL, null));
        runner.tick(200L).toCompletableFuture().join();

        runner.collect(new LevelEvent<>("y", 300L, LEVEL, null));
        runner.tick(400L).toCompletableFuture().join();

        assertThat(captured).containsExactly("1", "1+1");
    }

    @Test
    void directUse_withoutRunner() {
        ContentSummariser<String, String> cs = (items, prev) ->
            CompletableFuture.completedFuture(
                (prev == null ? "" : prev + " ") + String.join(",", items));

        var r1 = cs.summarise(List.of("a", "b"), null).toCompletableFuture().join();
        assertThat(r1).isEqualTo("a,b");

        var r2 = cs.summarise(List.of("c"), r1).toCompletableFuture().join();
        assertThat(r2).isEqualTo("a,b c");
    }
}
