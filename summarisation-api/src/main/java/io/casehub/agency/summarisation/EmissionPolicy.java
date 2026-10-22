package io.casehub.agency.summarisation;

import org.jspecify.annotations.Nullable;

import java.util.List;

@FunctionalInterface
public interface EmissionPolicy<IN, S> {
    boolean shouldEmit(List<LevelEvent<IN>> buffered,
                       @Nullable S currentState,
                       long now);

    static <IN, S> EmissionPolicy<IN, S> anyOf(
            List<EmissionPolicy<IN, S>> policies) {
        return (buffered, state, now) ->
                policies.stream().anyMatch(p -> p.shouldEmit(buffered, state, now));
    }

    static <IN, S> EmissionPolicy<IN, S> allOf(
            List<EmissionPolicy<IN, S>> policies) {
        return (buffered, state, now) ->
                policies.stream().allMatch(p -> p.shouldEmit(buffered, state, now));
    }
}
