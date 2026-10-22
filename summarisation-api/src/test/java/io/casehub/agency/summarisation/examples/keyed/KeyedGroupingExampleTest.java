package io.casehub.agency.summarisation.examples.keyed;

import io.casehub.agency.summarisation.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates keyed grouping — events partitioned by a key and summarised
 * per-group when a completion predicate fires or the group goes stale.
 *
 * Scenario: logistics warehouse monitoring. Package scans arrive with a
 * warehouseId. Events are grouped by warehouse and summarised when a group
 * reaches 3 scans (batch complete) or after 5 seconds without activity (stale).
 */
class KeyedGroupingExampleTest {

    record Scan(String packageId, String warehouseId, double weight) {}
    record WarehouseSummary(String warehouseId, int scanCount, double totalWeight) {}

    static final EventLevel SCANS = new EventLevel("scans", 0);
    static final EventLevel SUMMARIES = new EventLevel("summaries", 1);

    @Test
    void keyedAccumulator_groupsByWarehouse_drainsOnCompletion() {
        var accumulator = new KeyedAccumulator<String, Scan>(
                event -> event.payload().warehouseId(),
                group -> group.size() >= 3,
                5000L);

        accumulator.collect(event("PKG-1", "WH-A", 10.0, 100L));
        accumulator.collect(event("PKG-2", "WH-B", 20.0, 200L));
        accumulator.collect(event("PKG-3", "WH-A", 15.0, 300L));
        accumulator.collect(event("PKG-4", "WH-A", 25.0, 400L));

        assertThat(accumulator.groupCount()).isEqualTo(2);

        var completed = accumulator.drain(500L);
        assertThat(completed).hasSize(1);
        assertThat(completed.get(0)).hasSize(3);
        assertThat(completed.get(0).get(0).payload().warehouseId()).isEqualTo("WH-A");

        assertThat(accumulator.groupCount()).isEqualTo(1);
    }

    @Test
    void keyedAccumulator_drainsStaleGroups() {
        var accumulator = new KeyedAccumulator<String, Scan>(
                event -> event.payload().warehouseId(),
                group -> group.size() >= 10,
                5000L);

        accumulator.collect(event("PKG-1", "WH-A", 10.0, 1000L));
        accumulator.collect(event("PKG-2", "WH-A", 20.0, 2000L));

        var early = accumulator.drain(3000L);
        assertThat(early).isEmpty();

        var stale = accumulator.drain(7000L);
        assertThat(stale).hasSize(1);
        assertThat(stale.get(0)).hasSize(2);
    }

    @Test
    void keyedSummarisationRunner_fullPipeline() {
        var outputBus = new EventStreamBus<WarehouseSummary>();
        var results = new ArrayList<WarehouseSummary>();
        outputBus.subscribe(e -> true, e -> results.add(e.payload()));

        Summariser<Scan, WarehouseSummary> summariser = Summariser.ofSync(batch -> {
            var warehouseId = batch.get(0).payload().warehouseId();
            var totalWeight = batch.stream()
                    .mapToDouble(e -> e.payload().weight()).sum();
            return List.of(new WarehouseSummary(warehouseId, batch.size(), totalWeight));
        });

        var runner = new KeyedSummarisationRunner<>(
                event -> event.payload().warehouseId(),
                group -> group.size() >= 2,
                5000L,
                summariser,
                outputBus, SUMMARIES);

        runner.collect(event("PKG-1", "WH-A", 10.0, 100L));
        runner.collect(event("PKG-2", "WH-B", 20.0, 200L));
        runner.collect(event("PKG-3", "WH-A", 15.0, 300L));
        runner.collect(event("PKG-4", "WH-B", 30.0, 400L));

        runner.tick(500L).toCompletableFuture().join();

        assertThat(results).hasSize(2);
        assertThat(results).anySatisfy(s -> {
            assertThat(s.warehouseId()).isEqualTo("WH-A");
            assertThat(s.scanCount()).isEqualTo(2);
            assertThat(s.totalWeight()).isEqualTo(25.0);
        });
        assertThat(results).anySatisfy(s -> {
            assertThat(s.warehouseId()).isEqualTo("WH-B");
            assertThat(s.scanCount()).isEqualTo(2);
            assertThat(s.totalWeight()).isEqualTo(50.0);
        });
    }

    @Test
    void keyedGrouping_tenancyAwarePartitioning() {
        var accumulator = new KeyedAccumulator<String, Scan>(
                event -> event.tenancyId() + ":" + event.payload().warehouseId(),
                group -> group.size() >= 2,
                5000L);

        accumulator.collect(event("PKG-1", "WH-A", 10.0, 100L, "tenant-1"));
        accumulator.collect(event("PKG-2", "WH-A", 20.0, 200L, "tenant-2"));
        accumulator.collect(event("PKG-3", "WH-A", 15.0, 300L, "tenant-1"));

        var completed = accumulator.drain(400L);
        assertThat(completed).hasSize(1);
        assertThat(completed.get(0).get(0).tenancyId()).isEqualTo("tenant-1");

        assertThat(accumulator.groupCount()).isEqualTo(1);
    }

    private LevelEvent<Scan> event(String pkgId, String whId, double weight, long ts) {
        return new LevelEvent<>(new Scan(pkgId, whId, weight), ts, SCANS, null);
    }

    private LevelEvent<Scan> event(String pkgId, String whId, double weight, long ts, String tenancyId) {
        return new LevelEvent<>(new Scan(pkgId, whId, weight), ts, SCANS, tenancyId);
    }
}
