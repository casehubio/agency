package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.casehub.api.model.OnThresholdReached;
import org.jspecify.annotations.Nullable;

public record QuorumConfigSpec(
        int instances,
        int required,
        @JsonAlias("on-threshold-reached") @Nullable OnThresholdReached onThresholdReached,
        @JsonAlias("allow-same-assignee") boolean allowSameAssignee) {}
