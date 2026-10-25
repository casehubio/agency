package io.casehub.agency.stigmergy.llm;

import java.time.Duration;
import java.util.Objects;

public record LlmInvocationPolicy(
    Duration expectedDuration,
    Duration warnAfter,
    Duration errorAfter) {

    public LlmInvocationPolicy {
        Objects.requireNonNull(expectedDuration, "expectedDuration");
        Objects.requireNonNull(warnAfter, "warnAfter");
        Objects.requireNonNull(errorAfter, "errorAfter");
    }

    public static LlmInvocationPolicy of(Duration expectedDuration,
                                          Duration warnAfter,
                                          Duration errorAfter) {
        return new LlmInvocationPolicy(expectedDuration, warnAfter, errorAfter);
    }
}
