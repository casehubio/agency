package io.casehub.agency.summarisation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

class StatefulSummariserTest {

    static final EventLevel LEVEL = new EventLevel("test", 1);
    static final EventLevel OUT_LEVEL = new EventLevel("out", 2);

    @Test
    void runner_passesAndStoresState_forStatefulSummariser() {
        var stateLog = new ArrayList<String>();

        StatefulSummariser<String, String, String> summariser = (batch, prev) -> {
            stateLog.add("prev=" + prev);
            String newState = (prev == null ? "" : prev) + batch.size();
            return CompletableFuture.completedFuture(
                new StatefulSummariser.SummariseResult<>(List.of("out"), newState));
        };

        var outputBus = new EventStreamBus<String>();
        var runner = new SummarisationRunner<>(
            WindowPolicy.ofCount(1), summariser, outputBus, OUT_LEVEL);

        runner.collect(new LevelEvent<>("a", 100L, LEVEL, null));
        runner.tick(200L).toCompletableFuture().join();

        runner.collect(new LevelEvent<>("b", 300L, LEVEL, null));
        runner.tick(400L).toCompletableFuture().join();

        assertThat(stateLog).containsExactly("prev=null", "prev=1");
    }

    @Test
    void runner_maintainsSeparateState_perTenant() {
        StatefulSummariser<String, String, Integer> counter = (batch, prev) -> {
            int count = (prev == null ? 0 : prev) + batch.size();
            return CompletableFuture.completedFuture(
                new StatefulSummariser.SummariseResult<>(List.of("count=" + count), count));
        };

        var outputBus = new EventStreamBus<String>();
        var captured = new ArrayList<LevelEvent<String>>();
        outputBus.subscribe(s -> true, captured::add);

        var runner = new SummarisationRunner<>(
            WindowPolicy.ofCount(1), counter, outputBus, OUT_LEVEL);

        runner.collect(new LevelEvent<>("x", 100L, LEVEL, "t1"));
        runner.tick(200L).toCompletableFuture().join();

        runner.collect(new LevelEvent<>("y", 300L, LEVEL, "t2"));
        runner.tick(400L).toCompletableFuture().join();

        runner.collect(new LevelEvent<>("z", 500L, LEVEL, "t1"));
        runner.tick(600L).toCompletableFuture().join();

        assertThat(captured).extracting(e -> e.payload())
            .containsExactly("count=1", "count=1", "count=2");
        assertThat(captured).extracting(e -> e.tenancyId())
            .containsExactly("t1", "t2", "t1");
    }

    @Test
    void statelessSummariser_unaffectedByStateManagement() {
        Summariser<String, String> stateless = Summariser.ofSync(batch ->
            batch.stream().map(LevelEvent::payload).toList());

        var outputBus = new EventStreamBus<String>();
        var captured = new ArrayList<String>();
        outputBus.subscribe(s -> true, e -> captured.add(e.payload()));

        var runner = new SummarisationRunner<>(
            WindowPolicy.ofCount(1), stateless, outputBus, OUT_LEVEL);

        runner.collect(new LevelEvent<>("a", 100L, LEVEL, null));
        runner.tick(200L).toCompletableFuture().join();

        runner.collect(new LevelEvent<>("b", 300L, LEVEL, null));
        runner.tick(400L).toCompletableFuture().join();

        assertThat(captured).containsExactly("a", "b");
    }
}
