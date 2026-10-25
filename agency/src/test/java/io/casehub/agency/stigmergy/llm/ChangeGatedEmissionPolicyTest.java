package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.summarisation.EmissionPolicy;
import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.WindowPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeGatedEmissionPolicyTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    record TestEvent(Set<String> changedKeys) {}

    Function<LevelEvent<TestEvent>, Set<String>> extractor =
        e -> e.payload().changedKeys();

    @Test
    void emits_whenWatchedKeyIntersectsChangedKeys() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.*"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("risk.score")), 1L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isTrue();
    }

    @Test
    void suppresses_whenNoWatchedKeyChanged() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.*"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("account.name")), 1L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isFalse();
    }

    @Test
    void suppresses_whenDelegateNotReady() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.*"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(5)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("risk.score")), 1L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isFalse();
    }

    @Test
    void prefixMatching_matchesDottedKeys() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("transaction.*"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("transaction.amount")), 1L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isTrue();
    }

    @Test
    void exactMatch_matchesNonWildcardKeys() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.score"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("risk.score")), 1L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isTrue();
    }

    @Test
    void exactMatch_rejectsPartialMatch() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.score"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(1)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("risk.score.delta")), 1L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isFalse();
    }

    @Test
    void unionAcrossAllEventsInBuffer() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.*"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(2)));
        var buffer = List.of(
            new LevelEvent<>(new TestEvent(Set.of("account.name")), 1L, LEVEL, null),
            new LevelEvent<>(new TestEvent(Set.of("risk.score")), 2L, LEVEL, null));
        assertThat(policy.shouldEmit(buffer, null, 100L)).isTrue();
    }

    @Test
    void emptyBuffer_doesNotEmit() {
        var policy = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.*"),
            extractor,
            EmissionPolicy.window(WindowPolicy.ofCount(1)));
        assertThat(policy.shouldEmit(List.of(), null, 100L)).isFalse();
    }
}
