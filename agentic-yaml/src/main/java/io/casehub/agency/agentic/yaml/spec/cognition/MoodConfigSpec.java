package io.casehub.agency.agentic.yaml.spec.cognition;

import io.casehub.neocortex.memory.mood.MoodBaseline;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record MoodConfigSpec(
        @Nullable MoodBaseline baseline,
        @Nullable Duration decayTimeConstant,
        @Nullable Double maxDisplacement,
        @Nullable Double moodInfluence,
        @Nullable Duration staleStateTimeout) {}
