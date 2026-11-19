package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

public record MethodSpec(
        String name,
        @Nullable String guard,
        @JsonAlias("estimated-cost") @Nullable Double estimatedCost,
        @JsonAlias("estimated-duration") @Nullable Duration estimatedDuration,
        List<TaskNodeSpec> tasks
) {}
