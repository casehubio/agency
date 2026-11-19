package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record NarrativeSynthesisGateSpec(
        @JsonAlias("min-new-reflections") @Nullable Integer minNewReflections,
        @JsonAlias("novelty-threshold") @Nullable Double noveltyThreshold,
        @JsonAlias("quiet-period-bypass") @Nullable Duration quietPeriodBypass) {}
