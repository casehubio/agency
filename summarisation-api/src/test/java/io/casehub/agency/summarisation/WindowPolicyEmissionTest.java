package io.casehub.agency.summarisation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WindowPolicyEmissionTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);

    @Test
    void emptyBuffer_returnsFalse() {
        var policy = new WindowPolicyEmission<String, Object>(WindowPolicy.ofCount(2));
        assertThat(policy.shouldEmit(List.of(), null, 10)).isFalse();
    }

    @Test
    void countThreshold_met() {
        var policy = new WindowPolicyEmission<String, Object>(WindowPolicy.ofCount(2));
        var buffered = List.of(
                new LevelEvent<>("a", 1, LEVEL, null),
                new LevelEvent<>("b", 2, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, null, 10)).isTrue();
    }

    @Test
    void countThreshold_notMet() {
        var policy = new WindowPolicyEmission<String, Object>(WindowPolicy.ofCount(3));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, null, 10)).isFalse();
    }

    @Test
    void ageThreshold_met() {
        var policy = new WindowPolicyEmission<String, Object>(WindowPolicy.ofAge(100));
        var buffered = List.of(new LevelEvent<>("a", 5, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, null, 110)).isTrue();
    }

    @Test
    void ageThreshold_notMet() {
        var policy = new WindowPolicyEmission<String, Object>(WindowPolicy.ofAge(100));
        var buffered = List.of(new LevelEvent<>("a", 50, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, null, 60)).isFalse();
    }

    @Test
    void ignoresState() {
        var policy = new WindowPolicyEmission<String, Object>(WindowPolicy.ofCount(1));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, "any-state", 10)).isTrue();
    }
}
