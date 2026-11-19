package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.casehub.neocortex.memory.mood.MoodBaseline;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record MoodConfigSpec(
        @Nullable MoodBaseline baseline,
        @JsonAlias("decay-time-constant") @Nullable Duration decayTimeConstant,
        @JsonAlias("max-displacement") @Nullable Double maxDisplacement,
        @JsonAlias("mood-influence") @Nullable Double moodInfluence,
        @JsonAlias("stale-state-timeout") @Nullable Duration staleStateTimeout) {}
