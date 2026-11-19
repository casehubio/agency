package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ConvergencePolicySpec.Structural.class, name = "structural"),
        @Type(value = ConvergencePolicySpec.CommonGroundRatio.class, name = "common-ground-ratio"),
        @Type(value = ConvergencePolicySpec.Composite.class, name = "composite")
})
public sealed interface ConvergencePolicySpec {
    record Structural(@JsonAlias("similarity-threshold") double similarityThreshold, @JsonAlias("stale-rounds") int staleRounds) implements ConvergencePolicySpec {}
    record CommonGroundRatio(@JsonAlias("consensus-threshold") double consensusThreshold, @JsonAlias("deadlock-dispute-ratio") double deadlockDisputeRatio) implements ConvergencePolicySpec {}
    record Composite(List<ConvergencePolicySpec> policies) implements ConvergencePolicySpec {}
}
