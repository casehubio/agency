package io.casehub.agency.summarisation.yaml.builtin;

import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhaseDetectSummariserTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    private PhaseDetectSummariser createSummariser() {
        var config = Map.<String, Object>of(
                "initial", "NORMAL",
                "states", List.of("NORMAL", "CONGESTION", "RECOVERY"),
                "transitions", List.of(
                        Map.of("from", "NORMAL", "to", "CONGESTION",
                                "count-field", "severity", "count-value", "HIGH",
                                "op", ">=", "threshold", 3),
                        Map.of("from", "CONGESTION", "to", "RECOVERY",
                                "count-field", "severity", "count-value", "HIGH",
                                "op", "==", "threshold", 0)));

        return PhaseDetectSummariser.create(config, List.of("severity"));
    }

    private List<LevelEvent<Map<String, Object>>> createBatch(String severity, int count) {
        return Stream.generate(() -> new LevelEvent<>(
                        Map.<String, Object>of("severity", severity), 100L, LEVEL, null))
                .limit(count)
                .toList();
    }

    @Test
    void silentWhenStable() {
        var summariser = createSummariser();

        var batch = createBatch("HIGH", 2);
        var result = summariser.summarise(batch, null).toCompletableFuture().join();

        assertThat(result.outputs()).isEmpty();
        assertThat(result.newState()).isEqualTo("NORMAL");
    }

    @Test
    void emitsOnTransition() {
        var summariser = createSummariser();

        var batch = createBatch("HIGH", 3);
        var result = summariser.summarise(batch, null).toCompletableFuture().join();

        assertThat(result.outputs()).hasSize(1);
        assertThat(result.outputs().get(0))
                .containsEntry("from", "NORMAL")
                .containsEntry("to", "CONGESTION")
                .containsEntry("phase", "CONGESTION");
        assertThat(result.newState()).isEqualTo("CONGESTION");
    }

    @Test
    void maintainsState_acrossCalls() {
        var summariser = createSummariser();

        var r1 = summariser.summarise(createBatch("HIGH", 3), null)
                .toCompletableFuture().join();
        assertThat(r1.newState()).isEqualTo("CONGESTION");

        var r2 = summariser.summarise(createBatch("LOW", 5), r1.newState())
                .toCompletableFuture().join();
        assertThat(r2.outputs()).hasSize(1);
        assertThat(r2.outputs().get(0)).containsEntry("from", "CONGESTION")
                .containsEntry("to", "RECOVERY");
        assertThat(r2.newState()).isEqualTo("RECOVERY");
    }

    @Test
    void firstMatchWins() {
        var config = Map.<String, Object>of(
                "initial", "A",
                "states", List.of("A", "B", "C"),
                "transitions", List.of(
                        Map.of("from", "A", "to", "B", "min-batch-size", 1),
                        Map.of("from", "A", "to", "C", "min-batch-size", 1)));

        var summariser = PhaseDetectSummariser.create(config, List.of());

        var batch = List.of(new LevelEvent<>(Map.<String, Object>of("x", 1), 100L, LEVEL, null));
        var result = summariser.summarise(batch, null).toCompletableFuture().join();

        assertThat(result.outputs().get(0)).containsEntry("to", "B");
    }

    @Test
    void selfTransition_producesOutput() {
        var config = Map.<String, Object>of(
                "initial", "A",
                "states", List.of("A"),
                "transitions", List.of(
                        Map.of("from", "A", "to", "A", "min-batch-size", 1)));

        var summariser = PhaseDetectSummariser.create(config, List.of());

        var batch = List.of(new LevelEvent<>(Map.<String, Object>of("x", 1), 100L, LEVEL, null));
        var result = summariser.summarise(batch, null).toCompletableFuture().join();

        assertThat(result.outputs()).hasSize(1);
        assertThat(result.outputs().get(0)).containsEntry("from", "A").containsEntry("to", "A");
    }

    @Test
    void rejectsMissingInitial() {
        assertThatThrownBy(() -> PhaseDetectSummariser.create(
                Map.of("transitions", List.of()), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingTransitions() {
        assertThatThrownBy(() -> PhaseDetectSummariser.create(
                Map.of("initial", "A"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
