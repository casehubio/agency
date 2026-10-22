package io.casehub.agency.summarisation.yaml.builtin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

class BatchContextTest {

    @Test
    void computesSize() {
        var events = List.of(
                Map.<String, Object>of("a", 1),
                Map.<String, Object>of("b", 2));
        var ctx = BatchContext.compute(events, List.of());
        assertThat(ctx.get("size")).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void computesCounts_forDeclaredFields() {
        var events = List.of(
                Map.<String, Object>of("severity", "HIGH", "weight", 55.0),
                Map.<String, Object>of("severity", "LOW", "weight", 10.0),
                Map.<String, Object>of("severity", "HIGH", "weight", 60.0));

        var ctx = BatchContext.compute(events, List.of("severity", "weight"));

        var counts = (Map<String, Map<String, Integer>>) ctx.get("counts");
        assertThat(counts.get("severity")).containsEntry("HIGH", 2).containsEntry("LOW", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void computesSums_forNumericFields() {
        var events = List.of(
                Map.<String, Object>of("weight", 55.0),
                Map.<String, Object>of("weight", 10.0),
                Map.<String, Object>of("weight", 60.0));

        var ctx = BatchContext.compute(events, List.of("weight"));

        var sums = (Map<String, Double>) ctx.get("sums");
        assertThat(sums.get("weight")).isEqualTo(125.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void computesAvgs_forNumericFields() {
        var events = List.of(
                Map.<String, Object>of("weight", 30.0),
                Map.<String, Object>of("weight", 60.0));

        var ctx = BatchContext.compute(events, List.of("weight"));

        var avgs = (Map<String, Object>) ctx.get("avgs");
        assertThat(avgs.get("weight")).isEqualTo(45.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sumsZero_avgsNull_forNonNumericFields() {
        var events = List.of(
                Map.<String, Object>of("severity", "HIGH"),
                Map.<String, Object>of("severity", "LOW"));

        var ctx = BatchContext.compute(events, List.of("severity"));

        var sums = (Map<String, Double>) ctx.get("sums");
        assertThat(sums.get("severity")).isEqualTo(0.0);

        var avgs = (Map<String, Object>) ctx.get("avgs");
        assertThat(avgs.get("severity")).isNull();
    }

    @Test
    void includesBatchReference() {
        var events = List.of(Map.<String, Object>of("a", 1));
        var ctx = BatchContext.compute(events, List.of());
        assertThat(ctx.get("batch")).isEqualTo(events);
    }

    @Test
    void emptyAggregateFields_skipsAggregation() {
        var events = List.of(Map.<String, Object>of("a", 1));
        var ctx = BatchContext.compute(events, List.of());
        assertThat(ctx).doesNotContainKey("counts");
        assertThat(ctx).doesNotContainKey("sums");
        assertThat(ctx).doesNotContainKey("avgs");
    }
}
