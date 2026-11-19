package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.casehub.neocortex.cognition.relationship.StageTier;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record RelationshipStageConfigSpec(
        @Nullable List<StageTier> tiers,
        @JsonAlias("decay-rate") @Nullable Double decayRate,
        @JsonAlias("positive-weight") @Nullable Double positiveWeight,
        @JsonAlias("negative-weight") @Nullable Double negativeWeight) {}
