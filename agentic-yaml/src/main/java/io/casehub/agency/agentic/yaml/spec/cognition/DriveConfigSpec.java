package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

public record DriveConfigSpec(
        @JsonAlias("axis-weights") @Nullable Map<DriveAxis, Double> axisWeights,
        @JsonAlias("change-threshold") @Nullable Double changeThreshold,
        @JsonAlias("mood-pleasure-modulation") @Nullable Double moodPleasureModulation,
        @JsonAlias("mood-arousal-modulation") @Nullable Double moodArousalModulation,
        @JsonAlias("personality-modulation-strength") @Nullable Double personalityModulationStrength,
        @JsonAlias("max-intensity") @Nullable Double maxIntensity,
        @JsonAlias("min-intensity") @Nullable Double minIntensity,
        @JsonAlias("affiliation-decay-threshold") @Nullable Double affiliationDecayThreshold,
        @JsonAlias("affiliation-stale-duration") @Nullable Duration affiliationStaleDuration,
        @JsonAlias("autonomy-confidence-floor") @Nullable Double autonomyConfidenceFloor,
        @JsonAlias("narrative-modulation-strength") @Nullable Double narrativeModulationStrength) {}
