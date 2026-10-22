package io.casehub.agency.perception.observation.affordance;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record Principle(String text, @Nullable String category) {
    public Principle {
        Objects.requireNonNull(text);
    }
}
