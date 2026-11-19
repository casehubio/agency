package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

public record MultiSwarmCoordinationSpec(
        @JsonAlias("relevance-threshold") @Nullable Double relevanceThreshold,
        @JsonAlias("escalation-threshold") @Nullable Double escalationThreshold,
        @JsonAlias("convergence-threshold") @Nullable Double convergenceThreshold
) {}
