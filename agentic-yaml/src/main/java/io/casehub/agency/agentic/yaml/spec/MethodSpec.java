package io.casehub.agency.core.yaml.spec;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

public record MethodSpec(
        String name,
        @Nullable String guard,
        @Nullable Double estimatedCost,
        @Nullable Duration estimatedDuration,
        List<TaskNodeSpec> tasks
) {}
