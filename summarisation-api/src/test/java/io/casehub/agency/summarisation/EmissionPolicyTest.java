package io.casehub.agency.summarisation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmissionPolicyTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);

    @Test
    void anyOf_emitsWhenAnyPolicySaysYes() {
        EmissionPolicy<String, Object> never = (b, s, t) -> false;
        EmissionPolicy<String, Object> always = (b, s, t) -> true;
        var composite = EmissionPolicy.anyOf(List.of(never, always));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(composite.shouldEmit(buffered, null, 10)).isTrue();
    }

    @Test
    void anyOf_doesNotEmitWhenAllSayNo() {
        EmissionPolicy<String, Object> never1 = (b, s, t) -> false;
        EmissionPolicy<String, Object> never2 = (b, s, t) -> false;
        var composite = EmissionPolicy.anyOf(List.of(never1, never2));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(composite.shouldEmit(buffered, null, 10)).isFalse();
    }

    @Test
    void allOf_emitsOnlyWhenAllSayYes() {
        EmissionPolicy<String, Object> always = (b, s, t) -> true;
        EmissionPolicy<String, Object> never = (b, s, t) -> false;
        var composite = EmissionPolicy.allOf(List.of(always, never));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(composite.shouldEmit(buffered, null, 10)).isFalse();
    }

    @Test
    void allOf_emitsWhenAllAgree() {
        EmissionPolicy<String, Object> a1 = (b, s, t) -> true;
        EmissionPolicy<String, Object> a2 = (b, s, t) -> true;
        var composite = EmissionPolicy.allOf(List.of(a1, a2));
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(composite.shouldEmit(buffered, null, 10)).isTrue();
    }

    @Test
    void shouldEmit_receivesStateAndTime() {
        EmissionPolicy<String, String> policy =
                (b, state, now) -> "go".equals(state) && now > 100;
        var buffered = List.of(new LevelEvent<>("a", 1, LEVEL, null));
        assertThat(policy.shouldEmit(buffered, "go", 200)).isTrue();
        assertThat(policy.shouldEmit(buffered, "stop", 200)).isFalse();
        assertThat(policy.shouldEmit(buffered, "go", 50)).isFalse();
    }
}
