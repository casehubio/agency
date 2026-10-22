package io.casehub.agency.summarisation.examples.stateful;

import io.casehub.agency.summarisation.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates the unified summarisation model:
 *
 * 1. StatefulSummariser — framework-managed state per partition (tenant-aware)
 * 2. ContentSummariser.asSummariser() — bridges content summarisation into pipelines
 * 3. SummarisationRunner state management — automatically detects StatefulSummariser
 *    and manages state per partition without consumer boilerplate
 */
class StatefulSummariserExampleTest {

    static final EventLevel INPUT = new EventLevel("readings", 0);
    static final EventLevel OUTPUT = new EventLevel("averages", 1);

    /**
     * A running-average summariser that maintains cumulative state per partition.
     * The framework manages state — the summariser just declares what state it needs.
     */
    static class RunningAverageSummariser
            implements StatefulSummariser<Double, String, RunningAverageSummariser.AvgState> {

        record AvgState(double sum, int count) {
            double average() { return count > 0 ? sum / count : 0; }

            AvgState accumulate(List<Double> values) {
                double newSum = sum;
                int newCount = count;
                for (var v : values) {
                    newSum += v;
                    newCount++;
                }
                return new AvgState(newSum, newCount);
            }
        }

        @Override
        public CompletionStage<SummariseResult<String, AvgState>> summarise(
                List<LevelEvent<Double>> batch, AvgState previousState) {
            var state = previousState != null ? previousState : new AvgState(0, 0);
            var values = batch.stream().map(LevelEvent::payload).toList();
            var newState = state.accumulate(values);
            var output = "avg=%.2f (n=%d)".formatted(newState.average(), newState.count());
            return CompletableFuture.completedFuture(
                    new SummariseResult<>(List.of(output), newState));
        }
    }

    @Test
    void statefulSummariser_accumulatesAcrossBatches() {
        var outputBus = new EventStreamBus<String>();
        var results = new ArrayList<String>();
        outputBus.subscribe(e -> true, e -> results.add(e.payload()));

        var runner = new SummarisationRunner<>(
                WindowPolicy.ofCount(3),
                new RunningAverageSummariser(),
                outputBus, OUTPUT);

        // Batch 1: [10, 20, 30] → avg=20.00
        publish(runner, List.of(10.0, 20.0, 30.0), null);
        runner.tick(1000L).toCompletableFuture().join();
        assertThat(results).containsExactly("avg=20.00 (n=3)");

        // Batch 2: [40, 50, 60] → running avg across all 6 values = 35.00
        publish(runner, List.of(40.0, 50.0, 60.0), null);
        runner.tick(2000L).toCompletableFuture().join();
        assertThat(results).containsExactly("avg=20.00 (n=3)", "avg=35.00 (n=6)");
    }

    @Test
    void statefulSummariser_isolatesStatePerTenant() {
        var outputBus = new EventStreamBus<String>();
        var results = new ArrayList<LevelEvent<String>>();
        outputBus.subscribe(e -> true, results::add);

        var runner = new SummarisationRunner<>(
                WindowPolicy.ofCount(2),
                new RunningAverageSummariser(),
                outputBus, OUTPUT);

        // Tenant-homogeneous batches — each batch triggers separately
        publish(runner, List.of(10.0, 20.0), "tenant-A");
        runner.tick(1000L).toCompletableFuture().join();

        publish(runner, List.of(100.0, 200.0), "tenant-B");
        runner.tick(2000L).toCompletableFuture().join();

        // Second batch for tenant-A — state accumulates independently
        publish(runner, List.of(30.0, 40.0), "tenant-A");
        runner.tick(3000L).toCompletableFuture().join();

        var tenantA = results.stream()
                .filter(e -> "tenant-A".equals(e.tenancyId()))
                .map(LevelEvent::payload).toList();
        var tenantB = results.stream()
                .filter(e -> "tenant-B".equals(e.tenancyId()))
                .map(LevelEvent::payload).toList();

        assertThat(tenantA).containsExactly("avg=15.00 (n=2)", "avg=25.00 (n=4)");
        assertThat(tenantB).containsExactly("avg=150.00 (n=2)");
    }

    /**
     * ContentSummariser.asSummariser() bridges content summarisation into
     * pipeline infrastructure with state. No bridge adapter class needed.
     */
    @Test
    void contentSummariser_asSummariser_bridgesIntoPipeline() {
        ContentSummariser<String, String> appendSummariser = (items, previous) -> {
            var combined = previous != null ? previous + " | " : "";
            combined += String.join(", ", items);
            return CompletableFuture.completedFuture(combined);
        };

        var outputBus = new EventStreamBus<String>();
        var results = new ArrayList<String>();
        outputBus.subscribe(e -> true, e -> results.add(e.payload()));

        // asSummariser() bridges to StatefulSummariser<String, String, String>
        var runner = new SummarisationRunner<>(
                WindowPolicy.ofCount(2),
                appendSummariser.asSummariser(),
                outputBus, OUTPUT);

        publish(runner, List.of("alpha", "beta"), null);
        runner.tick(1000L).toCompletableFuture().join();
        assertThat(results).containsExactly("alpha, beta");

        publish(runner, List.of("gamma", "delta"), null);
        runner.tick(2000L).toCompletableFuture().join();
        assertThat(results).containsExactly("alpha, beta", "alpha, beta | gamma, delta");
    }

    private <E> void publish(SummarisationRunner<E, ?> runner,
                              List<E> values, String tenancyId) {
        long t = System.currentTimeMillis();
        for (var v : values) {
            runner.collect(new LevelEvent<>(v, t++, INPUT, tenancyId));
        }
    }
}
