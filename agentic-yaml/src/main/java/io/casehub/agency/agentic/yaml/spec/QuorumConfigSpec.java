package io.casehub.agency.agentic.yaml.spec;

import io.casehub.api.model.OnThresholdReached;
import org.jspecify.annotations.Nullable;

public record QuorumConfigSpec(
        int instances,
        int required,
        @Nullable OnThresholdReached onThresholdReached,
        boolean allowSameAssignee) {}
